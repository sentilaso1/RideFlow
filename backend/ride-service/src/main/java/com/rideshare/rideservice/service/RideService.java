package com.rideshare.rideservice.service;

import com.rideshare.rideservice.config.MatchingProperties;
import com.rideshare.rideservice.domain.ActiveRideExistsException;
import com.rideshare.rideservice.domain.Ride;
import com.rideshare.rideservice.domain.RideNotFoundException;
import com.rideshare.rideservice.domain.RideRepository;
import com.rideshare.rideservice.domain.RideStatus;
import com.rideshare.rideservice.dto.FareEstimateRequest;
import com.rideshare.rideservice.dto.FareEstimateResponse;
import com.rideshare.rideservice.dto.RideRequest;
import com.rideshare.rideservice.dto.RideResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class RideService {

    private static final Logger log = LoggerFactory.getLogger(RideService.class);

    static final Set<RideStatus> ACTIVE_STATUSES = EnumSet.of(
            RideStatus.REQUESTED, RideStatus.MATCHING, RideStatus.ACCEPTED,
            RideStatus.DRIVER_ARRIVING, RideStatus.RIDE_STARTED);

    static final String NO_DRIVER_TIMEOUT_REASON = "No driver accepted the ride in time";

    private final RideRepository rides;
    private final FareCalculator fareCalculator;
    private final ApplicationEventPublisher events;
    private final MatchingProperties matching;
    private final Clock clock;

    public RideService(RideRepository rides, FareCalculator fareCalculator, ApplicationEventPublisher events,
                       MatchingProperties matching, Clock clock) {
        this.rides = rides;
        this.fareCalculator = fareCalculator;
        this.events = events;
        this.matching = matching;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public FareEstimateResponse estimateFare(FareEstimateRequest request) {
        FareCalculator.FareQuote quote = fareCalculator.quote(
                request.pickupLatitude(), request.pickupLongitude(),
                request.dropLatitude(), request.dropLongitude());
        return new FareEstimateResponse(quote.distanceKm(), quote.fare(), quote.currency());
    }

    /** Persists the ride in MATCHING; the ride.requested event is published only after commit. */
    public RideResponse requestRide(RideRequest request) {
        if (rides.existsByRiderIdAndStatusIn(request.riderId(), ACTIVE_STATUSES)) {
            throw new ActiveRideExistsException(request.riderId());
        }
        FareCalculator.FareQuote quote = fareCalculator.quote(
                request.pickupLatitude(), request.pickupLongitude(),
                request.dropLatitude(), request.dropLongitude());

        Instant now = clock.instant();
        Ride ride = new Ride(request.riderId(),
                request.pickupLatitude(), request.pickupLongitude(), request.pickupAddress().trim(),
                request.dropLatitude(), request.dropLongitude(), request.dropAddress().trim(),
                quote.distanceKm(), quote.fare(), now);
        ride.startMatching(now);
        Ride saved = rides.save(ride);

        log.info("Ride {} requested by {} ({} km, fare {})", saved.getId(), saved.getRiderId(),
                quote.distanceKm(), quote.fare());
        return publish(saved, true);
    }

    /** Called when the matching service found and reserved a driver. Idempotent for redelivered events. */
    public void assignDriver(String rideId, String driverId) {
        Ride ride = load(rideId);
        if (ride.getStatus() != RideStatus.MATCHING) {
            if (!driverId.equals(ride.getDriverId())) {
                log.info("Ride {} is {}; releasing late-matched driver {}", rideId, ride.getStatus(), driverId);
                events.publishEvent(new DriverReleaseRequested(rideId, ride.getRiderId(), driverId));
            }
            return;
        }
        ride.assignDriver(driverId, clock.instant());
        log.info("Ride {} accepted by driver {}", rideId, driverId);
        publish(ride, false);
    }

    /** Called when matching found nobody; ignored if the ride already moved on. */
    public void cancelUnmatched(String rideId, String reason) {
        Ride ride = load(rideId);
        if (ride.getStatus() != RideStatus.MATCHING) {
            return;
        }
        ride.cancel(reason, clock.instant());
        log.info("Ride {} cancelled: {}", rideId, reason);
        publish(ride, false);
    }

    public RideResponse markDriverArriving(String rideId) {
        Ride ride = load(rideId);
        ride.markDriverArriving(clock.instant());
        return publish(ride, false);
    }

    public RideResponse startRide(String rideId) {
        Ride ride = load(rideId);
        ride.start(clock.instant());
        return publish(ride, false);
    }

    public RideResponse completeRide(String rideId) {
        Ride ride = load(rideId);
        ride.complete(clock.instant());
        log.info("Ride {} completed, fare {}", rideId, ride.getActualFare());
        return publish(ride, false);
    }

    public RideResponse cancelRide(String rideId, String reason) {
        Ride ride = load(rideId);
        ride.cancel(reason == null || reason.isBlank() ? "Cancelled" : reason.trim(), clock.instant());
        log.info("Ride {} cancelled: {}", rideId, ride.getCancellationReason());
        return publish(ride, false);
    }

    /** Safety net: cancels one ride still stuck in MATCHING past the timeout. Own transaction per ride. */
    public boolean cancelIfMatchingTimedOut(String rideId) {
        Ride ride = load(rideId);
        Instant cutoff = clock.instant().minus(matching.timeout());
        if (ride.getStatus() != RideStatus.MATCHING || ride.getUpdatedAt().isAfter(cutoff)) {
            return false;
        }
        ride.cancel(NO_DRIVER_TIMEOUT_REASON, clock.instant());
        publish(ride, false);
        return true;
    }

    @Transactional(readOnly = true)
    public List<String> findTimedOutMatchingRideIds() {
        Instant cutoff = clock.instant().minus(matching.timeout());
        return rides.findTop100ByStatusAndUpdatedAtBefore(RideStatus.MATCHING, cutoff).stream()
                .map(Ride::getId)
                .toList();
    }

    @Transactional(readOnly = true)
    public RideResponse getRide(String rideId) {
        return RideResponse.from(load(rideId));
    }

    @Transactional(readOnly = true)
    public List<RideResponse> getRidesByRider(String riderId) {
        return rides.findTop50ByRiderIdOrderByCreatedAtDesc(riderId).stream().map(RideResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<RideResponse> getRidesByDriver(String driverId) {
        return rides.findTop50ByDriverIdOrderByCreatedAtDesc(driverId).stream().map(RideResponse::from).toList();
    }

    private Ride load(String rideId) {
        return rides.findById(rideId).orElseThrow(() -> new RideNotFoundException(rideId));
    }

    private RideResponse publish(Ride ride, boolean newlyRequested) {
        RideResponse snapshot = RideResponse.from(ride);
        events.publishEvent(new RideChangedEvent(snapshot, newlyRequested));
        return snapshot;
    }
}
