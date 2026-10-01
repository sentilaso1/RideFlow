package com.rideshare.matchingservice.service;

import com.rideshare.matchingservice.client.NearbyDriver;
import com.rideshare.matchingservice.config.MatchingProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DriverScorerTest {

    private static final MatchingProperties PROPS = new MatchingProperties(5.0, 10, 0.7, "http://unused");

    private static DriverScorer scorerWith(Map<String, Double> ratings) {
        return new DriverScorer(id -> ratings.getOrDefault(id, 4.5), PROPS);
    }

    private static NearbyDriver driver(String id, double km) {
        return new NearbyDriver(id, 0, 0, km);
    }

    @Test
    void closerDriverWinsWhenRatingsAreEqual() {
        DriverScorer scorer = scorerWith(Map.of());
        List<NearbyDriver> ranked = scorer.rank(List.of(driver("far", 4.0), driver("near", 0.5)));
        assertThat(ranked).extracting(NearbyDriver::driverId).containsExactly("near", "far");
    }

    @Test
    void muchBetterRatingCanBeatSlightlyCloserDriver() {
        DriverScorer scorer = scorerWith(Map.of("great", 5.0, "poor", 2.0));
        List<NearbyDriver> ranked = scorer.rank(List.of(driver("poor", 1.0), driver("great", 1.3)));
        assertThat(ranked.getFirst().driverId()).isEqualTo("great");
    }

    @Test
    void scoreIsNormalisedEvenAtZeroDistance() {
        DriverScorer scorer = scorerWith(Map.of("x", 5.0));
        assertThat(scorer.score(driver("x", 0.0))).isEqualTo(1.0);
    }

    @Test
    void ranksAreDeterministicForTies() {
        DriverScorer scorer = scorerWith(Map.of());
        List<NearbyDriver> drivers = List.of(driver("b", 1.0), driver("a", 1.0));
        assertThat(scorer.rank(drivers)).extracting(NearbyDriver::driverId).containsExactly("a", "b");
    }

    @Test
    void simulatedRatingsAreStableAndInRange() {
        SimulatedDriverRatingProvider ratings = new SimulatedDriverRatingProvider();
        double first = ratings.ratingOf("driver:1");
        assertThat(ratings.ratingOf("driver:1")).isEqualTo(first);
        assertThat(first).isBetween(4.0, 5.0);
    }
}
