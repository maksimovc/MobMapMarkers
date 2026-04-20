package dev.thenexusgates.mobmapmarkers.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MobMapMarkersConfig {

    private static final int CONFIG_VERSION = 2;
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    public boolean enableMobMarkers = true;
    public boolean enableMobMapCommand = true;
    public boolean showMobNames = true;
    public boolean showDistance = true;
    public boolean showMobMarkersOnCompass = false;
    public boolean showMobMarkersOnFastMiniMap = true;
    public int mobMarkerRadius = 768;
    public int mobMarkerSize = 44;
    public int mobIconContentScalePercent = 96;
    public int maxVisibleMobMarkers = 128;
    public int scanIntervalMs = 1000;
    public boolean renderUnknownMobFallbacks = true;

    private MobMapMarkersConfig() {
    }

    public static MobMapMarkersConfig load(Path configPath) {
        MobMapMarkersConfig config = new MobMapMarkersConfig();
        if (!Files.exists(configPath)) {
            save(config, configPath);
            return config;
        }

        try {
            String json = Files.readString(configPath, StandardCharsets.UTF_8);
            JsonObject root = parseJsonObject(json);
            if (root != null) {
                config.enableMobMarkers = readBool(root, "enableMobMarkers", config.enableMobMarkers);
                config.enableMobMapCommand = readBool(root, "enableMobMapCommand", config.enableMobMapCommand);
                config.showMobNames = readBool(root, "showMobNames", config.showMobNames);
                config.showDistance = readBool(root, "showDistance", config.showDistance);
                config.showMobMarkersOnCompass = readBool(root, "showMobMarkersOnCompass", config.showMobMarkersOnCompass);
                config.showMobMarkersOnFastMiniMap = readBool(
                        root,
                        "showMobMarkersOnFastMiniMap",
                        config.showMobMarkersOnFastMiniMap);
                config.mobMarkerRadius = readInt(root, "mobMarkerRadius", config.mobMarkerRadius);
                config.mobMarkerSize = readInt(root, "mobMarkerSize", config.mobMarkerSize);
                config.mobIconContentScalePercent = readInt(
                        root,
                        "mobIconContentScalePercent",
                        config.mobIconContentScalePercent);
                config.maxVisibleMobMarkers = readInt(root, "maxVisibleMobMarkers", config.maxVisibleMobMarkers);
                config.scanIntervalMs = readInt(root, "scanIntervalMs", config.scanIntervalMs);
                config.renderUnknownMobFallbacks = readBool(
                        root,
                        "renderUnknownMobFallbacks",
                        config.renderUnknownMobFallbacks);
            }
        } catch (IOException e) {
        }

        normalize(config);
        save(config, configPath);
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

        JsonObject root = new JsonObject();
        root.addProperty("configVersion", CONFIG_VERSION);
        root.addProperty("enableMobMarkers", config.enableMobMarkers);
        root.addProperty("enableMobMapCommand", config.enableMobMapCommand);
        root.addProperty("showMobNames", config.showMobNames);
        root.addProperty("showDistance", config.showDistance);
        root.addProperty("showMobMarkersOnCompass", config.showMobMarkersOnCompass);
        root.addProperty("showMobMarkersOnFastMiniMap", config.showMobMarkersOnFastMiniMap);
        root.addProperty("mobMarkerRadius", config.mobMarkerRadius);
        root.addProperty("mobMarkerSize", config.mobMarkerSize);
        root.addProperty("mobIconContentScalePercent", config.mobIconContentScalePercent);
        root.addProperty("maxVisibleMobMarkers", config.maxVisibleMobMarkers);
        root.addProperty("scanIntervalMs", config.scanIntervalMs);
        root.addProperty("renderUnknownMobFallbacks", config.renderUnknownMobFallbacks);

        try {
            Path parent = configPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(configPath, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
        }
    }

    private static JsonObject parseJsonObject(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }

        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (IllegalStateException | JsonParseException exception) {
            return null;
        }
    }

    private static boolean readBool(JsonObject object, String key, boolean fallback) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }

        try {
            return object.get(key).getAsBoolean();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int readInt(JsonObject object, String key, int fallback) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }

        try {
            return object.get(key).getAsInt();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
