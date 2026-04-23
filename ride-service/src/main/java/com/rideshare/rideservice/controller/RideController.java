package com.rideshare.rideservice.controller;

import com.rideshare.rideservice.dto.RideRequest;
import com.rideshare.rideservice.dto.RideResponse;
import com.rideshare.rideservice.service.RideService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rides")
@Slf4j
@RequiredArgsConstructor
public class RideController {

    private final RideService rideService;


    //Rider requests a new ride

    public ResponseEntity<RideResponse> requestRide(
            @Valid @RequestBody RideRequest rideRequest){
        
    }
}
