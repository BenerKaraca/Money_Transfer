package com.bener.corebank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bener.corebank.account.Account;
import com.bener.corebank.account.AccountRepository;
import com.bener.corebank.account.AccountService;
import com.bener.corebank.customer.Customer;
import com.bener.corebank.payment.BillPayment;
import com.bener.corebank.payment.BillPaymentService;
import com.bener.corebank.payment.PaymentSagaOrchestrator;
import com.bener.corebank.shared.DomainException;
import com.bener.corebank.transfer.TransferRepository;
import com.bener.corebank.transfer.TransferService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class BillPaymentSagaTest {
    @Autowired AccountService accountService;
    @Autowired AccountRepository accounts;
    @Autowired BillPaymentService payments;
    @Autowired PaymentSagaOrchestrator saga;
    @Autowired TransferService transfers;
    @Autowired TransferRepository transferRepository;

    @Test
    void approvedPaymentReservesThenSettlesExactlyOnce() {
        Account account = funded("1000.00");
        BigDecimal clearingBefore = balance(BillPaymentService.CLEARING_ACCOUNT);
        BillPayment payment = payments.start(account.getId(), money("300.00"), "payment-approved", "APPROVE");
        assertThat(available(account)).isEqualByComparingTo("700.00");
        assertThatThrownBy(() -> transfers.transfer(account.getId(), BillPaymentService.CLEARING_ACCOUNT,
                money("800.00"), "over-reservation"))
                .isInstanceOf(DomainException.class);

        assertThat(saga.reconcile(payment.getId()).getStatus()).isEqualTo("SUCCEEDED");
        assertThat(saga.reconcile(payment.getId()).getStatus()).isEqualTo("SUCCEEDED");
        assertThat(balance(account.getId())).isEqualByComparingTo("700.00");
        assertThat(available(account)).isEqualByComparingTo("700.00");
        assertThat(balance(BillPaymentService.CLEARING_ACCOUNT).subtract(clearingBefore))
                .isEqualByComparingTo("300.00");
        assertThat(transferRepository.findByIdempotencyKey("bill-" + payment.getId())).isPresent();
    }

    @Test
    void rejectedPaymentCompensatesReservation() {
        Account account = funded("1000.00");
        BillPayment payment = payments.start(account.getId(), money("250.00"), "payment-rejected", "REJECT");
        assertThat(saga.reconcile(payment.getId()).getStatus()).isEqualTo("FAILED");
        assertThat(balance(account.getId())).isEqualByComparingTo("1000.00");
        assertThat(available(account)).isEqualByComparingTo("1000.00");
    }

    @Test
    void unknownResponseKeepsHoldUntilReconciliation() {
        Account account = funded("1000.00");
        BillPayment payment = payments.start(account.getId(), money("400.00"), "payment-timeout", "TIMEOUT_ONCE");
        assertThat(saga.reconcile(payment.getId()).getStatus()).isEqualTo("UNKNOWN");
        assertThat(balance(account.getId())).isEqualByComparingTo("1000.00");
        assertThat(available(account)).isEqualByComparingTo("600.00");
        assertThat(saga.reconcile(payment.getId()).getStatus()).isEqualTo("SUCCEEDED");
        assertThat(balance(account.getId())).isEqualByComparingTo("600.00");
    }

    private Account funded(String amount) {
        Customer customer = accountService.createCustomer("Bill payment example");
        Account account = accountService.openAccount(customer.getId());
        account.credit(money(amount));
        return accounts.saveAndFlush(account);
    }

    private BigDecimal balance(java.util.UUID id) { return accounts.findById(id).orElseThrow().getBalance(); }
    private BigDecimal available(Account account) {
        return accounts.findById(account.getId()).orElseThrow().getAvailableBalance();
    }
    private static BigDecimal money(String amount) { return new BigDecimal(amount); }
}
