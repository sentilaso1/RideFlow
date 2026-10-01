"use client";

import "leaflet/dist/leaflet.css";
import L from "leaflet";
import { useEffect, useMemo, useRef } from "react";
import { MapContainer, Marker, Polyline, TileLayer, Tooltip, useMap, useMapEvents } from "react-leaflet";
import type { LatLng } from "@/lib/types";
import type { MapCar, RideMapProps } from "./types";

const pickupIcon = L.divIcon({
  className: "",
  html: '<div class="pin pin-pickup"><span>A</span></div>',
  iconSize: [30, 30],
  iconAnchor: [4, 30],
});

const dropIcon = L.divIcon({
  className: "",
  html: '<div class="pin pin-drop"><span>B</span></div>',
  iconSize: [30, 30],
  iconAnchor: [4, 30],
});

const escapeHtml = (text: string) =>
  text.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]!);

const iconCache = new Map<string, L.DivIcon>();

function carIcon(car: MapCar) {
  const cacheKey = `${car.variant}|${car.driverId}|${car.smooth ? "s" : ""}`;
  const cached = iconCache.get(cacheKey);
  if (cached) return cached;
  const size = car.variant === "mine" ? 32 : 26;
  const label = escapeHtml(car.driverId.split(":").pop()?.slice(0, 3) ?? "");
  const icon = L.divIcon({
    className: "",
    html: `<div class="car car-${car.variant}">${label}</div>`,
    iconSize: [size, size],
    iconAnchor: [size / 2, size / 2],
  });
  iconCache.set(cacheKey, icon);
  return icon;
}

const toTuple = (p: LatLng): [number, number] => [p.lat, p.lng];

/**
 * A car marker. When `smooth` is set the icon gets a CSS transform transition so it
 * glides between the position updates that arrive every few seconds from the server.
 * The class is added one frame *after* mount, otherwise the marker would animate in
 * from the corner of the map the first time Leaflet positions it.
 */
function CarMarker({ car }: { car: MapCar }) {
  const markerRef = useRef<L.Marker>(null);
  const icon = carIcon(car);

  useEffect(() => {
    const element = markerRef.current?.getElement();
    if (!element || !car.smooth) return;
    const frame = requestAnimationFrame(() => element.classList.add("car-smooth"));
    return () => {
      cancelAnimationFrame(frame);
      element.classList.remove("car-smooth");
    };
    // `icon` identity changes whenever Leaflet swaps the element, so re-apply then.
  }, [car.smooth, icon]);

  return (
    <Marker
      ref={markerRef}
      position={toTuple(car.position)}
      icon={icon}
      zIndexOffset={car.variant === "mine" ? 1000 : 0}
      keyboard={false}
    >
      <Tooltip direction="top" offset={[0, -12]}>
        {car.driverId}
        {car.variant === "busy" ? " (on a trip)" : ""}
      </Tooltip>
    </Marker>
  );
}

function ClickHandler({ onClick }: { onClick?: (position: LatLng) => void }) {
  useMapEvents({
    click(event) {
      onClick?.({ lat: event.latlng.lat, lng: event.latlng.lng });
    },
  });
  return null;
}

/** Pans/zooms when the set of focus points changes (not on every driver tick). */
function FitTo({ points, fitKey }: { points: LatLng[]; fitKey: string }) {
  const map = useMap();
  useEffect(() => {
    if (points.length === 0) return;
    if (points.length === 1) {
      map.setView(toTuple(points[0]), Math.max(map.getZoom(), 14));
    } else {
      // On desktop the control panel covers the left ~440px of the map.
      const wide = map.getContainer().clientWidth >= 768;
      map.fitBounds(
        L.latLngBounds(points.map(toTuple)),
        wide
          ? { paddingTopLeft: [460, 60], paddingBottomRight: [60, 60], maxZoom: 15 }
          : { padding: [40, 40], maxZoom: 15 },
      );
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- refit only when fitKey changes
  }, [fitKey, map]);
  return null;
}

/** Leaflet needs a size recalculation when its container changes (mobile layout). */
function ResizeWatcher() {
  const map = useMap();
  useEffect(() => {
    const container = map.getContainer();
    const observer = new ResizeObserver(() => map.invalidateSize());
    observer.observe(container);
    return () => observer.disconnect();
  }, [map]);
  return null;
}

export default function LeafletMap({ center, pickup, drop, cars = [], focus = [], onMapClick, cursor }: RideMapProps) {
  const route = useMemo(() => (pickup && drop ? [toTuple(pickup), toTuple(drop)] : null), [pickup, drop]);
  const fitKey = focus.map((p) => `${p.lat.toFixed(4)},${p.lng.toFixed(4)}`).join("|");

  return (
    <MapContainer
      center={toTuple(center)}
      zoom={13}
      zoomControl={false}
      className="h-full w-full"
      style={{ cursor: cursor ?? "grab" }}
    >
      {/* Esri Light Gray Canvas: muted basemap, no API key. Imagery is only
          cached to z16, so Leaflet upscales beyond that via maxNativeZoom. */}
      <TileLayer
        attribution='Tiles &copy; <a href="https://www.esri.com/">Esri</a> &mdash; Esri, DeLorme, NAVTEQ'
        url="https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Light_Gray_Base/MapServer/tile/{z}/{y}/{x}"
        maxNativeZoom={16}
        maxZoom={19}
      />
      <TileLayer
        url="https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Light_Gray_Reference/MapServer/tile/{z}/{y}/{x}"
        maxNativeZoom={16}
        maxZoom={19}
      />
      <ClickHandler onClick={onMapClick} />
      <FitTo points={focus} fitKey={fitKey} />
      <ResizeWatcher />

      {route && (
        <>
          <Polyline positions={route} pathOptions={{ color: "#ffffff", weight: 9, opacity: 0.9 }} />
          <Polyline positions={route} pathOptions={{ color: "#2f5bea", weight: 5, dashArray: "1 10", lineCap: "round" }} />
        </>
      )}

      {cars.map((car) => (
        <CarMarker key={car.driverId} car={car} />
      ))}

      {pickup && (
        <Marker position={toTuple(pickup)} icon={pickupIcon} zIndexOffset={2000}>
          <Tooltip direction="top" offset={[10, -28]}>Pickup</Tooltip>
        </Marker>
      )}
      {drop && (
        <Marker position={toTuple(drop)} icon={dropIcon} zIndexOffset={2000}>
          <Tooltip direction="top" offset={[10, -28]}>Drop-off</Tooltip>
        </Marker>
      )}
    </MapContainer>
  );
}
