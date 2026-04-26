package com.rideshare.matchingservice.event;

/**
 * Event consumed from Kafka topic: ride.reuqested
 * Published by Ride Service when a rider requests a ride
 */

public class RideRequestedEvent {

    private String riderId;
    private String rideId;
    private double pickupLatitude;
    private double pickupLongitude;
    private String pickupAddress;
    private double dropLatitude;
    private double dropLongitude;
    private String dropAddress;
}
