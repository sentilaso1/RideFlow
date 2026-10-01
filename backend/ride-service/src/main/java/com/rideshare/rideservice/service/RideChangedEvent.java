package com.rideshare.rideservice.service;

import com.rideshare.rideservice.dto.RideResponse;

/**
 * In-process event raised inside a transaction whenever a ride changes. {@link RideEventRelay} forwards it to
 * Kafka and WebSocket clients only after the transaction commits.
 *
 * @param newlyRequested true for a brand-new ride, which also needs a {@code ride.requested} message
 */
public record RideChangedEvent(RideResponse ride, boolean newlyRequested) {
}
