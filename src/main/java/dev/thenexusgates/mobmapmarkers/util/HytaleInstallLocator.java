package dev.thenexusgates.mobmapmarkers.util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class HytaleInstallLocator {

    private static volatile Path assetsZipPath;

    private HytaleInstallLocator() {
    }

    public static Path resolveAssetsZipPath() {
        String explicitAssetsZip = firstNonBlank(
                System.getProperty("hytale.assets_zip"),
                System.getenv("HYTALE_ASSETS_ZIP"));
        if (explicitAssetsZip != null) {
            Path explicitPath = Paths.get(explicitAssetsZip).toAbsolutePath().normalize();
            if (Files.exists(explicitPath)) {
                assetsZipPath = explicitPath;
                return explicitPath;
            }
        }

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

    public static void clearCaches() {
        assetsZipPath = null;
    }

    public static List<Path> findInstalledModArchives(Class<?> anchorClass) {
        LinkedHashSet<Path> archives = new LinkedHashSet<>();
        addConfiguredArchivePaths(
                archives,
                firstNonBlank(
                        System.getProperty("hytale.mod_archives"),
                        System.getenv("HYTALE_MOD_ARCHIVES")));
        archives.addAll(findCoLocatedModArchives(anchorClass));
        return List.copyOf(archives);
    }

    public static List<Path> findCoLocatedModArchives(Class<?> anchorClass) {
        Path anchorPath = resolveCodeSourcePath(anchorClass);
        Path modsDirectory = resolveCoLocatedModsDirectory(anchorPath);
        if (modsDirectory == null || !Files.isDirectory(modsDirectory)) {
            return List.of();
        }

        LinkedHashSet<Path> archives = new LinkedHashSet<>();
        try (var modFiles = Files.list(modsDirectory)) {
            modFiles.filter(Files::isRegularFile)
                    .filter(HytaleInstallLocator::isArchiveFile)
                    .map(path -> path.toAbsolutePath().normalize())
                    .filter(path -> anchorPath == null || !path.equals(anchorPath))
                    .forEach(archives::add);
        } catch (Exception ignored) {
        }
        return List.copyOf(archives);
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

    private static Path resolveCodeSourcePath(Class<?> anchorClass) {
        if (anchorClass == null) {
            return null;
        }

        try {
            var codeSource = anchorClass.getProtectionDomain().getCodeSource();
            if (codeSource == null || codeSource.getLocation() == null) {
                return null;
            }
            return Paths.get(codeSource.getLocation().toURI()).toAbsolutePath().normalize();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Path resolveCoLocatedModsDirectory(Path anchorPath) {
        if (anchorPath == null) {
            return null;
        }

        if (Files.isDirectory(anchorPath)) {
            if (anchorPath.getFileName() != null && "mods".equalsIgnoreCase(anchorPath.getFileName().toString())) {
                return anchorPath;
            }

            Path parent = anchorPath.getParent();
            if (parent != null && parent.getFileName() != null
                    && "mods".equalsIgnoreCase(parent.getFileName().toString())) {
                return parent;
            }
            return null;
        }

        return anchorPath.getParent();
    }

    private static boolean isArchiveFile(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }

        String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return lower.endsWith(".jar") || lower.endsWith(".zip");
    }

    private static void addConfiguredArchivePaths(LinkedHashSet<Path> archives, String configuredPaths) {
        if (configuredPaths == null || configuredPaths.isBlank()) {
            return;
        }

        String separator = Pattern.quote(File.pathSeparator);
        for (String rawPart : configuredPaths.split(separator)) {
            if (rawPart == null || rawPart.isBlank()) {
                continue;
            }

            Path candidate = Paths.get(rawPart.trim()).toAbsolutePath().normalize();
            if (Files.isDirectory(candidate)) {
                try (var children = Files.list(candidate)) {
                    children.filter(Files::isRegularFile)
                            .filter(HytaleInstallLocator::isArchiveFile)
                            .map(path -> path.toAbsolutePath().normalize())
                            .forEach(archives::add);
                } catch (Exception ignored) {
                }
                continue;
            }

            if (Files.isRegularFile(candidate) && isArchiveFile(candidate)) {
                archives.add(candidate);
            }
        }
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