package com.rideshare.rideservice.messaging;

/** Consumed from {@code ride.unmatched}: no available driver could be reserved. */
public record RideUnmatchedEvent(String rideId, String riderId, String reason) {
}
