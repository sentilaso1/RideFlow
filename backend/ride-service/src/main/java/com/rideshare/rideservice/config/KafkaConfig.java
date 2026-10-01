package com.rideshare.rideservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    /** Declares every topic this service produces or consumes (idempotent if they already exist). */
    @Bean
    public KafkaAdmin.NewTopics rideTopics(TopicProperties topics) {
        return new KafkaAdmin.NewTopics(
                topic(topics.rideRequested(), topics),
                topic(topics.rideMatched(), topics),
                topic(topics.rideUnmatched(), topics),
                topic(topics.rideStatusChanged(), topics));
    }

    private static NewTopic topic(String name, TopicProperties topics) {
        // Records are keyed by rideId, so all events for one ride land on one partition, in order.
        return TopicBuilder.name(name).partitions(topics.partitions()).replicas(topics.replicas()).build();
    }

    /** 3 retries one second apart (covers optimistic-lock clashes), then the record goes to {@code <topic>.DLT}. */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template) {
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template), new FixedBackOff(1_000L, 3));
    }
}
