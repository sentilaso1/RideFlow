package com.rideshare.rideservice.dto;

import java.math.BigDecimal;

public record FareEstimateResponse(double distanceKm, BigDecimal estimatedFare, String currency) {
}
