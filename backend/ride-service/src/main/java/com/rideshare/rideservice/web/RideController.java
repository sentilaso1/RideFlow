package com.rideshare.rideservice.web;

import com.rideshare.rideservice.dto.FareEstimateRequest;
import com.rideshare.rideservice.dto.FareEstimateResponse;
import com.rideshare.rideservice.dto.RideRequest;
import com.rideshare.rideservice.dto.RideResponse;
import com.rideshare.rideservice.service.RideService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/rides")
public class RideController {

    private final RideService rideService;

    public RideController(RideService rideService) {
        this.rideService = rideService;
    }

    @PostMapping("/estimate")
    public FareEstimateResponse estimateFare(@Valid @RequestBody FareEstimateRequest request) {
        return rideService.estimateFare(request);
    }

    @PostMapping("/request")
    public ResponseEntity<RideResponse> requestRide(@Valid @RequestBody RideRequest request) {
        RideResponse ride = rideService.requestRide(request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/rides/{rideId}").buildAndExpand(ride.id()).toUri();
        return ResponseEntity.created(location).body(ride);
    }

    @GetMapping("/{rideId}")
    public RideResponse getRide(@PathVariable String rideId) {
        return rideService.getRide(rideId);
    }

    @GetMapping("/rider/{riderId}")
    public List<RideResponse> getRidesByRider(@PathVariable String riderId) {
        return rideService.getRidesByRider(riderId);
    }

    @GetMapping("/driver/{driverId}")
    public List<RideResponse> getRidesByDriver(@PathVariable String driverId) {
        return rideService.getRidesByDriver(driverId);
    }

    @PutMapping("/{rideId}/arriving")
    public RideResponse markDriverArriving(@PathVariable String rideId) {
        return rideService.markDriverArriving(rideId);
    }

    @PutMapping("/{rideId}/start")
    public RideResponse startRide(@PathVariable String rideId) {
        return rideService.startRide(rideId);
    }

    @PutMapping("/{rideId}/complete")
    public RideResponse completeRide(@PathVariable String rideId) {
        return rideService.completeRide(rideId);
    }

    @PutMapping("/{rideId}/cancel")
    public RideResponse cancelRide(@PathVariable String rideId,
                                   @RequestParam(required = false) @Size(max = 255) String reason) {
        return rideService.cancelRide(rideId, reason);
    }
}
