package com.rideshare.locationservice.dto;

/** One online driver as shown on the fleet map. {@code currentRideId} is null when the driver is free. */
public record DriverSnapshot(String driverId, double latitude, double longitude, boolean busy, String currentRideId) {
}
