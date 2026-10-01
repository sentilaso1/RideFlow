package com.rideshare.matchingservice.service;

import org.springframework.stereotype.Component;

/**
 * Stand-in until a driver-profile service exists. Derives a stable rating in [4.0, 5.0) from the driver id,
 * so the same driver always scores the same (the original used Math.random() inside a comparator,
 * which breaks the Comparator contract and makes matching non-repeatable).
 */
@Component
public class SimulatedDriverRatingProvider implements DriverRatingProvider {

    @Override
    public double ratingOf(String driverId) {
        int bucket = Math.floorMod(driverId.hashCode(), 100);
        return 4.0 + bucket / 100.0;
    }
}
