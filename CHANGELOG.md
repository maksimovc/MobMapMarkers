# Changelog

## 1.6.3 - 2026-04-20

This file fixes the release record for the current `1.6.3` codebase.

### Highlights

- Added stable world-map mob markers with per-player visibility filters exposed through `/mobmap`
- Added BetterMap radar and compass output through the same visibility rule set
- Added optional FastMiniMap mob overlays with progressive background icon delivery
- Finalized authored icon resolution through Hytale role and model JSON inheritance instead of direct png-name matching
- Added support for template-role traversal, descendant fallback, memories portrait lookup, and parent-model icon inheritance
- Added support for additional mod archive lookup from archives co-located with the installed `MobMapMarkers` jar
- Added explicit runtime overrides for asset discovery through `hytale.assets_zip` / `HYTALE_ASSETS_ZIP`
- Added explicit runtime overrides for mod archive discovery through `hytale.mod_archives` / `HYTALE_MOD_ARCHIVES`
- Kept the phased asset-delivery pipeline instead of relying on fixed delay timers

### Configuration and operations

- Active artifact name: `MobMapMarkers-1.6.3.jar`
- Config schema version: `2`
- Config path: `UserData/Saves/<World>/mods/thenexusgates_MobMapMarkers/config/mobmapmarkers.json`
- Supported server baseline: `2026.03.26-89796e57b`
- Java requirement: `25`

### Validation

- `gradlew.bat test`
- Resolver regression coverage includes template-chain resolution, descendant fallback, parent-model inheritance, and mod-archive portrait override precedence