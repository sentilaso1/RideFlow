package com.rideshare.rideservice.domain;

/**
 * Ride lifecycle. Allowed moves:
 * <pre>
 * REQUESTED -> MATCHING -> ACCEPTED -> DRIVER_ARRIVING -> RIDE_STARTED -> COMPLETED
 *                  |           |  \______________________/^
 *                  v           v             v
 *              CANCELLED   CANCELLED     CANCELLED
 * </pre>
 * A trip in progress cannot be cancelled, only completed.
 */
public enum RideStatus {
    REQUESTED,
    MATCHING,
    ACCEPTED,
    DRIVER_ARRIVING,
    RIDE_STARTED,
    COMPLETED,
    CANCELLED;

    public boolean canTransitionTo(RideStatus next) {
        return switch (this) {
            case REQUESTED -> next == MATCHING || next == CANCELLED;
            case MATCHING -> next == ACCEPTED || next == CANCELLED;
            case ACCEPTED -> next == DRIVER_ARRIVING || next == RIDE_STARTED || next == CANCELLED;
            case DRIVER_ARRIVING -> next == RIDE_STARTED || next == CANCELLED;
            case RIDE_STARTED -> next == COMPLETED;
            case COMPLETED, CANCELLED -> false;
        };
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }
}
