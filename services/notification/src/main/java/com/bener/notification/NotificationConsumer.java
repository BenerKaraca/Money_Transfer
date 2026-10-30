package com.bener.notification;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationConsumer {
    private final NotificationService service;

    public NotificationConsumer(NotificationService service) { this.service = service; }

    @KafkaListener(topics = "corebank.events", groupId = "corebank-notifications")
    public void consume(String payload) { service.record(payload); }
}
