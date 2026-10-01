package com.rideshare.rideservice.service;

import com.rideshare.rideservice.config.FareProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Fare = base + perKm x great-circle distance, rounded to 2 decimals (money is BigDecimal, never double). */
@Component
public class FareCalculator {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private final FareProperties properties;

    public FareCalculator(FareProperties properties) {
        this.properties = properties;
    }

    public FareQuote quote(double pickupLat, double pickupLng, double dropLat, double dropLng) {
        double distanceKm = haversineKm(pickupLat, pickupLng, dropLat, dropLng);
        BigDecimal fare = properties.baseFare()
                .add(properties.perKm().multiply(BigDecimal.valueOf(distanceKm)))
                .setScale(2, RoundingMode.HALF_UP);
        double roundedDistance = BigDecimal.valueOf(distanceKm).setScale(2, RoundingMode.HALF_UP).doubleValue();
        return new FareQuote(roundedDistance, fare, properties.currency());
    }

    static double haversineKm(double lat1Deg, double lng1Deg, double lat2Deg, double lng2Deg) {
        double lat1 = Math.toRadians(lat1Deg);
        double lat2 = Math.toRadians(lat2Deg);
        double dLat = lat2 - lat1;
        double dLng = Math.toRadians(lng2Deg - lng1Deg);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dLng / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
    }

    public record FareQuote(double distanceKm, BigDecimal fare, String currency) {
    }
}
