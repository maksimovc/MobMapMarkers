package dev.thenexusgates.mobmapmarkers.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.thenexusgates.mobmapmarkers.MobMapMarkersPlugin;
import dev.thenexusgates.mobmapmarkers.asset.MobMapAssetPack;
import dev.thenexusgates.mobmapmarkers.catalog.MobArchiveIndex;
import dev.thenexusgates.mobmapmarkers.catalog.MobCatalogEntry;
import dev.thenexusgates.mobmapmarkers.catalog.MobNameLocalization;
import dev.thenexusgates.mobmapmarkers.config.MobMapMarkersConfig;
import dev.thenexusgates.mobmapmarkers.filter.MobMarkerFilterRule;
import dev.thenexusgates.mobmapmarkers.filter.MobMarkerSurface;
import dev.thenexusgates.mobmapmarkers.security.MobMapPermissions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

public final class MobMapFiltersPage extends InteractiveCustomUIPage<MobMapFiltersPage.PageData> {

    private static final int PAGE_SIZE = 20;

    private enum SurfaceBulkState {
        ALL_ENABLED,
        ALL_DISABLED,
        MIXED,
        EMPTY
    }

    private static final String BULK_GREEN = "#4d6488";
    private static final String BULK_RED = "#5a3f46";
    private static final String BULK_MIXED = "#8a6a39";
    private static final String BULK_LOCKED = "#3e4655";

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (data, value) -> data.action = value, data -> data.action).add()
                .append(new KeyedCodec<>("@SearchInput", Codec.STRING), (data, value) -> data.searchInput = value, data -> data.searchInput).add()
                .build();

        String action;
        String searchInput;
    }

    private record RowModel(MobCatalogEntry entry, String displayName, MobMarkerFilterRule rule, String assetPath) {
    }

    private record PageSlice(List<RowModel> rows, int pageIndex, int pageCount, int startOrdinal, int endOrdinal) {
    }

    private static final String UI_PAGE = "Pages/MobMapFilters.ui";
    private static final String UI_ROW = "Pages/MobMapFilters_row.ui";
    private static final String GROUP_ROOT = "#RowGroup";

    private final MobMapMarkersPlugin plugin;

    private volatile List<RowModel> cachedRows = List.of();
    private volatile boolean rowsLoading = true;
    private volatile long rowsRefreshToken;
    private volatile long pageIconsToken;
    private String searchInput = "";
    private volatile int currentPage;
    private volatile boolean dismissed;

    public MobMapFiltersPage(PlayerRef playerRef, MobMapMarkersPlugin plugin) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.plugin = plugin;
    }

    @Override
    public void build(Ref<EntityStore> entityRef,
                      UICommandBuilder cmd,
                      UIEventBuilder evt,
                      Store<EntityStore> store) {
        render(cmd, evt);
        requestRowsReload(false);
    }

    @Override
    public void onDismiss(Ref<EntityStore> entityRef,
                          Store<EntityStore> store) {
        dismissed = true;
        super.onDismiss(entityRef, store);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> entityRef,
                                Store<EntityStore> store,
                                PageData data) {
        if (data == null) {
            return;
        }

        if (data.searchInput != null && !Objects.equals(searchInput, data.searchInput)) {
            searchInput = data.searchInput;
            refreshSearchResults();
        }

        if (data.action == null || data.action.isBlank()) {
            return;
        }

        String action = data.action;
        if ("Close".equals(action)) {
            plugin.getUiSounds().play(playerRef, MobMapUiSounds.Cue.NAVIGATE);
            close();
            return;
        }
        if ("ClearSearch".equals(action)) {
            searchInput = "";
            currentPage = 0;
            rowsLoading = true;
            plugin.getUiSounds().play(playerRef, MobMapUiSounds.Cue.NAVIGATE);
            refresh();
            requestRowsReload(false);
            return;
        }
        if ("Page|Prev".equals(action)) {
            changePage(currentPage - 1);
            return;
        }
        if ("Page|Next".equals(action)) {
            changePage(currentPage + 1);
            return;
        }
        if (action.startsWith("Bulk|")) {
            handleBulkToggle(action);
            refresh();
            return;
        }
        if (action.startsWith("Toggle|")) {
            handleToggle(action);
            refresh();
        }
    }

    private void handleToggle(String action) {
        String[] parts = action.split("\\|", 3);
        if (parts.length != 3) {
            plugin.getUiSounds().play(playerRef, MobMapUiSounds.Cue.NEGATIVE);
            return;
        }

        String mobKey = parts[1];
        MobMarkerSurface surface;
        try {
            surface = MobMarkerSurface.valueOf(parts[2]);
        } catch (IllegalArgumentException illegalArgumentException) {
            plugin.getUiSounds().play(playerRef, MobMapUiSounds.Cue.NEGATIVE);
            return;
        }
        if (!MobMapPermissions.canEditSurface(playerRef, surface)) {
            MobMapPermissions.sendDenied(playerRef, surface, false);
            plugin.getUiSounds().play(playerRef, MobMapUiSounds.Cue.NEGATIVE);
            return;
        }

        UUID viewerUuid = playerRef == null ? null : playerRef.getUuid();
        MobMarkerFilterRule current = plugin.getVisibilityService().resolveRule(viewerUuid, mobKey);
        boolean nextEnabled = !current.isEnabled(surface);
        plugin.getFilterStore().setSurface(viewerUuid, mobKey, surface, nextEnabled, MobMapMarkersPlugin.getConfig());
        cachedRows = updateCachedRule(cachedRows, mobKey, surface, nextEnabled);
        plugin.getUiSounds().play(playerRef, nextEnabled ? MobMapUiSounds.Cue.POSITIVE : MobMapUiSounds.Cue.NEGATIVE);
    }

    private void handleBulkToggle(String action) {
        String surfaceName = action.substring("Bulk|".length());
        MobMarkerSurface surface;
        try {
            surface = MobMarkerSurface.valueOf(surfaceName);
        } catch (IllegalArgumentException illegalArgumentException) {
            plugin.getUiSounds().play(playerRef, MobMapUiSounds.Cue.NEGATIVE);
            return;
        }
        if (!MobMapPermissions.canBulkSurface(playerRef, surface)) {
            MobMapPermissions.sendDenied(playerRef, surface, true);
            plugin.getUiSounds().play(playerRef, MobMapUiSounds.Cue.NEGATIVE);
            return;
        }

        List<RowModel> rows = cachedRows;
        if (rows.isEmpty()) {
            plugin.getUiSounds().play(playerRef, MobMapUiSounds.Cue.NEGATIVE);
            return;
        }

        SurfaceBulkState state = bulkState(rows, surface);
        boolean enable = state != SurfaceBulkState.ALL_ENABLED;
        List<String> mobKeys = rows.stream().map(row -> row.entry().mobKey()).toList();
        plugin.getFilterStore().setSurfaceForMany(playerRef.getUuid(), mobKeys, surface, enable, MobMapMarkersPlugin.getConfig());
        cachedRows = updateCachedRules(cachedRows, mobKeys, surface, enable);
        plugin.getUiSounds().play(playerRef, enable ? MobMapUiSounds.Cue.POSITIVE : MobMapUiSounds.Cue.APPLY);
    }

    private void refresh() {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();
        render(cmd, evt);
        sendUpdate(cmd, evt, true);
    }

    private void refreshSearchResults() {
        currentPage = 0;
        rowsLoading = true;
        sendResultsUpdate();
        requestRowsReload(false);
    }

    private void changePage(int requestedPage) {
        int clampedPage = clampPage(requestedPage, cachedRows.size());
        if (clampedPage == currentPage) {
            plugin.getUiSounds().play(playerRef, MobMapUiSounds.Cue.NEGATIVE);
            return;
        }

        currentPage = clampedPage;
        plugin.getUiSounds().play(playerRef, MobMapUiSounds.Cue.NAVIGATE);
        sendResultsUpdate();
        requestVisiblePageIcons();
    }

    private void render(UICommandBuilder cmd, UIEventBuilder evt) {
        cmd.append(UI_PAGE);
        bindClick(evt, "#CloseButton", "Close");
        bindClick(evt, "#ClearSearchButton", "ClearSearch");
        bindClick(evt, "#MapBulkButton", "Bulk|MAP");
        bindClick(evt, "#MinimapBulkButton", "Bulk|MINIMAP");
        bindClick(evt, "#CompassBulkButton", "Bulk|COMPASS");
        bindClick(evt, "#PrevPageButton", "Page|Prev");
        bindClick(evt, "#NextPageButton", "Page|Next");
        bindValue(evt, "#SearchInput", "@SearchInput");

        cmd.set("#Title.Text", k("page.title"));
        cmd.set("#MapStatusLabel.Text", k("label.map"));
        cmd.set("#MinimapStatusLabel.Text", k("label.minimap"));
        cmd.set("#CompassStatusLabel.Text", k("label.compass"));
        cmd.set("#SearchInput.Value", searchInput == null ? "" : searchInput);
        cmd.set("#SearchInput.PlaceholderText", k("search.placeholder"));
        cmd.set("#CloseButtonLabel.Text", k("button.close"));
        cmd.set("#ClearSearchButtonLabel.Text", k("button.clear"));
        cmd.set("#MapBulkButtonLabel.Text", k("label.map"));
        cmd.set("#MinimapBulkButtonLabel.Text", k("label.minimap"));
        cmd.set("#CompassBulkButtonLabel.Text", k("label.compass"));
        cmd.set("#PrevPageButtonLabel.Text", k("button.prev"));
        cmd.set("#NextPageButtonLabel.Text", k("button.next"));

        renderResults(cmd, evt);
    }

    private void renderResults(UICommandBuilder cmd, UIEventBuilder evt) {
        cmd.clear(GROUP_ROOT);

        List<RowModel> rows = cachedRows;
        PageSlice page = pageSlice(rows, currentPage);
        currentPage = page.pageIndex();

        int enabledMap = 0, enabledMinimap = 0, enabledCompass = 0;
        for (RowModel row : rows) {
            if (row.rule().map) enabledMap++;
            if (row.rule().minimap) enabledMinimap++;
            if (row.rule().compass) enabledCompass++;
        }
        int total = rows.size();
        cmd.set("#MapStatusCount.Text", kf("status.enabled", enabledMap, total));
        cmd.set("#MinimapStatusCount.Text", kf("status.enabled", enabledMinimap, total));
        cmd.set("#CompassStatusCount.Text", kf("status.enabled", enabledCompass, total));
        cmd.set("#MapStatusDot.Background", enabledMap == 0 ? "#5a3f46" : enabledMap == total ? "#6f86a8" : "#b88a3e");
        cmd.set("#MinimapStatusDot.Background", enabledMinimap == 0 ? "#5a3f46" : enabledMinimap == total ? "#6f86a8" : "#b88a3e");
        cmd.set("#CompassStatusDot.Background", enabledCompass == 0 ? "#5a3f46" : enabledCompass == total ? "#6f86a8" : "#b88a3e");
        cmd.set("#MapStatusBox.Visible", MobMapPermissions.canEditSurface(playerRef, MobMarkerSurface.MAP));
        cmd.set("#MinimapStatusBox.Visible", MobMapPermissions.canEditSurface(playerRef, MobMarkerSurface.MINIMAP));
        cmd.set("#CompassStatusBox.Visible", MobMapPermissions.canEditSurface(playerRef, MobMarkerSurface.COMPASS));

        if (rows.isEmpty()) {
            cmd.set("#PageIndicator.Text", k("page.indicator.empty"));
        } else {
            cmd.set("#PageIndicator.Text", kf(
                "page.indicator",
                page.pageIndex() + 1,
                page.pageCount()));
        }
        cmd.set("#HeaderRow.Visible", true);
        cmd.set("#PrevPageButton.Visible", page.pageCount() > 1);
        cmd.set("#NextPageButton.Visible", page.pageCount() > 1);
        cmd.set("#PrevPageButton.Background", page.pageIndex() > 0 ? "#343850" : BULK_LOCKED);
        cmd.set("#NextPageButton.Background", page.pageIndex() + 1 < page.pageCount() ? "#343850" : BULK_LOCKED);
        applyBulkButton(cmd, "#MapBulkButton", rows, MobMarkerSurface.MAP, MobMapPermissions.canBulkSurface(playerRef, MobMarkerSurface.MAP));
        applyBulkButton(cmd, "#MinimapBulkButton", rows, MobMarkerSurface.MINIMAP, MobMapPermissions.canBulkSurface(playerRef, MobMarkerSurface.MINIMAP));
        applyBulkButton(cmd, "#CompassBulkButton", rows, MobMarkerSurface.COMPASS, MobMapPermissions.canBulkSurface(playerRef, MobMarkerSurface.COMPASS));

        int index = 0;
        for (RowModel row : page.rows()) {
            cmd.append(GROUP_ROOT, UI_ROW);
            String rowId = GROUP_ROOT + "[" + index++ + "]";
            cmd.set(rowId + " #MobName.Text", row.displayName());
            cmd.set(rowId + " #MobKey.Text", row.entry().roleName());
            cmd.set(rowId + " #MapToggleButtonLabel.Text", toggleText(MobMarkerSurface.MAP, row.rule().map));
            cmd.set(rowId + " #MinimapToggleButtonLabel.Text", toggleText(MobMarkerSurface.MINIMAP, row.rule().minimap));
            cmd.set(rowId + " #CompassToggleButtonLabel.Text", toggleText(MobMarkerSurface.COMPASS, row.rule().compass));
            cmd.set(rowId + " #MapToggleButton.Background", rowButtonBackground(MobMarkerSurface.MAP, row.rule().map));
            cmd.set(rowId + " #MinimapToggleButton.Background", rowButtonBackground(MobMarkerSurface.MINIMAP, row.rule().minimap));
            cmd.set(rowId + " #CompassToggleButton.Background", rowButtonBackground(MobMarkerSurface.COMPASS, row.rule().compass));
            cmd.set(rowId + " #MapToggleButton.Visible", MobMapPermissions.canEditSurface(playerRef, MobMarkerSurface.MAP));
            cmd.set(rowId + " #MinimapToggleButton.Visible", MobMapPermissions.canEditSurface(playerRef, MobMarkerSurface.MINIMAP));
            cmd.set(rowId + " #CompassToggleButton.Visible", MobMapPermissions.canEditSurface(playerRef, MobMarkerSurface.COMPASS));
            bindClick(evt, rowId + " #MapToggleButton", "Toggle|" + row.entry().mobKey() + "|MAP");
            bindClick(evt, rowId + " #MinimapToggleButton", "Toggle|" + row.entry().mobKey() + "|MINIMAP");
            bindClick(evt, rowId + " #CompassToggleButton", "Toggle|" + row.entry().mobKey() + "|COMPASS");
            if (row.assetPath() != null && !row.assetPath().isBlank()) {
                cmd.set(rowId + " #MobIcon.AssetPath", row.assetPath());
                cmd.set(rowId + " #MobIcon.Visible", true);
            } else {
                cmd.set(rowId + " #MobIcon.Visible", false);
            }
            boolean anyEnabled = row.rule().map || row.rule().minimap || row.rule().compass;
            cmd.set(rowId + " #RowAccent.Background", anyEnabled ? "#6f86a8" : "#5a3f46");
        }

        cmd.set("#LoadingState.Visible", rowsLoading && rows.isEmpty());
        cmd.set("#EmptyState.Visible", rows.isEmpty());
        cmd.set("#EmptyState.Text", k("empty.noMatches"));
        if (rowsLoading && rows.isEmpty()) {
            cmd.set("#EmptyState.Visible", false);
        }
    }

    private List<RowModel> buildRows() {
        Map<String, MobCatalogEntry> entries = new LinkedHashMap<>();
        for (MobCatalogEntry entry : plugin.getMobMarkerManager().getCatalogEntries()) {
            entries.putIfAbsent(entry.mobKey(), entry);
        }
        for (MobCatalogEntry entry : MobArchiveIndex.getCatalogEntries()) {
            entries.putIfAbsent(entry.mobKey(), entry);
        }
        Set<String> explicitKeys = plugin.getFilterStore().getExplicitMobKeys(playerRef.getUuid());
        for (String explicitKey : explicitKeys) {
            entries.putIfAbsent(explicitKey, MobCatalogEntry.fallback(explicitKey));
        }

        String query = normalizedSearch();
        List<RowModel> rows = new ArrayList<>();
        for (MobCatalogEntry entry : entries.values()) {
            String displayName = MobNameLocalization.resolveDisplayName(
                    entry.nameTranslationKey(),
                    entry.fallbackDisplayName(),
                    playerRef.getLanguage());
            if (!matchesQuery(query, entry, displayName)) {
                continue;
            }

            MobMarkerFilterRule rule = plugin.getVisibilityService().resolveRule(playerRef.getUuid(), entry.mobKey());
            rows.add(new RowModel(entry, displayName, rule, null));
        }

        rows.sort(Comparator.comparing(RowModel::displayName, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private boolean matchesQuery(String query, MobCatalogEntry entry, String displayName) {
        if (query.isBlank()) {
            return true;
        }

        String normalizedName = displayName == null ? "" : displayName.toLowerCase(Locale.ROOT);
        String normalizedRole = entry.roleName() == null ? "" : entry.roleName().toLowerCase(Locale.ROOT);
        return normalizedName.contains(query) || normalizedRole.contains(query);
    }

    private String normalizedSearch() {
        return searchInput == null ? "" : searchInput.trim().toLowerCase(Locale.ROOT);
    }

    private void requestRowsReload(boolean refreshShell) {
        long token = ++rowsRefreshToken;
        ++pageIconsToken;
        rowsLoading = true;
        if (refreshShell) {
            sendResultsUpdate();
        }

        try {
            plugin.runUiTask(() -> loadRowsAsync(token));
        } catch (RejectedExecutionException ignored) {
        }
    }

    private void loadRowsAsync(long token) {
        List<RowModel> textRows = buildRows();
        applyRows(token, textRows);
    }

    private void applyRows(long token, List<RowModel> rows) {
        HytaleServer.SCHEDULED_EXECUTOR.execute(() -> {
            if (dismissed || token != rowsRefreshToken) {
                return;
            }

            cachedRows = List.copyOf(rows);
            currentPage = clampPage(currentPage, rows.size());
            rowsLoading = false;
            sendResultsUpdate(true);
            requestVisiblePageIcons();
        });
    }

    private void requestVisiblePageIcons() {
        List<RowModel> rows = cachedRows;
        PageSlice page = pageSlice(rows, currentPage);
        if (rows.isEmpty() || page.rows().isEmpty() || !pageHasMissingIcons(page.rows())) {
            return;
        }

        long rowsToken = rowsRefreshToken;
        long iconsToken = ++pageIconsToken;
        int pageIndex = page.pageIndex();
        List<RowModel> pageRows = List.copyOf(page.rows());
        try {
            plugin.runUiTask(() -> loadVisiblePageIcons(rowsToken, iconsToken, pageIndex, pageRows));
        } catch (RejectedExecutionException ignored) {
        }
    }

    private void loadVisiblePageIcons(long rowsToken, long iconsToken, int pageIndex, List<RowModel> pageRows) {
        if (dismissed || rowsToken != rowsRefreshToken || iconsToken != pageIconsToken) {
            return;
        }

        MobMapAssetPack.advanceViewerDeliveryPhase(playerRef);

        Map<String, String> iconByMobKey = new LinkedHashMap<>();
        List<String> imagePathsToDeliver = new ArrayList<>();
        UUID viewerUuid = playerRef == null ? null : playerRef.getUuid();
        boolean missingIconsRemain = false;
        MobMapMarkersConfig config = MobMapMarkersPlugin.getConfig();
        for (RowModel row : pageRows) {
            if (row.assetPath() != null) {
                continue;
            }

            String imagePath = MobMapAssetPack.ensureMobIcon(
                    row.entry().roleName(),
                    row.displayName(),
                    MobNameLocalization.buildAssetLocaleKey(row.entry().nameTranslationKey(), playerRef.getLanguage(), row.displayName()),
                    config != null ? config.mobMarkerSize : 44,
                    config != null ? config.mobIconContentScalePercent : 96,
                    row.entry().facingRight(),
                    config == null || config.renderUnknownMobFallbacks);
            if (imagePath == null) {
                iconByMobKey.put(row.entry().mobKey(), "");
                continue;
            }

            if (MobMapAssetPack.hasDeliveredAsset(viewerUuid, imagePath)) {
                iconByMobKey.put(row.entry().mobKey(), MobMapAssetPack.toUiAssetPath(imagePath));
            } else {
                missingIconsRemain = true;
                iconByMobKey.put(row.entry().mobKey(), null);
                imagePathsToDeliver.add(imagePath);
            }
        }

        if (!imagePathsToDeliver.isEmpty()) {
            MobMapAssetPack.deliverAssetsToViewer(playerRef, imagePathsToDeliver);
        }

        boolean requestAnotherPass = missingIconsRemain && MobMapAssetPack.hasPendingAssets(viewerUuid);

        HytaleServer.SCHEDULED_EXECUTOR.execute(() -> {
            if (dismissed || rowsToken != rowsRefreshToken || iconsToken != pageIconsToken || currentPage != pageIndex) {
                return;
            }

            cachedRows = updateCachedIcons(cachedRows, iconByMobKey);
            sendResultsUpdate(true);

            if (requestAnotherPass && currentPage == pageIndex) {
                requestVisiblePageIcons();
            }
        });
    }

    private void sendResultsUpdate() {
        sendResultsUpdate(false);
    }

    private void sendResultsUpdate(boolean fullRefresh) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();
        if (fullRefresh) {
            render(cmd, evt);
        } else {
            renderResults(cmd, evt);
        }
        sendUpdate(cmd, evt, fullRefresh);
    }

    private List<RowModel> updateCachedRule(List<RowModel> rows, String mobKey, MobMarkerSurface surface, boolean enabled) {
        if (rows.isEmpty()) {
            return rows;
        }

        List<RowModel> updated = new ArrayList<>(rows.size());
        for (RowModel row : rows) {
            if (row.entry().mobKey().equals(mobKey)) {
                updated.add(new RowModel(row.entry(), row.displayName(), row.rule().with(surface, enabled), row.assetPath()));
            } else {
                updated.add(row);
            }
        }
        return List.copyOf(updated);
    }

    private List<RowModel> updateCachedIcons(List<RowModel> rows, Map<String, String> iconByMobKey) {
        if (rows.isEmpty() || iconByMobKey == null || iconByMobKey.isEmpty()) {
            return rows;
        }

        List<RowModel> updated = new ArrayList<>(rows.size());
        for (RowModel row : rows) {
            String assetPath = iconByMobKey.get(row.entry().mobKey());
            if (assetPath != null || iconByMobKey.containsKey(row.entry().mobKey())) {
                updated.add(new RowModel(row.entry(), row.displayName(), row.rule(), assetPath));
            } else {
                updated.add(row);
            }
        }
        return List.copyOf(updated);
    }

    private boolean pageHasMissingIcons(List<RowModel> rows) {
        for (RowModel row : rows) {
            if (row.assetPath() == null) {
                return true;
            }
        }
        return false;
    }

    private PageSlice pageSlice(List<RowModel> rows, int requestedPage) {
        int total = rows.size();
        int pageCount = total == 0 ? 1 : (int) Math.ceil(total / (double) PAGE_SIZE);
        int pageIndex = total == 0 ? 0 : Math.max(0, Math.min(requestedPage, pageCount - 1));
        if (total == 0) {
            return new PageSlice(List.of(), pageIndex, pageCount, 0, 0);
        }

        int fromIndex = pageIndex * PAGE_SIZE;
        int toIndex = Math.min(fromIndex + PAGE_SIZE, total);
        return new PageSlice(
                rows.subList(fromIndex, toIndex),
                pageIndex,
                pageCount,
                fromIndex + 1,
                toIndex);
    }

    private int clampPage(int requestedPage, int rowCount) {
        int pageCount = rowCount == 0 ? 1 : (int) Math.ceil(rowCount / (double) PAGE_SIZE);
        return Math.max(0, Math.min(requestedPage, pageCount - 1));
    }

    private List<RowModel> updateCachedRules(List<RowModel> rows, Collection<String> mobKeys,
                                             MobMarkerSurface surface, boolean enabled) {
        if (rows.isEmpty() || mobKeys == null || mobKeys.isEmpty()) {
            return rows;
        }

        Set<String> keySet = Set.copyOf(mobKeys);
        List<RowModel> updated = new ArrayList<>(rows.size());
        for (RowModel row : rows) {
            if (keySet.contains(row.entry().mobKey())) {
                updated.add(new RowModel(row.entry(), row.displayName(), row.rule().with(surface, enabled), row.assetPath()));
            } else {
                updated.add(row);
            }
        }
        return List.copyOf(updated);
    }

    private String toggleText(MobMarkerSurface surface, boolean enabled) {
        String state = enabled ? k("toggle.on") : k("toggle.off");
        return switch (surface) {
            case MAP -> k("label.map") + ": " + state;
            case MINIMAP -> k("label.minimap") + ": " + state;
            case COMPASS -> k("label.compass") + ": " + state;
        };
    }

    private void applyBulkButton(UICommandBuilder cmd, String selector, List<RowModel> rows,
                                 MobMarkerSurface surface, boolean visible) {
        cmd.set(selector + ".Visible", visible);
        if (!visible) {
            return;
        }

        SurfaceBulkState state = bulkState(rows, surface);
        cmd.set(selector + ".Background", switch (state) {
            case ALL_ENABLED -> BULK_GREEN;
            case ALL_DISABLED -> BULK_RED;
            case MIXED, EMPTY -> BULK_MIXED;
        });
    }

    private SurfaceBulkState bulkState(List<RowModel> rows, MobMarkerSurface surface) {
        if (rows.isEmpty()) {
            return SurfaceBulkState.EMPTY;
        }

        int enabledCount = 0;
        for (RowModel row : rows) {
            if (row.rule().isEnabled(surface)) {
                enabledCount++;
            }
        }

        if (enabledCount == 0) {
            return SurfaceBulkState.ALL_DISABLED;
        }
        if (enabledCount == rows.size()) {
            return SurfaceBulkState.ALL_ENABLED;
        }
        return SurfaceBulkState.MIXED;
    }

    private String rowButtonBackground(MobMarkerSurface surface, boolean enabled) {
        if (!MobMapPermissions.canEditSurface(playerRef, surface)) {
            return BULK_LOCKED;
        }
        return enabled ? BULK_GREEN : BULK_RED;
    }

    private void bindClick(UIEventBuilder evt, String selector, String action) {
        evt.addEventBinding(CustomUIEventBindingType.Activating, selector, EventData.of("Action", action), false);
    }

    private void bindValue(UIEventBuilder evt, String selector, String key) {
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, selector, EventData.of(key, selector + ".Value"), false);
    }

    private String k(String key) {
        return MobMapUiText.key(playerRef, key);
    }

    private String kf(String key, Object... args) {
        return MobMapUiText.keyf(playerRef, key, args);
    }
}