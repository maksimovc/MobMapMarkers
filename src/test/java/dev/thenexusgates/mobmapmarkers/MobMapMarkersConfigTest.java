package dev.thenexusgates.mobmapmarkers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobMapMarkersConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void loadRewritesConfigWithoutDeadPrewarmField() throws IOException {
        Path configPath = tempDir.resolve("mobmapmarkers-config.json");
        Files.writeString(configPath, """
                {
                  "enableMobMarkers": false,
                  "showMobMarkersOnSimpleMinimap": true,
                  "mobMarkerRadius": 9999,
                  "mobMarkerSize": 8,
                  "mobIconContentScalePercent": 500,
                  "maxVisibleMobMarkers": 12,
                  "scanIntervalMs": 1,
                  "prewarmOfficialIcons": true,
                  "renderUnknownMobFallbacks": false
                }
                """);

        MobMapMarkersConfig config = MobMapMarkersConfig.load(configPath);

        assertFalse(config.enableMobMarkers);
        assertEquals(9999, config.mobMarkerRadius);
        assertEquals(16, config.mobMarkerSize);
        assertEquals(100, config.mobIconContentScalePercent);
        assertEquals(250, config.scanIntervalMs);
        assertFalse(config.renderUnknownMobFallbacks);

        String rewritten = Files.readString(configPath);
        assertFalse(rewritten.contains("prewarmOfficialIcons"));
    }

    @Test
    void missingConfigGeneratesCurrentSchema() throws IOException {
        Path configPath = tempDir.resolve("generated.json");
        MobMapMarkersConfig.load(configPath);

        String generated = Files.readString(configPath);
        assertTrue(generated.contains("showMobMarkersOnSimpleMinimap"));
        assertTrue(generated.contains("renderUnknownMobFallbacks"));
        assertFalse(generated.contains("prewarmOfficialIcons"));
    }
}
