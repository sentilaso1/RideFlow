package com.rideshare.matchingservice.client;

import java.util.List;

/** What matching needs from the location service. An interface so the algorithm can be unit-tested. */
public interface LocationClient {

    List<NearbyDriver> findAvailableDrivers(double latitude, double longitude, double radiusKm, int limit);

    /** Atomically claims the driver for the ride; false if someone else got them first or they went offline. */
    boolean reserveDriver(String driverId, String rideId);
}
