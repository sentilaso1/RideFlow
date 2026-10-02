"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { RideMap, type MapCar } from "@/components/map/RideMap";
import { MapLayout } from "@/components/Nav";
import { Button, cx, Field, LiveBadge, Notice, Panel, SectionTitle, StatusPill } from "@/components/ui";
import { queryKeys } from "@/lib/api";
import { distanceKm, formatCoords, formatDateTime, formatFare, formatKm, SAMPLE, stepTowards } from "@/lib/format";
import { useApi, useLiveState, useLiveSubscription, useStoredValue } from "@/lib/providers";
import { isTerminal, type LatLng, type Ride } from "@/lib/types";

const PING_INTERVAL_MS = 3_000; // same cadence as the original "driver phone" comment
const MOVE_INTERVAL_MS = 100; // local simulation tick, so the car glides instead of hopping
const DRIVE_SPEED_KMH = 135; // 3x city pace: slow enough to watch, quick enough to finish
const ARRIVAL_RADIUS_KM = 0.03; // within 30 m of a stop counts as arrived
const AT_STOP_KM = 0.1; // manual driving: the stop buttons unlock within 100 m

/** Where the simulated car is heading for a given ride state. */
const targetOf = (ride: Ride): LatLng =>
  ride.status === "RIDE_STARTED"
    ? { lat: ride.dropLatitude, lng: ride.dropLongitude }
    : { lat: ride.pickupLatitude, lng: ride.pickupLongitude };

export function DriverConsole() {
  const api = useApi();
  const queryClient = useQueryClient();
  const live = useLiveState() === "live";

  const [driverId, setDriverId] = useStoredValue("rideshare.driverId", "driver:1");
  const [position, setPosition] = useState<LatLng>(SAMPLE.drivers[0].position);
  const [online, setOnline] = useState(false);
  const [autoDrive, setAutoDrive] = useState(true);
  const [autoAssign, setAutoAssign] = useState(true);
  const [pingError, setPingError] = useState<string | null>(null);
  const [lastPing, setLastPing] = useState<Date | null>(null);

  useLiveSubscription("driver", online ? driverId : null);

  const drivers = useQuery({ queryKey: queryKeys.drivers, queryFn: api.getDrivers, refetchInterval: 3_000 });

  const rides = useQuery({
    queryKey: queryKeys.driverRides(driverId),
    queryFn: () => api.getDriverRides(driverId),
    enabled: driverId.trim().length > 0,
    refetchInterval: live ? false : 4_000,
  });

  const currentRide = rides.data?.find((ride) => !isTerminal(ride.status));
  const completed = rides.data?.filter((ride) => ride.status === "COMPLETED") ?? [];
  const earnings = completed.reduce((sum, ride) => sum + (ride.actualFare ?? 0), 0);

  const refreshAfter = (ride: Ride) => {
    queryClient.setQueryData(queryKeys.ride(ride.id), ride);
    void queryClient.invalidateQueries({ queryKey: ["rides"] });
    void queryClient.invalidateQueries({ queryKey: queryKeys.drivers });
  };

  const rideAction = useMutation({
    mutationFn: ({ action, rideId }: { action: "arriving" | "start" | "complete" | "cancel"; rideId: string }) => {
      switch (action) {
        case "arriving":
          return api.markArriving(rideId);
        case "start":
          return api.startRide(rideId);
        case "complete":
          return api.completeRide(rideId);
        case "cancel":
          return api.cancelRide(rideId, "Cancelled by driver");
      }
    },
    onSuccess: refreshAfter,
  });

  // The timers below read the latest values through refs so they are not recreated every tick.
  const positionRef = useRef(position);
  const rideRef = useRef(currentRide);
  const autoDriveRef = useRef(autoDrive);
  const actionRef = useRef(rideAction);
  useEffect(() => {
    positionRef.current = position;
    rideRef.current = currentRide;
    autoDriveRef.current = autoDrive;
    actionRef.current = rideAction;
  });

  // Transitions the simulator has already requested, so a stop it is parked on
  // does not fire the same call on every 100 ms tick.
  const firedRef = useRef(new Set<string>());

  // ── Follow whichever driver matching picked ──
  // location-service reports `currentRideId` per driver, so the assigned one can be
  // spotted without a new endpoint: no need to type "driver:3" by hand.
  // Never jump away from a driver who is mid-trip: with two rides in flight the
  // other matched driver may be listed first, and switching would strand this one.
  useEffect(() => {
    if (!autoAssign || currentRide) return;
    const list = drivers.data ?? [];
    if (list.some((driver) => driver.driverId === driverId && driver.currentRideId)) return;
    const assigned = list.find((driver) => driver.currentRideId);
    if (!assigned) return;
    const at = { lat: assigned.latitude, lng: assigned.longitude };
    positionRef.current = at;
    setDriverId(assigned.driverId);
    // eslint-disable-next-line react-hooks/set-state-in-effect -- start from where that driver actually is
    setPosition(at);
  }, [autoAssign, currentRide, drivers.data, driverId, setDriverId]);

  // ── Start from where this driver really is ──
  // While offline the car would otherwise sit at the sample start point, hiding the
  // driver's real marker. Synced once per driver id, so a tap on the map still wins.
  const syncedRef = useRef<string | null>(null);
  useEffect(() => {
    if (online || syncedRef.current === driverId) return;
    const known = drivers.data?.find((driver) => driver.driverId === driverId);
    if (!known) return;
    syncedRef.current = driverId;
    const at = { lat: known.latitude, lng: known.longitude };
    positionRef.current = at;
    // eslint-disable-next-line react-hooks/set-state-in-effect -- adopt the position location-service reports
    setPosition(at);
  }, [online, drivers.data, driverId]);

  // ── Driving simulation ──
  // Ticks at 10 Hz so the marker moves continuously, and marks "arriving" itself once
  // the pickup is reached. Starting the trip and finishing it at the drop-off are left
  // to the people involved: the car just parks at B until the driver taps "Ride finished".
  //
  // Each step is sized from the wall-clock time since the previous tick, never from
  // MOVE_INTERVAL_MS. Chrome clamps timers in a background tab to >= 1 s, and to roughly
  // one tick a minute once it has been hidden a while, so a fixed step per tick left the
  // car crawling at a fraction of its speed whenever the Drive window sat behind the Ride
  // window (measured: 4 km/h instead of 45). Measuring elapsed time keeps the ground
  // speed honest -- a throttled tab just moves in longer strides.
  useEffect(() => {
    if (!online) return;
    let previous = Date.now();

    const timer = setInterval(() => {
      const now = Date.now();
      const elapsedHours = (now - previous) / 3_600_000;
      previous = now;

      const ride = rideRef.current;
      if (!autoDriveRef.current || !ride) return;

      const target = targetOf(ride);
      const stepKm = DRIVE_SPEED_KMH * elapsedHours;
      const next = stepTowards(positionRef.current, target, stepKm);
      positionRef.current = next;
      setPosition(next);

      if (distanceKm(next, target) > ARRIVAL_RADIUS_KM) return;
      if (ride.status !== "ACCEPTED") return;
      const action = "arriving";
      const key = `${ride.id}:${action}`;
      if (firedRef.current.has(key)) return;
      firedRef.current.add(key);
      actionRef.current.mutate({ action, rideId: ride.id });
    }, MOVE_INTERVAL_MS);

    return () => clearInterval(timer);
  }, [online]);

  // ── GPS pings ──
  // Kept at the real cadence; the map smooths between them on the rider's screen.
  useEffect(() => {
    if (!online) return;
    let cancelled = false;

    const send = async () => {
      try {
        await api.updateDriverLocation(driverId, positionRef.current);
        if (cancelled) return;
        setLastPing(new Date());
        setPingError(null);
      } catch (error) {
        if (!cancelled) setPingError((error as Error).message);
      }
    };

    void send();
    const timer = setInterval(send, PING_INTERVAL_MS);

    // Coming back to a throttled tab, publish the caught-up position immediately
    // rather than leaving the rider on a stale fix until the next interval.
    const onVisibility = () => {
      if (document.visibilityState === "visible") void send();
    };
    document.addEventListener("visibilitychange", onVisibility);

    return () => {
      cancelled = true;
      clearInterval(timer);
      document.removeEventListener("visibilitychange", onVisibility);
    };
  }, [online, driverId, api]);

  const goOffline = useMutation({
    mutationFn: () => api.removeDriver(driverId),
    onSettled: () => {
      setOnline(false);
      void queryClient.invalidateQueries({ queryKey: queryKeys.drivers });
    },
  });

  const moveTo = (next: LatLng) => {
    setPosition(next);
    positionRef.current = next;
    if (online) {
      api.updateDriverLocation(driverId, next).then(
        () => setPingError(null),
        (error: Error) => setPingError(error.message),
      );
    }
  };

  const others: MapCar[] = (drivers.data ?? [])
    .filter((d) => d.driverId !== driverId)
    .map((d) => ({
      driverId: d.driverId,
      position: { lat: d.latitude, lng: d.longitude },
      variant: d.busy ? "busy" : "idle",
      smooth: true,
    }));
  // This car is simulated locally at 10 Hz, so it must not also be CSS-interpolated.
  const cars: MapCar[] = [...others, { driverId, position, variant: "mine", smooth: false }];

  const pickup = currentRide ? { lat: currentRide.pickupLatitude, lng: currentRide.pickupLongitude } : null;
  const drop = currentRide ? { lat: currentRide.dropLatitude, lng: currentRide.dropLongitude } : null;
  const focus = currentRide ? [pickup!, drop!] : [];

  const target = currentRide?.status === "RIDE_STARTED" ? drop : pickup;
  const toTarget = target ? distanceKm(position, target) : null;
  const atTarget = toTarget !== null && toTarget <= AT_STOP_KM;
  const approach = pickup && currentRide?.status === "ACCEPTED" ? { from: position, to: pickup } : null;
  const pending = rideAction.isPending ? rideAction.variables?.action : null;

  const panel = (
    <Panel title="Drive" aside={<LiveBadge />}>
      <div className="flex items-end gap-2">
        <Field
          className="flex-1"
          label="Driver"
          value={driverId}
          disabled={online}
          onChange={(e) => {
            setAutoAssign(false);
            setDriverId(e.target.value);
          }}
          placeholder="driver:1"
        />
        {online ? (
          <Button variant="secondary" busy={goOffline.isPending} onClick={() => goOffline.mutate()}>
            Go offline
          </Button>
        ) : (
          <Button disabled={!driverId.trim()} onClick={() => setOnline(true)}>
            Go online
          </Button>
        )}
      </div>

      <label className="flex items-center gap-3 text-sm">
        <input
          type="checkbox"
          className="size-4 accent-[var(--route)]"
          checked={autoAssign}
          onChange={(e) => setAutoAssign(e.target.checked)}
        />
        Follow whichever driver gets matched
      </label>

      <div className={cx("rounded-xl px-4 py-3", online ? "bg-pickup/10" : "bg-kerb")}>
        <p className="font-semibold">{online ? (currentRide ? "On a job" : "Waiting for requests") : "Offline"}</p>
        <p className="text-sm text-muted">
          {online
            ? `Sending location every ${PING_INTERVAL_MS / 1000} s${lastPing ? `, last at ${lastPing.toLocaleTimeString()}` : ""}`
            : "Tap the map to choose where you start, then go online."}
        </p>
        <p className="mt-1 text-xs text-muted tabular-nums">{formatCoords(position)}</p>
      </div>
      {pingError && <Notice>{pingError}</Notice>}
      {!online && currentRide && (
        <Notice tone="info">
          {driverId} has a trip waiting. Go online to drive it &mdash; the car only moves while this
          screen is online.
        </Notice>
      )}

      <label className="flex items-center gap-3 text-sm">
        <input
          type="checkbox"
          className="size-4 accent-[var(--route)]"
          checked={autoDrive}
          onChange={(e) => setAutoDrive(e.target.checked)}
        />
        Drive to the pickup and drop-off automatically
      </label>

      <div className="space-y-3">
        <SectionTitle>Current trip</SectionTitle>
        {!currentRide ? (
          <p className="text-sm text-muted">
            {online
              ? "Request a ride on the Ride screen with a pickup within 5 km of you. It will appear here."
              : "Go online to receive trips."}
          </p>
        ) : (
          <div className="space-y-4 rounded-xl border border-line p-4">
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <p className="text-sm text-muted">{currentRide.riderId}</p>
                <p className="truncate font-semibold">{currentRide.pickupAddress}</p>
                <p className="truncate text-sm text-muted">to {currentRide.dropAddress}</p>
              </div>
              <StatusPill status={currentRide.status} />
            </div>
            <div className="flex justify-between text-sm">
              <span>{formatFare(currentRide.estimatedFare)}</span>
              {toTarget !== null && (
                <span className="text-muted">
                  {atTarget ? "You are there" : `${formatKm(toTarget)} to ${currentRide.status === "RIDE_STARTED" ? "drop-off" : "pickup"}`}
                </span>
              )}
            </div>

            {currentRide.status === "DRIVER_ARRIVING" && (
              <p className="rounded-lg bg-signal/10 px-3 py-2 text-sm">
                You are at the pickup. Start the trip once {currentRide.riderId} is in the car (they can also start it from the Ride screen).
              </p>
            )}

            {currentRide.status === "ACCEPTED" && !atTarget && (
              <p className="text-sm text-muted">Drive to the pickup (A) first. You can mark arrival once you are there.</p>
            )}
            {currentRide.status === "RIDE_STARTED" &&
              (atTarget ? (
                <p className="rounded-lg bg-pickup/10 px-3 py-2 text-sm">
                  You have reached the drop-off (B). Tap <span className="font-semibold">Ride finished</span> once{" "}
                  {currentRide.riderId} is out of the car.
                </p>
              ) : (
                <p className="text-sm text-muted">Drive the rider to the drop-off (B). You can finish the ride once you are there.</p>
              ))}

            {/* One step at a time: reach A, pick up, then drive to B. */}
            <div className="grid grid-cols-2 gap-2">
              {currentRide.status === "ACCEPTED" && (
                <Button
                  className="col-span-2"
                  disabled={!atTarget}
                  busy={pending === "arriving"}
                  onClick={() => rideAction.mutate({ action: "arriving", rideId: currentRide.id })}
                >
                  Arrived at pickup
                </Button>
              )}
              {currentRide.status === "DRIVER_ARRIVING" && (
                <Button
                  className="col-span-2"
                  busy={pending === "start"}
                  onClick={() => rideAction.mutate({ action: "start", rideId: currentRide.id })}
                >
                  Rider picked up, start trip
                </Button>
              )}
              {currentRide.status === "RIDE_STARTED" && atTarget && (
                <Button
                  className="col-span-2"
                  busy={pending === "complete"}
                  onClick={() => rideAction.mutate({ action: "complete", rideId: currentRide.id })}
                >
                  Ride finished
                </Button>
              )}
              {currentRide.status !== "RIDE_STARTED" && (
                <Button
                  variant="danger"
                  className="col-span-2"
                  busy={pending === "cancel"}
                  onClick={() => rideAction.mutate({ action: "cancel", rideId: currentRide.id })}
                >
                  Cancel trip
                </Button>
              )}
            </div>
          </div>
        )}
        {rideAction.error && <Notice>{rideAction.error.message}</Notice>}
      </div>

      <div className="space-y-2 border-t border-line pt-5">
        <div className="flex items-baseline justify-between">
          <SectionTitle>Trip history</SectionTitle>
          <p className="text-sm">
            <span className="text-muted">Earned </span>
            <span className="font-semibold">{formatFare(earnings)}</span>
          </p>
        </div>
        {rides.error && <Notice>{rides.error.message}</Notice>}
        {rides.data?.filter((r) => isTerminal(r.status)).length === 0 && (
          <p className="text-sm text-muted">Finished trips will show up here.</p>
        )}
        <ul className="divide-y divide-line">
          {rides.data
            ?.filter((r) => isTerminal(r.status))
            .slice(0, 8)
            .map((ride) => (
              <li key={ride.id} className="flex items-center justify-between gap-3 py-2.5">
                <span className="min-w-0">
                  <span className="block truncate text-sm font-medium">{ride.dropAddress}</span>
                  <span className="block text-xs text-muted">{formatDateTime(ride.createdAt)}</span>
                </span>
                <span className="flex shrink-0 flex-col items-end gap-1">
                  <StatusPill status={ride.status} />
                  <span className="text-xs text-muted tabular-nums">{formatFare(ride.actualFare)}</span>
                </span>
              </li>
            ))}
        </ul>
      </div>
    </Panel>
  );

  return (
    <MapLayout
      panel={panel}
      map={
        <RideMap
          center={SAMPLE.center}
          pickup={pickup}
          drop={drop}
          approach={approach}
          cars={cars}
          focus={focus}
          onMapClick={currentRide && autoDrive ? undefined : moveTo}
          cursor={currentRide && autoDrive ? undefined : "crosshair"}
        />
      }
    />
  );
}
