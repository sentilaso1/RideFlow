package com.rideshare.rideservice.service;

import com.rideshare.rideservice.config.TopicProperties;
import com.rideshare.rideservice.domain.RideStatus;
import com.rideshare.rideservice.dto.RideResponse;
import com.rideshare.rideservice.messaging.RideRequestedEvent;
import com.rideshare.rideservice.messaging.RideStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;

/**
 * Fans out committed ride changes to Kafka and to WebSocket subscribers.
 * <p>
 * Runs AFTER_COMMIT, so other services never see a ride the database rolled back, and the matching service can
 * never reply before the ride row is visible (the race in the original version). Trade-off: a crash between commit
 * and send drops the event; the {@link MatchingTimeoutSweeper} covers that for matching. The fully durable
 * alternative is a transactional outbox table relayed by Debezium.
 */
@Component
public class RideEventRelay {

    private static final Logger log = LoggerFactory.getLogger(RideEventRelay.class);

    private final KafkaTemplate<String, Object> kafka;
    private final SimpMessagingTemplate websocket;
    private final TopicProperties topics;
    private final Clock clock;

    public RideEventRelay(KafkaTemplate<String, Object> kafka, SimpMessagingTemplate websocket,
                          TopicProperties topics, Clock clock) {
        this.kafka = kafka;
        this.websocket = websocket;
        this.topics = topics;
        this.clock = clock;
    }

    @TransactionalEventListener
    public void onRideChanged(RideChangedEvent event) {
        RideResponse ride = event.ride();

        if (event.newlyRequested()) {
            send(topics.rideRequested(), ride.id(), RideRequestedEvent.from(ride));
        }
        send(topics.rideStatusChanged(), ride.id(), new RideStatusChangedEvent(
                ride.id(), ride.riderId(), ride.driverId(), ride.status().name(), clock.instant()));

        pushToClients(ride);
    }

    @TransactionalEventListener
    public void onDriverReleaseRequested(DriverReleaseRequested event) {
        send(topics.rideStatusChanged(), event.rideId(), new RideStatusChangedEvent(
                event.rideId(), event.riderId(), event.driverId(), RideStatus.CANCELLED.name(), clock.instant()));
    }

    private void send(String topic, String key, Object payload) {
        kafka.send(topic, key, payload).whenComplete((result, error) -> {
            if (error != null) {
                log.error("Failed to publish {} for ride {}", topic, key, error);
            }
        });
    }

    private void pushToClients(RideResponse ride) {
        try {
            websocket.convertAndSend("/topic/rides/" + ride.id(), ride);
            websocket.convertAndSend("/topic/riders/" + ride.riderId(), ride);
            if (ride.driverId() != null) {
                websocket.convertAndSend("/topic/drivers/" + ride.driverId(), ride);
            }
        } catch (RuntimeException ex) {
            // Live push is best-effort; clients fall back to polling.
            log.warn("Could not push ride {} to WebSocket clients: {}", ride.id(), ex.getMessage());
        }
    }
}
