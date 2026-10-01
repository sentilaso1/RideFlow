package com.rideshare.locationservice.dto;

import jakarta.validation.constraints.NotBlank;

public record ReservationRequest(@NotBlank(message = "rideId is required") String rideId) {
}
