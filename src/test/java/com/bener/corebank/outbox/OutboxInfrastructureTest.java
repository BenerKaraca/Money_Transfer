package com.bener.corebank.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;

class OutboxInfrastructureTest {
    @Test
    void deliveryMarksEventOnlyAfterKafkaAcknowledges() throws Exception {
        OutboxRepository repository = mock(OutboxRepository.class);
        @SuppressWarnings("unchecked") KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        OutboxEvent event = new OutboxEvent(UUID.randomUUID(), UUID.randomUUID(), "TRANSFER_COMPLETED", "{}");
        when(repository.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Pageable.class))).thenReturn(List.of(event));
        when(kafka.send("corebank.events", event.getAggregateId().toString(), "{}"))
                .thenReturn(CompletableFuture.completedFuture(null));
        new OutboxDeliveryService(repository, kafka).deliverBatch();
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getId()).isNotNull();
        assertThat(event.getEventType()).isEqualTo("TRANSFER_COMPLETED");
        assertThat(event.getPayload()).isEqualTo("{}");
    }

    @Test
    void publisherKeepsFailedEventForRetryAndSchedulerCatchesFailure() throws Exception {
        OutboxRepository repository = mock(OutboxRepository.class);
        @SuppressWarnings("unchecked") KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        OutboxEvent event = new OutboxEvent(UUID.randomUUID(), UUID.randomUUID(), "TRANSFER_COMPLETED", "{}");
        when(repository.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Pageable.class))).thenReturn(List.of(event));
        var failed = new CompletableFuture<org.springframework.kafka.support.SendResult<String, String>>();
        failed.completeExceptionally(new IllegalStateException("broker unavailable"));
        when(kafka.send("corebank.events", event.getAggregateId().toString(), "{}")).thenReturn(failed);
        OutboxDeliveryService delivery = new OutboxDeliveryService(repository, kafka);
        assertThatThrownBy(delivery::deliverBatch).isInstanceOf(Exception.class);
        assertThat(event.getPublishedAt()).isNull();

        OutboxScheduler scheduler = new OutboxScheduler(delivery);
        scheduler.publish();
        OutboxDeliveryService mocked = mock(OutboxDeliveryService.class);
        new OutboxScheduler(mocked).publish();
        verify(mocked).deliverBatch();
    }

    @Test
    void topicHasThreePartitions() {
        var topic = new KafkaTopicConfiguration().coreBankEvents();
        assertThat(topic.name()).isEqualTo("corebank.events");
        assertThat(topic.numPartitions()).isEqualTo(3);
    }
}
