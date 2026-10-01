package com.rideshare.rideservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/** Pricing knobs, bound from {@code rideshare.fare.*}. */
@ConfigurationProperties("rideshare.fare")
public record FareProperties(BigDecimal baseFare, BigDecimal perKm, String currency) {
}
