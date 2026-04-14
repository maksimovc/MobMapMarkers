# MobMapMarkers

Shows nearby mobs as icon markers on the world map (M key) and optionally on the minimap when [FastMiniMap](https://www.curseforge.com/hytale/mods/fast-mini-map) is installed.

## Features

- Mob icons on the world map using official Hytale creature portraits
- Auto-generated fallback icons for unknown or modded mob types
- Nearest-first cap keeps crowded maps readable
- Radius filtering, distance labels, mob name labels
- Optional compass-mode marker display
- Startup icon pre-warming to avoid rebuild spam at runtime
- **Minimap overlay** — mob icons appear on the minimap when FastMiniMap is installed

## Installation

1. Copy `MobMapMarkers-1.6.0.jar` to `UserData/Saves/<World>/mods/`
2. Start the server — config is auto-generated on first run

## Configuration

Config path: `UserData/Saves/<World>/mods/MobMapMarkersAssets/mobmapmarkers-config.json`

| Key | Default | Description |
|-----|---------|-------------|
| `enableMobMarkers` | `true` | Enable/disable all mob markers |
| `showMobNames` | `true` | Show mob name below icon |
| `showDistance` | `true` | Show distance to mob on marker |
| `showMobMarkersOnCompass` | `false` | Also show markers during compass-only updates |
| `mobMarkerRadius` | `768` | Max viewing distance for mob markers (blocks) |
| `mobMarkerSize` | `44` | Icon render resolution in pixels |
| `mobIconContentScalePercent` | `96` | How much of the marker slot the portrait fills |
| `maxVisibleMobMarkers` | `128` | Nearest-first cap on visible markers |
| `scanIntervalMs` | `1000` | How often mobs are scanned (ms) |
| `prewarmOfficialIcons` | `true` | Pre-render official portraits on startup |
| `renderUnknownMobFallbacks` | `true` | Generate icons for unknown mob types |

## Commands

| Command | Alias | Description |
|---------|-------|-------------|
| `/mobmap` | `/mobmarkers` | Open the per-player mob filter UI |
| `/mobmap filters` | — | Same as above |

## Permissions

| Permission | Description |
|------------|-------------|
| `mobmapmarkers.use` | Open the filter UI (`/mobmap`). Grant to all players. |
| `mobmapmarkers.filters.map` | Edit world-map visibility filters |
| `mobmapmarkers.filters.minimap` | Edit minimap visibility filters |
| `mobmapmarkers.filters.compass` | Edit compass visibility filters |
| `mobmapmarkers.filters.bulk.map` | Bulk-toggle all mobs on the world map |
| `mobmapmarkers.filters.bulk.minimap` | Bulk-toggle all mobs on the minimap |
| `mobmapmarkers.filters.bulk.compass` | Bulk-toggle all mobs on the compass |
| `mobmapmarkers.admin` | Admin-level access (reserved for future use) |

> By default all permissions are **op-only**. Grant `mobmapmarkers.use` and the relevant `filters.*` nodes to let regular players manage their own filters.

## Compatibility

- Standalone — works without any other mods
- Gains **minimap mob overlay** with FastMiniMap installed

## Recommended mods

[![FastMiniMap](https://media.forgecdn.net/avatars/thumbnails/1753/613/256/256/639116048377503958.png)](https://www.curseforge.com/hytale/mods/fast-mini-map)
[![PlayerAvatarMarker](https://media.forgecdn.net/avatars/thumbnails/1744/173/256/256/639109874917134953.png)](https://www.curseforge.com/hytale/mods/player-avatar-marker)
