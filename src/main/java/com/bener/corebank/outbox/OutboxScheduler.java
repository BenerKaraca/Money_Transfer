package com.bener.corebank.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "corebank.messaging.enabled", havingValue = "true")
public class OutboxScheduler {
    private static final Logger log = LoggerFactory.getLogger(OutboxScheduler.class);
    private final OutboxDeliveryService delivery;

    public OutboxScheduler(OutboxDeliveryService delivery) { this.delivery = delivery; }

    @Scheduled(fixedDelayString = "${corebank.messaging.poll-ms:1000}")
    public void publish() {
        try {
            delivery.deliverBatch();
        } catch (Exception exception) {
            log.warn("Outbox publish failed; pending events remain for retry", exception);
        }
    }
}
