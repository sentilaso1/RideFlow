package com.rideshare.locationservice.web;

import com.rideshare.locationservice.dto.ReservationRequest;
import com.rideshare.locationservice.dto.ReservationResponse;
import com.rideshare.locationservice.service.LocationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service API used by the matching service. The gateway does not route {@code /internal/**},
 * so this is reachable only from inside the cluster network.
 */
@RestController
@RequestMapping("/internal/v1/drivers")
public class InternalDriverController {

    private final LocationService locationService;

    public InternalDriverController(LocationService locationService) {
        this.locationService = locationService;
    }

    @PostMapping("/{driverId}/reservations")
    public ReservationResponse reserve(@PathVariable String driverId, @Valid @RequestBody ReservationRequest request) {
        return locationService.reserveDriver(driverId, request.rideId());
    }
}
