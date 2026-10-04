package dev.ryan.tfcatlas.client;

import static dev.ryan.tfcatlas.client.XaeroBridge.call;
import static dev.ryan.tfcatlas.client.XaeroBridge.field;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Temporary native view choices. Never writes, deletes or regenerates Xaero map files. */
final class XaeroViews {
    private static Object dimension;
    private static int originalType, originalStart;
    private static String selected = "TFC layers";

    static String selected() {
        return selected;
    }

    static boolean layers() {
        return selected.equals("TFC layers");
    }

    static void cycle(Screen map) {
        try {
            if (selected.equals("Xaero caves")) {
                restore();
                return;
            }
            Object processor = field(map, "mapProcessor");
            Object currentDimension = call(call(processor, "getMapWorld"), "getCurrentDimension");
            if (dimension != null && dimension != currentDimension) {
                restore();
            }
            boolean caves = selected.equals("Xaero surface");
            if (caves && !cavesAllowed()) {
                restore();
                AtlasClient.message("Xaero cave maps are disabled here. Returned to TFC layers.");
                return;
            }
            if (dimension == null) {
                int start = caveStart();
                originalType = ((Number) call(currentDimension, "getCaveModeType")).intValue();
                originalStart = start;
                dimension = currentDimension;
            }
            setType(dimension, caves ? Math.max(1, originalType) : 0);
            if (caves) {
                var player = Minecraft.getInstance().player;
                setCaveStart(
                        originalStart == Integer.MAX_VALUE
                                ? player == null ? 64 : player.getBlockY()
                                : originalStart);
            }
            call(processor, "updateCaveStart");
            selected = caves ? "Xaero caves" : "Xaero surface";
        } catch (ReflectiveOperationException | RuntimeException ex) {
            restore();
            AtlasClient.message("Could not switch Xaero view; use Xaero’s own cave button.");
            com.mojang.logging.LogUtils.getLogger().warn("Atlas native view switch failed", ex);
        }
    }

    static void restore() {
        selected = "TFC layers";
        if (dimension == null) {
            return;
        }
        try {
            setType(dimension, originalType);
            setCaveStart(originalStart);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            com.mojang.logging.LogUtils.getLogger().warn("Atlas could not restore Xaero view", ex);
        } finally {
            dimension = null;
        }
    }

    private static void setType(Object target, int type) throws ReflectiveOperationException {
        for (int i = 0; i < 3; i++) {
            if (((Number) call(target, "getCaveModeType")).intValue() == type) {
                return;
            }
            call(target, "toggleCaveModeType", true);
        }
        throw new IllegalStateException("Unknown Xaero cave mode");
    }

    private static Object settings() throws ReflectiveOperationException {
        return field(Class.forName("xaero.map.WorldMap"), "settings");
    }

    private static boolean cavesAllowed() throws ReflectiveOperationException {
        return Boolean.TRUE.equals(call(settings(), "isCaveMapsAllowed"));
    }

    private static int caveStart() throws ReflectiveOperationException {
        return ((Number) field(settings(), "caveModeStart")).intValue();
    }

    private static void setCaveStart(int y) throws ReflectiveOperationException {
        XaeroBridge.set(settings(), "caveModeStart", y);
    }
}
