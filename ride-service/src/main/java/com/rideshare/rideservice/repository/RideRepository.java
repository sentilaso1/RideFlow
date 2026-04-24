package com.rideshare.rideservice.repository;

import com.rideshare.rideservice.model.Ride;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RideRepository extends JpaRepository<Ride,String> {

}
