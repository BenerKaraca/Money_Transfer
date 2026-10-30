package com.bener.corebank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.bener.corebank.account.Account;
import com.bener.corebank.account.AccountRepository;
import com.bener.corebank.account.AccountService;
import com.bener.corebank.customer.Customer;
import com.bener.corebank.fx.FxQuoteClient;
import com.bener.corebank.fx.FxTradeController;
import com.bener.corebank.fx.FxTradeService;
import com.bener.corebank.payment.BillPaymentService;
import com.bener.corebank.report.SpendingHistoryService;
import com.bener.corebank.security.BankAuthorization;
import com.bener.corebank.shared.DomainException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class FxTradeIntegrationTest {
    @Autowired AccountService accountService;
    @Autowired AccountRepository accounts;
    @Autowired FxTradeService trades;
    @Autowired BillPaymentService payments;
    @Autowired SpendingHistoryService spending;
    @MockitoBean FxQuoteClient quotes;

    @Test void buysAndSellsCurrencyWithAtomicBalancesAndIdempotency() {
        Customer customer = accountService.createCustomer("FX Trader");
        Account tryAccount = funded(customer, "TRY", "1000.00");
        Account eurAccount = funded(customer, "EUR", "0.00");
        UUID buyId = UUID.randomUUID();
        when(quotes.get(buyId)).thenReturn(quote(buyId, "TRY", "EUR", "500.00", "10.00", "0.02000000"));
        var buy = trades.execute(tryAccount.getId(), eurAccount.getId(), buyId, "fx-buy-1");
        assertThat(buy.sourceAmount()).isEqualByComparingTo("500.00");
        assertThat(balance(tryAccount)).isEqualByComparingTo("500.00");
        assertThat(balance(eurAccount)).isEqualByComparingTo("10.00");
        assertThat(trades.execute(tryAccount.getId(), eurAccount.getId(), buyId, "fx-buy-1").id()).isEqualTo(buy.id());
        assertThat(trades.list(tryAccount.getId())).extracting(FxTradeService.Trade::id).contains(buy.id());

        UUID sellId = UUID.randomUUID();
        when(quotes.get(sellId)).thenReturn(quote(sellId, "EUR", "TRY", "5.00", "250.00", "50.00000000"));
        trades.execute(eurAccount.getId(), tryAccount.getId(), sellId, "fx-sell-1");
        assertThat(balance(tryAccount)).isEqualByComparingTo("750.00");
        assertThat(balance(eurAccount)).isEqualByComparingTo("5.00");
        var history = spending.history(customer.getId());
        assertThat(history.items()).extracting(SpendingHistoryService.Item::type).containsOnly("FX_EXCHANGE");
        assertThat(history.totalsByCurrency()).containsEntry("TRY", new BigDecimal("500.00"))
                .containsEntry("EUR", new BigDecimal("5.00"));

        BankAuthorization authorization = org.mockito.Mockito.mock(BankAuthorization.class);
        var controller = new FxTradeController(trades, authorization);
        assertThat(controller.list(tryAccount.getId())).hasSize(2);
        UUID controllerId = UUID.randomUUID();
        when(quotes.get(controllerId)).thenReturn(quote(controllerId, "TRY", "EUR", "100.00", "2.00", "0.02000000"));
        var response = controller.execute("fx-controller-1", new FxTradeController.TradeRequest(
                tryAccount.getId(), eurAccount.getId(), controllerId));
        assertThat(response.getStatusCode().value()).isEqualTo(201);
    }

    @Test void rejectsBadKeysAccountsQuotesAndInsufficientFunds() {
        Customer customer = accountService.createCustomer("FX Validation");
        Account tryAccount = funded(customer, "TRY", "20.00");
        Account eurAccount = funded(customer, "EUR", "0.00");
        Account usdAccount = funded(customer, "USD", "0.00");
        Account foreign = funded(accountService.createCustomer("Other Trader"), "EUR", "0.00");
        UUID id = UUID.randomUUID();
        when(quotes.get(id)).thenReturn(quote(id, "TRY", "EUR", "10.00", "0.20", "0.02000000"));

        assertThatThrownBy(() -> trades.execute(tryAccount.getId(), eurAccount.getId(), id, null)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> trades.execute(tryAccount.getId(), eurAccount.getId(), id, " ")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> trades.execute(tryAccount.getId(), eurAccount.getId(), id, "x".repeat(101))).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> trades.execute(tryAccount.getId(), tryAccount.getId(), id, "fx-same")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> trades.execute(tryAccount.getId(), eurAccount.getId(), UUID.randomUUID(), "fx-null-quote")).isInstanceOf(DomainException.class);
        UUID expired = UUID.randomUUID();
        when(quotes.get(expired)).thenReturn(new FxQuoteClient.Quote(expired, "TRY", "EUR", new BigDecimal("10.00"),
                new BigDecimal("0.20"), new BigDecimal("0.02"), LocalDate.now(), Instant.EPOCH));
        assertThatThrownBy(() -> trades.execute(tryAccount.getId(), eurAccount.getId(), expired, "fx-expired")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> trades.execute(UUID.randomUUID(), eurAccount.getId(), id, "fx-missing")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> trades.execute(tryAccount.getId(), foreign.getId(), id, "fx-customer")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> trades.execute(usdAccount.getId(), eurAccount.getId(), id, "fx-source-currency")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> trades.execute(tryAccount.getId(), usdAccount.getId(), id, "fx-target-currency")).isInstanceOf(DomainException.class);
        UUID tooMuch = UUID.randomUUID();
        when(quotes.get(tooMuch)).thenReturn(quote(tooMuch, "TRY", "EUR", "30.00", "0.60", "0.02000000"));
        assertThatThrownBy(() -> trades.execute(tryAccount.getId(), eurAccount.getId(), tooMuch, "fx-too-much")).isInstanceOf(DomainException.class);
        assertThat(balance(tryAccount)).isEqualByComparingTo("20.00");
        assertThat(balance(eurAccount)).isEqualByComparingTo("0.00");

        trades.execute(tryAccount.getId(), eurAccount.getId(), id, "fx-conflict");
        assertThatThrownBy(() -> trades.execute(usdAccount.getId(), eurAccount.getId(), id, "fx-conflict")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> trades.execute(tryAccount.getId(), usdAccount.getId(), id, "fx-conflict")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> trades.execute(tryAccount.getId(), eurAccount.getId(), tooMuch, "fx-conflict")).isInstanceOf(DomainException.class);
    }

    @Test void accountOpeningSupportsThreeCurrenciesOnly() {
        Customer customer = accountService.createCustomer("Currency customer");
        assertThat(accountService.openAccount(customer.getId(), "USD").getCurrency()).isEqualTo("USD");
        assertThatThrownBy(() -> accountService.openAccount(customer.getId(), "GBP")).isInstanceOf(DomainException.class);
        Account tryAccount = funded(customer, "TRY", "20.00");
        Account eurAccount = funded(customer, "EUR", "10.00");
        assertThatThrownBy(() -> payments.start(eurAccount.getId(), new BigDecimal("1.00"),
                "fx-payment-eur", "APPROVE")).isInstanceOf(DomainException.class);
        payments.start(tryAccount.getId(), new BigDecimal("1.00"), "fx-payment-try", "APPROVE");
        assertThat(payments.list(tryAccount.getId())).hasSize(1);
    }

    @Test void concurrentRetryUsesOneTradeAfterRowLock() throws Exception {
        Customer customer = accountService.createCustomer("Concurrent FX Trader");
        Account source = funded(customer, "TRY", "100.00");
        Account target = funded(customer, "EUR", "0.00");
        UUID quoteId = UUID.randomUUID();
        CountDownLatch reachedQuote = new CountDownLatch(2);
        when(quotes.get(quoteId)).thenAnswer(invocation -> {
            reachedQuote.countDown();
            if (!reachedQuote.await(5, TimeUnit.SECONDS)) throw new AssertionError("Quotes did not overlap");
            return quote(quoteId, "TRY", "EUR", "10.00", "0.20", "0.02000000");
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> trades.execute(source.getId(), target.getId(), quoteId, "fx-concurrent-retry"));
            var second = executor.submit(() -> trades.execute(source.getId(), target.getId(), quoteId, "fx-concurrent-retry"));
            assertThat(first.get().id()).isEqualTo(second.get().id());
        }
        assertThat(balance(source)).isEqualByComparingTo("90.00");
        assertThat(balance(target)).isEqualByComparingTo("0.20");
    }

    private Account funded(Customer customer, String currency, String amount) {
        Account account = accountService.openAccount(customer.getId(), currency);
        account.credit(new BigDecimal(amount));
        return accounts.saveAndFlush(account);
    }

    private BigDecimal balance(Account account) {
        return accounts.findById(account.getId()).orElseThrow().getBalance();
    }

    private FxQuoteClient.Quote quote(UUID id, String source, String target, String sourceAmount, String targetAmount, String rate) {
        return new FxQuoteClient.Quote(id, source, target, new BigDecimal(sourceAmount), new BigDecimal(targetAmount),
                new BigDecimal(rate), LocalDate.parse("2026-10-27"), Instant.now().plusSeconds(60));
    }
}
