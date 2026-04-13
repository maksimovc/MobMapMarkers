package dev.thenexusgates.mobmapmarkers;

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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class MobMapFiltersPage extends InteractiveCustomUIPage<MobMapFiltersPage.PageData> {

    private enum SurfaceBulkState {
        ALL_ENABLED,
        ALL_DISABLED,
        MIXED,
        EMPTY
    }

    private static final long SEARCH_REFRESH_DELAY_MS = 450L;
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

    private static final String UI_PAGE = "Pages/MobMapFilters.ui";
    private static final String UI_ROW = "Pages/MobMapFilters_row.ui";
    private static final String GROUP_ROOT = "#RowGroup";

    private final MobMapMarkersPlugin plugin;

    private volatile List<RowModel> cachedRows = List.of();
    private volatile boolean rowsLoading = true;
    private volatile long rowsRefreshToken;
    private String searchInput = "";
    private volatile ScheduledFuture<?> pendingSearchRefresh;
    private volatile long searchRefreshToken;
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
        cancelPendingSearchRefresh();
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
            scheduleSearchRefresh();
        }

        if (data.action == null || data.action.isBlank()) {
            return;
        }

        cancelPendingSearchRefresh();

        String action = data.action;
        if ("Close".equals(action)) {
            plugin.getUiSounds().play(playerRef, MobMapUiSounds.Cue.NAVIGATE);
            close();
            return;
        }
        if ("ClearSearch".equals(action)) {
            searchInput = "";
            rowsLoading = true;
            plugin.getUiSounds().play(playerRef, MobMapUiSounds.Cue.NAVIGATE);
            refresh();
            requestRowsReload(false);
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
        rowsLoading = true;
        sendResultsUpdate();
        requestRowsReload(false);
    }

    private void scheduleSearchRefresh() {
        long token = ++searchRefreshToken;
        ScheduledFuture<?> existing = pendingSearchRefresh;
        if (existing != null && !existing.isCancelled()) {
            existing.cancel(false);
        }
        pendingSearchRefresh = HytaleServer.SCHEDULED_EXECUTOR.schedule(
                () -> refreshSearch(token),
                SEARCH_REFRESH_DELAY_MS,
                TimeUnit.MILLISECONDS);
    }

    private void refreshSearch(long token) {
        if (dismissed || token != searchRefreshToken) {
            return;
        }
        refreshSearchResults();
    }

    private void cancelPendingSearchRefresh() {
        ScheduledFuture<?> existing = pendingSearchRefresh;
        pendingSearchRefresh = null;
        if (existing != null && !existing.isCancelled()) {
            existing.cancel(false);
        }
    }

    private void render(UICommandBuilder cmd, UIEventBuilder evt) {
        cmd.append(UI_PAGE);
        bindClick(evt, "#CloseButton", "Close");
        bindClick(evt, "#ClearSearchButton", "ClearSearch");
        bindClick(evt, "#MapBulkButton", "Bulk|MAP");
        bindClick(evt, "#MinimapBulkButton", "Bulk|MINIMAP");
        bindClick(evt, "#CompassBulkButton", "Bulk|COMPASS");
        bindValue(evt, "#SearchInput", "@SearchInput");

        cmd.set("#Title.Text", t("Mob Marker Filters", "Фільтри маркерів мобів"));
        cmd.set("#MapStatusLabel.Text", t("Map", "Мапа"));
        cmd.set("#MinimapStatusLabel.Text", t("Minimap", "Мінімапа"));
        cmd.set("#CompassStatusLabel.Text", t("Compass", "Компас"));
        cmd.set("#SearchInput.Value", searchInput == null ? "" : searchInput);
        cmd.set("#SearchInput.PlaceholderText", t("Type a mob name or role key", "Введіть назву моба або role key"));
        cmd.set("#ColumnName.Text", t("Mob", "Моб"));
        cmd.set("#ColumnMap.Text", t("Map", "Мапа"));
        cmd.set("#ColumnMinimap.Text", t("Minimap", "Мінімапа"));
        cmd.set("#ColumnCompass.Text", t("Compass", "Компас"));
        cmd.set("#CloseButtonLabel.Text", t("X", "X"));
        cmd.set("#ClearSearchButtonLabel.Text", t("Clear", "Очистити"));
        cmd.set("#MapBulkButtonLabel.Text", t("Map", "Мапа"));
        cmd.set("#MinimapBulkButtonLabel.Text", t("Minimap", "Мінімапа"));
        cmd.set("#CompassBulkButtonLabel.Text", t("Compass", "Компас"));

        renderResults(cmd, evt);
    }

    private void renderResults(UICommandBuilder cmd, UIEventBuilder evt) {
        cmd.clear(GROUP_ROOT);

        List<RowModel> rows = cachedRows;

        int enabledMap = 0, enabledMinimap = 0, enabledCompass = 0;
        for (RowModel row : rows) {
            if (row.rule().map) enabledMap++;
            if (row.rule().minimap) enabledMinimap++;
            if (row.rule().compass) enabledCompass++;
        }
        int total = rows.size();
        cmd.set("#MapStatusCount.Text", f("%d/%d on", "%d/%d увімк.", enabledMap, total));
        cmd.set("#MinimapStatusCount.Text", f("%d/%d on", "%d/%d увімк.", enabledMinimap, total));
        cmd.set("#CompassStatusCount.Text", f("%d/%d on", "%d/%d увімк.", enabledCompass, total));
        cmd.set("#MapStatusDot.Background", enabledMap == 0 ? "#5a3f46" : enabledMap == total ? "#6f86a8" : "#b88a3e");
        cmd.set("#MinimapStatusDot.Background", enabledMinimap == 0 ? "#5a3f46" : enabledMinimap == total ? "#6f86a8" : "#b88a3e");
        cmd.set("#CompassStatusDot.Background", enabledCompass == 0 ? "#5a3f46" : enabledCompass == total ? "#6f86a8" : "#b88a3e");
        cmd.set("#MapStatusBox.Visible", MobMapPermissions.canEditSurface(playerRef, MobMarkerSurface.MAP));
        cmd.set("#MinimapStatusBox.Visible", MobMapPermissions.canEditSurface(playerRef, MobMarkerSurface.MINIMAP));
        cmd.set("#CompassStatusBox.Visible", MobMapPermissions.canEditSurface(playerRef, MobMarkerSurface.COMPASS));

        cmd.set("#ResultsSummary.Text", f(
                "Showing %d mob types",
                "Показано %d типів мобів",
                rows.size()));
        cmd.set("#ResultsSummary.Visible", !rows.isEmpty());
        applyBulkButton(cmd, "#MapBulkButton", rows, MobMarkerSurface.MAP, MobMapPermissions.canBulkSurface(playerRef, MobMarkerSurface.MAP));
        applyBulkButton(cmd, "#MinimapBulkButton", rows, MobMarkerSurface.MINIMAP, MobMapPermissions.canBulkSurface(playerRef, MobMarkerSurface.MINIMAP));
        applyBulkButton(cmd, "#CompassBulkButton", rows, MobMarkerSurface.COMPASS, MobMapPermissions.canBulkSurface(playerRef, MobMarkerSurface.COMPASS));
        cmd.set("#ColumnMap.Visible", MobMapPermissions.canEditSurface(playerRef, MobMarkerSurface.MAP));
        cmd.set("#ColumnMinimap.Visible", MobMapPermissions.canEditSurface(playerRef, MobMarkerSurface.MINIMAP));
        cmd.set("#ColumnCompass.Visible", MobMapPermissions.canEditSurface(playerRef, MobMarkerSurface.COMPASS));

        int index = 0;
        for (RowModel row : rows) {
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
            if (row.assetPath() != null) {
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
        cmd.set("#EmptyState.Text", t(
                "No mob types match the current search yet.",
                "Поки що жоден тип мобів не відповідає поточному пошуку."));
        if (rowsLoading && rows.isEmpty()) {
            cmd.set("#EmptyState.Visible", false);
        }
    }

    private List<RowModel> buildRows(boolean includeIcons) {
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
        List<String> imagePathsToDeliver = new ArrayList<>();
        MobMapMarkersConfig config = MobMapMarkersPlugin.getConfig();
        for (MobCatalogEntry entry : entries.values()) {
            String displayName = MobNameLocalization.resolveDisplayName(
                    entry.nameTranslationKey(),
                    entry.fallbackDisplayName(),
                    playerRef.getLanguage());
            if (!matchesQuery(query, entry, displayName)) {
                continue;
            }

            MobMarkerFilterRule rule = plugin.getVisibilityService().resolveRule(playerRef.getUuid(), entry.mobKey());
            String assetPath = null;
            if (includeIcons) {
                String imagePath = MobMapAssetPack.ensureMobIcon(
                        entry.roleName(),
                        displayName,
                        MobNameLocalization.buildAssetLocaleKey(entry.nameTranslationKey(), playerRef.getLanguage(), displayName),
                        config != null ? config.mobMarkerSize : 44,
                        config != null ? config.mobIconContentScalePercent : 96,
                        entry.facingRight(),
                        config == null || config.renderUnknownMobFallbacks);
                assetPath = MobMapAssetPack.toUiAssetPath(imagePath);
                if (imagePath != null) {
                    imagePathsToDeliver.add(imagePath);
                }
            }

            rows.add(new RowModel(entry, displayName, rule, assetPath));
        }

        rows.sort(Comparator.comparing(RowModel::displayName, String.CASE_INSENSITIVE_ORDER));
        if (!imagePathsToDeliver.isEmpty()) {
            MobMapAssetPack.deliverAssetsToViewer(playerRef, imagePathsToDeliver);
        }
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
        List<RowModel> textRows = buildRows(false);
        applyRows(token, textRows);

        if (dismissed || token != rowsRefreshToken) {
            return;
        }

        List<RowModel> iconRows = buildRows(true);
        applyRows(token, iconRows);
    }

    private void applyRows(long token, List<RowModel> rows) {
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            if (dismissed || token != rowsRefreshToken) {
                return;
            }

            cachedRows = List.copyOf(rows);
            rowsLoading = false;
            sendResultsUpdate();
        }, 0L, TimeUnit.MILLISECONDS);
    }

    private void sendResultsUpdate() {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();
        renderResults(cmd, evt);
        sendUpdate(cmd, evt, false);
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
        String state = enabled ? t("On", "Увімк.") : t("Off", "Вимк.");
        return switch (surface) {
            case MAP -> t("Map", "Мапа") + ": " + state;
            case MINIMAP -> t("Minimap", "Мінімапа") + ": " + state;
            case COMPASS -> t("Compass", "Компас") + ": " + state;
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

    private String t(String english, String ukrainian) {
        return MobMapUiText.choose(playerRef, english, ukrainian);
    }

    private String f(String english, String ukrainian, Object... args) {
        return MobMapUiText.format(playerRef, english, ukrainian, args);
    }
}