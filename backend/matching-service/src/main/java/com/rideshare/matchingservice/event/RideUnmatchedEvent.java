package com.rideshare.matchingservice.event;

/** Published to {@code ride.unmatched} when no available driver could be reserved. */
public record RideUnmatchedEvent(String rideId, String riderId, String reason) {
}
