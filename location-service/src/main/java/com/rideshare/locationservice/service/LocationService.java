package com.rideshare.locationservice.service;

import com.rideshare.locationservice.dto.DriverLocationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class LocationService {

    private final RedisTemplate<String, String> redisTemplate;
    //Redis key for all driver locations
    private static final String DRIVERS_GEO_KEY = "drivers:locations";

    /**
     * Update driver location in Redis
     * And calling every 3 seconds by drivers phone
     * And Map to Redis GEOADD command
     */

    public void updateDriverLocation(DriverLocationRequest driverLocationRequest){
        log.info("Updating location for driver: {}", driverLocationRequest.getDriverId());

        // IMP NOTE - longitude is always FIRST, and latitude is SECOND, since GeoSpatial standard
        Point driverPoint = new Point(
                driverLocationRequest.getLongitude(),
                driverLocationRequest.getLatitude()
        );

        redisTemplate.opsForGeo().add(
                DRIVERS_GEO_KEY,
                driverPoint,
                driverLocationRequest.getDriverId()
        );
    }
}
