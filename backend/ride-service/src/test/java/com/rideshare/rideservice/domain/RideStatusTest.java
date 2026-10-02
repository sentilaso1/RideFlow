package com.rideshare.rideservice.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.rideshare.rideservice.domain.RideStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

class RideStatusTest {

    @Test
    void happyPathIsAllowed() {
        assertThat(REQUESTED.canTransitionTo(MATCHING)).isTrue();
        assertThat(MATCHING.canTransitionTo(ACCEPTED)).isTrue();
        assertThat(ACCEPTED.canTransitionTo(DRIVER_ARRIVING)).isTrue();
        assertThat(DRIVER_ARRIVING.canTransitionTo(RIDE_STARTED)).isTrue();
        assertThat(RIDE_STARTED.canTransitionTo(COMPLETED)).isTrue();
    }

    @Test
    void tripCannotStartBeforeDriverReachesPickup() {
        assertThat(ACCEPTED.canTransitionTo(RIDE_STARTED)).isFalse();
    }

    @Test
    void tripInProgressCannotBeCancelled() {
        assertThat(RIDE_STARTED.canTransitionTo(CANCELLED)).isFalse();
    }

    @Test
    void cannotSkipMatching() {
        assertThat(REQUESTED.canTransitionTo(ACCEPTED)).isFalse();
        assertThat(MATCHING.canTransitionTo(RIDE_STARTED)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(RideStatus.class)
    void terminalStatesAreFinal(RideStatus next) {
        assertThat(COMPLETED.canTransitionTo(next)).isFalse();
        assertThat(CANCELLED.canTransitionTo(next)).isFalse();
    }

    @Test
    void onlyCompletedAndCancelledAreTerminal() {
        assertThat(RideStatus.values())
                .filteredOn(RideStatus::isTerminal)
                .containsExactlyInAnyOrder(COMPLETED, CANCELLED);
    }
}
