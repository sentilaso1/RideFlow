package com.rideshare.rideservice.dto;

import com.rideshare.rideservice.domain.Ride;
import com.rideshare.rideservice.domain.RideStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** Immutable snapshot of a ride; the JSON shape the web app consumes over REST and WebSocket. */
public record RideResponse(
        String id,
        String riderId,
        String driverId,
        double pickupLatitude,
        double pickupLongitude,
        String pickupAddress,
        double dropLatitude,
        double dropLongitude,
        String dropAddress,
        double distanceKm,
        RideStatus status,
        BigDecimal estimatedFare,
        BigDecimal actualFare,
        String cancellationReason,
        Instant createdAt,
        Instant updatedAt,
        Instant acceptedAt,
        Instant startedAt,
        Instant completedAt,
        Instant cancelledAt
) {

    public static RideResponse from(Ride ride) {
        return new RideResponse(
                ride.getId(),
                ride.getRiderId(),
                ride.getDriverId(),
                ride.getPickupLatitude(),
                ride.getPickupLongitude(),
                ride.getPickupAddress(),
                ride.getDropLatitude(),
                ride.getDropLongitude(),
                ride.getDropAddress(),
                ride.getDistanceKm(),
                ride.getStatus(),
                ride.getEstimatedFare(),
                ride.getActualFare(),
                ride.getCancellationReason(),
                ride.getCreatedAt(),
                ride.getUpdatedAt(),
                ride.getAcceptedAt(),
                ride.getStartedAt(),
                ride.getCompletedAt(),
                ride.getCancelledAt());
    }
}
