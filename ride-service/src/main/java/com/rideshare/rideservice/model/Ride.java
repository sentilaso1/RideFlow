package com.rideshare.rideservice.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "rides")
public class Ride {
    private String id;

    private String riderId;

    private String drvierId;

    private double pickupLatitude;

    private double pickupLongitude;

    private String pickupAddress;

    private double dropLatitude;

    private double dropLongitude;

    private String dropAddress;

    //Ride status = tracks the lifecycle
    private RideStatus status;

    //Fare Details
    private double estimatedFare;
    private double actualFare;

    //Timestamps
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

}
