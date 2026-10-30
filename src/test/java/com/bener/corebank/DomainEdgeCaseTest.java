package com.bener.corebank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bener.corebank.account.Account;
import com.bener.corebank.account.AccountRepository;
import com.bener.corebank.account.AccountService;
import com.bener.corebank.customer.Customer;
import com.bener.corebank.customer.CustomerRepository;
import com.bener.corebank.outbox.OutboxRepository;
import com.bener.corebank.payment.BillPayment;
import com.bener.corebank.payment.BillPaymentRepository;
import com.bener.corebank.payment.BillPaymentService;
import com.bener.corebank.payment.PaymentGateway;
import com.bener.corebank.shared.DomainException;
import com.bener.corebank.transfer.LedgerEntryRepository;
import com.bener.corebank.transfer.Transfer;
import com.bener.corebank.transfer.TransferRepository;
import com.bener.corebank.transfer.TransferService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class DomainEdgeCaseTest {
    private static final BigDecimal ONE = new BigDecimal("1.00");

    @Test
    void accountServiceRejectsMissingCustomersAccountsAndInvalidPages() {
        CustomerRepository customers = mock(CustomerRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        AccountService service = new AccountService(customers, accounts);
        UUID missing = UUID.randomUUID();
        assertCode(() -> service.openAccount(missing), "NOT_FOUND");
        assertCode(() -> service.getAccount(missing), "NOT_FOUND");
        assertCode(() -> service.listAccounts(-1, 20), "INVALID_PAGE");
        assertCode(() -> service.listAccounts(0, 0), "INVALID_PAGE");
        assertCode(() -> service.listAccounts(0, 101), "INVALID_PAGE");
        assertCode(() -> service.listAccounts(missing, -1, 20), "INVALID_PAGE");
        assertCode(() -> service.listAccounts(missing, 0, 0), "INVALID_PAGE");
        assertCode(() -> service.listAccounts(missing, 0, 101), "INVALID_PAGE");
        assertCode(() -> service.payees(missing, "GBP"), "INVALID_CURRENCY");

        Customer customer = new Customer("Test");
        Account account = new Account(customer);
        when(customers.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(customers.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accounts.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accounts.findById(account.getId())).thenReturn(Optional.of(account));
        when(accounts.findAllByOrderByCreatedAtDesc(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(account)));
        when(accounts.findAllByCustomer_IdOrderByCreatedAtDesc(eq(customer.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(account)));
        when(accounts.findAllByCurrencyAndCustomer_IdNotOrderByCreatedAtDesc("TRY", customer.getId()))
                .thenReturn(List.of(account));
        assertThat(service.createCustomer("Test").getFullName()).isEqualTo("Test");
        assertThat(service.openAccount(customer.getId()).getCurrency()).isEqualTo("TRY");
        assertThat(service.getAccount(account.getId())).isEqualTo(account);
        assertThat(service.listAccounts(0, 20).getContent()).singleElement()
                .extracting(AccountService.AccountOverview::customerName).isEqualTo("Test");
        assertThat(service.listAccounts(customer.getId(), 0, 20).getContent()).singleElement()
                .extracting(AccountService.AccountOverview::currency).isEqualTo("TRY");
        assertThat(service.payees(customer.getId(), "TRY")).singleElement()
                .extracting(AccountService.AccountOverview::customerName).isEqualTo("Test");
    }

    @Test
    void accountReservationAndDebitRejectOverdrawAndUnderflow() {
        Account account = funded("5.00");
        assertCode(() -> account.debit(new BigDecimal("6.00")), "INSUFFICIENT_FUNDS");
        assertCode(() -> account.reserve(new BigDecimal("6.00")), "INSUFFICIENT_FUNDS");
        account.reserve(new BigDecimal("3.00"));
        assertThat(account.getAvailableBalance()).isEqualByComparingTo("2.00");
        assertThatThrownBy(() -> account.release(new BigDecimal("4.00"))).isInstanceOf(IllegalStateException.class);
        account.release(new BigDecimal("3.00"));
        account.debit(new BigDecimal("2.00"));
        assertThat(account.getBalance()).isEqualByComparingTo("3.00");
    }

    @Test
    void transferRejectsInvalidKeysAccountsAndAmountsBeforeWriting() {
        TransferService service = transferService(mock(AccountRepository.class), mock(TransferRepository.class));
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        for (String key : new String[] {null, "", " ", "x".repeat(101)}) {
            assertCode(() -> service.transfer(source, destination, ONE, key), "INVALID_KEY");
        }
        assertCode(() -> service.transfer(source, source, ONE, "same"), "SAME_ACCOUNT");
        for (BigDecimal amount : new BigDecimal[] {null, BigDecimal.ZERO, BigDecimal.ONE.negate(),
                new BigDecimal("1.001"), new BigDecimal("123456789012345678.00")}) {
            assertCode(() -> service.transfer(source, destination, amount, "amount"), "INVALID_AMOUNT");
        }
        assertCode(() -> service.transfer(source, destination, ONE, "missing"), "NOT_FOUND");
        assertCode(() -> service.getTransfer(source), "NOT_FOUND");
    }

    @Test
    void transferDetectsCurrencyAndIdempotencyConflicts() {
        AccountRepository accounts = mock(AccountRepository.class);
        TransferRepository transfers = mock(TransferRepository.class);
        TransferService service = transferService(accounts, transfers);
        Account first = funded("10.00");
        Account second = funded("0.00");
        UUID source = first.getId();
        UUID destination = second.getId();
        when(accounts.findByIdForUpdate(source)).thenReturn(Optional.of(first));
        when(accounts.findByIdForUpdate(destination)).thenReturn(Optional.of(second));
        org.springframework.test.util.ReflectionTestUtils.setField(second, "currency", "USD");
        assertCode(() -> service.transfer(source, destination, ONE, "currency"), "CURRENCY_MISMATCH");
        org.springframework.test.util.ReflectionTestUtils.setField(second, "currency", "TRY");

        Transfer existing = new Transfer("repeat", first, second, ONE);
        when(transfers.findByIdempotencyKey("repeat")).thenReturn(Optional.of(existing));
        assertThat(service.transfer(source, destination, ONE, "repeat")).isEqualTo(existing);
        assertCode(() -> service.transfer(destination, source, ONE, "repeat"), "IDEMPOTENCY_CONFLICT");
        assertCode(() -> service.transfer(source, UUID.randomUUID(), ONE, "repeat"), "IDEMPOTENCY_CONFLICT");
        assertCode(() -> service.transfer(source, destination, new BigDecimal("2.00"), "repeat"), "IDEMPOTENCY_CONFLICT");
        when(transfers.findById(source)).thenReturn(Optional.of(existing));
        assertThat(service.getTransfer(source)).isEqualTo(existing);

        when(transfers.findByIdempotencyKey("late")).thenReturn(Optional.empty(), Optional.of(existing));
        assertThat(service.transfer(source, destination, ONE, "late")).isEqualTo(existing);
        assertThat(existing.getIdempotencyKey()).isEqualTo("repeat");
    }

    @Test
    void paymentValidatesRequestAndReturnsStoredIdempotentResult() {
        BillPaymentRepository payments = mock(BillPaymentRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        BillPaymentService service = new BillPaymentService(payments, accounts, mock(TransferService.class));
        UUID id = UUID.randomUUID();
        for (String key : new String[] {null, "", " ", "x".repeat(101)}) {
            assertCode(() -> service.start(id, ONE, key, "APPROVE"), "INVALID_KEY");
        }
        for (BigDecimal amount : new BigDecimal[] {null, BigDecimal.ZERO, BigDecimal.ONE.negate(),
                new BigDecimal("1.001"), new BigDecimal("123456789012345678.00")}) {
            assertCode(() -> service.start(id, amount, "key", "APPROVE"), "INVALID_AMOUNT");
        }
        assertCode(() -> service.start(id, ONE, "scenario", null), "INVALID_SCENARIO");
        assertCode(() -> service.start(id, ONE, "scenario", "MAYBE"), "INVALID_SCENARIO");
        assertCode(() -> service.start(id, ONE, "missing", "APPROVE"), "NOT_FOUND");
        assertCode(() -> service.get(id), "NOT_FOUND");
        assertCode(() -> service.applyOutcome(id, PaymentGateway.Outcome.UNKNOWN), "NOT_FOUND");

        Account account = funded("10.00");
        BillPayment existing = new BillPayment("repeat", account, ONE, "APPROVE");
        when(payments.findByIdempotencyKey("repeat")).thenReturn(Optional.of(existing));
        assertThat(service.start(account.getId(), ONE, "repeat", "APPROVE")).isEqualTo(existing);
        assertCode(() -> service.start(UUID.randomUUID(), ONE, "repeat", "APPROVE"), "IDEMPOTENCY_CONFLICT");
        assertCode(() -> service.start(account.getId(), new BigDecimal("2.00"), "repeat", "APPROVE"), "IDEMPOTENCY_CONFLICT");
        assertCode(() -> service.start(account.getId(), ONE, "repeat", "REJECT"), "IDEMPOTENCY_CONFLICT");
        when(payments.findById(id)).thenReturn(Optional.of(existing));
        assertThat(service.get(id)).isEqualTo(existing);
        assertThat(existing.getIdempotencyKey()).isEqualTo("repeat");
        assertThat(existing.getUpdatedAt()).isNotNull();

        when(accounts.findByIdForUpdate(account.getId())).thenReturn(Optional.of(account));
        when(payments.findByIdempotencyKey("late")).thenReturn(Optional.empty(), Optional.of(existing));
        assertThat(service.start(account.getId(), ONE, "late", "APPROVE")).isEqualTo(existing);
        when(payments.findByIdForUpdate(id)).thenReturn(Optional.of(existing));
        existing.status("FAILED");
        service.applyOutcome(id, PaymentGateway.Outcome.UNKNOWN);
        assertThat(existing.getStatus()).isEqualTo("FAILED");
        existing.status("SUCCEEDED");
        service.applyOutcome(id, PaymentGateway.Outcome.UNKNOWN);
        assertThat(existing.getStatus()).isEqualTo("SUCCEEDED");
    }

    private static TransferService transferService(AccountRepository accounts, TransferRepository transfers) {
        return new TransferService(accounts, transfers, mock(LedgerEntryRepository.class), mock(OutboxRepository.class));
    }

    private static Account funded(String amount) {
        Account account = new Account(new Customer("Account holder"));
        account.credit(new BigDecimal(amount));
        return account;
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, String code) {
        assertThatThrownBy(action).isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).getCode()).isEqualTo(code);
    }
}
