# MobMapMarkers Release Guide

Release checklist and store-ready text for **MobMapMarkers**.

Current release target: `v1.5.0`
Repository: `https://github.com/maksimovc/MobMapMarkers`
Project type: Hytale server mod
Main class: `dev.thenexusgates.mobmapmarkers.MobMapMarkersPlugin`
Asset model: in-memory marker generation and viewer-scoped packet delivery

## Mod Summary

**MobMapMarkers** is a dedicated Hytale map mod that shows mobs on the default world map and, when installed, draws real mob icons on `SimpleMinimap-8.4.0`.

The mod:

- Scans NPC entities server-side
- Resolves official Hytale creature portraits from `Assets.zip` when possible
- Falls back to generated icons for unknown or modded roles
- Trims transparent portrait padding so icons render visibly larger inside Hytale's fixed marker slot
- Separates the large-map provider path from the SimpleMinimap HUD overlay path
- Uses in-memory icon caching and direct client delivery instead of a runtime asset pack directory
- Shuts down cleanly by stopping schedulers, deregistering packet watchers, and clearing cached viewer state

## Release Facts

| Field | Value |
|---|---|
| Project name | `MobMapMarkers` |
| Current version | `1.5.0` |
| Jar name | `MobMapMarkers-1.5.0.jar` |
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
build/libs/MobMapMarkers-1.5.0.jar
```

CI/stub build:

```bash
./gradlew clean build -PmobMapMarkers.useSimpleMinimapStubs=true
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
- Mob markers appear on `SimpleMinimap-8.4.0` when that mod is installed and `showMobMarkersOnSimpleMinimap` stays enabled
- Mob markers do not leak onto the vanilla Hytale compass unless `showMobMarkersOnCompass` is enabled
- Default SimpleMinimap waypoint diamonds do not overlap the custom mob overlay while the large map is open
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
  "showMobMarkersOnSimpleMinimap": true,
  "mobMarkerRadius": 768,
  "mobMarkerSize": 44,
  "mobIconContentScalePercent": 96,
  "maxVisibleMobMarkers": 128,
  "scanIntervalMs": 1000,
  "renderUnknownMobFallbacks": true
}
```

## What Changed In 1.5.0

- Added full shutdown cleanup for schedulers, packet watchers, active-player state, and asset caches
- Removed stale documentation and release text that still described the deleted `MobMapMarkersAssets` runtime pack
- Removed the dead `prewarmOfficialIcons` setting and synced generated config output with runtime behavior
- Added optional stub-based source builds so the project can compile without a local `SimpleMinimap-8.4.0.jar`
- Debounced marker asset rebuild requests to reduce duplicate rebuild triggers when large-map and minimap paths run together
- Replaced deprecated player UUID lookup with stable `CommandSender` access
- Added unit tests for config persistence, portrait candidate generation, and facing resolution
- Migrated plugin runtime data out of `mods/MobMapMarkersData` into the plugin data directory to stop manifest-scan noise during startup
- Documented the SimpleMinimap/Hytale compatibility contract and the marker render pipeline

## GitHub Release Title

```text
MobMapMarkers v1.5.0
```

## GitHub Release Description

```markdown
## MobMapMarkers v1.5.0

Dedicated Hytale server mod for showing mobs on the default world map and `SimpleMinimap-8.4.0`.

### Highlights
- Uses in-memory marker delivery instead of a runtime asset-pack directory
- Draws real mob portrait/generated icons directly on `SimpleMinimap-8.4.0`
- Cleans up scheduler threads, packet watchers, and viewer caches on shutdown
- Removes the dead `prewarmOfficialIcons` setting and syncs docs/config with actual behavior
- Supports clean source builds even without a local SimpleMinimap jar through compile-only stubs
- Coalesces asset rebuild requests to reduce duplicate rebuild traffic

### Installation
1. Copy `MobMapMarkers-1.5.0.jar` to `UserData/Saves/<YourWorld>/mods/`
2. Start the server

### Requirements
- Hytale Server `2026.03.26-89796e57b` or newer
- Java 25
```

## CurseForge Short Summary

```text
Shows mobs on the Hytale world map and `SimpleMinimap-8.4.0` with real direct minimap overlays, in-memory asset delivery, clean shutdown behavior, labels, radius filtering, and fallback icons for unknown mobs.
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
- `SimpleMinimap-8.4.0` compatibility that draws the mod's real mob PNG icons directly on the minimap overlay
- No mob leakage onto the vanilla Hytale compass unless `showMobMarkersOnCompass` is enabled
- In-memory asset delivery with coalesced rebuild requests instead of a runtime asset-pack directory
- Clean shutdown that stops background tasks and deregisters packet watchers

### Installation
1. Download `MobMapMarkers-1.5.0.jar`
2. Copy it to `UserData/Saves/<YourWorld>/mods/`
3. Start the server

### Requirements
- Hytale Server `2026.03.26-89796e57b` or newer
- Java 25
```

## Release Checklist

- Confirm `build.gradle.kts` version matches `manifest.json`
- Run `./gradlew clean test build`
- Run `./gradlew clean build -PmobMapMarkers.useSimpleMinimapStubs=true`
- Verify the output jar name is correct
- Confirm startup log lines match the expected version
- Confirm generated config no longer contains `prewarmOfficialIcons`
- Confirm docs mention `mods/thenexusgates_MobMapMarkers/mobmapmarkers-config.json`
- Confirm the release text mentions clean shutdown, stub build support, and in-memory asset delivery
