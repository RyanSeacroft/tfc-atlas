package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.BiomeGroups;
import dev.ryan.tfcatlas.core.Cell;
import dev.ryan.tfcatlas.core.GroupSelection;
import dev.ryan.tfcatlas.core.SmoothScroll;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Native Minecraft buttons select individual members without losing choices in other groups. */
final class GroupMembersScreen extends AtlasMenuScreen {
    private final Screen back;
    private final BiomeGroups.Group group;
    private final GroupSelection selected;
    private final String label;
    private final SmoothScroll scroll = new SmoothScroll();

    GroupMembersScreen(
            Screen back, BiomeGroups.Group group, GroupSelection selected, String label) {
        super(Component.literal(group.label()));
        this.back = back;
        this.group = group;
        this.selected = selected;
        this.label = label;
    }

    @Override
    protected void init() {
        int content = Math.min(width - 24, 500), left = (width - content) / 2;
        var list =
                addRenderableWidget(
                        new MinecraftScrollPanel(left, 44, content, height - 90, scroll));
        int pitch =
                Math.max(
                        26,
                        group.members().stream()
                                .mapToInt(
                                        n ->
                                                font.split(
                                                                                Component.literal(
                                                                                        "[x] "
                                                                                                + Cell
                                                                                                        .label(
                                                                                                                n)),
                                                                                list.contentWidth()
                                                                                        - 12)
                                                                        .size()
                                                                * 10
                                                        + 12)
                                .max()
                                .orElse(26));
        int row = 0;
        for (String name :
                group.members().stream()
                        .sorted(java.util.Comparator.comparing(Cell::label))
                        .toList()) {
            list.cell(
                    0,
                    row++ * pitch,
                    list.contentWidth(),
                    pitch - 4,
                    () -> (selected.contains(name) ? "[x] " : "[ ] ") + Cell.label(name),
                    () -> selected.toggle(name),
                    "Select or deselect " + Cell.label(name),
                    null);
        }
        int half = (content - 4) / 2;
        addRenderableWidget(
                Button.builder(
                                Component.literal("Select / clear all"),
                                ignored -> selected.toggle(group))
                        .bounds(left, height - 28, half, 20)
                        .build());
        addRenderableWidget(
                Button.builder(Component.literal("Back to groups"), ignored -> onClose())
                        .bounds(left + half + 4, height - 28, content - half - 4, 20)
                        .build());
    }

    @Override
    public void renderContent(GuiGraphics g, int mx, int my, float delta) {
        g.drawCenteredString(font, group.label(), width / 2, 14, HudStyle.TITLE);
        g.drawCenteredString(
                font,
                selected.count(group)
                        + " / "
                        + group.members().size()
                        + " "
                        + label.toLowerCase(java.util.Locale.ROOT)
                        + " selected",
                width / 2,
                28,
                0xB9B5A7);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(back);
    }
}
