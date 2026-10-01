package com.rideshare.rideservice.domain;

public class ActiveRideExistsException extends RuntimeException {

    public ActiveRideExistsException(String riderId) {
        super("Rider %s already has a ride in progress. Finish or cancel it first.".formatted(riderId));
    }
}
