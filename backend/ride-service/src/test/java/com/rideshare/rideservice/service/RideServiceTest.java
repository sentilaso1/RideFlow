package com.rideshare.rideservice.service;

import com.rideshare.rideservice.config.FareProperties;
import com.rideshare.rideservice.config.MatchingProperties;
import com.rideshare.rideservice.domain.ActiveRideExistsException;
import com.rideshare.rideservice.domain.InvalidRideStateException;
import com.rideshare.rideservice.domain.Ride;
import com.rideshare.rideservice.domain.RideNotFoundException;
import com.rideshare.rideservice.domain.RideRepository;
import com.rideshare.rideservice.domain.RideStatus;
import com.rideshare.rideservice.dto.RideRequest;
import com.rideshare.rideservice.dto.RideResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RideServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");

    private RideRepository repository;
    private ApplicationEventPublisher events;
    private RideService service;

    @BeforeEach
    void setUp() {
        repository = mock(RideRepository.class);
        events = mock(ApplicationEventPublisher.class);
        when(repository.save(any(Ride.class))).thenAnswer(invocation -> invocation.getArgument(0));
        FareCalculator fares = new FareCalculator(new FareProperties(new BigDecimal("50"), new BigDecimal("12"), "INR"));
        service = new RideService(repository, fares, events, new MatchingProperties(Duration.ofSeconds(60)),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static RideRequest request() {
        return new RideRequest("rider:1", 12.9716, 77.5946, " MG Road ", 12.9352, 77.6245, "Koramangala");
    }

    private static Ride matchingRide() {
        Ride ride = new Ride("rider:1", 12.9716, 77.5946, "MG Road", 12.9352, 77.6245, "Koramangala",
                5.2, new BigDecimal("112.40"), NOW.minusSeconds(5));
        ride.startMatching(NOW.minusSeconds(5));
        return ride;
    }

    @Test
    void requestRideSavesInMatchingAndAnnouncesIt() {
        when(repository.existsByRiderIdAndStatusIn(anyString(), anyCollection())).thenReturn(false);

        RideResponse response = service.requestRide(request());

        assertThat(response.status()).isEqualTo(RideStatus.MATCHING);
        assertThat(response.pickupAddress()).isEqualTo("MG Road");
        assertThat(response.estimatedFare()).isPositive();
        assertThat(response.createdAt()).isEqualTo(NOW);

        ArgumentCaptor<RideChangedEvent> event = ArgumentCaptor.forClass(RideChangedEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().newlyRequested()).isTrue();
    }

    @Test
    void riderWithActiveRideCannotRequestAnother() {
        when(repository.existsByRiderIdAndStatusIn(anyString(), anyCollection())).thenReturn(true);

        assertThatThrownBy(() -> service.requestRide(request())).isInstanceOf(ActiveRideExistsException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void assignDriverAcceptsMatchingRide() {
        Ride ride = matchingRide();
        when(repository.findById("r1")).thenReturn(Optional.of(ride));

        service.assignDriver("r1", "driver:2");

        assertThat(ride.getStatus()).isEqualTo(RideStatus.ACCEPTED);
        assertThat(ride.getDriverId()).isEqualTo("driver:2");
        verify(events).publishEvent(any(RideChangedEvent.class));
    }

    @Test
    void lateMatchForCancelledRideReleasesTheDriver() {
        Ride ride = matchingRide();
        ride.cancel("Cancelled by rider", NOW);
        when(repository.findById("r1")).thenReturn(Optional.of(ride));

        service.assignDriver("r1", "driver:2");

        assertThat(ride.getStatus()).isEqualTo(RideStatus.CANCELLED);
        ArgumentCaptor<DriverReleaseRequested> release = ArgumentCaptor.forClass(DriverReleaseRequested.class);
        verify(events).publishEvent(release.capture());
        assertThat(release.getValue().driverId()).isEqualTo("driver:2");
    }

    @Test
    void redeliveredMatchIsIgnored() {
        Ride ride = matchingRide();
        ride.assignDriver("driver:2", NOW);
        when(repository.findById("r1")).thenReturn(Optional.of(ride));

        service.assignDriver("r1", "driver:2");

        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void startingAnUnmatchedRideIsRejected() {
        when(repository.findById("r1")).thenReturn(Optional.of(matchingRide()));
        assertThatThrownBy(() -> service.startRide("r1")).isInstanceOf(InvalidRideStateException.class);
    }

    @Test
    void unknownRideIsNotFound() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getRide("missing")).isInstanceOf(RideNotFoundException.class);
    }

    @Test
    void blankCancelReasonGetsDefault() {
        Ride ride = matchingRide();
        when(repository.findById("r1")).thenReturn(Optional.of(ride));

        RideResponse response = service.cancelRide("r1", "  ");

        assertThat(response.status()).isEqualTo(RideStatus.CANCELLED);
        assertThat(response.cancellationReason()).isEqualTo("Cancelled");
    }

    @Test
    void timeoutSweepCancelsOnlyStaleMatchingRides() {
        Ride stale = new Ride("rider:1", 1, 1, "a", 2, 2, "b", 1, BigDecimal.TEN, NOW.minusSeconds(120));
        stale.startMatching(NOW.minusSeconds(120));
        Ride fresh = matchingRide();
        when(repository.findById("stale")).thenReturn(Optional.of(stale));
        when(repository.findById("fresh")).thenReturn(Optional.of(fresh));

        assertThat(service.cancelIfMatchingTimedOut("stale")).isTrue();
        assertThat(service.cancelIfMatchingTimedOut("fresh")).isFalse();
        assertThat(stale.getCancellationReason()).isEqualTo(RideService.NO_DRIVER_TIMEOUT_REASON);
        assertThat(fresh.getStatus()).isEqualTo(RideStatus.MATCHING);
    }

    @Test
    void historyIsMappedToResponses() {
        when(repository.findTop50ByRiderIdOrderByCreatedAtDesc("rider:1")).thenReturn(List.of(matchingRide()));
        assertThat(service.getRidesByRider("rider:1")).singleElement()
                .extracting(RideResponse::status).isEqualTo(RideStatus.MATCHING);
    }
}
