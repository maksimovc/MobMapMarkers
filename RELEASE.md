# MobMapMarkers Release Guide

Release checklist and store-ready text for **MobMapMarkers**.

Current release target: `v1.6.0`
Repository: `https://github.com/maksimovc/MobMapMarkers`
Project type: Hytale server mod
Main class: `dev.thenexusgates.mobmapmarkers.MobMapMarkersPlugin`
Asset model: in-memory marker generation and viewer-scoped packet delivery

## Mod Summary

**MobMapMarkers** is a dedicated Hytale map mod that shows mobs on the default world map and, when `FastMiniMap` is installed alongside it, overlays mob dots on the minimap.

The mod:

- Scans NPC entities server-side
- Resolves official Hytale creature portraits from `Assets.zip` when possible
- Falls back to generated icons for unknown or modded roles
- Trims transparent portrait padding so icons render visibly larger inside Hytale's fixed marker slot
- Separates the large-map provider path from the FastMiniMap dot overlay path
- Uses in-memory icon caching and direct client delivery instead of a runtime asset pack directory
- Shuts down cleanly by stopping schedulers, deregistering packet watchers, and clearing cached viewer state

## Release Facts

| Field | Value |
|---|---|
| Project name | `MobMapMarkers` |
| Current version | `1.6.0` |
| Jar name | `MobMapMarkers-1.6.0.jar` |
| Java version | `25` |
| Target server version | `2026.03.26-89796e57b` |
| Manifest file | `src/main/resources/manifest.json` |
| Config file | `mods/thenexusgates_MobMapMarkers/mobmapmarkers-config.json` |
| Includes asset pack | `false` |
| License | `AGPL-3.0` |

## Build

```bash
cd MobMapMarkers
./gradlew clean build
```

Expected output:

```text
build/libs/MobMapMarkers-1.6.0.jar
```

## Runtime Validation

After copying the jar to `UserData/Saves/<YourWorld>/mods/`, verify these startup lines:

```text
[MobMapMarkers] Starting v1.5.0
[MobMapMarkers] Provider registered: <world>
[MobMapMarkers] Ready.
```

Then validate in game:

- Mob markers appear on the large default world map
- Mob dots appear on `FastMiniMap` when both mods are installed and `showMobMarkersOnFastMiniMap` stays enabled
- Mob markers do not leak onto the vanilla Hytale compass unless `showMobMarkersOnCompass` is enabled
- Unknown or modded mobs use fallback icons when `renderUnknownMobFallbacks` is enabled
- Repeated asset rebuilds are coalesced instead of being triggered per render path
- Server shutdown leaves no lingering MobMapMarkers scheduler threads behind

## Key Configuration To Mention In Release Notes

```json
{
  "enableMobMarkers": true,
  "showMobNames": true,
  "showDistance": true,
  "showMobMarkersOnCompass": false,
  "showMobMarkersOnFastMiniMap": true,
  "mobMarkerRadius": 768,
  "mobMarkerSize": 44,
  "mobIconContentScalePercent": 96,
  "maxVisibleMobMarkers": 128,
  "scanIntervalMs": 1000,
  "renderUnknownMobFallbacks": true
}
```

## What Changed In 1.6.0

- Removed SimpleMinimap integration entirely (deleted `SimpleMinimapCompat`, `SimpleMinimapOverlayService`, `MobMapMarkersMinimapHud`, stub sources)
- Added native FastMiniMap mob dot overlay via `FastMiniMapMobLayerApi.setProvider()`
- `FastMiniMapCompatService` reads from `MobMarkerManager`, filters by radius and visibility rules, and assigns stable per-type hue colors
- `FastMiniMapCompat` detects FastMiniMap at runtime without a hard dependency
- Renamed config field `showMobMarkersOnSimpleMinimap` → `showMobMarkersOnFastMiniMap`
- Build no longer requires or references any SimpleMinimap jar or stub sources

## GitHub Release Title

```text
MobMapMarkers v1.6.0
```

## GitHub Release Description

```markdown
## MobMapMarkers v1.6.0

Dedicated Hytale server mod for showing mobs on the default world map with optional FastMiniMap mob dot overlay.

### Highlights
- Removed SimpleMinimap integration; added native FastMiniMap mob dot overlay
- `FastMiniMapCompatService` registers with `FastMiniMapMobLayerApi` at startup when FastMiniMap is present
- Mob dots use stable per-type hue with shadow outlines for readability
- Renamed config field `showMobMarkersOnSimpleMinimap` → `showMobMarkersOnFastMiniMap`
- Cleans up scheduler threads, packet watchers, and viewer caches on shutdown
- No SimpleMinimap jar or stubs required at build time

### Installation
1. Copy `MobMapMarkers-1.6.0.jar` to `UserData/Saves/<YourWorld>/mods/`
2. Optionally install `FastMiniMap.jar` alongside it for mob dots on the minimap
3. Start the server

### Requirements
- Hytale Server `2026.03.26-89796e57b` or newer
- Java 25
```

## CurseForge Short Summary

```text
Shows mobs on the Hytale world map with optional FastMiniMap mob dot overlay, in-memory asset delivery, clean shutdown, labels, radius filtering, and fallback icons for unknown mobs.
```

## CurseForge Long Description

```markdown
## MobMapMarkers

MobMapMarkers is a dedicated Hytale server mod for showing mobs on the default world map.

### Features
- Mob-only map markers without bundled player-avatar functionality
- Official Hytale creature portraits when available
- Generated fallback icons for unknown or modded mob roles
- Cropped portraits so icons render larger inside the fixed marker slot
- `FastMiniMap` compatibility that overlays colored mob dots on the minimap when both mods are installed
- No mob leakage onto the vanilla Hytale compass unless `showMobMarkersOnCompass` is enabled
- In-memory asset delivery with coalesced rebuild requests instead of a runtime asset-pack directory
- Clean shutdown that stops background tasks and deregisters packet watchers

### Installation
1. Download `MobMapMarkers-1.6.0.jar`
2. Copy it to `UserData/Saves/<YourWorld>/mods/`
3. Optionally install `FastMiniMap.jar` for mob dots on the minimap
4. Start the server

### Requirements
- Hytale Server `2026.03.26-89796e57b` or newer
- Java 25
```

## Release Checklist

- Confirm `build.gradle.kts` version matches `manifest.json`
- Run `./gradlew clean test build`
- Verify the output jar name is correct
- Confirm startup log lines match the expected version
- Confirm generated config contains `showMobMarkersOnFastMiniMap`
- Confirm docs mention `mods/thenexusgates_MobMapMarkers/mobmapmarkers-config.json`
- Confirm the release text mentions clean shutdown and in-memory asset delivery
