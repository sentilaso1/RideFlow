package com.rideshare.rideservice.domain;

public class RideNotFoundException extends RuntimeException {

    public RideNotFoundException(String rideId) {
        super("Ride %s was not found".formatted(rideId));
    }
}
