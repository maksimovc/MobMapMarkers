# MobMapMarkers

MobMapMarkers `1.6.5` is a Hytale server mod that adds per-player mob markers to the default world map, BetterMap radar, and optionally to FastMiniMap.

This release keeps the phased icon pipeline but caps each delivery phase so large `mmm-*` asset bursts rebuild the client atlas in smaller chunks.

## Release 1.6.5

- Limits new marker assets per delivery phase to reduce heavy client atlas bursts
- Keeps the phased world-map and minimap icon-delivery pipeline intact
- Updates release metadata, artifact names, and docs for `1.6.5`

## Features

- Shows nearby mobs on the large default Hytale world map without bundling unrelated avatar systems
- Resolves authored creature icons from Hytale assets and mod archives through JSON-defined role/model inheritance
- Falls back to generated icons for unresolved or modded roles when fallback rendering is enabled
- Mirrors icon facing from movement with yaw fallback so creatures consistently face left or right
- Supports BetterMap compass/radar output through the same per-mob visibility filters used by the map UI
- Shows mob dots on FastMiniMap when that mod is installed and enabled in config
- Loads minimap icons progressively in the background instead of blocking the first overlay update
- Uses a paginated `/mobmap` page with lazy row and icon loading so opening the UI does not append the full catalog at once
- Cleans up schedulers, packet watchers, cached assets, and viewer state on plugin shutdown

## Configuration

Config file is generated automatically at:
`UserData/Saves/<world>/mods/thenexusgates_MobMapMarkers/config/mobmapmarkers.json`

If an older `plugins/MobMapMarkers` or `mods/MobMapMarkersData` folder already exists, the mod migrates the config forward automatically when possible.

```json
{
  "configVersion": 3,
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
  "scanIntervalMs": 50,
  "renderUnknownMobFallbacks": true
}
```

| Field | Default | Description |
|---|---|---|
| `configVersion` | `3` | Internal schema version written by the mod; leave this field under mod control |
| `enableMobMarkers` | `true` | Master switch for all mob markers |
| `enableMobMapCommand` | `true` | Allow players to open the `/mobmap` GUI; set to `false` to disable the GUI entirely |
| `showMobNames` | `true` | Show creature names in the map label |
| `showDistance` | `true` | Append distance in meters to labels |
| `showMobMarkersOnCompass` | `false` | Allow markers during compass-only and BetterMap radar updates |
| `showMobMarkersOnFastMiniMap` | `true` | Show mob dots on `FastMiniMap` when that mod is installed |
| `mobMarkerRadius` | `768` | Max distance from the viewer; `0` means unlimited |
| `mobMarkerSize` | `44` | Internal render resolution for generated marker icons; clamped to `16..256` |
| `mobIconContentScalePercent` | `96` | How much of the fixed Hytale marker slot the icon art should fill; clamped to `50..100` |
| `maxVisibleMobMarkers` | `128` | Hard cap per viewer after nearest-first sorting; `0` means unlimited |
| `scanIntervalMs` | `50` | NPC scan cadence in milliseconds; clamped to `50..60000` |
| `renderUnknownMobFallbacks` | `true` | Generate fallback icons for unresolved or modded mob roles |

## Commands and permissions

| Command | Description |
|---|---|
| `/mobmap` | Open the per-player mob visibility page |

`/mobmap` can open for any player while `enableMobMapCommand` is `true`. The actual edit controls shown inside the page depend on permission grants from the server permissions module.

| Permission | Description |
|---|---|
| `mobmapmarkers.filters.map` | Show and use map visibility toggles |
| `mobmapmarkers.filters.minimap` | Show and use minimap visibility toggles |
| `mobmapmarkers.filters.compass` | Show and use compass/radar visibility toggles |
| `mobmapmarkers.filters.bulk.map` | Show and use the bulk world-map toggle |
| `mobmapmarkers.filters.bulk.minimap` | Show and use the bulk minimap toggle |
| `mobmapmarkers.filters.bulk.compass` | Show and use the bulk compass/radar toggle |

## Asset resolution

Version `1.6.5` resolves mob icons from the following sources, in order of availability:

- The nearest valid `Assets.zip` discovered from the runtime or from the explicit `hytale.assets_zip` system property or `HYTALE_ASSETS_ZIP` environment variable
- Additional mod archives placed next to the installed `MobMapMarkers` jar
- Additional mod archives supplied through `hytale.mod_archives` or `HYTALE_MOD_ARCHIVES`

The resolver follows authored asset data instead of matching png names directly. That includes role references, template roles, descendant fallbacks, memories portrait names, model selectors, and model parent chains.

## Installation

1. Copy `MobMapMarkers-1.6.5.jar` to `UserData/Saves/<YourWorld>/mods/`.
2. Start the server.
3. If `FastMiniMap.jar` is installed too, keep `showMobMarkersOnFastMiniMap` enabled to show mob dots on the minimap.
4. If `BetterMap.jar` is installed too, enable `showMobMarkersOnCompass` or the matching per-mob compass filter to surface them on BetterMap radar.

## Building from source

```bash
cd MobMapMarkers
./gradlew clean build
```

Output:

```text
build/libs/MobMapMarkers-1.6.5.jar
```

Notes:

- The build references `../FastMiniMap/build/libs/FastMiniMap-*.jar` as an optional `compileOnly` dependency; build FastMiniMap first.
- If the FastMiniMap jar is absent, the build still succeeds — the FastMiniMap integration is detected at runtime.

## Validation

The `1.6.5` codebase is currently validated with the included JUnit suite in `src/test/java`, including resolver coverage for template chains, descendant fallback, parent-model inheritance, mod override precedence, and the current build pipeline.

## Requirements

- Hytale Server `2026.03.26-89796e57b` or newer
- Java 25

## License

GNU Affero General Public License v3.0 — see [LICENSE](LICENSE)
