package com.bener.corebank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bener.corebank.account.Account;
import com.bener.corebank.account.AccountService;
import com.bener.corebank.api.BankController;
import com.bener.corebank.customer.Customer;
import com.bener.corebank.payment.BillPayment;
import com.bener.corebank.payment.BillPaymentController;
import com.bener.corebank.payment.BillPaymentService;
import com.bener.corebank.payment.PaymentSagaOrchestrator;
import com.bener.corebank.report.AccountReportController;
import com.bener.corebank.report.AccountReportService;
import com.bener.corebank.shared.ApiExceptionHandler;
import com.bener.corebank.shared.DomainException;
import com.bener.corebank.shared.RequestIdInterceptor;
import com.bener.corebank.transfer.LedgerEntry;
import com.bener.corebank.transfer.LedgerEntryRepository;
import com.bener.corebank.transfer.Transfer;
import com.bener.corebank.transfer.TransferService;
import com.bener.corebank.security.BankAuthorization;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.data.domain.PageImpl;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AdapterCoverageTest {
    @Test
    void bankEndpointsMapServiceResultsAndLedger() {
        AccountService accounts = mock(AccountService.class);
        TransferService transfers = mock(TransferService.class);
        LedgerEntryRepository ledger = mock(LedgerEntryRepository.class);
        BankAuthorization authorization = mock(BankAuthorization.class);
        BankController controller = new BankController(accounts, transfers, ledger, authorization);
        Customer customer = new Customer("Ada");
        Account source = new Account(customer);
        Account destination = new Account(customer);
        Transfer transfer = new Transfer("key", source, destination, new BigDecimal("2.00"));
        LedgerEntry entry = new LedgerEntry(transfer, source, "DEBIT", new BigDecimal("2.00"));
        when(accounts.createCustomer("Ada")).thenReturn(customer);
        when(accounts.openAccount(customer.getId(), "TRY")).thenReturn(source);
        when(accounts.getAccount(source.getId())).thenReturn(source);
        when(authorization.customerId()).thenReturn(customer.getId());
        when(accounts.listAccounts(customer.getId(), 0, 20)).thenReturn(new PageImpl<>(List.of()));
        when(accounts.payees(customer.getId(), "TRY")).thenReturn(List.of());
        when(transfers.transfer(eq(source.getId()), eq(destination.getId()), any(), eq("key"))).thenReturn(transfer);
        when(transfers.getTransfer(transfer.getId())).thenReturn(transfer);
        when(ledger.findByAccountIdOrderByCreatedAtDesc(source.getId())).thenReturn(List.of(entry));

        assertThat(controller.createCustomer(new BankController.CreateCustomer("Ada")).getBody().fullName()).isEqualTo("Ada");
        assertThat(controller.openAccount(customer.getId(), "TRY").getBody().id()).isEqualTo(source.getId());
        assertThat(controller.getAccount(source.getId()).customerId()).isEqualTo(customer.getId());
        assertThat(controller.listAccounts(0, 20).getTotalElements()).isZero();
        assertThat(controller.payees("TRY")).isEmpty();
        assertThat(controller.ledger(source.getId())).singleElement().extracting(BankController.LedgerResponse::direction).isEqualTo("DEBIT");
        var request = new BankController.TransferRequest(source.getId(), destination.getId(), new BigDecimal("2.00"));
        assertThat(controller.transfer("key", request).getBody().id()).isEqualTo(transfer.getId());
        assertThat(controller.getTransfer(transfer.getId()).status()).isEqualTo("COMPLETED");
    }

    @Test
    void paymentAndReportEndpointsMapResults() {
        BillPaymentService service = mock(BillPaymentService.class);
        PaymentSagaOrchestrator saga = mock(PaymentSagaOrchestrator.class);
        BankAuthorization authorization = mock(BankAuthorization.class);
        BillPaymentController controller = new BillPaymentController(service, saga, authorization);
        BillPayment payment = mock(BillPayment.class);
        UUID id = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(payment.getId()).thenReturn(id);
        when(payment.getAccountId()).thenReturn(accountId);
        when(payment.getAmount()).thenReturn(BigDecimal.ONE);
        when(payment.getStatus()).thenReturn("PENDING");
        when(service.start(accountId, BigDecimal.ONE, "key", "APPROVE")).thenReturn(payment);
        when(service.get(id)).thenReturn(payment);
        when(service.list(accountId)).thenReturn(List.of(payment));
        when(saga.reconcile(id)).thenReturn(payment);

        assertThat(controller.start("key", new BillPaymentController.PaymentRequest(accountId, BigDecimal.ONE, "APPROVE"))
                .getBody().status()).isEqualTo("PENDING");
        assertThat(controller.get(id).id()).isEqualTo(id);
        assertThat(controller.list(accountId)).hasSize(1);
        assertThat(controller.reconcile(id).accountId()).isEqualTo(accountId);

        AccountReportService report = mock(AccountReportService.class);
        when(report.transferCount(accountId)).thenReturn(3L);
        assertThat(new AccountReportController(report, authorization).transferCount(accountId).count()).isEqualTo(3L);
    }

    @Test
    void exceptionHandlerReturnsAllExpectedStatuses() {
        ApiExceptionHandler handler = new ApiExceptionHandler();
        assertThat(handler.handleDomain(new DomainException("NOT_FOUND", "missing")).getStatusCode().value()).isEqualTo(404);
        assertThat(handler.handleDomain(new DomainException("INSUFFICIENT_FUNDS", "low")).getStatusCode().value()).isEqualTo(409);
        assertThat(handler.handleDomain(new DomainException("IDEMPOTENCY_CONFLICT", "used")).getStatusCode().value()).isEqualTo(409);
        assertThat(handler.handleDomain(new DomainException("FORBIDDEN", "denied")).getStatusCode().value()).isEqualTo(403);
        assertThat(handler.handleDomain(new DomainException("INVALID_KEY", "bad")).getStatusCode().value()).isEqualTo(400);
        assertThat(handler.handleBadCredentials(new BadCredentialsException("bad credentials")).getStatusCode().value()).isEqualTo(401);
        assertThat(handler.handleValidation(null).getBody()).containsEntry("code", "VALIDATION_ERROR");
        assertThat(handler.handleConstraint(new DataIntegrityViolationException("duplicate")).getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void requestIdPreservesValidHeaderAndReplacesInvalidOne() {
        RequestIdInterceptor interceptor = new RequestIdInterceptor();
        MockHttpServletRequest valid = new MockHttpServletRequest();
        valid.addHeader("X-Request-Id", "request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(valid, response, new Object())).isTrue();
        assertThat(response.getHeader("X-Request-Id")).isEqualTo("request-123");

        MockHttpServletRequest invalid = new MockHttpServletRequest();
        invalid.addHeader("X-Request-Id", "not valid!");
        MockHttpServletResponse generated = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(invalid, generated, new Object())).isTrue();
        assertThat(generated.getHeader("X-Request-Id")).isNotBlank().isNotEqualTo("not valid!");
        MockHttpServletResponse missing = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(new MockHttpServletRequest(), missing, new Object())).isTrue();
        assertThat(missing.getHeader("X-Request-Id")).isNotBlank();
    }
}
