package dev.thenexusgates.mobmapmarkers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MobMapMarkersConfig {

    private static final Logger LOGGER = Logger.getLogger(MobMapMarkersConfig.class.getName());

    boolean enableMobMarkers = true;
    boolean showMobNames = true;
    boolean showDistance = true;
    boolean showMobMarkersOnCompass = false;
    boolean showMobMarkersOnFastMiniMap = true;
    int mobMarkerRadius = 768;
    int mobMarkerSize = 44;
    int mobIconContentScalePercent = 96;
    int maxVisibleMobMarkers = 128;
    int scanIntervalMs = 1000;
    boolean renderUnknownMobFallbacks = true;

    private MobMapMarkersConfig() {
    }

    static MobMapMarkersConfig load(Path configPath) {
        MobMapMarkersConfig config = new MobMapMarkersConfig();
        if (!Files.exists(configPath)) {
            save(config, configPath);
            return config;
        }

        try {
            String json = Files.readString(configPath, StandardCharsets.UTF_8);
            config.enableMobMarkers = readBool(json, "enableMobMarkers", config.enableMobMarkers);
            config.showMobNames = readBool(json, "showMobNames", config.showMobNames);
            config.showDistance = readBool(json, "showDistance", config.showDistance);
            config.showMobMarkersOnCompass = readBool(json, "showMobMarkersOnCompass", config.showMobMarkersOnCompass);
            config.showMobMarkersOnFastMiniMap = readBool(
                    json,
                    "showMobMarkersOnFastMiniMap",
                    config.showMobMarkersOnFastMiniMap);
            config.mobMarkerRadius = readInt(json, "mobMarkerRadius", config.mobMarkerRadius);
            config.mobMarkerSize = readInt(json, "mobMarkerSize", config.mobMarkerSize);
            config.mobIconContentScalePercent = readInt(
                    json,
                    "mobIconContentScalePercent",
                    config.mobIconContentScalePercent);
            config.maxVisibleMobMarkers = readInt(json, "maxVisibleMobMarkers", config.maxVisibleMobMarkers);
            config.scanIntervalMs = readInt(json, "scanIntervalMs", config.scanIntervalMs);
            config.renderUnknownMobFallbacks = readBool(json, "renderUnknownMobFallbacks", config.renderUnknownMobFallbacks);
            normalize(config);
            save(config, configPath);
            LOGGER.info("[MobMapMarkers] Config loaded: iconSize=" + config.mobMarkerSize
                    + ", contentScale=" + config.mobIconContentScalePercent
                    + ", radius=" + config.mobMarkerRadius
                    + ", maxVisible=" + config.maxVisibleMobMarkers
                    + ", scanIntervalMs=" + config.scanIntervalMs);
        } catch (IOException e) {
            LOGGER.warning("[MobMapMarkers] Failed to read config, using defaults: " + e.getMessage());
        }
        return config;
    }

    private static void normalize(MobMapMarkersConfig config) {
        config.mobMarkerRadius = Math.max(0, config.mobMarkerRadius);
        config.mobMarkerSize = clamp(config.mobMarkerSize, 16, 256);
        config.mobIconContentScalePercent = clamp(config.mobIconContentScalePercent, 50, 100);
        config.maxVisibleMobMarkers = Math.max(0, config.maxVisibleMobMarkers);
        config.scanIntervalMs = clamp(config.scanIntervalMs, 250, 60000);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void save(MobMapMarkersConfig config, Path configPath) {
        normalize(config);
        String json = """
                {
                  "enableMobMarkers": %s,
                  "showMobNames": %s,
                  "showDistance": %s,
                  "showMobMarkersOnCompass": %s,
                  "showMobMarkersOnFastMiniMap": %s,
                  "mobMarkerRadius": %d,
                  "mobMarkerSize": %d,
                  "mobIconContentScalePercent": %d,
                  "maxVisibleMobMarkers": %d,
                  "scanIntervalMs": %d,
                  "renderUnknownMobFallbacks": %s
                }
                """.formatted(
                config.enableMobMarkers,
                config.showMobNames,
                config.showDistance,
                config.showMobMarkersOnCompass,
                config.showMobMarkersOnFastMiniMap,
                config.mobMarkerRadius,
                config.mobMarkerSize,
                config.mobIconContentScalePercent,
                config.maxVisibleMobMarkers,
                config.scanIntervalMs,
                config.renderUnknownMobFallbacks);
        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warning("[MobMapMarkers] Failed to save config: " + e.getMessage());
        }
    }

    private static boolean readBool(String json, String key, boolean fallback) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(true|false)");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? Boolean.parseBoolean(matcher.group(1)) : fallback;
    }

    private static int readInt(String json, String key, int fallback) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }
}
