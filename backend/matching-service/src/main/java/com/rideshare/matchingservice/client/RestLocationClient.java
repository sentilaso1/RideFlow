package com.rideshare.matchingservice.client;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class RestLocationClient implements LocationClient {

    private static final ParameterizedTypeReference<List<NearbyDriver>> DRIVER_LIST = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;

    public RestLocationClient(RestClient locationRestClient) {
        this.restClient = locationRestClient;
    }

    @Override
    public List<NearbyDriver> findAvailableDrivers(double latitude, double longitude, double radiusKm, int limit) {
        List<NearbyDriver> drivers = restClient.get()
                .uri(uri -> uri.path("/api/v1/locations/drivers/nearby")
                        .queryParam("latitude", latitude)
                        .queryParam("longitude", longitude)
                        .queryParam("radius", radiusKm)
                        .queryParam("limit", limit)
                        .queryParam("availableOnly", true)
                        .build())
                .retrieve()
                .body(DRIVER_LIST);
        return drivers == null ? List.of() : drivers;
    }

    @Override
    public boolean reserveDriver(String driverId, String rideId) {
        ReservationResult result = restClient.post()
                .uri("/internal/v1/drivers/{driverId}/reservations", driverId)
                .body(Map.of("rideId", rideId))
                .retrieve()
                .body(ReservationResult.class);
        return result != null && result.reserved();
    }
}
