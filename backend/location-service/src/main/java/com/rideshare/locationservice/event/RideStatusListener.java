package com.rideshare.locationservice.event;

import com.rideshare.locationservice.service.LocationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Frees a driver once their ride is completed or cancelled. */
@Component
public class RideStatusListener {

    private final LocationService locationService;

    public RideStatusListener(LocationService locationService) {
        this.locationService = locationService;
    }

    @KafkaListener(topics = "${rideshare.kafka.topics.ride-status-changed}")
    public void onRideStatusChanged(RideStatusChangedEvent event) {
        if (event.isTerminal() && event.driverId() != null) {
            locationService.releaseDriver(event.driverId(), event.rideId());
        }
    }
}
