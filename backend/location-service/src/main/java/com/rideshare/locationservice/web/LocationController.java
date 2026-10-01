package com.rideshare.locationservice.web;

import com.rideshare.locationservice.dto.DriverLocationRequest;
import com.rideshare.locationservice.dto.DriverSnapshot;
import com.rideshare.locationservice.dto.MessageResponse;
import com.rideshare.locationservice.dto.NearbyDriverResponse;
import com.rideshare.locationservice.service.LocationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public driver-location API, exposed through the gateway. */
@RestController
@RequestMapping("/api/v1/locations/drivers")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    /** The driver's phone calls this every few seconds. */
    @PostMapping("/update")
    public MessageResponse updateDriverLocation(@Valid @RequestBody DriverLocationRequest request) {
        locationService.updateDriverLocation(request.driverId(), request.latitude(), request.longitude());
        return new MessageResponse("Driver location updated");
    }

    /** Every online driver with their busy state, for the fleet and rider maps. */
    @GetMapping({"", "/"})
    public List<DriverSnapshot> getAllDrivers() {
        return locationService.getAllDrivers();
    }

    @GetMapping("/nearby")
    public List<NearbyDriverResponse> getNearbyDrivers(
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double latitude,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double longitude,
            @RequestParam(defaultValue = "5.0") @DecimalMin("0.1") @DecimalMax("50.0") double radius,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit,
            @RequestParam(defaultValue = "false") boolean availableOnly) {
        return locationService.findNearbyDrivers(latitude, longitude, radius, limit, availableOnly);
    }

    @DeleteMapping("/{driverId}")
    public MessageResponse removeDriver(@PathVariable String driverId) {
        locationService.removeDriver(driverId);
        return new MessageResponse("Driver removed");
    }
}
