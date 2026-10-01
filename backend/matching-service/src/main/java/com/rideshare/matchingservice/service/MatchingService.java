package com.rideshare.matchingservice.service;

import com.rideshare.matchingservice.client.LocationClient;
import com.rideshare.matchingservice.client.NearbyDriver;
import com.rideshare.matchingservice.config.MatchingProperties;
import com.rideshare.matchingservice.event.RideMatchedEvent;
import com.rideshare.matchingservice.event.RideRequestedEvent;
import com.rideshare.matchingservice.event.RideUnmatchedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Matching flow for one ride request:
 * <ol>
 *   <li>Ask the location service for available drivers near the pickup.</li>
 *   <li>Rank them with {@link DriverScorer}.</li>
 *   <li>Walk the ranking and atomically reserve the first driver still free - two concurrent rides can never
 *       both win the same driver.</li>
 *   <li>Publish {@code ride.matched}, or {@code ride.unmatched} if nobody could be reserved.</li>
 * </ol>
 */
@Service
public class MatchingService {

    private static final Logger log = LoggerFactory.getLogger(MatchingService.class);

    static final String NO_DRIVERS_REASON = "No drivers available near the pickup";

    private final LocationClient locationClient;
    private final DriverScorer scorer;
    private final MatchResultPublisher publisher;
    private final MatchingProperties properties;

    public MatchingService(LocationClient locationClient, DriverScorer scorer, MatchResultPublisher publisher,
                           MatchingProperties properties) {
        this.locationClient = locationClient;
        this.scorer = scorer;
        this.publisher = publisher;
        this.properties = properties;
    }

    public void match(RideRequestedEvent request) {
        List<NearbyDriver> candidates = locationClient.findAvailableDrivers(
                request.pickupLatitude(), request.pickupLongitude(),
                properties.searchRadiusKm(), properties.candidateLimit());

        for (NearbyDriver driver : scorer.rank(candidates)) {
            if (locationClient.reserveDriver(driver.driverId(), request.rideId())) {
                log.info("Ride {} matched with driver {} ({} km away)",
                        request.rideId(), driver.driverId(), driver.distanceInKm());
                publisher.matched(new RideMatchedEvent(request.rideId(), request.riderId(), driver.driverId(),
                        driver.latitude(), driver.longitude(), driver.distanceInKm()));
                return;
            }
            log.debug("Driver {} was taken before ride {} could reserve them", driver.driverId(), request.rideId());
        }

        log.info("Ride {}: no available driver among {} candidates", request.rideId(), candidates.size());
        publisher.unmatched(new RideUnmatchedEvent(request.rideId(), request.riderId(), NO_DRIVERS_REASON));
    }
}
