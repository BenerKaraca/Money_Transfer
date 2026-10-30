package com.bener.corebank.outbox;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "corebank.messaging.enabled", havingValue = "true")
public class KafkaTopicConfiguration {
    @Bean
    NewTopic coreBankEvents() { return TopicBuilder.name("corebank.events").partitions(3).replicas(1).build(); }
}
