# RideFlow web

Next.js 16 (App Router) + TypeScript frontend for the RideFlow Spring Boot microservices.

| Screen | Path | What it does |
|---|---|---|
| Ride | `/` | Pick pickup/drop-off on the map, see the fare estimate, request a ride and follow it live |
| Drive | `/driver` | Driver simulator: goes online, sends its location every 3 s, drives itself to the pickup and drop-off, and starts/completes trips |
| Fleet | `/fleet` | All drivers in Redis, busy state, add sample drivers or place new ones on the map |

## Stack

Next.js 16.3, React 19.3, Tailwind CSS 4, TanStack Query 5, `@stomp/stompjs` 7 for live ride
updates, Leaflet + react-leaflet 5 (CARTO tiles on OpenStreetMap data, no API key needed).

## Running

```bash
npm install
cp .env.example .env.local   # GATEWAY_URL=http://localhost:8080
npm run dev                  # http://localhost:3000
```

## How it talks to the backend

Everything goes through the API gateway: REST under `/api/v1/**` and STOMP over WebSocket at `/ws`.
The ride service pushes every committed ride change to `/topic/rides/{rideId}`, `/topic/riders/{riderId}`
and `/topic/drivers/{driverId}`. Subscriptions are reference-counted and re-opened after every reconnect.
If the socket is down the badge shows "Polling" and screens refresh every few seconds instead.

## Scripts

`npm run dev`, `npm run build`, `npm run lint`, `npm run typecheck`.
