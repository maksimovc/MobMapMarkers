package dev.thenexusgates.mobmapmarkers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobMapAssetPackInitTest {

    @TempDir
    Path tempDir;

    @Test
    void migratesLegacyConfigOutOfModsDirectory() throws IOException {
        Path legacyModsDirectory = tempDir.resolve("mods");
        Path legacyDataDirectory = legacyModsDirectory.resolve("MobMapMarkersData");
        Files.createDirectories(legacyDataDirectory);

        Path legacyConfigPath = legacyDataDirectory.resolve("mobmapmarkers-config.json");
        Files.writeString(legacyConfigPath, "{\"enableMobMarkers\":false}");

        Path pluginDataDirectory = tempDir.resolve("plugins").resolve("MobMapMarkers");

        MobMapAssetPack.shutdown();
        try {
            MobMapAssetPack.init(pluginDataDirectory, legacyModsDirectory);

            Path migratedConfigPath = pluginDataDirectory.resolve("mobmapmarkers-config.json");
            assertEquals(pluginDataDirectory, MobMapAssetPack.getDataRoot());
            assertTrue(Files.exists(migratedConfigPath));
            assertEquals("{\"enableMobMarkers\":false}", Files.readString(migratedConfigPath));
            assertFalse(Files.exists(legacyConfigPath));
            assertFalse(Files.exists(legacyDataDirectory));
        } finally {
            MobMapAssetPack.shutdown();
        }
    }
}