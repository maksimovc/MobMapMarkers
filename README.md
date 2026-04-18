# MobMapMarkers

MobMapMarkers is a Hytale server mod focused on mob markers for the default world map, with optional compatibility for `FastMiniMap`.

Version `1.6.0` removes the old SimpleMinimap integration entirely and adds native FastMiniMap mob dot support. Marker icons are generated and delivered in memory, and the plugin shuts down cleanly.

## Features

- Shows mobs on the large default Hytale world map without bundling player-avatar features
- Uses official Hytale creature portraits from `Assets.zip` when they can be resolved
- Falls back to generated icons for unknown or modded mob roles
- Mirrors icon facing from movement with yaw fallback so creatures point left or right consistently
- Shows mob dots on `FastMiniMap` when that mod is installed alongside this one
- Uses a paginated mob-filter page with lazy icon loading so opening the UI does not build and append 1000+ rows at once
- Keeps large-map behavior and FastMiniMap overlay on separate code paths
- Uses a phased asset-delivery pipeline instead of millisecond delays when icons are rebuilt and exposed to viewers
- Cleans up schedulers, packet watchers, cached assets, and viewer state on plugin shutdown

## Configuration

Config file is generated automatically at:
`UserData/Saves/<world>/mods/thenexusgates_MobMapMarkers/mobmapmarkers-config.json`

If an older `plugins/MobMapMarkers` or `mods/MobMapMarkersData` folder already exists, the mod migrates the config forward automatically when possible.

```json
{
  "enableMobMarkers": true,
  "enableMobMapCommand": true,
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

| Field | Default | Description |
|---|---|---|
| `enableMobMarkers` | `true` | Master switch for all mob markers |
| `enableMobMapCommand` | `true` | Allow players to open the `/mobmap` GUI; set to `false` to disable the GUI entirely |
| `showMobNames` | `true` | Show creature names in the map label |
| `showDistance` | `true` | Append distance in meters to labels |
| `showMobMarkersOnCompass` | `false` | Allow markers during compass-only updates |
| `showMobMarkersOnFastMiniMap` | `true` | Show mob dots on `FastMiniMap` when that mod is installed |
| `mobMarkerRadius` | `768` | Max distance from the viewer; `0` means unlimited |
| `mobMarkerSize` | `44` | Internal render resolution for generated marker icons |
| `mobIconContentScalePercent` | `96` | How much of the fixed Hytale marker slot the icon art should fill |
| `maxVisibleMobMarkers` | `128` | Hard cap per viewer after nearest-first sorting; `0` means unlimited |
| `scanIntervalMs` | `1000` | NPC scan cadence in milliseconds |
| `renderUnknownMobFallbacks` | `true` | Generate fallback icons for unresolved or modded mob roles |

## Installation

1. Copy `MobMapMarkers-1.6.0.jar` to `UserData/Saves/<YourWorld>/mods/`.
2. Start the server.
3. If `FastMiniMap.jar` is installed too, keep `showMobMarkersOnFastMiniMap` enabled to show mob dots on the minimap.

## Building from source

```bash
cd MobMapMarkers
./gradlew clean build
```

Output:

```text
build/libs/MobMapMarkers-1.6.0.jar
```

Notes:

- The build references `../FastMiniMap/build/libs/FastMiniMap-1.0.0.jar` as a `compileOnly` dependency; build FastMiniMap first.
- If the FastMiniMap jar is absent, the build still succeeds — the FastMiniMap integration is detected at runtime.

## Requirements

- Hytale Server `2026.03.26-89796e57b` or newer
- Java 25

## License

GNU Affero General Public License v3.0 — see [LICENSE](LICENSE)
