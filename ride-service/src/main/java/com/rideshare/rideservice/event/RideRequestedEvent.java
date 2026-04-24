package com.rideshare.rideservice.event;

/**
 * Event published to Kafka when a ride is requested
 * Matching service consumes this event
 * TOPIC: ride.requested
 */

public class RideRequestedEvent {
    private String rideID;
    private String riderID;

    //PICKUP
    private double pickupLatitude;
    private double pickupLongitude;
    private String pickupAddress;

    //DROP
    private double dropLatitude;
    private double dropLongitude;
    private String dropAddress;
}
