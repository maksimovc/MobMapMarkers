package dev.thenexusgates.mobmapmarkers.compat;

import com.hypixel.hytale.server.core.entity.entities.Player;

import java.lang.reflect.Method;

final class BetterMapBridge {

    private static volatile BridgeState bridgeState;

    private BetterMapBridge() {
    }

    static boolean isAvailable() {
        return resolveBridgeState().available;
    }

    static ViewerSettings resolveViewerSettings(Player viewer) {
        BridgeState state = resolveBridgeState();
        if (!state.available || viewer == null) {
            return ViewerSettings.disabled();
        }

        try {
            Object modConfig = state.modConfigGetInstance.invoke(null);
            if (modConfig == null) {
                return ViewerSettings.disabled();
            }

            boolean radarEnabled = (boolean) state.modConfigIsRadarEnabled.invoke(modConfig);
            if (!radarEnabled) {
                return ViewerSettings.disabled();
            }

            int radarRange = (int) state.modConfigGetRadarRange.invoke(modConfig);
            return new ViewerSettings(true, radarRange);
        } catch (Exception e) {
            return ViewerSettings.disabled();
        }
    }

    private static BridgeState resolveBridgeState() {
        BridgeState current = bridgeState;
        if (current != null) {
            return current;
        }

        synchronized (BetterMapBridge.class) {
            current = bridgeState;
            if (current != null) {
                return current;
            }

            bridgeState = current = loadBridgeState();
            return current;
        }
    }

    private static BridgeState loadBridgeState() {
        try {
            ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
            Class<?> modConfigClass = loadClass("dev.ninesliced.configs.ModConfig", contextLoader);

            return new BridgeState(
                    true,
                    modConfigClass.getMethod("getInstance"),
                    modConfigClass.getMethod("isRadarEnabled"),
                    modConfigClass.getMethod("getRadarRange"));
        } catch (Exception e) {
            return BridgeState.unavailable();
        }
    }

    private static Class<?> loadClass(String className, ClassLoader contextLoader) throws ClassNotFoundException {
        if (contextLoader != null) {
            try {
                return Class.forName(className, false, contextLoader);
            } catch (ClassNotFoundException ignored) {
            }
        }

        try {
            return Class.forName(className, false, BetterMapBridge.class.getClassLoader());
        } catch (ClassNotFoundException ignored) {
        }

        return Class.forName(className);
    }

    record ViewerSettings(boolean enabled, int radarRange) {
        static ViewerSettings disabled() {
            return new ViewerSettings(false, 0);
        }
    }

    private record BridgeState(
            boolean available,
            Method modConfigGetInstance,
            Method modConfigIsRadarEnabled,
            Method modConfigGetRadarRange) {

        static BridgeState unavailable() {
            return new BridgeState(false, null, null, null);
        }
    }
}