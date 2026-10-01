"use client";

import { useSyncExternalStore } from "react";

// Ratings live in the browser only: the backend has no rating column yet, and
// matching-service still derives its score from SimulatedDriverRatingProvider.
// Swapping this for a real endpoint means replacing `rateRide` and `useRatings`.

const KEY = "rideshare.ratings";
const EMPTY: Ratings = {};

export type Ratings = Record<string, number>;

let cache: Ratings = EMPTY;
let loaded = false;
const listeners = new Set<() => void>();

/** Reads storage once, then serves a stable reference so useSyncExternalStore stays happy. */
function snapshot(): Ratings {
  if (loaded) return cache;
  loaded = true;
  try {
    const raw = window.localStorage.getItem(KEY);
    cache = raw ? (JSON.parse(raw) as Ratings) : EMPTY;
  } catch {
    cache = EMPTY; // Storage unavailable (private mode).
  }
  return cache;
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export function useRatings(): Ratings {
  return useSyncExternalStore(subscribe, snapshot, () => EMPTY);
}

export function rateRide(rideId: string, stars: number) {
  cache = { ...snapshot(), [rideId]: stars };
  try {
    window.localStorage.setItem(KEY, JSON.stringify(cache));
  } catch {
    // Keep the in-memory rating even if it cannot be persisted.
  }
  for (const listener of listeners) listener();
}
