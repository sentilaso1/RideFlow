package com.rideshare.rideservice.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Wrapper types + @NotNull so a missing coordinate is rejected instead of silently becoming 0.0. */
public record RideRequest(
        @NotBlank(message = "riderId is required") @Size(max = 64) String riderId,

        @NotNull(message = "pickupLatitude is required") @DecimalMin("-90.0") @DecimalMax("90.0")
        Double pickupLatitude,
        @NotNull(message = "pickupLongitude is required") @DecimalMin("-180.0") @DecimalMax("180.0")
        Double pickupLongitude,
        @NotBlank(message = "pickupAddress is required") @Size(max = 255) String pickupAddress,

        @NotNull(message = "dropLatitude is required") @DecimalMin("-90.0") @DecimalMax("90.0")
        Double dropLatitude,
        @NotNull(message = "dropLongitude is required") @DecimalMin("-180.0") @DecimalMax("180.0")
        Double dropLongitude,
        @NotBlank(message = "dropAddress is required") @Size(max = 255) String dropAddress
) {
}
