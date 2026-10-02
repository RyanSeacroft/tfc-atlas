package dev.ryan.tfcatlas.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.level.Level;

/** Xaero's public API plus a small isolated adapter for camera internals. */
public final class XaeroBridge {
    private static final Map<String, Field> FIELDS = new ConcurrentHashMap<>();
    private static final Map<String, Method> METHODS = new ConcurrentHashMap<>();

    public static boolean isMap(Screen s) {
        return s != null && s.getClass().getName().equals("xaero.map.gui.GuiMap");
    }

    public static Object field(Object o, String name) throws ReflectiveOperationException {
        Class<?> cls = o instanceof Class<?> c ? c : o.getClass();
        String key = cls.getName() + "#" + name;
        Field f = FIELDS.get(key);
        if (f == null) {
            Class<?> cursor = cls;
            while (cursor != null) {
                try {
                    f = cursor.getDeclaredField(name);
                    break;
                } catch (NoSuchFieldException e) {
                    cursor = cursor.getSuperclass();
                }
            }
            if (f == null) {
                throw new NoSuchFieldException(key);
            }
            f.setAccessible(true);
            FIELDS.put(key, f);
        }
        return f.get(o instanceof Class ? null : o);
    }

    public static void set(Object o, String name, Object value)
            throws ReflectiveOperationException {
        field(o, name);
        FIELDS.get(o.getClass().getName() + "#" + name).set(o, value);
    }

    public static Object call(Object o, String name, Object... args)
            throws ReflectiveOperationException {
        String key = o.getClass().getName() + "#" + name + "/" + args.length;
        Method m = METHODS.get(key);
        if (m == null) {
            for (Method candidate : o.getClass().getMethods()) {
                if (candidate.getName().equals(name)
                        && candidate.getParameterCount() == args.length) {
                    m = candidate;
                    break;
                }
            }
            if (m == null) {
                throw new NoSuchMethodException(key);
            }
            METHODS.put(key, m);
        }
        return m.invoke(o, args);
    }

    public record View(
            double x,
            double z,
            double scale,
            String world,
            boolean overworld,
            boolean surface,
            Object processor,
            double centerX,
            double centerZ) {}

    public static String sessionStatus = "Waiting for Xaero's connection";

    /** Available after Xaero's connection setup, before any map screen exists. */
    public static String liveWorld() throws ReflectiveOperationException {
        Object session =
                Class.forName("xaero.map.WorldMapSession")
                        .getMethod("getCurrentSession")
                        .invoke(null);
        if (session == null || !Boolean.TRUE.equals(call(session, "isUsable"))) {
            sessionStatus = "Waiting for Xaero's connection";
            return null;
        }
        Object processor = call(session, "getMapProcessor");
        if (processor == null || !Boolean.TRUE.equals(call(processor, "isMapWorldUsable"))) {
            sessionStatus = "Waiting for Xaero's world identity";
            return null;
        }
        Object world = call(processor, "getMapWorld"),
                dimension = call(world, "getDimension", Level.OVERWORLD);
        if (dimension == null) {
            sessionStatus = "Waiting for Xaero's Overworld";
            return null;
        }
        Object main = call(world, "getMainId"), multi = call(dimension, "getCurrentMultiworld");
        sessionStatus =
                main == null || multi == null
                        ? "Waiting for Xaero's world identity"
                        : "Waiting for TFC prediction settings";
        return main == null || multi == null ? null : main + "/" + multi;
    }

    public static View view(Screen map) throws ReflectiveOperationException {
        Object processor = field(map, "mapProcessor"),
                world = call(processor, "getMapWorld"),
                dimension = call(world, "getCurrentDimension");
        Object key = call(dimension, "getDimId");
        String identity = call(world, "getMainId") + "/" + call(dimension, "getCurrentMultiworld");
        boolean overworld = key.equals(Level.OVERWORLD);
        return new View(
                (double) field(map, "cameraX"),
                (double) field(map, "cameraZ"),
                (double) field(map, "scale") / Minecraft.getInstance().getWindow().getGuiScale(),
                identity,
                overworld,
                ((Number) call(processor, "getCurrentCaveLayer")).intValue() == Integer.MAX_VALUE,
                processor,
                (Minecraft.getInstance().getWindow().getWidth() / 2)
                        / Minecraft.getInstance().getWindow().getGuiScale(),
                (Minecraft.getInstance().getWindow().getHeight() / 2)
                        / Minecraft.getInstance().getWindow().getGuiScale());
    }

    public static void center(Screen map, int x, int z) {
        try {
            set(map, "cameraX", (double) x);
            set(map, "cameraZ", (double) z);
            set(map, "cameraDestinationAnimX", null);
            set(map, "cameraDestinationAnimZ", null);
            set(map, "cameraDestination", null);
            set(map, "shouldResetCameraPos", false);
            try {
                Field f = map.getClass().getDeclaredField("attachedCamera");
                f.setAccessible(true);
                f.setBoolean(null, false);
            } catch (NoSuchFieldException ignored) {
            }
        } catch (Exception e) {
            AtlasClient.message("Could not centre Xaero map: " + e.getMessage());
        }
    }

    public static void waypoint(Screen map, int x, int z, String name) {
        try {
            Object support = field(Class.forName("xaero.map.mods.SupportMods"), "xaeroMinimap");
            if (support == null) {
                throw new IllegalStateException("Xaero's Minimap is required for waypoints");
            }
            call(support, "createWaypoint", map, x, Short.MAX_VALUE, z, 1.0, false);
            Screen current = Minecraft.getInstance().screen;
            if (current == map) {
                throw new IllegalStateException(
                        "Waypoint world is still loading; return to the map and try again");
            }
            // Xaero keeps its own confirmation UI; only prefill the name when the field is present.
            try {
                Object box = field(current, "nameTextField");
                if (box instanceof EditBox edit) {
                    edit.setValue(name);
                }
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            AtlasClient.message("Waypoint: " + e.getMessage());
        }
    }
}
