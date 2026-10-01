package com.rideshare.rideservice.messaging;

import com.rideshare.rideservice.service.RideService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Applies matching outcomes. Each listener overrides the JSON target type, because the two topics carry
 * different payloads and producers do not send type headers (services do not share Java classes).
 */
@Component
public class MatchingResultListener {

    private final RideService rideService;

    public MatchingResultListener(RideService rideService) {
        this.rideService = rideService;
    }

    @KafkaListener(topics = "${rideshare.kafka.topics.ride-matched}",
            properties = "spring.json.value.default.type=com.rideshare.rideservice.messaging.RideMatchedEvent")
    public void onRideMatched(RideMatchedEvent event) {
        rideService.assignDriver(event.rideId(), event.driverId());
    }

    @KafkaListener(topics = "${rideshare.kafka.topics.ride-unmatched}",
            properties = "spring.json.value.default.type=com.rideshare.rideservice.messaging.RideUnmatchedEvent")
    public void onRideUnmatched(RideUnmatchedEvent event) {
        rideService.cancelUnmatched(event.rideId(), event.reason());
    }
}
