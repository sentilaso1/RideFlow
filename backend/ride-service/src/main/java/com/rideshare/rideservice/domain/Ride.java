package com.rideshare.rideservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Ride aggregate. All state changes go through the intention-revealing methods below, which enforce
 * {@link RideStatus#canTransitionTo} - nothing outside this class sets {@code status} directly.
 * Schema is owned by Flyway ({@code db/migration}); column names follow Spring's snake_case naming.
 */
@Entity
@Table(name = "rides")
public class Ride {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** Optimistic lock: a concurrent update (e.g. driver match vs. rider cancel) fails instead of being lost. */
    @Version
    private Long version;

    @Column(nullable = false, length = 64)
    private String riderId;

    @Column(length = 64)
    private String driverId;

    private double pickupLatitude;
    private double pickupLongitude;

    @Column(nullable = false)
    private String pickupAddress;

    private double dropLatitude;
    private double dropLongitude;

    @Column(nullable = false)
    private String dropAddress;

    private double distanceKm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RideStatus status;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal estimatedFare;

    @Column(precision = 10, scale = 2)
    private BigDecimal actualFare;

    private String cancellationReason;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant acceptedAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant cancelledAt;

    protected Ride() {
        // for JPA
    }

    public Ride(String riderId,
                double pickupLatitude, double pickupLongitude, String pickupAddress,
                double dropLatitude, double dropLongitude, String dropAddress,
                double distanceKm, BigDecimal estimatedFare, Instant now) {
        this.riderId = riderId;
        this.pickupLatitude = pickupLatitude;
        this.pickupLongitude = pickupLongitude;
        this.pickupAddress = pickupAddress;
        this.dropLatitude = dropLatitude;
        this.dropLongitude = dropLongitude;
        this.dropAddress = dropAddress;
        this.distanceKm = distanceKm;
        this.estimatedFare = estimatedFare;
        this.status = RideStatus.REQUESTED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    // ── State transitions ──

    public void startMatching(Instant now) {
        transitionTo(RideStatus.MATCHING, now);
    }

    public void assignDriver(String driverId, Instant now) {
        transitionTo(RideStatus.ACCEPTED, now);
        this.driverId = driverId;
        this.acceptedAt = now;
    }

    public void markDriverArriving(Instant now) {
        transitionTo(RideStatus.DRIVER_ARRIVING, now);
    }

    public void start(Instant now) {
        transitionTo(RideStatus.RIDE_STARTED, now);
        this.startedAt = now;
    }

    public void complete(Instant now) {
        transitionTo(RideStatus.COMPLETED, now);
        this.completedAt = now;
        // Straight-line pricing for now; a real system would price the driven route here.
        this.actualFare = estimatedFare;
    }

    public void cancel(String reason, Instant now) {
        transitionTo(RideStatus.CANCELLED, now);
        this.cancellationReason = reason;
        this.cancelledAt = now;
    }

    private void transitionTo(RideStatus next, Instant now) {
        if (!status.canTransitionTo(next)) {
            throw new InvalidRideStateException(id, status, next);
        }
        this.status = next;
        this.updatedAt = now;
    }

    // ── Read access ──

    public String getId() { return id; }
    public Long getVersion() { return version; }
    public String getRiderId() { return riderId; }
    public String getDriverId() { return driverId; }
    public double getPickupLatitude() { return pickupLatitude; }
    public double getPickupLongitude() { return pickupLongitude; }
    public String getPickupAddress() { return pickupAddress; }
    public double getDropLatitude() { return dropLatitude; }
    public double getDropLongitude() { return dropLongitude; }
    public String getDropAddress() { return dropAddress; }
    public double getDistanceKm() { return distanceKm; }
    public RideStatus getStatus() { return status; }
    public BigDecimal getEstimatedFare() { return estimatedFare; }
    public BigDecimal getActualFare() { return actualFare; }
    public String getCancellationReason() { return cancellationReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
}
