package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.Cell;
import dev.ryan.tfcatlas.core.HudLayout;
import dev.ryan.tfcatlas.core.Layer;
import dev.ryan.tfcatlas.core.RockLayer;
import dev.ryan.tfcatlas.core.SearchDetails;
import dev.ryan.tfcatlas.core.ToolbarLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;

public final class AtlasClient {
    public static Profile profile = new Profile();
    public static RegionEngine engine;
    public static Screen map;
    public static XaeroBridge.View view;
    public static String world = "", error = "";
    public static int hoverX, hoverZ;
    public static final MapRenderer renderer = new MapRenderer();
    private static long lastHook = 0,
            lastError = 0,
            lastSeedAttempt = 0,
            lastSessionAttempt = 0,
            lastEngineAttempt = 0,
            lastStartupError = 0;
    private static String cacheStartup = "Waiting for an Overworld session";
    private static boolean held = false, criteriaClipped = false;
    private static final HudEditor hud = new HudEditor();
    private static final List<CompactButton> toolbar = new ArrayList<>();
    private static HudResizeHandle toolbarGrip;
    private static final KeyMapping OPEN =
            new KeyMapping("key.tfcatlas.settings", GLFW.GLFW_KEY_G, "key.categories.tfcatlas");
    private static final KeyMapping TOGGLE =
            new KeyMapping("key.tfcatlas.toggle", GLFW.GLFW_KEY_UNKNOWN, "key.categories.tfcatlas");
    private static final KeyMapping HOLD =
            new KeyMapping("key.tfcatlas.hold", GLFW.GLFW_KEY_UNKNOWN, "key.categories.tfcatlas");

    public static void init() {
        FMLJavaModLoadingContext.get()
                .getModEventBus()
                .addListener(
                        (RegisterKeyMappingsEvent e) -> {
                            e.register(OPEN);
                            e.register(TOGGLE);
                            e.register(HOLD);
                        });
        FMLJavaModLoadingContext.get().getModEventBus().addListener(SearchOutlines::register);
        MinecraftForge.EVENT_BUS.addListener(AtlasClient::screenInit);
        MinecraftForge.EVENT_BUS.addListener(
                (ClientPlayerNetworkEvent.LoggingOut e) -> {
                    close();
                    world = "";
                    map = null;
                    view = null;
                    profile = new Profile();
                });
        MinecraftForge.EVENT_BUS.addListener(AtlasClient::beforeUiRender);
        MinecraftForge.EVENT_BUS.addListener(AtlasClient::renderUi);
        MinecraftForge.EVENT_BUS.addListener(AtlasClient::key);
        MinecraftForge.EVENT_BUS.addListener(AtlasClient::keyUp);
        MinecraftForge.EVENT_BUS.addListener(AtlasClient::tick);
        MinecraftForge.EVENT_BUS.addListener(AtlasClient::mousePressed);
        MinecraftForge.EVENT_BUS.addListener(AtlasClient::mouseDragged);
        MinecraftForge.EVENT_BUS.addListener(AtlasClient::mouseReleased);
    }

    private static void ensure(Screen s) throws Exception {
        view = XaeroBridge.view(s);
        map = s;
        if (view.overworld()) {
            ensureWorld(view.world());
        }
    }

    private static void ensureWorld(String identity) {
        if (!identity.equals(world)) {
            close();
            world = identity;
            profile = Profiles.load(world);
            var mc = Minecraft.getInstance();
            var server = mc.getSingleplayerServer();
            if (profile.seed.isBlank()
                    && server != null
                    && server.getLevel(net.minecraft.world.level.Level.OVERWORLD) != null) {
                profile.seed =
                        Long.toString(
                                server.getLevel(net.minecraft.world.level.Level.OVERWORLD)
                                        .getSeed());
            }
            lastSeedAttempt = 0;
            lastEngineAttempt = 0;
        }
        if (profile.seed.isBlank() && System.currentTimeMillis() - lastSeedAttempt > 1000) {
            lastSeedAttempt = System.currentTimeMillis();
            if (ClientSeed.detect(profile)) {
                save();
            }
        }
        if (engine == null
                && !profile.seed.isBlank()
                && System.currentTimeMillis() - lastEngineAttempt
                        > (error.isEmpty() ? 1000 : 10000)) {
            lastEngineAttempt = System.currentTimeMillis();
            rebuild(false);
        }
    }

    public static void rebuild() {
        rebuild(true);
    }

    private static void rebuild(boolean announce) {
        if (engine != null) {
            engine.close();
        }
        engine = null;
        renderer.clear();
        error = "";
        if (profile.seed.isBlank()) {
            return;
        }
        try {
            engine = new RegionEngine(profile, world);
            cacheStartup = "Ready";
            com.mojang.logging.LogUtils.getLogger()
                    .info(
                            "Atlas prediction engine started with the map {}",
                            XaeroBridge.isMap(Minecraft.getInstance().screen) ? "open" : "closed");
        } catch (Exception ex) {
            String text = "Cannot load prediction settings: " + ex.getClass().getSimpleName();
            if (announce) {
                message(text);
            } else {
                error = text;
            }
        }
    }

    public static void save() {
        Profiles.save(world, profile);
    }

    private static void close() {
        hud.reset();
        if (engine != null) {
            engine.close();
        }
        engine = null;
        renderer.clear();
        held = false;
        error = "";
        cacheStartup = "Waiting for an Overworld session";
    }

    public static List<String> cacheStatus() {
        if (engine != null) {
            return engine.cacheStatus();
        }
        return List.of(
                "Background cache: " + (error.isEmpty() ? cacheStartup : error),
                profile.seed.isBlank() ? ClientSeed.status : "Waiting for prediction engine",
                "Starts during play; opening the map is not required.");
    }

    private static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        var mc = Minecraft.getInstance();
        if (mc.level == null && !world.isEmpty()) {
            close();
            world = "";
            map = null;
            view = null;
            return;
        }
        boolean mapOpen = XaeroBridge.isMap(mc.screen);
        if (mc.player != null
                && mc.level != null
                && mc.level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)
                && !mapOpen
                && !(mc.screen instanceof AtlasMenuScreen)
                && System.currentTimeMillis() - lastSessionAttempt > 1000) {
            lastSessionAttempt = System.currentTimeMillis();
            try {
                String identity = XaeroBridge.liveWorld();
                cacheStartup = XaeroBridge.sessionStatus;
                if (identity != null) {
                    ensureWorld(identity);
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
                cacheStartup = "Xaero startup unavailable · see latest.log";
                if (System.currentTimeMillis() - lastStartupError > 30000) {
                    lastStartupError = System.currentTimeMillis();
                    com.mojang.logging.LogUtils.getLogger()
                            .warn(
                                    "Atlas could not start background caching from the live Xaero session",
                                    ex);
                }
            }
        }
        if (engine != null && mc.player != null && mc.level != null) {
            if (!mapOpen) {
                engine.mapClosed();
            }
            engine.background(
                    mc.player.getBlockX(),
                    mc.player.getBlockZ(),
                    mc.level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD));
        }
    }

    private static void screenInit(ScreenEvent.Init.Post e) {
        if (!XaeroBridge.isMap(e.getScreen())) {
            return;
        }
        try {
            ensure(e.getScreen());
        } catch (Exception ex) {
            message("Xaero integration: " + ex.getMessage());
            return;
        }
        if (profile.mode.equals("Unexplored only")) {
            try {
                ExploredMask.prepare();
            } catch (RuntimeException ex) {
                message("Cannot prepare terrain coverage: " + ex.getMessage());
            }
        }
        hud.reset();
        toolbar.clear();
        String[] names = {
            "Settings",
            "Search",
            "Search radius",
            "Layer",
            "Rock: " + profile.rockLayer,
            "Key",
            profile.mode,
            "Resize / Move UI"
        };
        Button.OnPress[] actions = {
            b -> open(0),
            b -> open(1),
            b -> {
                profile.searchCircle = !profile.searchCircle;
                save();
            },
            b -> {
                Layer[] layers = Layer.values();
                profile.layer = layers[(profile.selected().ordinal() + 1) % layers.length].name();
                save();
            },
            b -> {
                profile.rockLayer = profile.selectedRockLayer().next().label;
                save();
            },
            b -> Minecraft.getInstance().setScreen(new LegendScreen(map)),
            b -> cycleCoverage(),
            b -> hud.toggle()
        };
        for (int i = 0; i < names.length; i++) {
            CompactButton button =
                    new CompactButton(names[i], 0, 0, 10, (float) profile.toolbarScale, actions[i]);
            toolbar.add(button);
            e.addListener(button);
        }
        toolbarGrip = new HudResizeHandle();
        e.addListener(toolbarGrip);
        layoutToolbar(e.getScreen(), true);
    }

    private static void cycleCoverage() {
        profile.cycleCoverage();
        if (profile.mode.equals("Unexplored only")) {
            try {
                ExploredMask.prepare();
            } catch (RuntimeException ex) {
                message("Cannot prepare terrain coverage: " + ex.getMessage());
            }
        }
        save();
    }

    private static void layoutToolbar(Screen screen, boolean show) {
        hud.hide(HudEditor.Panel.TOOLBAR);
        hud.toolbarGrip(null);
        if (toolbar.size() != 8) {
            return;
        }
        toolbar.get(2)
                .setMessage(
                        Component.literal(
                                profile.searchCircle ? "Search radius ✓" : "Search radius"));
        toolbar.get(4).setMessage(Component.literal("Rock: " + profile.rockLayer));
        toolbar.get(6).setMessage(Component.literal(profile.mode));
        toolbar.get(7).setMessage(Component.literal(hud.editing ? "Done" : "Resize / Move UI"));
        HudLayout.Box safe = HudLayout.safeArea(screen.width, screen.height);
        int[] textWidths = new int[toolbar.size()];
        var font = Minecraft.getInstance().font;
        for (int i = 0; i < textWidths.length; i++) {
            textWidths[i] =
                    i == 4 && profile.selected() != Layer.ROCKS
                            ? -1
                            : font.width(toolbar.get(i).getMessage());
        }
        var fit = ToolbarLayout.fit(textWidths, hud.scale(HudEditor.Panel.TOOLBAR), safe.width());
        float scale = fit.scale();
        int height = fit.height(), width = fit.width();
        int[] widths = fit.widths();
        HudLayout.Box dock =
                show
                        ? HudLayout.place(
                                safe,
                                width,
                                height,
                                hud.x(HudEditor.Panel.TOOLBAR, safe.x()),
                                hud.y(HudEditor.Panel.TOOLBAR, safe.y()),
                                widgetBounds(screen))
                        : null;
        int x = dock == null ? 0 : dock.x();
        for (int i = 0; i < toolbar.size(); i++) {
            CompactButton button = toolbar.get(i);
            button.visible = dock != null && widths[i] > 0;
            button.active = !hud.editing || i == toolbar.size() - 1;
            if (dock != null && widths[i] > 0) {
                button.layout(x, dock.y(), widths[i], height, scale);
                x += widths[i] + 3;
            }
        }
        toolbarGrip.visible = dock != null && hud.editing;
        toolbarGrip.active = hud.editing;
        if (dock != null) {
            int size = Math.min(7, height);
            toolbarGrip.layout(x + height - size, dock.y() + height - size, size);
            hud.toolbarGrip(
                    new HudLayout.Box(x + height - size, dock.y() + height - size, size, size));
            hud.drawn(HudEditor.Panel.TOOLBAR, dock, scale);
        }
    }

    private static List<HudLayout.Box> widgetBounds(Screen screen) {
        List<HudLayout.Box> bounds = new ArrayList<>();
        for (var child : screen.children()) {
            if (child instanceof AbstractWidget widget
                    && !(widget instanceof CompactButton)
                    && !(widget instanceof HudResizeHandle)
                    && widget.visible) {
                bounds.add(
                        new HudLayout.Box(
                                        widget.getX(),
                                        widget.getY(),
                                        widget.getWidth(),
                                        widget.getHeight())
                                .padded(2));
            }
        }
        return bounds;
    }

    private static boolean nativeMenuOpen(Screen screen) {
        for (String name : new String[] {"waypointMenu", "playersMenu", "hopMenu"}) {
            try {
                if (Boolean.TRUE.equals(XaeroBridge.field(screen, name))) {
                    return true;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        try {
            if (XaeroBridge.field(screen, "rightClickMenu") != null) {
                return true;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return false;
    }

    public static void open(int tab) {
        if (map != null) {
            Minecraft.getInstance().setScreen(new AtlasScreen(map, tab));
        }
    }

    public static void overlay(Screen s, GuiGraphics g, int mx, int my) {
        lastHook = System.currentTimeMillis();
        try {
            ensure(s);
            if (!view.overworld() || engine == null || (!profile.overlayVisible() && !held)) {
                return;
            }
            if (!(view.scale() > 0) || !Double.isFinite(view.scale())) {
                return;
            }
            if (!hud.interacting() && !hud.over(mx, my)) {
                hoverX = (int) Math.floor(view.x() + (mx - view.centerX()) / view.scale());
                hoverZ = (int) Math.floor(view.z() + (my - view.centerZ()) / view.scale());
            }
            renderer.render(g, view, profile, engine);
        } catch (Exception ex) {
            if (System.currentTimeMillis() - lastError > 10000) {
                lastError = System.currentTimeMillis();
                message("Overlay: " + ex.getMessage());
            }
        }
    }

    private static void beforeUiRender(ScreenEvent.Render.Pre event) {
        Screen screen = event.getScreen();
        if (!XaeroBridge.isMap(screen)) {
            return;
        }
        boolean show = !nativeMenuOpen(screen);
        layoutToolbar(screen, show);
        if (!show) {
            hud.finish();
            hud.hide(HudEditor.Panel.KEY);
            hud.hide(HudEditor.Panel.INFO);
            hud.hide(HudEditor.Panel.CRITERIA);
        }
    }

    private static void renderUi(ScreenEvent.Render.Post event) {
        Screen s = event.getScreen();
        if (!XaeroBridge.isMap(s) || nativeMenuOpen(s)) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        HudLayout.Box safe = HudLayout.safeArea(s.width, s.height);
        List<HudLayout.Box> occupied = widgetBounds(s);
        var toolbarBounds = hud.bounds(HudEditor.Panel.TOOLBAR);
        if (toolbarBounds != null) {
            occupied.add(toolbarBounds);
        }
        hud.hide(HudEditor.Panel.KEY);
        hud.hide(HudEditor.Panel.INFO);
        hud.hide(HudEditor.Panel.CRITERIA);
        List<String> lines = new ArrayList<>();
        if (!error.isEmpty()) {
            lines.add(error);
        }
        if (profile.seed.isBlank()) {
            lines.add("TFC Atlas: " + ClientSeed.status);
        } else if (view != null && !view.overworld()) {
            lines.add("TFC Atlas: Overworld only");
        } else if (System.currentTimeMillis() - lastHook > 2500) {
            lines.add("TFC Atlas: overlay hook unavailable for this Xaero version");
        } else if (engine != null && (profile.overlayVisible() || held)) {
            lines.add(
                    profile.layerTitle()
                            + " · "
                            + profile.mode
                            + (engine.pendingCount() > 0
                                    ? " · " + engine.pendingCount() + " loading"
                                    : ""));
            if (view != null && !view.surface()) {
                lines.add("Atlas predictions · Xaero cave map above · rocks use selected stratum");
            }
            if (profile.hover) {
                Cell c = engine.cell(hoverX, hoverZ);
                lines.add("X " + hoverX + "  Z " + hoverZ + " · " + renderer.notice);
                if (c != null) {
                    lines.add(
                            profile.selectedRockLayer().label
                                    + ": "
                                    + Cell.label(c.rock(profile.selectedRockLayer()))
                                    + " · "
                                    + c.typeName()
                                    + " · "
                                    + Cell.label(c.biome()));
                    lines.add(
                            String.format(
                                    Locale.ROOT,
                                    "%.0f mm · %.1f °C mean · %s · %s",
                                    c.rain(),
                                    c.temperature(),
                                    Layer.altitudeBand(c),
                                    Layer.inlandBand(c)));
                } else {
                    lines.add(engine.cursorStatus(hoverX, hoverZ));
                }
                var height = engine.hoverHeight(hoverX, hoverZ);
                lines.add(
                        "Predicted surface Y: "
                                + (height == null
                                        ? "calculating…"
                                        : height.failed() ? "unavailable" : "~" + height.y()));
            }
            if (engine.activeQuery != null) {
                lines.add(engine.searchStatus);
                if (!engine.searching && profile.highlights) {
                    lines.add("Outlined + shaded regions = search matches");
                }
            }
            if (profile.spawn) {
                lines.add("Gold boundary: configured spawn search area");
            }
            String tileStatus = engine.statusText();
            if (!tileStatus.equals("Ready")) {
                lines.add(tileStatus);
            }
        }
        boolean keyVisible =
                profile.legend
                        && engine != null
                        && (profile.overlayVisible() || held)
                        && view != null
                        && view.overworld();
        if (keyVisible) {
            float scale = (float) hud.scale(HudEditor.Panel.KEY);
            int requestedWidth =
                    Math.max(
                            24,
                            Math.min(
                                    (int) (safe.width() * .6),
                                    profile.keyWidth() == 0
                                            ? LegendScreen.defaultKeyWidth(profile, scale)
                                            : profile.keyWidth()));
            for (int limit = safe.height(); limit >= 12; limit -= 8) {
                var plan = LegendScreen.plan(profile, requestedWidth, limit, scale);
                if (plan == null) {
                    continue;
                }
                int w = (int) Math.ceil(plan.width() * scale),
                        h = (int) Math.ceil(plan.height() * scale);
                HudLayout.Box key =
                        hud.place(
                                safe,
                                w,
                                h,
                                hud.x(HudEditor.Panel.KEY, safe.right() - w),
                                hud.y(HudEditor.Panel.KEY, safe.y()),
                                occupied);
                if (key != null) {
                    LegendScreen.compact(g, key, profile, scale, plan);
                    occupied.add(hud.occupied(HudEditor.Panel.KEY, key));
                    hud.drawn(HudEditor.Panel.KEY, key, scale);
                    break;
                }
            }
        }
        SearchDetails details =
                engine != null && view != null && view.overworld() ? engine.searchDetails : null;
        HudLayout.Box criteria = drawCriteria(g, safe, occupied, details);
        if (criteria != null) {
            occupied.add(hud.occupied(HudEditor.Panel.CRITERIA, criteria));
        }
        if (!lines.isEmpty()) {
            var font = Minecraft.getInstance().font;
            List<net.minecraft.util.FormattedCharSequence> wrapped = new ArrayList<>();
            float scale = (float) hud.scale(HudEditor.Panel.INFO);
            int panelWidth =
                    Math.max(30, (int) (Math.min(348 * scale, safe.width() * .65) / scale) - 8);
            for (String line : lines) {
                wrapped.addAll(font.split(Component.literal(line), panelWidth));
            }
            int maxLines =
                    Math.min(wrapped.size(), Math.max(1, (int) (safe.height() / scale - 6) / 10));
            for (; maxLines >= 1; maxLines--) {
                var visible = wrapped.subList(0, maxLines);
                int textWidth = visible.stream().mapToInt(font::width).max().orElse(0);
                int logicalWidth = Math.min(panelWidth + 8, hud.stableInfoWidth(textWidth + 8)),
                        logicalHeight =
                                Math.min(
                                        (int) (safe.height() / scale),
                                        hud.stableInfoHeight(maxLines * 10 + 4));
                int physicalWidth = (int) Math.ceil(logicalWidth * scale),
                        panelHeight = (int) Math.ceil(logicalHeight * scale);
                HudLayout.Box panel =
                        hud.place(
                                safe,
                                physicalWidth,
                                panelHeight,
                                hud.x(HudEditor.Panel.INFO, safe.x()),
                                hud.y(HudEditor.Panel.INFO, safe.bottom() - panelHeight),
                                occupied);
                if (panel == null) {
                    continue;
                }
                g.pose().pushPose();
                g.pose().translate(panel.x(), panel.y(), 0);
                g.pose().scale(scale, scale, 1);
                g.fill(0, 0, logicalWidth, logicalHeight, 0xBD161B1D);
                int y = 2;
                for (var line : visible) {
                    g.drawString(font, line, 4, y, 0xE9E2CF);
                    y += 10;
                }
                g.pose().popPose();
                hud.drawn(HudEditor.Panel.INFO, panel, scale);
                break;
            }
        }
        hud.render(g, s.width, s.height);
        if (criteria != null
                && criteriaClipped
                && !hud.editing
                && criteria.contains(event.getMouseX(), event.getMouseY())) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal("Current search criteria"));
            for (String line : details.lines()) {
                tooltip.add(Component.literal(line));
            }
            var text = Component.empty();
            for (int i = 0; i < tooltip.size(); i++) {
                if (i > 0) {
                    text.append("\n");
                }
                text.append(tooltip.get(i));
            }
            AtlasTooltips.draw(
                    g,
                    Tooltip.create(text),
                    event.getMouseX(),
                    event.getMouseY(),
                    s.width,
                    s.height);
        }
        if (toolbarGrip != null
                && toolbarGrip.visible
                && toolbarGrip.isMouseOver(event.getMouseX(), event.getMouseY())) {
            AtlasTooltips.draw(
                    g, toolbarGrip.tip, event.getMouseX(), event.getMouseY(), s.width, s.height);
        }
    }

    private static HudLayout.Box drawCriteria(
            GuiGraphics g,
            HudLayout.Box safe,
            List<HudLayout.Box> occupied,
            SearchDetails details) {
        criteriaClipped = false;
        if (details == null) {
            return null;
        }
        var font = Minecraft.getInstance().font;
        float scale = (float) hud.scale(HudEditor.Panel.CRITERIA);
        int wrapWidth = Math.max(30, (int) (Math.min(330 * scale, safe.width() * .65) / scale) - 8);
        List<net.minecraft.util.FormattedCharSequence> wrapped = new ArrayList<>();
        for (String line : details.lines()) {
            wrapped.addAll(font.split(Component.literal(line), wrapWidth));
        }
        if (details.query().layered()) {
            wrapped.add(Component.literal("Match colours:").getVisualOrderText());
            for (RockLayer layer : RockLayer.values()) {
                if ((RockLayer.mask(details.query().rockLayer()) & layer.bit()) != 0) {
                    wrapped.add(
                            Component.literal("■ " + layer.label)
                                    .withStyle(style -> style.withColor(layer.colour))
                                    .getVisualOrderText());
                }
            }
            if (details.query().dikes()) {
                wrapped.add(
                        Component.literal("■ Predicted dikes")
                                .withStyle(style -> style.withColor(RockLayer.colour(8)))
                                .getVisualOrderText());
            }
            if (details.query().rockLayer().equals("Any layer")) {
                wrapped.add(
                        Component.literal("■ Multiple layers")
                                .withStyle(style -> style.withColor(0xF1F1E5))
                                .getVisualOrderText());
            }
        }
        String title = "Current search";
        int limit =
                Math.min(wrapped.size(), Math.max(1, (int) (safe.height() * .6 / scale - 30) / 10));
        for (int count = limit; count >= 1; count--) {
            boolean clipped = count < wrapped.size();
            var visible = wrapped.subList(0, count);
            int width =
                    Math.max(
                                    font.width(title),
                                    visible.stream().mapToInt(font::width).max().orElse(0))
                            + 8;
            if (clipped) {
                width =
                        Math.max(
                                width,
                                Math.min(wrapWidth, font.width("… hover for full criteria")) + 8);
            }
            int height = 17 + count * 10 + (clipped ? 11 : 0);
            var box =
                    hud.place(
                            safe,
                            (int) Math.ceil(width * scale),
                            (int) Math.ceil(height * scale),
                            hud.x(HudEditor.Panel.CRITERIA, safe.x()),
                            hud.y(HudEditor.Panel.CRITERIA, safe.y() + 14),
                            occupied);
            if (box == null) {
                continue;
            }
            g.pose().pushPose();
            g.pose().translate(box.x(), box.y(), 0);
            g.pose().scale(scale, scale, 1);
            g.fill(0, 0, width, height, 0xCD161B1D);
            g.drawString(font, title, 4, 3, 0xE8D9B6);
            int y = 15;
            for (var line : visible) {
                g.drawString(font, line, 4, y, 0xE9E2CF);
                y += 10;
            }
            if (clipped) {
                g.drawString(
                        font,
                        font.plainSubstrByWidth("… hover for full criteria", width - 8),
                        4,
                        y,
                        0xAAA99D);
            }
            g.pose().popPose();
            criteriaClipped = clipped;
            hud.drawn(HudEditor.Panel.CRITERIA, box, scale);
            return box;
        }
        return null;
    }

    private static void mousePressed(ScreenEvent.MouseButtonPressed.Pre e) {
        if (!XaeroBridge.isMap(e.getScreen())
                || nativeMenuOpen(e.getScreen())
                || e.getButton() != 0) {
            return;
        }
        if (!hud.editing) {
            return;
        }
        if (!toolbar.isEmpty()) {
            var done = toolbar.get(toolbar.size() - 1);
            if (done.visible && done.isMouseOver(e.getMouseX(), e.getMouseY())) {
                return;
            }
        }
        if (hud.press(e.getMouseX(), e.getMouseY())) {
            e.setCanceled(true);
        }
    }

    private static void mouseDragged(ScreenEvent.MouseDragged.Pre e) {
        if (XaeroBridge.isMap(e.getScreen())
                && e.getMouseButton() == 0
                && hud.drag(e.getMouseX(), e.getMouseY(), e.getScreen())) {
            e.setCanceled(true);
        }
    }

    private static void mouseReleased(ScreenEvent.MouseButtonReleased.Pre e) {
        if (XaeroBridge.isMap(e.getScreen()) && e.getButton() == 0 && hud.finish()) {
            e.setCanceled(true);
        }
    }

    private static void key(ScreenEvent.KeyPressed.Pre e) {
        if (!XaeroBridge.isMap(e.getScreen())) {
            return;
        }
        if (hud.editing && e.getKeyCode() == GLFW.GLFW_KEY_ESCAPE) {
            hud.toggle();
            e.setCanceled(true);
        } else if (OPEN.matches(e.getKeyCode(), e.getScanCode())) {
            open(0);
            e.setCanceled(true);
        } else if (TOGGLE.matches(e.getKeyCode(), e.getScanCode())) {
            cycleCoverage();
            e.setCanceled(true);
        } else if (HOLD.matches(e.getKeyCode(), e.getScanCode())) {
            held = true;
            e.setCanceled(true);
        } else if (Screen.hasControlDown() && e.getKeyCode() == GLFW.GLFW_KEY_C) {
            Minecraft.getInstance().keyboardHandler.setClipboard(hoverX + ", " + hoverZ);
            message("Copied " + hoverX + ", " + hoverZ);
            e.setCanceled(true);
        } else if (Screen.hasControlDown() && e.getKeyCode() == GLFW.GLFW_KEY_W) {
            Cell c = engine == null ? null : engine.cell(hoverX, hoverZ);
            XaeroBridge.waypoint(
                    map,
                    hoverX,
                    hoverZ,
                    c == null
                            ? "TFC candidate"
                            : Cell.label(c.rock()) + " · " + Cell.label(c.biome()));
            e.setCanceled(true);
        }
    }

    private static void keyUp(ScreenEvent.KeyReleased.Post e) {
        if (HOLD.matches(e.getKeyCode(), e.getScanCode())) {
            held = false;
        }
    }

    public static void message(String text) {
        error = text;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("[TFC Atlas] " + text), false);
        }
    }
}
