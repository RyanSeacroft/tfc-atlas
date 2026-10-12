package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.BiomeGroups;
import dev.ryan.tfcatlas.core.CustomGroupStore;
import dev.ryan.tfcatlas.core.GroupMenuLayout;
import dev.ryan.tfcatlas.core.GroupSelection;
import dev.ryan.tfcatlas.core.SmoothScroll;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Equal-sized family and custom columns use native Minecraft controls. */
final class SearchGroupScreen extends AtlasMenuScreen {
    private final Screen back;
    private final String field, label, help;
    private final List<BiomeGroups.Group> builtins;
    private List<BiomeGroups.Group> groups;
    private final GroupSelection selected;
    private final BiConsumer<String, String> save;
    private final SmoothScroll scroll = new SmoothScroll();
    private final SmoothScroll customScroll = new SmoothScroll();
    private String error = "";
    private Button create;

    SearchGroupScreen(
            Screen back,
            String field,
            String label,
            String selection,
            String names,
            List<BiomeGroups.Group> groups,
            String help,
            BiConsumer<String, String> save) {
        super(Component.literal(label + " groups"));
        this.back = back;
        this.field = field;
        this.label = label;
        this.builtins = groups.stream().filter(g -> !CustomGroupStore.custom(g)).toList();
        this.groups = groups;
        this.help = help;
        this.save = save;
        selected = new GroupSelection(groups, selection, names);
    }

    private void refreshCatalog() {
        groups = SavedGroups.catalog(field, builtins);
        selected.catalog(groups);
        rebuildWidgets();
    }

    private List<BiomeGroups.Group> custom() {
        return groups.stream()
                .filter(g -> g.id().equals("kaolin") || CustomGroupStore.custom(g))
                .sorted(
                        java.util.Comparator.comparing(
                                        (BiomeGroups.Group g) -> !g.id().equals("kaolin"))
                                .thenComparing(
                                        BiomeGroups.Group::label, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    protected void init() {
        var layout = GroupMenuLayout.of(width, height);
        int left = layout.left(), content = layout.width();
        create =
                addRenderableWidget(
                        Button.builder(
                                        Component.literal("Save selection"),
                                        ignored -> minecraft.setScreen(new SaveGroupScreen(this)))
                                .bounds(layout.right(), layout.actionsY(), layout.columnWidth(), 20)
                                .build());
        create.setTooltip(
                Tooltip.create(
                        Component.literal("Save the current selection as your own named group.")));
        create.active = !selected.names().isEmpty();
        var list =
                addRenderableWidget(
                        new MinecraftScrollPanel(
                                        left,
                                        layout.listY(),
                                        layout.columnWidth(),
                                        layout.listHeight(),
                                        scroll)
                                .singleLine());
        var shortcuts =
                addRenderableWidget(
                        new MinecraftScrollPanel(
                                        layout.right(),
                                        layout.listY(),
                                        layout.columnWidth(),
                                        layout.listHeight(),
                                        customScroll)
                                .singleLine());
        var normal =
                groups.stream()
                        .filter(g -> !g.id().equals("kaolin") && !CustomGroupStore.custom(g))
                        .sorted(
                                java.util.Comparator.comparing(
                                        BiomeGroups.Group::label, String.CASE_INSENSITIVE_ORDER))
                        .toList();
        int viewWidth = Math.min(90, Math.max(46, (list.contentWidth() - 28) * 45 / 100));
        int rowHeight = 20;
        int pitch = rowHeight + 6;
        for (int row = 0; row < normal.size(); row++) {
            addRow(list, normal.get(row), row * pitch, rowHeight, viewWidth);
        }
        var custom = custom();
        for (int row = 0; row < custom.size(); row++) {
            addRow(shortcuts, custom.get(row), row * pitch, rowHeight, viewWidth);
        }
        int half = (content - 4) / 2;
        addRenderableWidget(
                Button.builder(Component.literal("Clear selection"), ignored -> selected.clear())
                        .bounds(left, height - 28, half, 20)
                        .build());
        addRenderableWidget(
                new SearchButton(
                        "Select " + label.toLowerCase(java.util.Locale.ROOT) + " for search",
                        left + half + 4,
                        height - 28,
                        content - half - 4,
                        ignored -> onClose()));
    }

    private String viewText(BiomeGroups.Group group) {
        return "View "
                + group.members().size()
                + " "
                + (label.equals("Climate zones")
                        ? "zones"
                        : label.toLowerCase(java.util.Locale.ROOT));
    }

    private void addRow(
            MinecraftScrollPanel list,
            BiomeGroups.Group group,
            int y,
            int rowHeight,
            int viewWidth) {
        boolean removable = CustomGroupStore.custom(group);
        int view = viewWidth;
        int trailing = removable ? 24 : 0;
        int toggle = Math.max(22, list.contentWidth() - view - trailing - 4);
        int viewX = toggle + 4, viewY = y, viewHeight = rowHeight;
        list.cell(
                0,
                y,
                toggle,
                rowHeight,
                () ->
                        (group.members().isEmpty()
                                        ? "[—] "
                                        : selected.contains(group)
                                                ? "[x] "
                                                : selected.count(group) > 0 ? "[-] " : "[ ] ")
                                + group.label(),
                () -> {
                    if (!group.members().isEmpty()) {
                        selected.toggle(group);
                    }
                },
                group.id().equals("kaolin")
                        ? help
                        : group.members().isEmpty()
                                ? "This group's members are not available in this world."
                                : "Select or clear the whole group. View lets you choose individual members.",
                null);
        list.cell(
                viewX,
                viewY,
                view,
                viewHeight,
                () -> viewText(group),
                () -> minecraft.setScreen(new GroupMembersScreen(this, group, selected, label)),
                "View and select individual members.",
                null);
        if (removable) {
            list.cell(
                    viewX + view + 4,
                    viewY,
                    20,
                    viewHeight,
                    () -> "×",
                    () -> {
                        try {
                            SavedGroups.delete(field, group.label());
                            error = "";
                            refreshCatalog();
                        } catch (Exception failure) {
                            error = failure.getMessage();
                        }
                    },
                    "Delete this saved group. Your current selection and saved presets are kept.",
                    null);
        }
    }

    @Override
    public void renderContent(GuiGraphics g, int mx, int my, float delta) {
        create.active = !selected.names().isEmpty();
        var layout = GroupMenuLayout.of(width, height);
        g.drawCenteredString(font, label + " groups", width / 2, 14, HudStyle.TITLE);
        g.drawCenteredString(
                font,
                font.plainSubstrByWidth(
                        error.isEmpty()
                                ? "Tick a group, or View to choose individual members"
                                : error,
                        layout.width()),
                width / 2,
                28,
                error.isEmpty() ? 0xB9B5A7 : 0xFFAAAA);
        g.drawCenteredString(
                font,
                "Standard groups",
                layout.left() + layout.columnWidth() / 2,
                46,
                HudStyle.TITLE);
        g.drawCenteredString(
                font,
                "Custom groups",
                layout.right() + layout.columnWidth() / 2,
                46,
                HudStyle.TITLE);
        if (custom().isEmpty()) {
            g.drawCenteredString(
                    font,
                    "No saved groups yet",
                    layout.right() + layout.columnWidth() / 2,
                    layout.listY() + 7,
                    0xB9B5A7);
        }
        g.fill(layout.dividerX(), 42, layout.dividerX() + 1, layout.actionsY() - 8, HudStyle.GREY);
        g.drawString(
                font,
                font.plainSubstrByWidth(
                        selected.names().size() + " entries selected", layout.columnWidth()),
                layout.left(),
                layout.actionsY() + 6,
                HudStyle.TEXT);
    }

    @Override
    public void onClose() {
        save.accept("", selected.text());
        minecraft.setScreen(back);
    }

    private static final class SaveGroupScreen extends AtlasMenuScreen {
        private final SearchGroupScreen back;
        private net.minecraft.client.gui.components.EditBox name;
        private String error = "";

        SaveGroupScreen(SearchGroupScreen back) {
            super(Component.literal("Save custom group"));
            this.back = back;
        }

        @Override
        protected void init() {
            int content = Math.min(width - 24, 340), left = (width - content) / 2;
            name = new HoverEditBox(font, left, 58, content, 20, Component.literal("Group name"));
            name.setMaxLength(40);
            name.setTooltip(
                    Tooltip.create(
                            Component.literal(
                                    "Enter a short name for this selection. You can type it into the search field later.")));
            addRenderableWidget(name);
            addRenderableWidget(
                    Button.builder(
                                    Component.literal("Save group"),
                                    ignored -> {
                                        try {
                                            SavedGroups.save(
                                                    back.field,
                                                    name.getValue(),
                                                    back.selected.names(),
                                                    back.builtins);
                                            back.groups =
                                                    SavedGroups.catalog(back.field, back.builtins);
                                            back.selected.catalog(back.groups);
                                            var group =
                                                    dev.ryan.tfcatlas.core.SearchGroups.group(
                                                            name.getValue(), back.groups);
                                            if (group != null) {
                                                back.selected.prefer(group);
                                            }
                                            minecraft.setScreen(back);
                                        } catch (Exception failure) {
                                            error = failure.getMessage();
                                        }
                                    })
                            .bounds(left, height - 28, (content - 4) / 2, 20)
                            .build());
            addRenderableWidget(
                    Button.builder(Component.literal("Cancel"), ignored -> onClose())
                            .bounds(left + (content + 4) / 2, height - 28, (content - 4) / 2, 20)
                            .build());
        }

        @Override
        protected void renderContent(GuiGraphics g, int mx, int my, float delta) {
            int content = Math.min(width - 24, 340), left = (width - content) / 2;
            g.drawCenteredString(font, "Save custom group", width / 2, 18, HudStyle.TITLE);
            g.drawString(font, "Group name", left, 45, HudStyle.TEXT);
            g.drawString(
                    font,
                    back.selected.names().size()
                            + " "
                            + back.label.toLowerCase(java.util.Locale.ROOT)
                            + " selected",
                    left,
                    88,
                    HudStyle.TEXT);
            int y = 105;
            String message =
                    error.isEmpty()
                            ? "Saved groups appear at the top of Groups and in autocomplete. Saving an existing name updates that group."
                            : error;
            for (var line : font.split(Component.literal(message), content)) {
                g.drawString(font, line, left, y, error.isEmpty() ? 0xB9B5A7 : 0xFFAAAA);
                y += 10;
            }
        }

        @Override
        public void onClose() {
            minecraft.setScreen(back);
        }
    }
}
