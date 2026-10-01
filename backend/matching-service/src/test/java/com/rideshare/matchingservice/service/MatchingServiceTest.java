package com.rideshare.matchingservice.service;

import com.rideshare.matchingservice.client.LocationClient;
import com.rideshare.matchingservice.client.NearbyDriver;
import com.rideshare.matchingservice.config.MatchingProperties;
import com.rideshare.matchingservice.event.RideMatchedEvent;
import com.rideshare.matchingservice.event.RideRequestedEvent;
import com.rideshare.matchingservice.event.RideUnmatchedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchingServiceTest {

    private static final MatchingProperties PROPS = new MatchingProperties(5.0, 10, 0.7, "http://unused");
    private static final RideRequestedEvent REQUEST =
            new RideRequestedEvent("ride-1", "rider:1", 12.97, 77.59, "MG Road", 12.93, 77.62, "Koramangala");

    private LocationClient locationClient;
    private MatchResultPublisher publisher;
    private MatchingService service;

    @BeforeEach
    void setUp() {
        locationClient = mock(LocationClient.class);
        publisher = mock(MatchResultPublisher.class);
        DriverScorer scorer = new DriverScorer(id -> 4.5, PROPS);
        service = new MatchingService(locationClient, scorer, publisher, PROPS);
    }

    @Test
    void reservesBestDriverAndPublishesMatch() {
        when(locationClient.findAvailableDrivers(anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(new NearbyDriver("far", 1, 1, 3.0), new NearbyDriver("near", 2, 2, 0.4)));
        when(locationClient.reserveDriver("near", "ride-1")).thenReturn(true);

        service.match(REQUEST);

        ArgumentCaptor<RideMatchedEvent> matched = ArgumentCaptor.forClass(RideMatchedEvent.class);
        verify(publisher).matched(matched.capture());
        assertThat(matched.getValue().driverId()).isEqualTo("near");
        assertThat(matched.getValue().distanceToPickupKm()).isEqualTo(0.4);
        verify(locationClient, never()).reserveDriver(eq("far"), any());
    }

    @Test
    void fallsBackToNextDriverWhenBestWasTaken() {
        when(locationClient.findAvailableDrivers(anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(new NearbyDriver("near", 0, 0, 0.4), new NearbyDriver("second", 0, 0, 1.0)));
        when(locationClient.reserveDriver("near", "ride-1")).thenReturn(false);
        when(locationClient.reserveDriver("second", "ride-1")).thenReturn(true);

        service.match(REQUEST);

        ArgumentCaptor<RideMatchedEvent> matched = ArgumentCaptor.forClass(RideMatchedEvent.class);
        verify(publisher).matched(matched.capture());
        assertThat(matched.getValue().driverId()).isEqualTo("second");
    }

    @Test
    void publishesUnmatchedWhenNoDriversNearby() {
        when(locationClient.findAvailableDrivers(anyDouble(), anyDouble(), anyDouble(), anyInt())).thenReturn(List.of());

        service.match(REQUEST);

        ArgumentCaptor<RideUnmatchedEvent> unmatched = ArgumentCaptor.forClass(RideUnmatchedEvent.class);
        verify(publisher).unmatched(unmatched.capture());
        assertThat(unmatched.getValue().rideId()).isEqualTo("ride-1");
        assertThat(unmatched.getValue().reason()).isEqualTo(MatchingService.NO_DRIVERS_REASON);
        verify(publisher, never()).matched(any());
    }

    @Test
    void publishesUnmatchedWhenEveryCandidateIsTaken() {
        when(locationClient.findAvailableDrivers(anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(new NearbyDriver("a", 0, 0, 1.0)));
        when(locationClient.reserveDriver("a", "ride-1")).thenReturn(false);

        service.match(REQUEST);

        verify(publisher).unmatched(any());
        verify(publisher, never()).matched(any());
    }
}
