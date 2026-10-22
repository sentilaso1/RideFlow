# RideFlow

**An event-driven ride-hailing demo with driver matching, live maps, and a complete trip lifecycle.**

Java 21 · Spring Boot 4.1 · Kafka · Redis GEO · MySQL · Next.js 16 · TypeScript · Docker Compose

RideFlow connects rider, driver, and fleet interfaces to four Spring services. A rider requests a trip, the matching service ranks nearby drivers and reserves one, and ride updates reach the browser over WebSocket. The repository demonstrates asynchronous service coordination, concurrent driver reservation, and domain rules in a runnable local application.

[Quick start](#quick-start) · [Engineering decisions](#engineering-decisions) · [API](#api-overview) · [Technical reference](docs/TECHNICAL_REFERENCE.md) · [Build guide](docs/BUILD_GUIDE.md)

## Features

- **Rider:** estimate a fare, request a trip, follow the assigned driver, start the trip after pickup, and view ride history.
- **Driver:** publish a location, follow the assigned ride, simulate travel to pickup and destination, and complete the trip.
- **Fleet:** view online drivers and their availability on a map; seed sample drivers for the demo.
- **Live updates:** STOMP subscriptions update the React Query cache, with polling as a fallback when the connection is unavailable.
- **Ride lifecycle:** explicit transitions from matching through pickup and completion, with cancellation rules and a matching timeout.

## Architecture

The browser uses the API gateway for public REST and WebSocket traffic. Each stateful service owns its data; matching queries and reserves drivers through an internal HTTP API, while ride events travel through Kafka.

| Component | Responsibility | Storage / communication |
| --- | --- | --- |
| `api-gateway` | Public entry point, routing, CORS, WebSocket forwarding | Spring Cloud Gateway |
| `location-service` | Driver positions, nearby search, reservation and release | Redis GEO, Lua, Kafka |
| `ride-service` | Ride state, fare calculation, history, live updates | MySQL, JPA, Flyway, Kafka, STOMP |
| `matching-service` | Rank nearby candidates and reserve an available driver | Internal HTTP, Kafka |
| `frontend` | Rider, driver, and fleet screens | Next.js, React Query, Leaflet, STOMP.js |

The main event flow is `ride.requested` → `ride.matched` or `ride.unmatched` → `ride.status-changed`. The [technical reference](docs/TECHNICAL_REFERENCE.md#architecture) includes architecture diagrams, event contracts, and storage models.

## Engineering decisions

| Problem | Implementation | Trade-off |
| --- | --- | --- |
| Concurrent requests select the same driver | A Redis Lua script atomically checks availability and reserves the driver. Release checks the owning ride before deleting the reservation. | Reservations depend on Redis availability; recovery still needs operational tooling. |
| Events expose data before a transaction commits | Transactional listeners publish Kafka and WebSocket updates after the database commit. | A process failure between commit and publish can lose an event; a transactional outbox is a next step. |
| Conflicting or invalid ride updates | Domain methods enforce allowed state transitions; JPA optimistic locking detects concurrent updates. | Callers must handle conflicts; this does not provide exactly-once delivery. |
| A match arrives twice or after cancellation | Ride assignment handles redelivery and requests release of a late reservation. | This covers specific event paths rather than a general deduplication framework. |
| Matching cannot finish | Consumer retries and dead-letter topics isolate failures; a scheduled sweeper cancels expired matching attempts. | Dead-letter replay is manual. |
| Candidate distance and rating use different scales | Normalize both before applying configurable weights, then break ties by driver ID. | Ratings used by matching are simulated, not persisted rider feedback. |

Source examples: [reservation script](backend/location-service/src/main/resources/scripts/reserve-driver.lua), [ride domain](backend/ride-service/src/main/java/com/rideshare/rideservice/domain/Ride.java), [event relay](backend/ride-service/src/main/java/com/rideshare/rideservice/service/RideEventRelay.java), and [driver scoring](backend/matching-service/src/main/java/com/rideshare/matchingservice/service/DriverScorer.java).

## Quick start

### Run with Docker

Install Docker with Compose v2. The application profile builds the Java and Node applications inside containers; host installations of Java, Maven, and Node.js are not required for this path.

```bash
git clone https://github.com/sentilaso1/RideFlow.git
cd RideFlow
cp .env.example .env
docker compose --profile app up --build
```

In PowerShell, replace `cp .env.example .env` with `Copy-Item .env.example .env`. Review the local database settings in `.env` before starting. Initial builds download Maven and npm dependencies.

| Address | Purpose |
| --- | --- |
| [localhost:3000](http://localhost:3000) | Web application |
| [localhost:8080/actuator/health](http://localhost:8080/actuator/health) | Gateway health |
| [localhost:8090](http://localhost:8090) | Kafka UI, when started with `--profile tools` as well |

Stop the stack with `docker compose --profile app down`. Named volumes retain data between runs.

### Walk through a ride

1. Open **Fleet** and add the three sample drivers.
2. Open **Ride**, choose the sample Bangalore trip, and request a ride.
3. Open **Drive** in another window and go online with the assigned driver.
4. Use automatic driving to reach the pickup, then start the trip from the rider or driver screen.
5. Reach the destination and complete the ride from the driver screen. Check the ride history and the driver's availability in Fleet.

To explore failure paths, request a ride with all drivers offline or attempt a second request while a ride is active.

### Develop locally

Use JDK 21 and Node.js 22. Start infrastructure with `docker compose up -d`, then run each backend service from `backend/` in a separate terminal:

```bash
./mvnw -pl location-service spring-boot:run
./mvnw -pl ride-service spring-boot:run
./mvnw -pl matching-service spring-boot:run
./mvnw -pl api-gateway spring-boot:run
```

On Windows use `.\mvnw.cmd` instead of `./mvnw`. From `frontend/`, run `npm ci`, copy `.env.example` to `.env.local`, and run `npm run dev`. See the [build guide](docs/BUILD_GUIDE.md) for configuration and troubleshooting.

## Verification

The repository's [CI workflow](.github/workflows/ci.yml) defines backend verification, frontend lint/type checking/build, and container image builds. Use these commands to reproduce the checks:

| Directory | Command | Check |
| --- | --- | --- |
| `backend/` | `./mvnw -B verify` | Backend build and unit tests |
| `frontend/` | `npm ci` | Install locked dependencies |
| `frontend/` | `npm run lint` | ESLint |
| `frontend/` | `npm run typecheck` | TypeScript |
| `frontend/` | `npm run build` | Next.js build |
| Repository root | `docker compose --profile app build` | Container images |

Existing backend tests cover ride transitions, service behavior, fares, candidate scoring, matching, and location service behavior. Tests with real MySQL, Redis, and Kafka containers are a planned addition.

## API overview

Public requests use `http://localhost:8080`. Validation and domain errors use Problem Details responses.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/locations/drivers/update` | Publish a driver location |
| `GET` | `/api/v1/locations/drivers/nearby` | Search nearby drivers |
| `POST` | `/api/v1/rides/estimate` | Estimate distance and fare |
| `POST` | `/api/v1/rides/request` | Request a ride |
| `GET` | `/api/v1/rides/{rideId}` | Read a ride |
| `GET` | `/api/v1/rides/rider/{riderId}` | Rider history |
| `PUT` | `/api/v1/rides/{rideId}/arriving` | Mark arrival at pickup |
| `PUT` | `/api/v1/rides/{rideId}/start` | Start after pickup |
| `PUT` | `/api/v1/rides/{rideId}/complete` | Complete the trip |
| `PUT` | `/api/v1/rides/{rideId}/cancel` | Cancel an eligible ride |
| `WS` | `/ws` | STOMP ride updates |

Full endpoints, request examples, and subscription destinations are in the [technical reference](docs/TECHNICAL_REFERENCE.md#api-reference). The internal reservation endpoint is not routed by the gateway.

## Project structure

```text
backend/
  api-gateway/         Public REST and WebSocket routing
  location-service/    Driver positions and atomic reservations
  ride-service/        Ride domain, persistence, and event relay
  matching-service/    Candidate ranking and matching
frontend/              Next.js rider, driver, and fleet UI
docs/                  Setup and technical documentation
.github/workflows/     CI checks and image builds
docker-compose.yml     Infrastructure and application profiles
```

## Scope and next steps

RideFlow is a local demonstration project. Authentication and role-based authorization are not implemented, so the current API must not be exposed as a public ride-hailing service. Fares use geographic distance rather than road routing; fare completion records an amount without processing a payment. Rider ratings are stored in the browser, and matching uses simulated ratings.

Next steps are JWT authentication and ownership checks, a transactional outbox, integration tests with real infrastructure, stale-driver expiry, and a shared WebSocket broker for multiple service instances. There are no published load-test or production availability results in this repository.
