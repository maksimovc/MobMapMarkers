package dev.thenexusgates.mobmapmarkers.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;

public final class HytaleInstallLocator {

    private static volatile Path assetsZipPath;

    private HytaleInstallLocator() {
    }

    public static Path resolveAssetsZipPath() {
        Path cached = assetsZipPath;
        if (cached != null && Files.exists(cached)) {
            return cached;
        }

        for (Path candidate : buildAssetsZipCandidates()) {
            if (candidate != null && Files.exists(candidate)) {
                assetsZipPath = candidate.toAbsolutePath().normalize();
                return assetsZipPath;
            }
        }

        return null;
    }

    public static List<Path> findSaveRoots() {
        LinkedHashSet<Path> roots = new LinkedHashSet<>();
        for (Path base : buildSearchRoots()) {
            for (Path path = base; path != null; path = path.getParent()) {
                Path saves = path.resolve("UserData").resolve("Saves");
                if (Files.isDirectory(saves)) {
                    roots.add(saves.toAbsolutePath().normalize());
                }
            }
        }

        Path assetsZip = resolveAssetsZipPath();
        if (assetsZip != null) {
            for (Path path = assetsZip.toAbsolutePath().normalize().getParent(); path != null; path = path.getParent()) {
                Path saves = path.resolve("UserData").resolve("Saves");
                if (Files.isDirectory(saves)) {
                    roots.add(saves.toAbsolutePath().normalize());
                }
            }
        }

        return List.copyOf(roots);
    }

    private static List<Path> buildAssetsZipCandidates() {
        LinkedHashSet<Path> candidates = new LinkedHashSet<>();
        for (Path root : buildSearchRoots()) {
            addAssetsZipCandidates(root, candidates);
            for (Path path = root; path != null; path = path.getParent()) {
                addAssetsZipCandidates(path, candidates);
            }
        }

        String explicitAssetsZip = firstNonBlank(
                System.getProperty("hytale.assets_zip"),
                System.getenv("HYTALE_ASSETS_ZIP"));
        if (explicitAssetsZip != null) {
            candidates.add(Paths.get(explicitAssetsZip));
        }

        return List.copyOf(candidates);
    }

    private static List<Path> buildSearchRoots() {
        LinkedHashSet<Path> roots = new LinkedHashSet<>();
        roots.add(Paths.get("").toAbsolutePath().normalize());

        String userDir = System.getProperty("user.dir");
        if (userDir != null && !userDir.isBlank()) {
            roots.add(Paths.get(userDir).toAbsolutePath().normalize());
        }

        String explicitHome = firstNonBlank(
                System.getProperty("hytale.home_path"),
                System.getProperty("hytale.home"),
                System.getenv("HYTALE_HOME_PATH"),
                System.getenv("HYTALE_HOME"));
        if (explicitHome != null) {
            roots.add(Paths.get(explicitHome).toAbsolutePath().normalize());
        }

        String workspaceRoot = firstNonBlank(
                System.getenv("VSCODE_CWD"),
                System.getenv("WORKSPACE_FOLDER"));
        if (workspaceRoot != null) {
            roots.add(Paths.get(workspaceRoot).toAbsolutePath().normalize());
        }

        return List.copyOf(roots);
    }

    private static void addAssetsZipCandidates(Path base, LinkedHashSet<Path> candidates) {
        if (base == null) {
            return;
        }

        candidates.add(base.resolve("Assets.zip"));
        candidates.add(base.resolve("latest/Assets.zip"));
        candidates.add(base.resolve("release/latest/Assets.zip"));
        candidates.add(base.resolve("Client/latest/Assets.zip"));
        candidates.add(base.resolve("Client/release/latest/Assets.zip"));
        candidates.add(base.resolve("game/latest/Assets.zip"));
        candidates.add(base.resolve("release/package/game/latest/Assets.zip"));
        candidates.add(base.resolve("install/release/package/game/latest/Assets.zip"));
        candidates.add(base.resolve("Hytale-API/latest/Assets.zip"));
        candidates.add(base.resolve("Hytale-API/Client/latest/Assets.zip"));
        candidates.add(base.resolve("Hytale-API/Client/release/latest/Assets.zip"));
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}