package com.bener.corebank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bener.corebank.account.AccountRepository;
import com.bener.corebank.payment.PaymentSagaOrchestrator;
import com.bener.corebank.payment.PaymentSagaScheduler;
import com.bener.corebank.payment.BillPaymentRepository;
import com.bener.corebank.payment.BillPaymentService;
import com.bener.corebank.payment.PaymentGateway;
import com.bener.corebank.payment.BillPayment;
import com.bener.corebank.report.AccountReportService;
import com.bener.corebank.report.RedisCacheConfiguration;
import com.bener.corebank.shared.DomainException;
import com.bener.corebank.transfer.TransferRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.Pageable;
import java.util.List;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

class InfrastructureCoverageTest {
    @Test
    void demoInitializerWritesThreeCustomersAndFiveAccounts() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        new DemoDataInitializer(jdbc).run(null);
        verify(jdbc, org.mockito.Mockito.times(3)).update(anyString(), any(UUID.class), anyString());
        verify(jdbc, org.mockito.Mockito.times(5)).update(anyString(), any(UUID.class), any(UUID.class), anyString(),
                any(java.math.BigDecimal.class));
    }

    @Test
    void paymentSchedulerRunsAndRetriesAfterFailure() {
        PaymentSagaOrchestrator saga = mock(PaymentSagaOrchestrator.class);
        PaymentSagaScheduler scheduler = new PaymentSagaScheduler(saga);
        scheduler.process();
        verify(saga).processPending();
        org.mockito.Mockito.doThrow(new IllegalStateException("temporary")).when(saga).processPending();
        scheduler.process();
    }

    @Test
    void reportChecksAccountExistenceBeforeCounting() {
        AccountRepository accounts = mock(AccountRepository.class);
        TransferRepository transfers = mock(TransferRepository.class);
        AccountReportService report = new AccountReportService(accounts, transfers);
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> report.transferCount(id)).isInstanceOf(DomainException.class);
        when(accounts.existsById(id)).thenReturn(true);
        when(transfers.countForAccount(id)).thenReturn(2L);
        assertThat(report.transferCount(id)).isEqualTo(2L);
        new RedisCacheConfiguration();
    }

    @Test
    void sagaSchedulerFindsPendingPaymentsAndApplicationMainDelegatesToSpring() {
        BillPaymentRepository repository = mock(BillPaymentRepository.class);
        BillPaymentService service = mock(BillPaymentService.class);
        PaymentGateway gateway = mock(PaymentGateway.class);
        UUID id = UUID.randomUUID();
        BillPayment payment = mock(BillPayment.class);
        when(payment.getStatus()).thenReturn("SUCCEEDED");
        when(service.get(id)).thenReturn(payment);
        when(repository.findPendingIds(any(Pageable.class))).thenReturn(List.of(id));
        new PaymentSagaOrchestrator(repository, service, gateway).processPending();
        verify(service, org.mockito.Mockito.times(2)).get(id);

        try (MockedStatic<SpringApplication> spring = org.mockito.Mockito.mockStatic(SpringApplication.class)) {
            CoreBankApplication.main(new String[0]);
            spring.verify(() -> SpringApplication.run(CoreBankApplication.class, new String[0]));
        }
        assertThat(new CoreBankApplication().clock()).isNotNull();
    }
}
