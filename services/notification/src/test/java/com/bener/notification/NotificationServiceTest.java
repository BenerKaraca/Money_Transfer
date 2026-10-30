package com.bener.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class NotificationServiceTest {
    @Autowired NotificationService service;
    @Autowired JdbcTemplate jdbc;

    @Test
    void duplicateEventCreatesOneNotification() {
        UUID eventId = UUID.randomUUID();
        String json = "{\"eventId\":\"" + eventId + "\",\"type\":\"TRANSFER_COMPLETED\","
                + "\"transferId\":\"" + UUID.randomUUID() + "\",\"sourceAccountId\":\"" + UUID.randomUUID()
                + "\",\"destinationAccountId\":\"" + UUID.randomUUID() + "\",\"amount\":200.00}";
        service.record(json);
        service.record(json);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE event_id = ?", Long.class, eventId))
                .isEqualTo(1L);
    }
}
