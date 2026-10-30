package com.bener.corebank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bener.corebank.account.Account;
import com.bener.corebank.account.AccountRepository;
import com.bener.corebank.account.AccountService;
import com.bener.corebank.customer.Customer;
import com.bener.corebank.customer.CustomerRepository;
import com.bener.corebank.shared.DomainException;
import com.bener.corebank.outbox.OutboxRepository;
import com.bener.corebank.payment.BillPaymentRepository;
import com.bener.corebank.payment.BillPaymentService;
import com.bener.corebank.transfer.LedgerEntryRepository;
import com.bener.corebank.transfer.Transfer;
import com.bener.corebank.transfer.TransferRepository;
import com.bener.corebank.transfer.TransferService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class TransferIntegrationTest {
    @Autowired AccountService accountService;
    @Autowired AccountRepository accounts;
    @Autowired CustomerRepository customers;
    @Autowired TransferService transferService;
    @Autowired TransferRepository transfers;
    @Autowired LedgerEntryRepository ledger;
    @Autowired OutboxRepository outbox;
    @Autowired BillPaymentRepository payments;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM fx_trades");
        outbox.deleteAll();
        ledger.deleteAll();
        transfers.deleteAll();
        payments.deleteAll();
        accounts.findAll().stream().filter(a -> !a.getId().equals(BillPaymentService.CLEARING_ACCOUNT))
                .forEach(accounts::delete);
        customers.findAll().stream()
                .filter(c -> !c.getId().equals(UUID.fromString("ffffffff-ffff-ffff-ffff-fffffffffff0")))
                .forEach(customers::delete);
    }

    @Test
    void transferUpdatesBothBalancesAndCreatesBalancedLedger() {
        Account sender = fundedAccount("Sender", "1000.00");
        Account receiver = emptyAccount("Receiver");

        Transfer result = transferService.transfer(sender.getId(), receiver.getId(), money("200.00"), "one");

        assertThat(balance(sender)).isEqualByComparingTo("800.00");
        assertThat(balance(receiver)).isEqualByComparingTo("200.00");
        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        var entries = ledger.findByTransferId(result.getId());
        assertThat(entries).hasSize(2);
        assertThat(entries.stream().map(e -> e.getDirection()).toList()).containsExactlyInAnyOrder("DEBIT", "CREDIT");
        assertThat(entries.stream().map(e -> e.getAmount()).toList()).allMatch(a -> a.compareTo(money("200.00")) == 0);
        assertThat(outbox.count()).isEqualTo(1);
    }

    @Test
    void repeatedKeyDoesNotMoveMoneyTwiceAndCannotChangePayload() {
        Account sender = fundedAccount("Sender", "1000.00");
        Account receiver = emptyAccount("Receiver");
        UUID id = transferService.transfer(sender.getId(), receiver.getId(), money("200.00"), "retry").getId();

        assertThat(transferService.transfer(sender.getId(), receiver.getId(), money("200.00"), "retry").getId()).isEqualTo(id);
        assertThatThrownBy(() -> transferService.transfer(sender.getId(), receiver.getId(), money("300.00"), "retry"))
                .isInstanceOf(DomainException.class).hasMessageContaining("farklı");
        assertThat(balance(sender)).isEqualByComparingTo("800.00");
        assertThat(transfers.count()).isEqualTo(1);
        assertThat(ledger.count()).isEqualTo(2);
        assertThat(outbox.count()).isEqualTo(1);
    }

    @Test
    void insufficientFundsRollsBackFinancialAndOutboxWrites() {
        Account sender = fundedAccount("Sender", "100.00");
        Account receiver = emptyAccount("Receiver");

        assertThatThrownBy(() -> transferService.transfer(sender.getId(), receiver.getId(), money("200.00"), "too-much"))
                .isInstanceOf(DomainException.class);

        assertThat(balance(sender)).isEqualByComparingTo("100.00");
        assertThat(balance(receiver)).isEqualByComparingTo("0.00");
        assertThat(transfers.count()).isZero();
        assertThat(ledger.count()).isZero();
        assertThat(outbox.count()).isZero();
    }

    @Test
    void concurrentWithdrawalsCannotOverdrawAccount() throws Exception {
        Account sender = fundedAccount("Sender", "1000.00");
        Account firstReceiver = emptyAccount("First");
        Account secondReceiver = emptyAccount("Second");
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results = new ArrayList<>();
            results.add(executor.submit(() -> attempt(start, sender.getId(), firstReceiver.getId(), "parallel-1")));
            results.add(executor.submit(() -> attempt(start, sender.getId(), secondReceiver.getId(), "parallel-2")));
            start.countDown();
            int succeeded = (results.get(0).get() ? 1 : 0) + (results.get(1).get() ? 1 : 0);
            assertThat(succeeded).isEqualTo(1);
        }
        assertThat(transfers.count()).isEqualTo(1);
        assertThat(ledger.count()).isEqualTo(2);
        assertThat(balance(sender)).isEqualByComparingTo("200.00");
        assertThat(balance(firstReceiver).add(balance(secondReceiver))).isEqualByComparingTo("800.00");
    }

    private boolean attempt(CountDownLatch start, UUID source, UUID destination, String key) throws InterruptedException {
        start.await();
        try {
            transferService.transfer(source, destination, money("800.00"), key);
            return true;
        } catch (DomainException exception) {
            return false;
        }
    }

    private Account emptyAccount(String name) {
        Customer customer = accountService.createCustomer(name);
        return accountService.openAccount(customer.getId());
    }

    private Account fundedAccount(String name, String amount) {
        Account account = emptyAccount(name);
        account.credit(money(amount)); // Integration fixture: opening balance before tested transfers.
        return accounts.saveAndFlush(account);
    }

    private BigDecimal balance(Account account) {
        return accounts.findById(account.getId()).orElseThrow().getBalance();
    }

    private static BigDecimal money(String amount) { return new BigDecimal(amount); }
}
