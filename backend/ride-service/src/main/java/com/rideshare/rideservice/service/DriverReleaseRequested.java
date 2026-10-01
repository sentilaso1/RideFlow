package com.rideshare.rideservice.service;

/** A driver was reserved for a ride that had already been cancelled; tell the location service to free them. */
public record DriverReleaseRequested(String rideId, String riderId, String driverId) {
}
