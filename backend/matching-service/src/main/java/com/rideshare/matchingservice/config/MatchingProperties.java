package com.rideshare.matchingservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param searchRadiusKm   how far from the pickup to look for drivers
 * @param candidateLimit   how many nearby drivers to score
 * @param distanceWeight   weight of proximity in the score (rating gets the rest)
 * @param locationServiceUrl base URL of the location service
 */
@ConfigurationProperties("rideshare.matching")
public record MatchingProperties(double searchRadiusKm, int candidateLimit, double distanceWeight,
                                 String locationServiceUrl) {
}
