package com.rideshare.matchingservice.service;

import com.rideshare.matchingservice.config.TopicProperties;
import com.rideshare.matchingservice.event.RideMatchedEvent;
import com.rideshare.matchingservice.event.RideUnmatchedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Publishes matching outcomes keyed by rideId (preserves per-ride ordering). */
@Component
public class MatchResultPublisher {

    private final KafkaTemplate<String, Object> kafka;
    private final TopicProperties topics;

    public MatchResultPublisher(KafkaTemplate<String, Object> kafka, TopicProperties topics) {
        this.kafka = kafka;
        this.topics = topics;
    }

    public void matched(RideMatchedEvent event) {
        sendAndWait(topics.rideMatched(), event.rideId(), event);
    }

    public void unmatched(RideUnmatchedEvent event) {
        sendAndWait(topics.rideUnmatched(), event.rideId(), event);
    }

    /**
     * Waits for the broker ack so a failed publish surfaces as an exception in the listener; the consumer offset
     * is then not committed and the request is retried instead of silently lost.
     */
    private void sendAndWait(String topic, String key, Object payload) {
        try {
            kafka.send(topic, key, payload).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing to " + topic, ex);
        } catch (ExecutionException | TimeoutException ex) {
            throw new IllegalStateException("Failed to publish to " + topic, ex);
        }
    }
}
