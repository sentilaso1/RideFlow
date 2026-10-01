package com.rideshare.locationservice.dto;

public record NearbyDriverResponse(String driverId, double latitude, double longitude, double distanceInKm) {
}
