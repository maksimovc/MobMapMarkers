# Release Guide

Instructions for publishing **MobMapMarkers** on GitHub and CurseForge.

Current release target: `v1.0.1`

## Build locally

```bash
cd MobMapMarkers
./gradlew clean build
```

The output jar is at:
```
build/libs/MobMapMarkers-1.0.1.jar
```

Expected startup lines:
```
[MobMapMarkers] Starting v1.0.1
[MobMapMarkers] Provider registered: <world>
[MobMapMarkers] Ready.
```

## What Changed In 1.0.1

- Fixed effective icon sizing by trimming transparent borders from official portraits before scaling them into the Hytale marker slot
- Added `mobIconContentScalePercent` so the visible art can fill more of the fixed map marker slot
- Kept startup prewarming for official icons to avoid runtime asset rebuild spam
- Updated docs, manifest metadata, and release notes to match the real mod behavior

## Suggested Release Notes

```markdown
## MobMapMarkers v1.0.1

A dedicated Hytale server mod for showing mobs on the default world map.

### Highlights
- Uses official Hytale creature portraits when they can be resolved from `Assets.zip`
- Trims transparent padding so portrait-based markers render visibly larger in the fixed map marker slot
- Adds `mobIconContentScalePercent` for tuning how much of the slot the icon art fills
- Falls back to generated role icons for unknown or modded mobs
- Supports radius filtering, compass visibility, name labels, distance labels, scan interval, and nearest-marker caps
- Prewarms official icons at startup to avoid vanilla runtime asset rebuild spam
```

## CurseForge Summary

`Shows mobs on the Hytale world map with cropped official portraits, configurable icon fill, radius, labels, and startup-prewarmed assets.`
