package com.rideshare.rideservice.messaging;

import com.rideshare.rideservice.dto.RideResponse;

/** Published to {@code ride.requested}; consumed by the matching service. */
public record RideRequestedEvent(String rideId, String riderId,
                                 double pickupLatitude, double pickupLongitude, String pickupAddress,
                                 double dropLatitude, double dropLongitude, String dropAddress) {

    public static RideRequestedEvent from(RideResponse ride) {
        return new RideRequestedEvent(ride.id(), ride.riderId(),
                ride.pickupLatitude(), ride.pickupLongitude(), ride.pickupAddress(),
                ride.dropLatitude(), ride.dropLongitude(), ride.dropAddress());
    }
}
