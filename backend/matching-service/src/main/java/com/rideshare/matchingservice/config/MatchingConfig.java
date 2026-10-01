package com.rideshare.matchingservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class MatchingConfig {

    /** Tight timeouts: a slow location service must not stall the Kafka consumer thread. */
    @Bean
    public RestClient locationRestClient(MatchingProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder()
                .baseUrl(properties.locationServiceUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public KafkaAdmin.NewTopics matchingTopics(TopicProperties topics) {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(topics.rideRequested()).partitions(topics.partitions()).replicas(topics.replicas()).build(),
                TopicBuilder.name(topics.rideMatched()).partitions(topics.partitions()).replicas(topics.replicas()).build(),
                TopicBuilder.name(topics.rideUnmatched()).partitions(topics.partitions()).replicas(topics.replicas()).build());
    }

    /**
     * If the location service is down, retry 3 times one second apart, then park the request on
     * {@code ride.requested.DLT}. The ride service's timeout sweeper then cancels the ride for the rider.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template) {
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template), new FixedBackOff(1_000L, 3));
    }
}
