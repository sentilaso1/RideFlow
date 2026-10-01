package com.rideshare.matchingservice.event;

/** Consumed from {@code ride.requested}. */
public record RideRequestedEvent(String rideId, String riderId,
                                 double pickupLatitude, double pickupLongitude, String pickupAddress,
                                 double dropLatitude, double dropLongitude, String dropAddress) {
}
