package com.rideshare.rideservice.domain;

public class InvalidRideStateException extends RuntimeException {

    public InvalidRideStateException(String rideId, RideStatus current, RideStatus requested) {
        super("Ride %s cannot move from %s to %s".formatted(rideId, current, requested));
    }
}
