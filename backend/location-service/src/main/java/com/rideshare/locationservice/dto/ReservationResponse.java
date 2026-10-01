package com.rideshare.locationservice.dto;

public record ReservationResponse(String driverId, String rideId, boolean reserved, String reason) {
}
