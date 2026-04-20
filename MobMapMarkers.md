# MobMapMarkers

Release target: `1.6.3`

MobMapMarkers shows nearby mobs as icon markers on the world map (M key), BetterMap radar, and optionally on the minimap when [FastMiniMap](https://www.curseforge.com/hytale/mods/fast-mini-map) is installed.

This document is the short-form release sheet for version `1.6.3`.

## Features

- Mob icons on the world map using authored Hytale creature icons resolved from portraits, role chains, and model JSON inheritance
- Auto-generated fallback icons for unknown or modded mob types
- Nearest-first cap keeps crowded maps readable
- Radius filtering, distance labels, mob name labels
- Optional BetterMap compass/radar marker display
- Progressive minimap icon loading to avoid blocking the first overlay update
- Paginated `/mobmap` filter UI with lazy icon loading
- **Minimap overlay** — mob icons appear on the minimap when FastMiniMap is installed and enabled in config

## 1.6.3 highlights

- Stable phased asset delivery with no fixed-delay timer dependency
- Icon resolution through `Assets.zip` plus authored role/model inheritance
- Additional mod archive lookup from co-located archives or explicit archive overrides
- BetterMap radar support and optional FastMiniMap compatibility through the same per-player filters
- Packaged regression tests for the icon resolver

## Installation

1. Copy `MobMapMarkers-1.6.3.jar` to `UserData/Saves/<World>/mods/`
2. Start the server — config is auto-generated on first run

## Configuration

Config path: `UserData/Saves/<World>/mods/thenexusgates_MobMapMarkers/config/mobmapmarkers.json`

| Key | Default | Description |
|-----|---------|-------------|
| `configVersion` | `2` | Internal schema version written by the mod |
| `enableMobMarkers` | `true` | Enable/disable all mob markers |
| `enableMobMapCommand` | `true` | Allow players to open the `/mobmap` GUI; set to `false` to disable the GUI entirely |
| `showMobNames` | `true` | Show mob name below icon |
| `showDistance` | `true` | Show distance to mob on marker |
| `showMobMarkersOnCompass` | `false` | Also show markers during compass-only and BetterMap radar updates |
| `showMobMarkersOnFastMiniMap` | `true` | Enable FastMiniMap mob overlay when FastMiniMap is installed |
| `mobMarkerRadius` | `768` | Max viewing distance for mob markers (blocks) |
| `mobMarkerSize` | `44` | Icon render resolution in pixels, clamped to `16..256` |
| `mobIconContentScalePercent` | `96` | How much of the marker slot the portrait fills, clamped to `50..100` |
| `maxVisibleMobMarkers` | `128` | Nearest-first cap on visible markers; `0` disables the cap |
| `scanIntervalMs` | `1000` | How often mobs are scanned in milliseconds, clamped to `250..60000` |
| `renderUnknownMobFallbacks` | `true` | Generate icons for unknown mob types |

## Commands

| Command | Alias | Description |
|---------|-------|-------------|
| `/mobmap` | — | Open the per-player mob filter UI |

## Permissions

| Permission | Description |
|------------|-------------|
| `mobmapmarkers.filters.map` | Show and use world-map visibility filters |
| `mobmapmarkers.filters.minimap` | Show and use minimap visibility filters |
| `mobmapmarkers.filters.compass` | Show and use compass visibility filters |
| `mobmapmarkers.filters.bulk.map` | Show and use bulk world-map toggles |
| `mobmapmarkers.filters.bulk.minimap` | Show and use bulk minimap toggles |
| `mobmapmarkers.filters.bulk.compass` | Show and use bulk compass toggles |

> `/mobmap` opens while `enableMobMapCommand` is enabled. The actual edit buttons inside the page appear only when the corresponding permission node is granted by the server permissions module.

## Compatibility

- Standalone — works without any other mods
- Gains **minimap mob overlay** with FastMiniMap installed
- Gains **compass/radar mob overlay** with BetterMap installed

## Asset sources

- Auto-discovers `Assets.zip` from the runtime, workspace, or an explicit `hytale.assets_zip` / `HYTALE_ASSETS_ZIP` override
- Reads mod archives next to the installed `MobMapMarkers` jar
- Accepts extra mod archive paths through `hytale.mod_archives` / `HYTALE_MOD_ARCHIVES`

Artifact: `build/libs/MobMapMarkers-1.6.3.jar`

## Recommended mods

[![FastMiniMap](https://media.forgecdn.net/avatars/thumbnails/1753/613/256/256/639116048377503958.png)](https://www.curseforge.com/hytale/mods/fast-mini-map)
[![PlayerAvatarMarker](https://media.forgecdn.net/avatars/thumbnails/1744/173/256/256/639109874917134953.png)](https://www.curseforge.com/hytale/mods/player-avatar-marker)
