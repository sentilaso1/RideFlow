import type { LatLng } from "@/lib/types";

export interface MapCar {
  driverId: string;
  position: LatLng;
  variant: "idle" | "busy" | "mine";
  /**
   * True when this car's position arrives from the server every few seconds, so the
   * marker should glide between updates. The locally simulated car moves at 10 Hz
   * and must stay false, or it would always lag a transition behind.
   */
  smooth?: boolean;
}

export interface RideMapProps {
  center: LatLng;
  pickup?: LatLng | null;
  drop?: LatLng | null;
  cars?: MapCar[];
  /** The assigned car's way to the pickup, drawn until the rider is picked up. */
  approach?: { from: LatLng; to: LatLng } | null;
  /** Points to keep in view; the map refits only when these change. */
  focus?: LatLng[];
  onMapClick?: (position: LatLng) => void;
  cursor?: string;
}
