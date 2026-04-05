# MobMapMarkers Technical Notes

## Data Flow

The runtime pipeline is intentionally split into two rendering paths.

1. `MobMarkerTicker` scans live worlds on a schedule.
2. NPC snapshots and cached player positions are written into `MobMarkerManager`.
3. `MobMarkerProvider` consumes the snapshots for the large Hytale world map.
4. `SimpleMinimapOverlayService` replaces SimpleMinimap HUD instances with `MobMapMarkersMinimapHud` when SimpleMinimap is present.
5. `MobMapMarkersMinimapHud` projects nearby mobs into minimap space and renders direct `AssetImage` nodes.
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
- `SimpleMinimapOverlayService` stops its scheduler and cancels replacement tick tasks.
- `LivePlayerTracker` deregisters its inbound packet filter.
- `MobMapAssetPack` clears cached assets, delivered-viewer state, and pending rebuild tasks.
- `MobMapMarkersPlugin` clears active player state and releases references.

## Compatibility Contract

`SimpleMinimap` support remains optional at runtime.

The compatibility layer assumes the presence of these types when `SimpleMinimap-8.4.0` is installed:

- `com.Landscaper.plugin.external.MinimapApi`
- `com.Landscaper.plugin.MinimapPlugin`
- `com.Landscaper.plugin.ui.MinimapHud`
- `com.Landscaper.plugin.config.MinimapConfig`
- `com.Landscaper.plugin.external.MultipleHudHelper`

The integration also relies on the following private upstream members remaining compatible:

- `MinimapApi.plugin`
- `MinimapPlugin.huds`
- `MinimapPlugin.tickTasks`
- `MinimapPlugin.multipleHudHelper`
- `MinimapPlugin.initializePlayerHud(...)`

On the Hytale side, large-map visibility detection currently depends on `WorldMapTracker.clientHasWorldMapVisible`.

If any of those fields or methods change in a future upstream update, `SimpleMinimap` support may need to be adjusted even if the rest of MobMapMarkers still compiles.

## Source Build Contract

- If a local `../../OtherMapMods/SimpleMinimap-8.4.0.jar` exists, Gradle uses it as the compile-only API.
- If it does not exist, Gradle compiles against internal stub sources under `src/simpleminimap-stubs/java`.
- The produced mod jar excludes those stub classes, so runtime compatibility still depends on the real SimpleMinimap mod being installed.
