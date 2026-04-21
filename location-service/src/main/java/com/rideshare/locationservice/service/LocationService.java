package com.rideshare.locationservice.service;

import com.rideshare.locationservice.dto.DriverLocationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class LocationService {

    //Redis key for all driver locations
    private static final String DRIVERS_GEO_KEY = "drivers:locations";

    /**
     * Update driver location in Redis
     * And calling every 3 seconds by drivers phone
     * And Map to Redis GEOADD command
     */

    public void updateDriverLocation(DriverLocationRequest driverLocationRequest){
        log.info("Updating location for driver: {}", driverLocationRequest.getDriverId());
    }
}
