package com.rideshare.matchingservice.service;

import com.rideshare.matchingservice.client.NearbyDriver;
import com.rideshare.matchingservice.config.MatchingProperties;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Ranks candidates by a weighted score in [0, 1]:
 * <pre>
 *   score = w * (1 - distance / radius) + (1 - w) * (rating - 1) / 4
 * </pre>
 * Both terms are normalised to [0, 1] so the weight means what it says (the original mixed 1/distance,
 * which explodes near zero, with raw ratings). Ties break on driver id for repeatable results.
 */
@Component
public class DriverScorer {

    private final DriverRatingProvider ratings;
    private final MatchingProperties properties;

    public DriverScorer(DriverRatingProvider ratings, MatchingProperties properties) {
        this.ratings = ratings;
        this.properties = properties;
    }

    public double score(NearbyDriver driver) {
        double radius = properties.searchRadiusKm();
        double proximity = Math.max(0.0, 1.0 - driver.distanceInKm() / radius);
        double rating = (ratings.ratingOf(driver.driverId()) - 1.0) / 4.0;
        double w = properties.distanceWeight();
        return w * proximity + (1.0 - w) * rating;
    }

    public List<NearbyDriver> rank(List<NearbyDriver> candidates) {
        return candidates.stream()
                .sorted(Comparator.comparingDouble(this::score).reversed()
                        .thenComparing(NearbyDriver::driverId))
                .toList();
    }
}
