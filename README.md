# MobMapMarkers

MobMapMarkers is a Hytale server mod focused on mob markers for the default world map, with optional compatibility for `SimpleMinimap 8.4.0`.

Version `1.5.0` completes the migration away from the old runtime asset-pack approach. Marker icons are generated and delivered in memory, the plugin now shuts down cleanly, and source builds no longer require a local SimpleMinimap jar to compile.

## Features

- Shows mobs on the large default Hytale world map without bundling player-avatar features
- Uses official Hytale creature portraits from `Assets.zip` when they can be resolved
- Falls back to generated icons for unknown or modded mob roles
- Mirrors icon facing from movement with yaw fallback so creatures point left or right consistently
- Draws real mob PNGs directly on `SimpleMinimap 8.4.0` through a custom HUD overlay
- Keeps large-map behavior and SimpleMinimap behavior on separate render paths
- Debounces client asset rebuild requests so large-map and minimap updates do not spam rebuild packets
- Cleans up schedulers, packet watchers, cached assets, and viewer state on plugin shutdown

## Configuration

Config file is generated automatically at:
`UserData/Saves/<world>/mods/thenexusgates_MobMapMarkers/mobmapmarkers-config.json`

If an older `mods/MobMapMarkersData` folder already exists, version `1.5.0` migrates the config forward automatically and removes the empty legacy directory.

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

| Field | Default | Description |
|---|---|---|
| `enableMobMarkers` | `true` | Master switch for all mob markers |
| `showMobNames` | `true` | Show creature names in the map label |
| `showDistance` | `true` | Append distance in meters to labels |
| `showMobMarkersOnCompass` | `false` | Allow markers during compass-only updates |
| `showMobMarkersOnSimpleMinimap` | `true` | Draw mob icons directly on `SimpleMinimap 8.4.0` when that mod is installed |
| `mobMarkerRadius` | `768` | Max distance from the viewer; `0` means unlimited |
| `mobMarkerSize` | `44` | Internal render resolution for generated marker icons |
| `mobIconContentScalePercent` | `96` | How much of the fixed Hytale marker slot the icon art should fill |
| `maxVisibleMobMarkers` | `128` | Hard cap per viewer after nearest-first sorting; `0` means unlimited |
| `scanIntervalMs` | `1000` | NPC scan cadence in milliseconds |
| `renderUnknownMobFallbacks` | `true` | Generate fallback icons for unresolved or modded mob roles |

## Installation

1. Copy `MobMapMarkers-1.5.0.jar` to `UserData/Saves/<YourWorld>/mods/`.
2. Start the server.
3. If `SimpleMinimap-8.4.0.jar` is installed too, keep `showMobMarkersOnSimpleMinimap` enabled to draw mob markers on the minimap.

## Building from source

```bash
cd MobMapMarkers
./gradlew clean build
```

Output:

```text
build/libs/MobMapMarkers-1.5.0.jar
```

Notes:

- If `../../OtherMapMods/SimpleMinimap-8.4.0.jar` exists, the build compiles against the real SimpleMinimap API.
- If that jar is missing, the build automatically falls back to internal compile-only stubs and still produces the mod jar.
- You can force stub mode in CI with `-PmobMapMarkers.useSimpleMinimapStubs=true`.

## Technical Notes

- The compatibility contract and render/data-flow notes are documented in [TECHNICAL.md](TECHNICAL.md).

## Requirements

- Hytale Server `2026.03.26-89796e57b` or newer
- Java 25

## License

GNU Affero General Public License v3.0 — see [LICENSE](LICENSE)
