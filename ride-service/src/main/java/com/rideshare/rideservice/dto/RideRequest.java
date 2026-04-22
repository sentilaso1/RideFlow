package com.rideshare.rideservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RideRequest {

    @NotBlank(message = "Rider Id is required")
    private String riderID;

    @NotNull(message = "Pickup latitude is required")
    private double pickUpLatitude;

    @NotNull(message = "Pickup longititude is required")
    private double getPickUpLongitude;

    @NotNull(message = "Pickup Address is required")
    private String pickupAddress;

    @NotNull(message = "Drop latitude is required")
    private double dropLatitude;

    @NotNull(message = "Drop longitude is required")
    private double dropLongitude;

    @NotNull(message = "Drop address is required")
    private String dropAddress;
}
