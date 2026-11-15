package de.leon.simplelitematica.client.gui;

import de.leon.simplelitematica.data.SchematicFiles;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class SchematicScreen extends Screen {
    private List<Path> files = List.of();
    private String message = "Drop .slite files into config/simplelitematica/schematics";
    private int firstVisible;

    public SchematicScreen() {
        super(Component.literal("SimpleLitematica"));
        refresh();
    }

    private void refresh() {
        files = SchematicFiles.list();
        firstVisible = Math.min(firstVisible, Math.max(0, files.size() - 1));
    }

    @Override
    protected void init() {
        int left = width / 2 - 150;
        int top = 48;
        addRenderableWidget(Button.builder(Component.literal("Refresh"), button -> {
            refresh();
            rebuildWidgets();
        }).bounds(left, height - 34, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Open folder"), button -> {
            try {
                java.awt.Desktop.getDesktop().open(SchematicFiles.directory().toFile());
            } catch (Exception exception) {
                message = "Folder: " + SchematicFiles.directory();
            }
        }).bounds(left + 96, height - 34, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(left + 204, height - 34, 96, 20).build());

        int visible = Math.min(7, Math.max(0, files.size() - firstVisible));
        for (int i = 0; i < visible; i++) {
            Path file = files.get(firstVisible + i);
            String name = file.getFileName().toString();
            int y = top + i * 28;
            addRenderableWidget(Button.builder(Component.literal(name), button -> {
                try {
                    SchematicFiles.setActive(name);
                    message = "Selected: " + name;
                } catch (IOException exception) {
                    message = "Could not select file: " + exception.getMessage();
                }
            }).bounds(left, y, 220, 22).build());
            addRenderableWidget(Button.builder(Component.literal("X"), button -> {
                try {
                    SchematicFiles.delete(name);
                    refresh();
                    rebuildWidgets();
                    message = "Deleted: " + name;
                } catch (IOException exception) {
                    message = "Could not delete file.";
                }
            }).bounds(left + 226, y, 24, 22).build());
        }
        if (files.size() > 7) {
            addRenderableWidget(Button.builder(Component.literal("↑"), button -> {
                firstVisible = Math.max(0, firstVisible - 1);
                rebuildWidgets();
            }).bounds(left + 256, top, 24, 22).build());
            addRenderableWidget(Button.builder(Component.literal("↓"), button -> {
                firstVisible = Math.min(files.size() - 7, firstVisible + 1);
                rebuildWidgets();
            }).bounds(left + 256, top + 28, 24, 22).build());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        graphics.drawString(font, "Active: " + SchematicFiles.getActiveName(), width / 2 - 150, 32, 0xB0B0B0);
        graphics.drawString(font, message, width / 2 - 150, height - 58, 0xB0B0B0);
        if (files.isEmpty()) {
            graphics.drawCenteredString(font, "No .slite files found.", width / 2, 90, 0xAAAAAA);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(null);
    }
}
