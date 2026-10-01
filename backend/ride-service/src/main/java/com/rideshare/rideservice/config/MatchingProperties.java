package com.rideshare.rideservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** How long a ride may wait for a driver before it is cancelled automatically. */
@ConfigurationProperties("rideshare.matching")
public record MatchingProperties(Duration timeout) {
}
