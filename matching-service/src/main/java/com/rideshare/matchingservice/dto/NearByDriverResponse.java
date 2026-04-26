package com.rideshare.matchingservice.dto;

/**
 * Response received from Location Service
 * When querying for nearby drivers
 */
public class NearByDriverResponse {
    private String driverId;
    private double latitude;
    private double longitude;
    private double distanceInKm;
}
