package com.rideshare.locationservice.service;

import com.rideshare.locationservice.dto.NearbyDriverResponse;
import com.rideshare.locationservice.dto.ReservationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocationServiceTest {

    private DriverLocationRepository repository;
    private LocationService service;

    @BeforeEach
    void setUp() {
        repository = mock(DriverLocationRepository.class);
        service = new LocationService(repository);
    }

    @Test
    void availableOnlyFiltersBusyDriversAndRespectsLimit() {
        when(repository.busyDriverIds()).thenReturn(Set.of("d2"));
        when(repository.searchNearby(anyDouble(), anyDouble(), anyDouble(), anyInt())).thenReturn(List.of(
                new NearbyDriverResponse("d1", 0, 0, 0.1),
                new NearbyDriverResponse("d2", 0, 0, 0.2),
                new NearbyDriverResponse("d3", 0, 0, 0.3),
                new NearbyDriverResponse("d4", 0, 0, 0.4)));

        List<NearbyDriverResponse> result = service.findNearbyDrivers(12.9, 77.6, 5, 2, true);

        assertThat(result).extracting(NearbyDriverResponse::driverId).containsExactly("d1", "d3");
    }

    @Test
    void withoutAvailableOnlyBusyStateIsNotQueried() {
        when(repository.searchNearby(12.9, 77.6, 5, 10)).thenReturn(List.of());
        service.findNearbyDrivers(12.9, 77.6, 5, 10, false);
        verify(repository, never()).busyDriverIds();
    }

    @Test
    void reservationOutcomesAreTranslated() {
        when(repository.reserve("d1", "r1")).thenReturn(1L);
        when(repository.reserve("d2", "r1")).thenReturn(0L);
        when(repository.reserve("d3", "r1")).thenReturn(-1L);

        assertThat(service.reserveDriver("d1", "r1")).extracting(ReservationResponse::reserved).isEqualTo(true);
        assertThat(service.reserveDriver("d2", "r1").reason()).isEqualTo("DRIVER_BUSY");
        assertThat(service.reserveDriver("d3", "r1").reason()).isEqualTo("DRIVER_OFFLINE");
    }
}
