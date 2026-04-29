#  RideFlow — Microservices System Design

A production-inspired ride-sharing backend built to study and demonstrate real-world **system design concepts** for FAANG-level interviews. Built with Java, Spring Boot, Redis, MySQL, and Kafka.

---

## 📌 Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Services](#services)
- [Tech Stack](#tech-stack)
- [Event Flow](#event-flow)
- [Driver Matching Algorithm](#driver-matching-algorithm)
- [Ride Status Lifecycle](#ride-status-lifecycle)
- [API Endpoints](#api-endpoints)
- [Getting Started](#getting-started)
- [Project Structure](#project-structure)

---

## Overview

This project simulates how Uber's backend works under the hood — real-time driver tracking, ride matching, and event-driven communication between microservices. It is designed as a learning project for system design interviews, with a focus on:

- Real-time geospatial queries with Redis
- Event-driven architecture with Kafka
- Clean microservice separation with Spring Boot
- Persistent ride data with MySQL

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        CLIENT (Mobile App)                       │
└───────────────────────────────┬─────────────────────────────────┘
                                │ HTTP
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                         RIDE SERVICE                             │
│                         Port: 8083                               │
│                                                                  │
│  • Manages full ride lifecycle (REQUESTED → COMPLETED)           │
│  • Calculates estimated fare (Haversine formula)                 │
│  • Publishes RideRequestedEvent to Kafka                         │
│  • Consumes RideMatchedEvent from Kafka                          │
└──────────────┬──────────────────────────┬───────────────────────┘
               │                          │
        Kafka publish              Kafka consume
     (ride.requested)            (ride.matched)
               │                          │
               ▼                          │
┌──────────────────────────┐             │
│     MATCHING SERVICE     │─────────────┘
│       Port: 8084         │
│                          │
│  • Consumes ride.requested│
│  • Calls location-service │
│  • Scores & picks driver  │
│  • Publishes ride.matched │
└──────────┬───────────────┘
           │ HTTP
           ▼
┌──────────────────────────┐        ┌──────────────────────┐
│    LOCATION SERVICE      │        │        REDIS         │
│       Port: 8082         │◄──────►│     Port: 6379       │
│                          │        │                      │
│  • Updates driver GPS    │        │  Geo commands:       │
│  • Finds nearby drivers  │        │  GEOADD              │
│  • Removes offline       │        │  GEOSEARCH           │
│    drivers               │        │  GEODIST             │
└──────────────────────────┘        │  ZREM                │
                                    └──────────────────────┘
┌──────────────────────────┐        ┌──────────────────────┐
│       RIDE SERVICE       │        │        MYSQL         │
│       (continued)        │◄──────►│     Port: 3306       │
│                          │        │                      │
│  • Persists all rides    │        │  Table: rides        │
│  • Stores rider data     │        │  Database: uberapp   │
└──────────────────────────┘        └──────────────────────┘
```

---

## Services

### 🔵 Location Service — `port 8082`

Responsible for all real-time driver location tracking using Redis geospatial commands.

| Method | Description | Redis Command |
|--------|-------------|---------------|
| `updateDriverLocation()` | Updates driver GPS every 3 seconds | `GEOADD` |
| `findNearbyDrivers()` | Finds drivers within a given radius | `GEORADIUS` |
| `removeDriver()` | Removes driver when they go offline | `ZREM` |

### 🟢 Ride Service — `port 8083`

Manages the complete lifecycle of every ride and communicates with other services via Kafka.

| Method | Description |
|--------|-------------|
| `requestRide()` | Creates ride, calculates fare, fires Kafka event |
| `updateRideWithDriver()` | Assigns driver when matched |
| `startRide()` | Starts ride if status is ACCEPTED |
| `completeRide()` | Completes ride, records time and fare |
| `cancelRide()` | Cancels ride at any point |

### 🟡 Matching Service — `port 8084`

The brain of the system. Listens to Kafka, scores nearby drivers, and publishes the best match.

| Component | Description |
|-----------|-------------|
| `RideEventConsumer` | Kafka listener for `ride.requested` topic |
| `MatchingService` | Scoring algorithm + location-service caller |
| `findBestDriver()` | Weighted scoring — distance 70%, rating 30% |

---

## Tech Stack

| Technology | Role |
|------------|------|
| **Java + Spring Boot** | Core backend framework for all microservices |
| **Redis** | Real-time geospatial driver location storage |
| **MySQL** | Persistent storage for rides and rider data |
| **Apache Kafka** | Event streaming between microservices |
| **ZooKeeper** | Kafka distributed coordination |
| **Docker** | Containerisation of Redis, MySQL, and Kafka |
| **Lombok** | Boilerplate reduction (getters, constructors, logging) |
| **Spring Data JPA / Hibernate** | ORM for MySQL interactions |

---

## Event Flow

This is how a complete ride flows through the system from request to driver assignment:

```
┌────────────┐     POST /api/v1/rides/request
│   Rider    │──────────────────────────────────────────────────┐
└────────────┘                                                  │
                                                                ▼
                                                    ┌─────────────────────┐
                                                    │    Ride Service      │
                                                    │                      │
                                                    │ 1. Save ride to MySQL│
                                                    │    status=REQUESTED  │
                                                    │                      │
                                                    │ 2. Calculate fare    │
                                                    │    (Haversine)       │
                                                    │                      │
                                                    │ 3. Publish event to  │
                                                    │    Kafka             │
                                                    └──────────┬───────────┘
                                                               │
                                              Kafka: ride.requested
                                                               │
                                                               ▼
                                                    ┌─────────────────────┐
                                                    │  Matching Service    │
                                                    │                      │
                                                    │ 1. Consume event     │
                                                    │                      │
                                                    │ 2. Call location-    │
                                                    │    service for       │
                                                    │    nearby drivers    │
                                                    │    (5km radius)      │
                                                    │                      │
                                                    │ 3. Score each driver │
                                                    │    (distance + rating│
                                                    │    weighted algo)    │
                                                    │                      │
                                                    │ 4. Pick best driver  │
                                                    │                      │
                                                    │ 5. Publish match     │
                                                    └──────────┬───────────┘
                                                               │
                                               Kafka: ride.matched
                                                               │
                                                               ▼
                                                    ┌─────────────────────┐
                                                    │    Ride Service      │
                                                    │                      │
                                                    │ Consume match event  │
                                                    │ Assign driverId      │
                                                    │ status=ACCEPTED  ✅  │
                                                    └─────────────────────┘
```

---

## Driver Matching Algorithm

The matching-service uses a **weighted scoring algorithm** to select the best available driver:

```
Score = (1 / distance + 0.1) × 0.7  +  rating × 0.3
         └──────────────────────┘      └──────────┘
              Distance (70%)           Rating (30%)
```

- **Distance score** — closer drivers get a higher score. The `+ 0.1` prevents division by zero if a driver is at the exact pickup location
- **Rating score** — currently simulated between 4.0–5.0. In production this would be fetched from a dedicated Driver Service
- The driver with the **highest combined score** wins the ride

---

## Ride Status Lifecycle

```
                    ┌───────────┐
                    │ REQUESTED │  ← Rider submits ride request
                    └─────┬─────┘
                          │ Kafka event fired
                          ▼
                    ┌───────────┐
                    │  MATCHING │  ← Matching service searching for driver
                    └─────┬─────┘
                          │ Driver found
                          ▼
                    ┌───────────┐
                    │  ACCEPTED │  ← Driver assigned to ride
                    └─────┬─────┘
                          │ Driver starts trip
                          ▼
                  ┌──────────────┐
                  │ RIDE_STARTED │  ← Trip in progress
                  └──────┬───────┘
                         │
              ┌──────────┴──────────┐
              │                     │
              ▼                     ▼
       ┌───────────┐         ┌───────────┐
       │ COMPLETED │         │ CANCELLED │
       └───────────┘         └───────────┘
```

---

## API Endpoints

### Location Service (`localhost:8082`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/location/update` | Update driver GPS location |
| `GET` | `/api/v1/location/nearby` | Get nearby drivers within radius |
| `DELETE` | `/api/v1/location/remove/{driverId}` | Remove driver (offline) |

### Ride Service (`localhost:8083`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/rides/request` | Request a new ride |
| `GET` | `/api/v1/rides/{rideId}` | Get ride by ID |
| `GET` | `/api/v1/rides/rider/{riderId}` | Get all rides for a rider |
| `PUT` | `/api/v1/rides/{rideId}/start` | Start a ride |
| `PUT` | `/api/v1/rides/{rideId}/complete` | Complete a ride |
| `PUT` | `/api/v1/rides/{rideId}/cancel` | Cancel a ride |

---

## Getting Started

### Prerequisites

- Java 17+
- Docker & Docker Compose
- Maven

### 1. Start infrastructure

```bash
docker-compose up -d
```

This starts Redis (6379), MySQL (3306), and Kafka (9092) + ZooKeeper.

### 2. Run the services

Start each service in order:

```bash
# Terminal 1 - Location Service
cd location-service && mvn spring-boot:run

# Terminal 2 - Ride Service
cd ride-service && mvn spring-boot:run

# Terminal 3 - Matching Service
cd matching-service && mvn spring-boot:run
```

### 3. Verify all services are healthy

```bash
curl http://localhost:8082/actuator/health   # location-service
curl http://localhost:8083/actuator/health   # ride-service
curl http://localhost:8084/actuator/health   # matching-service
```

---

## Project Structure

```
developer_proj_uber_driver_locator/
│
├── docker-compose.yml                  # Redis, MySQL, Kafka, ZooKeeper
│
├── location-service/                   # Port 8082
│   └── src/main/java/
│       └── com/rideshare/locationservice/
│           ├── config/
│           │   └── RedisConfig.java    # Redis template + serializers
│           ├── controller/
│           │   └── LocationController.java
│           ├── dto/
│           │   └── NearByDriverResponse.java
│           └── service/
│               └── LocationService.java # GEOADD, GEORADIUS, ZREM
│
├── ride-service/                       # Port 8083
│   └── src/main/java/
│       └── com/rideshare/rideservice/
│           ├── config/
│           │   └── KafkaConfig.java    # ride.requested + ride.matched topics
│           ├── controller/
│           │   ├── RideController.java
│           │   └── GlobalExceptionHandler.java
│           ├── model/
│           │   ├── Ride.java           # MySQL entity
│           │   └── RideStatus.java     # Enum: REQUESTED → COMPLETED
│           └── service/
│               └── RideService.java    # Full ride lifecycle
│
└── matching-service/                   # Port 8084
    └── src/main/java/
        └── com/rideshare/matchingservice/
            ├── client/
            │   └── LocationServiceClient.java
            ├── event/
            │   ├── RideRequestedEvent.java
            │   └── RideMatchedEvent.java
            └── service/
                ├── MatchingService.java      # Scoring algorithm
                └── RideEventConsumer.java    # Kafka listener
```

---

## 📚 Concepts Demonstrated

- **Event-driven microservices** — services communicate via Kafka, never directly
- **Geospatial indexing** — Redis geo commands for real-time location queries
- **Weighted scoring algorithm** — distance + rating to pick the optimal driver
- **Clean architecture** — controller → service → repository separation throughout
- **Fault tolerance patterns** — dead letter queue placeholder for failed Kafka events
- **Spring Boot best practices** — `@RestControllerAdvice`, Lombok, YAML config, Actuator

---

*Built as a system design study project for FAANG interview preparation.*
