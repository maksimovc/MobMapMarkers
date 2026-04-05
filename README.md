# MobMapMarkers

A Hytale server mod focused only on mob markers for the default world map. It scans NPC entities server-side, resolves official Hytale creature portraits from `Assets.zip` where possible, trims their transparent padding so they render larger inside the fixed map marker slot, and falls back to generated role icons for unknown or modded mobs when enabled.

## Features

- Shows mobs on the large default world map without bundling player-avatar functionality
- Uses official Hytale portraits from `Common/UI/Custom/Pages/Memories/npcs/*.png` when a role can be resolved
- Crops transparent portrait borders before rendering, so configured larger icons are visibly larger instead of just higher-resolution
- Mirrors icon facing from movement with yaw fallback so creatures visually point left or right on the map
- Prewarms official vanilla mob icons on startup to avoid in-session asset rebuild spam
- Skips scanning worlds with no players and caches player positions for cheap radius checks
- Lets you cap visible mob markers per viewer so crowded worlds stay readable

## Configuration

Config file is generated automatically at:
`UserData/Saves/<world>/mods/MobMapMarkersAssets/mobmapmarkers-config.json`

```json
{
  "enableMobMarkers": true,
  "showMobNames": true,
  "showDistance": true,
  "showMobMarkersOnCompass": false,
  "mobMarkerRadius": 768,
  "mobMarkerSize": 44,
  "mobIconContentScalePercent": 96,
  "maxVisibleMobMarkers": 128,
  "scanIntervalMs": 1000,
  "prewarmOfficialIcons": true,
  "renderUnknownMobFallbacks": true
}
```

| Field | Default | Description |
|---|---|---|
| `enableMobMarkers` | `true` | Master switch for all mob markers |
| `showMobNames` | `true` | Show creature names in the map label |
| `showDistance` | `true` | Append distance in meters to labels |
| `showMobMarkersOnCompass` | `false` | Show mob markers even when only the compass overlay is open |
| `mobMarkerRadius` | `768` | Max distance from the viewer; `0` means unlimited |
| `mobMarkerSize` | `44` | Internal render resolution used when generating marker icons |
| `mobIconContentScalePercent` | `96` | How much of the fixed Hytale marker slot the icon art should fill after transparent padding is trimmed |
| `maxVisibleMobMarkers` | `128` | Hard cap per viewer after nearest-first sorting; `0` means unlimited |
| `scanIntervalMs` | `1000` | NPC scan cadence in milliseconds |
| `prewarmOfficialIcons` | `true` | Build all official vanilla portrait markers at startup |
| `renderUnknownMobFallbacks` | `true` | Generate role-based fallback icons for unresolved or modded mobs |

## Installation

1. Copy `MobMapMarkers-1.0.1.jar` to `UserData/Saves/<YourWorld>/mods/`
2. Start the server.

## Building from source

```bash
cd MobMapMarkers
./gradlew clean build
```

Output: `build/libs/MobMapMarkers-1.0.1.jar`

## Requirements

- Hytale Server `2026.03.26-89796e57b` or newer
- Java 25

## License

GNU Affero General Public License v3.0 — see [LICENSE](LICENSE)
