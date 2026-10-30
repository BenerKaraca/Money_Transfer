package com.bener.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ProviderPaymentServiceTest {
    @Autowired ProviderPaymentService service;

    @Test
    void retryReturnsStoredOutcomeAndRejectsChangedPayload() {
        UUID id = UUID.randomUUID();
        var first = service.submit(id, new BigDecimal("100.00"), "TIMEOUT_ONCE");
        assertThat(first.simulateLostReply()).isTrue();
        assertThat(service.submit(id, new BigDecimal("100.00"), "TIMEOUT_ONCE").simulateLostReply()).isFalse();
        assertThat(service.status(id)).isEqualTo("ACCEPTED");
        assertThatThrownBy(() -> service.submit(id, new BigDecimal("200.00"), "TIMEOUT_ONCE"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
