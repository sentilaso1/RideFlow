package com.rideshare.matchingservice.service;

/** Source of driver ratings on a 1.0-5.0 scale. */
public interface DriverRatingProvider {

    double ratingOf(String driverId);
}
