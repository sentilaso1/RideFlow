package com.rideshare.matchingservice.client;

public record ReservationResult(String driverId, String rideId, boolean reserved, String reason) {
}
