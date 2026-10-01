package com.rideshare.matchingservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("rideshare.kafka.topics")
public record TopicProperties(String rideRequested, String rideMatched, String rideUnmatched,
                              int partitions, int replicas) {
}
