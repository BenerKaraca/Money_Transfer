package com.bener.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

class ProviderCoverageTest {
    @Test
    void controllerReturnsCommittedResultAndStatus() throws Exception {
        ProviderPaymentService service = mock(ProviderPaymentService.class);
        ProviderPaymentController controller = new ProviderPaymentController(service);
        UUID id = UUID.randomUUID();
        when(service.submit(id, BigDecimal.ONE, "APPROVE"))
                .thenReturn(new ProviderPaymentService.Result("ACCEPTED", false));
        when(service.status(id)).thenReturn("ACCEPTED");
        assertThat(controller.submit(new ProviderPaymentController.PaymentRequest(id, BigDecimal.ONE, "APPROVE"))
                .getBody().status()).isEqualTo("ACCEPTED");
        assertThat(controller.status(id).status()).isEqualTo("ACCEPTED");

        when(service.submit(id, BigDecimal.ONE, "TIMEOUT_ONCE"))
                .thenReturn(new ProviderPaymentService.Result("ACCEPTED", true));
        assertThat(controller.submit(new ProviderPaymentController.PaymentRequest(id, BigDecimal.ONE, "TIMEOUT_ONCE"))
                .getBody().status()).isEqualTo("ACCEPTED");
    }

    @Test
    void serviceRejectsInvalidInputAndStoresBothOutcomes() {
        ProviderPaymentRepository repository = mock(ProviderPaymentRepository.class);
        ProviderPaymentService service = new ProviderPaymentService(repository);
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> service.submit(null, BigDecimal.ONE, "APPROVE"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.submit(id, null, "APPROVE"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.submit(id, BigDecimal.ZERO, "APPROVE"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.submit(id, BigDecimal.ONE, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.submit(id, BigDecimal.ONE, "UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.submit(id, BigDecimal.ONE, "REJECT").status()).isEqualTo("REJECTED");
        assertThat(service.submit(id, BigDecimal.ONE, "APPROVE").status()).isEqualTo("ACCEPTED");
        assertThat(service.status(id)).isEqualTo("UNKNOWN");

        ProviderPayment payment = new ProviderPayment(id, BigDecimal.ONE, "APPROVE", "ACCEPTED");
        assertThat(payment.getId()).isEqualTo(id);
        when(repository.findById(id)).thenReturn(java.util.Optional.of(payment));
        assertThat(service.status(id)).isEqualTo("ACCEPTED");
        assertThat(service.submit(id, BigDecimal.ONE, "APPROVE").simulateLostReply()).isFalse();
        assertThatThrownBy(() -> service.submit(id, BigDecimal.ONE, "REJECT"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void applicationMainDelegatesToSpring() {
        try (MockedStatic<SpringApplication> spring = org.mockito.Mockito.mockStatic(SpringApplication.class)) {
            PaymentProviderApplication.main(new String[0]);
            spring.verify(() -> SpringApplication.run(PaymentProviderApplication.class, new String[0]));
        }
    }
}
