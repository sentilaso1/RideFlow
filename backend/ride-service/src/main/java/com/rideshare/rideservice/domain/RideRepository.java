package com.rideshare.rideservice.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface RideRepository extends JpaRepository<Ride, String> {

    List<Ride> findTop50ByRiderIdOrderByCreatedAtDesc(String riderId);

    List<Ride> findTop50ByDriverIdOrderByCreatedAtDesc(String driverId);

    boolean existsByRiderIdAndStatusIn(String riderId, Collection<RideStatus> statuses);

    /** Rides that have sat in one status since before {@code cutoff}; served by idx_rides_status_updated. */
    List<Ride> findTop100ByStatusAndUpdatedAtBefore(RideStatus status, Instant cutoff);
}
