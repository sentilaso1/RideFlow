package com.rideshare.locationservice.event;

import java.time.Instant;

/** Consumed from {@code ride.status-changed}; published by the ride service after every state change. */
public record RideStatusChangedEvent(String rideId, String riderId, String driverId, String status, Instant occurredAt) {

    public boolean isTerminal() {
        return "COMPLETED".equals(status) || "CANCELLED".equals(status);
    }
}
