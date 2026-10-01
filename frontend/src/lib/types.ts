// Mirrors the Java DTOs (records serialised by Jackson; enums as SNAKE_CASE_UPPER, timestamps ISO-8601 UTC).

export type RideStatus =
  | "REQUESTED"
  | "MATCHING"
  | "ACCEPTED"
  | "DRIVER_ARRIVING"
  | "RIDE_STARTED"
  | "COMPLETED"
  | "CANCELLED";

export interface Ride {
  id: string;
  riderId: string;
  driverId: string | null;
  pickupLatitude: number;
  pickupLongitude: number;
  pickupAddress: string;
  dropLatitude: number;
  dropLongitude: number;
  dropAddress: string;
  distanceKm: number;
  status: RideStatus;
  estimatedFare: number;
  actualFare: number | null;
  cancellationReason: string | null;
  createdAt: string;
  updatedAt: string;
  acceptedAt: string | null;
  startedAt: string | null;
  completedAt: string | null;
  cancelledAt: string | null;
}

export interface RideRequest {
  riderId: string;
  pickupLatitude: number;
  pickupLongitude: number;
  pickupAddress: string;
  dropLatitude: number;
  dropLongitude: number;
  dropAddress: string;
}

export interface FareEstimate {
  distanceKm: number;
  estimatedFare: number;
  currency: string;
}

export interface DriverSnapshot {
  driverId: string;
  latitude: number;
  longitude: number;
  busy: boolean;
  currentRideId: string | null;
}

export interface NearbyDriver {
  driverId: string;
  latitude: number;
  longitude: number;
  distanceInKm: number;
}

export interface LatLng {
  lat: number;
  lng: number;
}

export const TERMINAL_STATUSES: ReadonlySet<RideStatus> = new Set(["COMPLETED", "CANCELLED"]);

export const isTerminal = (status: RideStatus) => TERMINAL_STATUSES.has(status);
