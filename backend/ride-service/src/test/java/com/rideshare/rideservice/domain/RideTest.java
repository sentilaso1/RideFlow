package com.rideshare.rideservice.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RideTest {

    private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");

    static Ride newRide() {
        return new Ride("rider:1", 12.9716, 77.5946, "MG Road", 12.9352, 77.6245, "Koramangala",
                5.2, new BigDecimal("112.40"), T0);
    }

    @Test
    void fullLifecycleRecordsTimestampsAndFare() {
        Ride ride = newRide();
        ride.startMatching(T0);
        ride.assignDriver("driver:7", T0.plusSeconds(5));
        ride.markDriverArriving(T0.plusSeconds(60));
        ride.start(T0.plusSeconds(120));
        ride.complete(T0.plusSeconds(900));

        assertThat(ride.getStatus()).isEqualTo(RideStatus.COMPLETED);
        assertThat(ride.getDriverId()).isEqualTo("driver:7");
        assertThat(ride.getAcceptedAt()).isEqualTo(T0.plusSeconds(5));
        assertThat(ride.getStartedAt()).isEqualTo(T0.plusSeconds(120));
        assertThat(ride.getCompletedAt()).isEqualTo(T0.plusSeconds(900));
        assertThat(ride.getUpdatedAt()).isEqualTo(T0.plusSeconds(900));
        assertThat(ride.getActualFare()).isEqualByComparingTo("112.40");
    }

    @Test
    void actualFareIsUnsetUntilCompletion() {
        Ride ride = newRide();
        ride.startMatching(T0);
        assertThat(ride.getActualFare()).isNull();
    }

    @Test
    void cancelRecordsReason() {
        Ride ride = newRide();
        ride.startMatching(T0);
        ride.cancel("Cancelled by rider", T0.plusSeconds(10));

        assertThat(ride.getStatus()).isEqualTo(RideStatus.CANCELLED);
        assertThat(ride.getCancellationReason()).isEqualTo("Cancelled by rider");
        assertThat(ride.getCancelledAt()).isEqualTo(T0.plusSeconds(10));
    }

    @Test
    void illegalTransitionIsRejectedAndStateUnchanged() {
        Ride ride = newRide();
        ride.startMatching(T0);

        assertThatThrownBy(() -> ride.start(T0.plusSeconds(1)))
                .isInstanceOf(InvalidRideStateException.class)
                .hasMessageContaining("MATCHING")
                .hasMessageContaining("RIDE_STARTED");
        assertThat(ride.getStatus()).isEqualTo(RideStatus.MATCHING);
        assertThat(ride.getStartedAt()).isNull();
    }
}
