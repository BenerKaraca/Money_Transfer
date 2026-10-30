package com.bener.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@ActiveProfiles("test")
class NotificationCoverageTest {
    @Autowired JsonMapper json;

    @Test
    void ignoresUnrelatedEventsAndHandlesNullInboxCount() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        NotificationService service = new NotificationService(jdbc, json);
        UUID id = UUID.randomUUID();
        UUID transfer = UUID.randomUUID();
        String unrelated = payload(id, transfer, "CUSTOMER_CREATED");
        service.record(unrelated);
        verifyNoInteractions(jdbc);

        service.record(payload(id, transfer, "TRANSFER_COMPLETED"));
        verify(jdbc).update(anyString(), any(UUID.class));
        verify(jdbc).update(anyString(), any(UUID.class), any(UUID.class), anyString());
        assertThat(new NotificationService.TransferEvent(id, "TRANSFER_COMPLETED", transfer,
                UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ONE).eventId()).isEqualTo(id);
    }

    @Test
    void consumerPassesKafkaPayloadToServiceAndMainStartsSpring() {
        NotificationService service = mock(NotificationService.class);
        new NotificationConsumer(service).consume("payload");
        verify(service).record("payload");
        try (MockedStatic<SpringApplication> spring = org.mockito.Mockito.mockStatic(SpringApplication.class)) {
            NotificationApplication.main(new String[0]);
            spring.verify(() -> SpringApplication.run(NotificationApplication.class, new String[0]));
        }
    }

    private static String payload(UUID id, UUID transfer, String type) {
        return "{\"eventId\":\"" + id + "\",\"type\":\"" + type + "\",\"transferId\":\"" + transfer
                + "\",\"sourceAccountId\":\"" + UUID.randomUUID() + "\",\"destinationAccountId\":\""
                + UUID.randomUUID() + "\",\"amount\":1.00}";
    }
}
