package com.rideshare.locationservice.service;

import com.rideshare.locationservice.dto.DriverSnapshot;
import com.rideshare.locationservice.dto.NearbyDriverResponse;
import com.rideshare.locationservice.dto.ReservationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class LocationService {

    private static final Logger log = LoggerFactory.getLogger(LocationService.class);

    /** Over-fetch when filtering busy drivers so the caller still gets {@code limit} free ones. */
    private static final int AVAILABLE_OVERFETCH_FACTOR = 5;

    private final DriverLocationRepository repository;

    public LocationService(DriverLocationRepository repository) {
        this.repository = repository;
    }

    /** Called by the driver app every few seconds. */
    public void updateDriverLocation(String driverId, double latitude, double longitude) {
        repository.upsertPosition(driverId, latitude, longitude);
        log.debug("Location updated for driver {} at {},{}", driverId, latitude, longitude);
    }

    public List<DriverSnapshot> getAllDrivers() {
        return repository.findAll();
    }

    public List<NearbyDriverResponse> findNearbyDrivers(double latitude, double longitude, double radiusKm,
                                                        int limit, boolean availableOnly) {
        if (!availableOnly) {
            return repository.searchNearby(latitude, longitude, radiusKm, limit);
        }
        Set<String> busy = repository.busyDriverIds();
        List<NearbyDriverResponse> available = repository
                .searchNearby(latitude, longitude, radiusKm, limit * AVAILABLE_OVERFETCH_FACTOR)
                .stream()
                .filter(driver -> !busy.contains(driver.driverId()))
                .limit(limit)
                .toList();
        log.info("Found {} available drivers within {} km of {},{}", available.size(), radiusKm, latitude, longitude);
        return available;
    }

    /** Driver goes offline: drop their position and any busy marker. */
    public void removeDriver(String driverId) {
        repository.remove(driverId);
        log.info("Driver {} went offline", driverId);
    }

    /** Atomically claims a free, online driver for a ride. Safe under concurrent matchers. */
    public ReservationResponse reserveDriver(String driverId, String rideId) {
        long outcome = repository.reserve(driverId, rideId);
        if (outcome == 1) {
            log.info("Driver {} reserved for ride {}", driverId, rideId);
            return new ReservationResponse(driverId, rideId, true, null);
        }
        String reason = outcome == 0 ? "DRIVER_BUSY" : "DRIVER_OFFLINE";
        log.info("Could not reserve driver {} for ride {}: {}", driverId, rideId, reason);
        return new ReservationResponse(driverId, rideId, false, reason);
    }

    public void releaseDriver(String driverId, String rideId) {
        if (repository.release(driverId, rideId)) {
            log.info("Driver {} released from ride {}", driverId, rideId);
        }
    }
}
