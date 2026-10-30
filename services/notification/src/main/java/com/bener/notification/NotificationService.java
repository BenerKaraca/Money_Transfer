package com.bener.notification;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class NotificationService {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    public NotificationService(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional
    public void record(String payload) {
        TransferEvent event = json.readValue(payload, TransferEvent.class);
        if (!"TRANSFER_COMPLETED".equals(event.type())) return;
        Long seen = jdbc.queryForObject("SELECT count(*) FROM inbox_events WHERE event_id = ?", Long.class, event.eventId());
        if (seen != null && seen > 0) return;
        jdbc.update("INSERT INTO inbox_events (event_id, consumed_at) VALUES (?, CURRENT_TIMESTAMP)", event.eventId());
        jdbc.update("INSERT INTO notifications (event_id, transfer_id, message, created_at) "
                        + "VALUES (?, ?, ?, CURRENT_TIMESTAMP)",
                event.eventId(), event.transferId(), "Transfer completed: " + event.amount() + " TRY");
    }

    public record TransferEvent(UUID eventId, String type, UUID transferId, UUID sourceAccountId,
                                UUID destinationAccountId, BigDecimal amount) {}
}
