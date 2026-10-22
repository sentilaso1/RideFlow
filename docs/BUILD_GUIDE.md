# RideFlow Build and Run Guide

This guide takes you from a fresh machine to a running system, then shows you how to demo it, test it, and put it on GitHub.

There are two ways to run the project. **Option A** runs everything in Docker with one command; use it to see the app working. **Option B** runs only the infrastructure in Docker and the Java services from your IDE; use it while you are developing and debugging.

## 1. Install the prerequisites

| Tool | Version | Check with | Needed for |
|---|---|---|---|
| Docker Desktop (or Docker Engine + Compose v2) | Recent | `docker compose version` | Both options |
| JDK | 21 or newer (21 or 25 LTS recommended) | `java -version` | Option B, running tests |
| Node.js | 22 LTS (20.9+ works) | `node -v` | Option B frontend |
| Git | Any | `git --version` | Publishing |

You do **not** need to install Maven. The project ships the Maven Wrapper (`./mvnw`), which downloads the right Maven version on first use.

Give Docker at least **6 GB of memory** (Docker Desktop → Settings → Resources). Kafka, MySQL and four JVMs together need it.

On Windows, use PowerShell or Git Bash and replace `./mvnw` with `mvnw.cmd`.

## 2. Get the code

Unzip the project and open a terminal in the project root (the folder containing `docker-compose.yml`).

```bash
cp .env.example .env
```

`.env` holds the database passwords. The defaults are fine for local use; change them for anything else. It is git-ignored, so it never gets committed.

## 3. Option A: run everything in Docker

```bash
docker compose --profile app up --build
```

The first build takes several minutes, because Maven and npm download all dependencies. Later builds reuse a cache and are much faster.

Wait until the logs settle and you see lines like `Started RideServiceApplication`. Then open **http://localhost:3000**.

To also get a web UI for browsing Kafka topics and messages:

```bash
docker compose --profile app --profile tools up --build
# Kafka UI: http://localhost:8090
```

To stop, press `Ctrl+C`, or run `docker compose --profile app down`. Add `-v` to also delete the database, Redis and Kafka data.

## 4. Option B: run the services from your IDE

### 4.1 Start the infrastructure

```bash
docker compose up -d
docker compose ps        # wait until redis, mysql and kafka show "healthy"
```

This starts Redis on 6379, MySQL on 3306 and Kafka on 9092. The services' default configuration already points at these addresses, so nothing needs editing.

### 4.2 Build the backend and run the tests

```bash
cd backend
./mvnw clean verify
```

This compiles all four services and runs the unit tests. The tests don't need Docker or any infrastructure.

### 4.3 Start the four services

Open four terminals in `backend/` and start one service in each, in this order:

```bash
./mvnw -pl location-service spring-boot:run
./mvnw -pl ride-service spring-boot:run
./mvnw -pl matching-service spring-boot:run
./mvnw -pl api-gateway spring-boot:run
```

In IntelliJ IDEA, you can instead open `backend/pom.xml` as a project and run each `*Application` class.

On first start, ride-service runs the Flyway migration that creates the `rides` table. You will see `Successfully applied 1 migration` in its log.

### 4.4 Start the frontend

```bash
cd frontend
npm install
cp .env.example .env.local
npm run dev
```

Open **http://localhost:3000**.

## 5. Check that everything is healthy

```bash
curl http://localhost:8080/actuator/health   # api-gateway
curl http://localhost:8082/actuator/health   # location-service (Option B only)
curl http://localhost:8083/actuator/health   # ride-service     (Option B only)
curl http://localhost:8084/actuator/health   # matching-service (Option B only)
```

Each should return `{"status":"UP",...}`. In Option A only the gateway (8080) is published to your machine; the other services are reachable only inside the Docker network.

## 6. Demo walkthrough

This sequence shows every part of the system working together.

1. **Fleet** tab: click **Add the 3 sample drivers**. Three cars appear in Bangalore.
2. **Ride** tab: click **Use the sample Bangalore trip**. The fare estimate appears.
3. Click **Request ride**. Within a second or two the status line moves from "Finding a driver" to "Driver assigned", pushed over WebSocket. The badge in the panel header should say **Live**.
4. Note which driver was assigned (for example `driver:1`). Open the **Drive** tab in a second browser window, enter that driver id, and click **Go online**.
5. The trip appears. With "Drive automatically" ticked, the car drives to the pickup (A). Once it is there, click **Rider picked up, start trip**, let it drive to the drop-off (B), then click **Ride finished**.
6. Back on **Ride**, the trip shows as completed with the charged fare. On **Fleet**, the driver is free again.

Things worth showing off:

- **No drivers nearby.** Take all drivers offline on Fleet, then request a ride. It is cancelled with "No drivers available near the pickup".
- **One active ride per rider.** Request a second ride while one is active and you get a clear 409 error.
- **Kafka events.** With the tools profile, open Kafka UI and look at `ride.requested`, `ride.matched` and `ride.status-changed`.

## 7. Try the API directly

These go through the gateway.

```bash
# Put a driver online
curl -X POST localhost:8080/api/v1/locations/drivers/update \
  -H 'Content-Type: application/json' \
  -d '{"driverId":"driver:9","latitude":12.9716,"longitude":77.5946}'

# Estimate a fare
curl -X POST localhost:8080/api/v1/rides/estimate \
  -H 'Content-Type: application/json' \
  -d '{"pickupLatitude":12.9716,"pickupLongitude":77.5946,"dropLatitude":12.9352,"dropLongitude":77.6245}'

# Request a ride
curl -X POST localhost:8080/api/v1/rides/request \
  -H 'Content-Type: application/json' \
  -d '{"riderId":"rider:9","pickupLatitude":12.9716,"pickupLongitude":77.5946,"pickupAddress":"MG Road",
       "dropLatitude":12.9352,"dropLongitude":77.6245,"dropAddress":"Koramangala"}'

# See the result (it should be ACCEPTED with driverId set)
curl localhost:8080/api/v1/rides/rider/rider:9

# Validation errors come back as Problem Details
curl -X POST localhost:8080/api/v1/rides/request -H 'Content-Type: application/json' -d '{}'
```

You can also inspect the data stores:

```bash
docker compose exec redis redis-cli ZRANGE "{drivers}:locations" 0 -1
docker compose exec redis redis-cli HGETALL "{drivers}:busy"
docker compose exec mysql mysql -urideshare -prideshare ride_db -e "SELECT id, status, driver_id FROM rides"
```

## 8. Run the checks that CI runs

```bash
cd backend && ./mvnw verify
cd ../frontend && npm ci && npm run lint && npm run typecheck && npm run build
cd .. && docker compose --profile app build
```

## 9. Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `Port 3306 / 6379 / 9092 is already in use` | A local MySQL, Redis or Kafka is already running. Stop it, or change the left-hand port in `docker-compose.yml`. |
| ride-service: `Public Key Retrieval is not allowed` or `Access denied` | The MySQL volume was created with different credentials. Run `docker compose down -v` and start again. |
| Services log `Connection to node -1 could not be established` | Kafka isn't ready yet. Wait for `docker compose ps` to show it healthy; the clients reconnect on their own. |
| The badge says **Polling** instead of **Live** | The WebSocket can't connect. Check the gateway is up on 8080 and that `FRONTEND_ORIGINS` matches the URL in your browser (default `http://localhost:3000`). The app still works by polling. |
| Ride is cancelled with "No drivers available" | No online, free driver within 5 km of the pickup. Add drivers on the Fleet tab first. |
| Ride sits in "Finding a driver", then cancels after about a minute | matching-service isn't running or can't reach location-service. Check its logs; the timeout sweeper cancelled the ride. |
| CORS error in the browser console | You are serving the frontend from a different origin. Set `FRONTEND_ORIGINS` (comma-separated) on api-gateway and ride-service. |
| `./mvnw: Permission denied` | Run `chmod +x backend/mvnw`. |
| Docker build is killed or very slow | Give Docker more memory (6 GB or more). |

**A note on the first build.** The project targets Spring Boot 4.1, which reorganised starters and switched to Jackson 3. Everything was written against the 4.1 APIs, but the Java code has not yet been compiled against the real libraries. If the first `./mvnw verify` reports an error, read the first error in the output; it will typically be a moved import or a renamed class, which is a one-line fix.

## 10. Configuration reference

All settings have sensible local defaults and can be overridden with environment variables.

| Variable | Used by | Default |
|---|---|---|
| `REDIS_HOST`, `REDIS_PORT` | location-service | `localhost`, `6379` |
| `KAFKA_BOOTSTRAP_SERVERS` | location, ride, matching | `localhost:9092` |
| `DB_HOST`, `DB_PORT`, `DB_NAME` | ride-service | `localhost`, `3306`, `ride_db` |
| `DB_USERNAME`, `DB_PASSWORD` | ride-service | `rideshare`, `rideshare` |
| `LOCATION_SERVICE_URL` | matching-service, api-gateway | `http://localhost:8082` |
| `RIDE_SERVICE_URL`, `RIDE_SERVICE_WS_URL` | api-gateway | `http://localhost:8083`, `ws://localhost:8083` |
| `FRONTEND_ORIGINS` | api-gateway, ride-service | `http://localhost:3000` |
| `GATEWAY_URL` | frontend | `http://localhost:8080` |

Business settings live in each service's `application.yaml` under `rideshare.*`: fare (`base-fare`, `per-km`, `currency`), matching timeout, search radius and distance weight.

## 11. Publish it on GitHub

This checkout uses `https://github.com/sentilaso1/RideFlow.git` as its destination. Inspect the local state and the destination branch before publishing:

```bash
git remote -v
git status
git fetch origin
```

For a new, empty destination, publish with `git push -u origin main`. If the destination already has history, compare it with the local branch first. Rewritten history changes commit IDs; coordinate with collaborators before replacing an existing branch.

The CI workflow runs on pushes to `main` and pull requests. Confirm its result on this repository before presenting a passing badge. Keep `.env` out of commits.

## 12. Discussing the project in an interview

Useful technical topics to demonstrate include:

- How Kafka connects ride requests, matching results, and driver release.
- How Redis GEO finds nearby candidates and Lua makes reservations atomic.
- How the ride domain enforces pickup-before-start transitions and optimistic locking detects conflicting updates.
- How after-commit publication avoids exposing rolled-back data, while leaving a crash window that an outbox would address.
- How the browser consumes STOMP updates and falls back to polling.
- Which cases unit tests cover and where integration or load testing is still needed.

Walk through a ride locally and explain the current limits: no authentication, simulated matching ratings, geographic rather than road distance, and an in-memory WebSocket broker. See the README's scope and next steps for planned improvements.
