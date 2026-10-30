package com.bener.corebank.outbox;

import java.util.concurrent.TimeUnit;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxDeliveryService {
    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;

    public OutboxDeliveryService(OutboxRepository outbox, KafkaTemplate<String, String> kafka) {
        this.outbox = outbox;
        this.kafka = kafka;
    }

    @Transactional
    public void deliverBatch() throws Exception {
        for (OutboxEvent event : outbox.findByPublishedAtIsNullOrderByCreatedAtAsc(PageRequest.of(0, 50))) {
            // A crash after Kafka ack but before DB commit can redeliver; consumers use an inbox.
            kafka.send("corebank.events", event.getAggregateId().toString(), event.getPayload())
                    .get(5, TimeUnit.SECONDS);
            event.markPublished();
        }
    }
}
