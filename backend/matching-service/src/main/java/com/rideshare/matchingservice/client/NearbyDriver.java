package com.rideshare.matchingservice.client;

/** Response item from the location service's nearby search. */
public record NearbyDriver(String driverId, double latitude, double longitude, double distanceInKm) {
}
