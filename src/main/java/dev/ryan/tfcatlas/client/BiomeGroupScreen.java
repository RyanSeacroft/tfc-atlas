package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.BiomeGroups;
import dev.ryan.tfcatlas.core.SearchQuery;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Optional shortcuts; selecting a group never changes climate or geology filters. */
final class BiomeGroupScreen extends AtlasMenuScreen {
    private final Screen back;
    private final BiConsumer<String, String> save;
    private final BiomeGroups catalog;
    private final Set<String> selected;
    private String biomes;
    private String message = "Kaolin biomes indicate suitable terrain, not guaranteed deposits.";

    BiomeGroupScreen(Screen back, String groups, String biomes, BiConsumer<String, String> save) {
        super(Component.literal("Biome groups"));
        this.back = back;
        this.save = save;
        this.biomes = biomes;
        catalog = WorldBiomeGroups.current();
        selected = new TreeSet<>(SearchQuery.names(groups));
    }

    @Override
    protected void init() {
        int content = Math.min(width - 24, 360), left = (width - content) / 2;
        int row = 0;
        for (var group : catalog.groups()) {
            boolean enabled = selected.contains(group.id());
            String label =
                    (enabled ? "[x] " : "[ ] ")
                            + font.plainSubstrByWidth(group.label(), content - 72)
                            + " ("
                            + group.members().size()
                            + ")";
            Button button =
                    Button.builder(
                                    Component.literal(label),
                                    ignored -> {
                                        if (!selected.remove(group.id())) {
                                            selected.add(group.id());
                                        }
                                        rebuildWidgets();
                                    })
                            .bounds(left, 40 + row++ * 22, content, 20)
                            .build();
            button.active = enabled || !group.members().isEmpty();
            String help =
                    group.members().isEmpty()
                            ? "Unavailable in this world's biome registry or tags."
                            : group.id().equals("kaolin")
                                    ? "Uses this world's kaolin biome tag, including data-pack changes. Climate and other deposit requirements still apply."
                                    : "Includes matching TFC biome names in this version, including their lake variants where present.";
            button.setTooltip(Tooltip.create(Component.literal(help)));
            addRenderableWidget(button);
        }
        int third = (content - 8) / 3;
        addRenderableWidget(
                Button.builder(
                                Component.literal("Clear groups"),
                                ignored -> {
                                    selected.clear();
                                    rebuildWidgets();
                                })
                        .bounds(left, height - 28, third, 20)
                        .build());
        Button edit =
                Button.builder(
                                Component.literal("Edit as list"),
                                ignored -> {
                                    try {
                                        biomes =
                                                catalog.editableList(
                                                        biomes, String.join(", ", selected));
                                        selected.clear();
                                        onClose();
                                    } catch (IllegalArgumentException error) {
                                        message = error.getMessage();
                                    }
                                })
                        .bounds(left + third + 4, height - 28, third, 20)
                        .build();
        edit.active = !selected.isEmpty();
        edit.setTooltip(
                Tooltip.create(
                        Component.literal(
                                "Expand groups into the biome field so you can add or remove individual names.")));
        addRenderableWidget(edit);
        addRenderableWidget(
                Button.builder(Component.literal("Done"), ignored -> onClose())
                        .bounds(left + 2 * (third + 4), height - 28, content - 2 * (third + 4), 20)
                        .build());
    }

    @Override
    public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.drawCenteredString(font, "Biome groups", width / 2, 16, 0xE8D9B6);
        int content = Math.min(width - 24, 360), left = (width - content) / 2;
        var lines = font.split(Component.literal(message), content);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            graphics.drawString(font, lines.get(i), left, height - 57 + i * 10, 0xCEC9BD);
        }
    }

    @Override
    public void onClose() {
        save.accept(String.join(", ", selected), biomes);
        minecraft.setScreen(back);
    }
}
