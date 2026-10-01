package com.rideshare.rideservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("rideshare.kafka.topics")
public record TopicProperties(String rideRequested, String rideMatched, String rideUnmatched,
                              String rideStatusChanged, int partitions, int replicas) {
}
