package com.rideshare.matchingservice.event;

/** Published to {@code ride.matched} once a driver has been reserved. */
public record RideMatchedEvent(String rideId, String riderId, String driverId,
                               double driverLatitude, double driverLongitude, double distanceToPickupKm) {
}
