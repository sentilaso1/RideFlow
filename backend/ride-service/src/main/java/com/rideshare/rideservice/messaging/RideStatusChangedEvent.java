package com.rideshare.rideservice.messaging;

import java.time.Instant;

/** Published to {@code ride.status-changed} after every committed change; the location service frees drivers on it. */
public record RideStatusChangedEvent(String rideId, String riderId, String driverId, String status, Instant occurredAt) {
}
