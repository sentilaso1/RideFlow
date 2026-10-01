package com.rideshare.rideservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * If the matching service is down or a message is lost, a ride would wait in MATCHING forever.
 * This job cancels such rides so riders always get an answer. Safe to run on several instances:
 * optimistic locking makes a duplicate cancel fail instead of applying twice.
 */
@Component
public class MatchingTimeoutSweeper {

    private static final Logger log = LoggerFactory.getLogger(MatchingTimeoutSweeper.class);

    private final RideService rideService;

    public MatchingTimeoutSweeper(RideService rideService) {
        this.rideService = rideService;
    }

    @Scheduled(fixedDelayString = "${rideshare.matching.sweep-interval-ms:15000}",
            initialDelayString = "${rideshare.matching.sweep-interval-ms:15000}")
    public void cancelStaleMatchingRides() {
        for (String rideId : rideService.findTimedOutMatchingRideIds()) {
            try {
                if (rideService.cancelIfMatchingTimedOut(rideId)) {
                    log.warn("Ride {} timed out waiting for a driver and was cancelled", rideId);
                }
            } catch (RuntimeException ex) {
                log.debug("Skipped ride {} in timeout sweep: {}", rideId, ex.getMessage());
            }
        }
    }
}
