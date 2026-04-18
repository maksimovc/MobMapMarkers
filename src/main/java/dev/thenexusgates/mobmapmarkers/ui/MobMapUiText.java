package dev.thenexusgates.mobmapmarkers.ui;

import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class MobMapUiText {

    private static final Map<String, String> EN = load("mobmapmarkers/lang/en.txt");
    private static final Map<String, String> PT_BR = load("mobmapmarkers/lang/pt-br.txt");
    private static final Map<String, String> RU = load("mobmapmarkers/lang/ru.txt");
    private static final Map<String, String> UK = load("mobmapmarkers/lang/uk.txt");

    private MobMapUiText() {
    }

    public static String key(PlayerRef playerRef, String key) {
        String value = select(playerRef).get(key);
        if (value != null) {
            return value;
        }

        value = EN.get(key);
        return value != null ? value : key;
    }

    public static String keyf(PlayerRef playerRef, String key, Object... args) {
        return String.format(Locale.ROOT, key(playerRef, key), args);
    }

    private static Map<String, String> select(PlayerRef playerRef) {
        String language = playerRef == null ? null : playerRef.getLanguage();
        String normalized = language == null ? "en" : language.replace('_', '-').toLowerCase(Locale.ROOT);
        if (normalized.startsWith("uk")) {
            return UK;
        }
        if (normalized.startsWith("ru")) {
            return RU;
        }
        if (normalized.startsWith("pt-br") || normalized.equals("pt") || normalized.startsWith("pt-")) {
            return PT_BR;
        }
        return EN;
    }

    private static Map<String, String> load(String resourcePath) {
        try (InputStream stream = MobMapUiText.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Missing MobMapMarkers language resource: " + resourcePath);
            }

            return parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load MobMapMarkers language resource: " + resourcePath, e);
        }
    }

    private static Map<String, String> parse(String rawText) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (String rawLine : rawText.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            int separator = line.indexOf('=');
            if (separator <= 0) {
                continue;
            }

            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if (!key.isEmpty()) {
                values.put(key, value);
            }
        }
        return Collections.unmodifiableMap(values);
    }
}