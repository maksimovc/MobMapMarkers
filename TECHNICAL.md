# MobMapMarkers Technical Notes

## Data Flow

The runtime pipeline is intentionally split into two rendering paths.

1. `MobMarkerTicker` scans live worlds on a schedule.
2. NPC snapshots and cached player positions are written into `MobMarkerManager`.
3. `MobMarkerProvider` consumes the snapshots for the large Hytale world map.
4. `FastMiniMapCompatService` implements `FastMiniMapMobLayerApi.MobDotProvider` and is registered at startup when `FastMiniMap` is detected.
5. On each FastMiniMap render tick the session queries the provider; `FastMiniMapCompatService` filters by radius and visibility rules and returns colored dot positions.
6. `MobMapAssetPack` generates portrait/fallback PNGs in memory and delivers only the needed assets to each viewer.

## Asset Delivery Contract

- Marker icons are not written to a monitored runtime asset-pack directory.
- Marker icons are cached in memory and sent directly as `CommonAsset` packets.
- Asset hashes are stable SHA-256 values per PNG blob.
- Rebuild requests are debounced so simultaneous large-map and minimap updates do not each force a separate immediate rebuild.
- Runtime config now lives under the plugin data directory instead of a `mods/MobMapMarkersData` pseudo-mod folder; legacy config is migrated forward on startup.

## Shutdown Contract

Version `1.5.0` treats plugin shutdown as a first-class lifecycle step.

- `MobMarkerTicker` stops its scheduler.
- `FastMiniMapCompatService` calls `FastMiniMapMobLayerApi.setProvider(null)` so no stale provider reference is left inside FastMiniMap.
- `LivePlayerTracker` deregisters its inbound packet filter.
- `MobMapAssetPack` clears cached assets, delivered-viewer state, and pending rebuild tasks.
- `MobMapMarkersPlugin` clears active player state and releases references.

## FastMiniMap Compatibility Contract

`FastMiniMap` support is optional at runtime and uses a stable public API.

The integration relies on `FastMiniMapMobLayerApi` (package `dev.thenexusgates.fastminimap`):

- `FastMiniMapMobLayerApi.MobDot(double x, double z, int argb)` — a dot position record
- `FastMiniMapMobLayerApi.MobDotProvider` — functional interface returning `List<MobDot>` per viewer
- `FastMiniMapMobLayerApi.setProvider(MobDotProvider)` — registers the active provider
- `FastMiniMapMobLayerApi.getProvider()` — queried by the FastMiniMap render session each tick

`FastMiniMapCompat` detects the API class at startup via `Class.forName`. If FastMiniMap is absent the provider is never registered and no dots appear.

On the Hytale side, large-map visibility detection currently depends on `WorldMapTracker.clientHasWorldMapVisible`.

## Source Build Contract

- The build references `../FastMiniMap/build/libs/FastMiniMap-1.0.0.jar` as a `compileOnly` dependency.
- Build FastMiniMap first; if the jar is absent the build still succeeds — only the FastMiniMap-related classes use it.
- No stub sources are required or present.
