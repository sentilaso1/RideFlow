package com.rideshare.locationservice.service;

import com.rideshare.locationservice.dto.DriverLocationRequest;
import com.rideshare.locationservice.dto.NearByDriverResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

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

        log.info("Location update for driver: {}", driverLocationRequest.getDriverId());
    }

    /**
     * Find nearby drivers within the given radius.
     * Called by Matching service on ride request.
     * Maps to Redis GEORADIUS command.
     */

    public List<NearByDriverResponse> findNearbyDrivers(
            double latitude, double longitude, double radiusInKm){

        log.info("Finding drivers near lat: {} long:{} within {}Km",
                latitude, longitude, radiusInKm);

        Circle searchArea = new Circle(
                new Point(longitude, latitude),
                new Distance(radiusInKm, Metrics. KILOMETERS)
        );
    }
}
