"use client";

import { Client, ReconnectionTimeMode, type IMessage, type StompSubscription } from "@stomp/stompjs";
import { QueryClient, QueryClientProvider, useQueryClient } from "@tanstack/react-query";
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { createApi, queryKeys, type Api } from "./api";
import type { Ride } from "./types";

// ───────────────────────── Config / API ─────────────────────────

const ApiContext = createContext<Api | null>(null);

export function useApi(): Api {
  const api = useContext(ApiContext);
  if (!api) throw new Error("useApi must be used inside <Providers>");
  return api;
}

// ───────────────────────── Live updates ─────────────────────────
//
// The ride service runs a STOMP broker behind the gateway at /ws. Every committed ride change is pushed to
// /topic/rides/{rideId}, /topic/riders/{riderId} and /topic/drivers/{driverId}. When the socket is down the
// screens fall back to polling (see `refetchInterval` in the consoles).

export type LiveChannel = "ride" | "rider" | "driver";
export type LiveState = "connecting" | "live" | "reconnecting" | "offline";

const TOPIC_PREFIX: Record<LiveChannel, string> = {
  ride: "/topic/rides/",
  rider: "/topic/riders/",
  driver: "/topic/drivers/",
};

interface LiveContextValue {
  state: LiveState;
  subscribe: (channel: LiveChannel, id: string) => () => void;
}

const LiveContext = createContext<LiveContextValue | null>(null);

export function useLiveState(): LiveState {
  return useContext(LiveContext)?.state ?? "offline";
}

/** Subscribes to live updates for a ride, rider or driver while the calling component is mounted. */
export function useLiveSubscription(channel: LiveChannel, id: string | null | undefined) {
  const live = useContext(LiveContext);
  useEffect(() => {
    if (!live || !id) return;
    return live.subscribe(channel, id);
  }, [live, channel, id]);
}

/** http://host:8080 -> ws://host:8080/ws (https -> wss). */
function toWebSocketUrl(gatewayUrl: string): string {
  const url = new URL("/ws", gatewayUrl);
  url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
  return url.toString();
}

interface Subscription {
  count: number;
  handle?: StompSubscription;
}

function LiveUpdatesProvider({ gatewayUrl, children }: { gatewayUrl: string; children: ReactNode }) {
  const queryClient = useQueryClient();
  const [state, setState] = useState<LiveState>("connecting");
  const clientRef = useRef<Client | null>(null);
  // Desired subscriptions keyed by destination, reference-counted across components.
  const subscriptions = useRef(new Map<string, Subscription>());

  const onRide = useCallback(
    (ride: Ride) => {
      queryClient.setQueryData(queryKeys.ride(ride.id), ride);
      void queryClient.invalidateQueries({ queryKey: ["rides"] });
      void queryClient.invalidateQueries({ queryKey: queryKeys.drivers });
    },
    [queryClient],
  );

  /** Opens the STOMP subscription for a wanted destination if connected and not already open. */
  const attach = useCallback(
    (destination: string) => {
      const client = clientRef.current;
      const entry = subscriptions.current.get(destination);
      if (!client?.connected || !entry || entry.handle) return;
      entry.handle = client.subscribe(destination, (message: IMessage) => {
        try {
          onRide(JSON.parse(message.body) as Ride);
        } catch {
          // Ignore malformed frames; the next poll or push corrects the cache.
        }
      });
    },
    [onRide],
  );

  useEffect(() => {
    let disposed = false;
    const client = new Client({
      brokerURL: toWebSocketUrl(gatewayUrl),
      reconnectDelay: 1_000,
      maxReconnectDelay: 15_000,
      reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
      debug: () => {},
    });
    clientRef.current = client;

    client.onConnect = () => {
      if (disposed) return;
      setState("live");
      // Subscriptions belong to a connection, so re-open all of them after every (re)connect.
      for (const [destination, entry] of subscriptions.current) {
        entry.handle = undefined;
        attach(destination);
      }
    };
    client.onWebSocketClose = () => {
      if (disposed) return;
      for (const entry of subscriptions.current.values()) entry.handle = undefined;
      // First drop after being live shows "Reconnecting"; repeated failures fall back to "Polling".
      setState((previous) => (previous === "live" ? "reconnecting" : "offline"));
    };
    client.onStompError = () => {
      if (!disposed) setState("offline");
    };

    client.activate();

    return () => {
      disposed = true;
      clientRef.current = null;
      void client.deactivate();
    };
  }, [gatewayUrl, attach]);

  const subscribe = useCallback(
    (channel: LiveChannel, id: string) => {
      const destination = TOPIC_PREFIX[channel] + id;
      const existing = subscriptions.current.get(destination);
      if (existing) {
        existing.count += 1;
      } else {
        subscriptions.current.set(destination, { count: 1 });
        attach(destination);
      }

      return () => {
        const entry = subscriptions.current.get(destination);
        if (!entry) return;
        entry.count -= 1;
        if (entry.count > 0) return;
        subscriptions.current.delete(destination);
        try {
          entry.handle?.unsubscribe();
        } catch {
          // Connection already gone.
        }
      };
    },
    [attach],
  );

  const value = useMemo(() => ({ state, subscribe }), [state, subscribe]);
  return <LiveContext.Provider value={value}>{children}</LiveContext.Provider>;
}

// ───────────────────────── Root provider ─────────────────────────

export function Providers({ gatewayUrl, children }: { gatewayUrl: string; children: ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: { staleTime: 2_000, retry: 1, refetchOnWindowFocus: false },
        },
      }),
  );
  const api = useMemo(() => createApi(gatewayUrl), [gatewayUrl]);

  return (
    <QueryClientProvider client={queryClient}>
      <ApiContext.Provider value={api}>
        <LiveUpdatesProvider gatewayUrl={api.gatewayUrl}>{children}</LiveUpdatesProvider>
      </ApiContext.Provider>
    </QueryClientProvider>
  );
}

// ───────────────────────── Local identity ─────────────────────────

/** A small persisted value (rider / driver id). Starts with the fallback to keep SSR output stable. */
export function useStoredValue(key: string, fallback: string) {
  const [value, setValue] = useState(fallback);

  useEffect(() => {
    try {
      const stored = window.localStorage.getItem(key);
      // eslint-disable-next-line react-hooks/set-state-in-effect -- hydrate from storage after mount
      if (stored) setValue(stored);
    } catch {
      // Storage unavailable (private mode); keep the fallback.
    }
  }, [key]);

  const update = useCallback(
    (next: string) => {
      setValue(next);
      try {
        window.localStorage.setItem(key, next);
      } catch {
        // Ignore.
      }
    },
    [key],
  );

  return [value, update] as const;
}
