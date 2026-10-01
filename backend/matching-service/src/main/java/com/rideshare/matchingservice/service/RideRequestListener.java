package com.rideshare.matchingservice.service;

import com.rideshare.matchingservice.event.RideRequestedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Ride Service -> Kafka (ride.requested) -> this listener -> MatchingService.
 * Exceptions are deliberately not swallowed: the container's error handler retries, then dead-letters.
 */
@Component
public class RideRequestListener {

    private final MatchingService matchingService;

    public RideRequestListener(MatchingService matchingService) {
        this.matchingService = matchingService;
    }

    @KafkaListener(topics = "${rideshare.kafka.topics.ride-requested}")
    public void onRideRequested(RideRequestedEvent event) {
        matchingService.match(event);
    }
}
