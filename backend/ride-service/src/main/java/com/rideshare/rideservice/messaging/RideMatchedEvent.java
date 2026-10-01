package com.rideshare.rideservice.messaging;

/** Consumed from {@code ride.matched}: the matching service reserved {@code driverId} for the ride. */
public record RideMatchedEvent(String rideId, String riderId, String driverId,
                               double driverLatitude, double driverLongitude, double distanceToPickupKm) {
}
