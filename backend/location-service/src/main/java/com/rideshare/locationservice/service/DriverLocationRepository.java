package com.rideshare.locationservice.service;

import com.rideshare.locationservice.dto.DriverSnapshot;
import com.rideshare.locationservice.dto.NearbyDriverResponse;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoLocation;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoSearchCommandArgs;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * All Redis access for driver state.
 * <ul>
 *   <li>{@code {drivers}:locations} - GEO sorted set of online drivers (GEOADD / GEOSEARCH / ZREM)</li>
 *   <li>{@code {drivers}:busy} - hash of driverId to the rideId they are serving</li>
 * </ul>
 * The shared {@code {drivers}} hash tag keeps both keys in one slot, so the Lua scripts also work on Redis Cluster.
 */
@Repository
public class DriverLocationRepository {

    static final String GEO_KEY = "{drivers}:locations";
    static final String BUSY_KEY = "{drivers}:busy";

    private final StringRedisTemplate redis;
    private final RedisScript<Long> reserveDriverScript;
    private final RedisScript<Long> releaseDriverScript;

    public DriverLocationRepository(StringRedisTemplate redis,
                                    RedisScript<Long> reserveDriverScript,
                                    RedisScript<Long> releaseDriverScript) {
        this.redis = redis;
        this.reserveDriverScript = reserveDriverScript;
        this.releaseDriverScript = releaseDriverScript;
    }

    public void upsertPosition(String driverId, double latitude, double longitude) {
        // GEO uses (longitude, latitude) order.
        redis.opsForGeo().add(GEO_KEY, new Point(longitude, latitude), driverId);
    }

    public void remove(String driverId) {
        redis.opsForGeo().remove(GEO_KEY, driverId);
        redis.opsForHash().delete(BUSY_KEY, driverId);
    }

    public List<DriverSnapshot> findAll() {
        Set<String> ids = redis.opsForZSet().range(GEO_KEY, 0, -1);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<String> idList = new ArrayList<>(ids);
        List<Point> positions = redis.opsForGeo().position(GEO_KEY, idList.toArray(String[]::new));
        Map<Object, Object> busy = redis.opsForHash().entries(BUSY_KEY);

        List<DriverSnapshot> drivers = new ArrayList<>(idList.size());
        Iterator<String> idIterator = idList.iterator();
        for (Point point : positions == null ? List.<Point>of() : positions) {
            String driverId = idIterator.next();
            if (point == null) {
                continue; // removed between the two reads
            }
            Object rideId = busy.get(driverId);
            drivers.add(new DriverSnapshot(driverId, point.getY(), point.getX(), rideId != null,
                    rideId == null ? null : rideId.toString()));
        }
        return drivers;
    }

    public List<NearbyDriverResponse> searchNearby(double latitude, double longitude, double radiusKm, int limit) {
        GeoResults<GeoLocation<String>> results = redis.opsForGeo().search(
                GEO_KEY,
                GeoReference.fromCoordinate(new Point(longitude, latitude)),
                new Distance(radiusKm, Metrics.KILOMETERS),
                GeoSearchCommandArgs.newGeoSearchArgs()
                        .includeCoordinates()
                        .includeDistance()
                        .sortAscending()
                        .limit(limit));

        if (results == null) {
            return List.of();
        }
        return results.getContent().stream()
                .map(result -> {
                    GeoLocation<String> location = result.getContent();
                    return new NearbyDriverResponse(
                            location.getName(),
                            location.getPoint().getY(),
                            location.getPoint().getX(),
                            result.getDistance().getValue());
                })
                .toList();
    }

    public Set<String> busyDriverIds() {
        return redis.opsForHash().keys(BUSY_KEY).stream()
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());
    }

    /** @return 1 reserved, 0 busy with another ride, -1 driver offline */
    public long reserve(String driverId, String rideId) {
        Long result = redis.execute(reserveDriverScript, List.of(GEO_KEY, BUSY_KEY), driverId, rideId);
        return result == null ? -1 : result;
    }

    public boolean release(String driverId, String rideId) {
        Long result = redis.execute(releaseDriverScript, List.of(BUSY_KEY), driverId, rideId);
        return result != null && result > 0;
    }
}
