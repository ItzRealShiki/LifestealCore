package de.leon.survivalclient.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class MainConfigScreen extends Screen {
    private static final float UI_SCALE = 0.805f;
    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_HEIGHT = 222;

    private static final int PANEL = 0xFF0D0A12;
    private static final int HEADER = 0xFF17121E;
    private static final int CARD = 0xFF1C1724;
    private static final int BORDER = 0xFF3A2B4D;
    private static final int PURPLE = 0xFFA875F2;
    private static final int TEXT = 0xFFF5F0FC;
    private static final int MUTED = 0xFFAA9BB9;
    private static final int GREEN = 0xFF64D69A;

    private final Screen parent;

    public MainConfigScreen(Screen parent) {
        super(Component.literal("ProVisuals"));
        this.parent = parent;
        ProVisualsSettings.load();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(this.width / 2.0f, this.height / 2.0f);
        graphics.pose().scale(UI_SCALE, UI_SCALE);
        graphics.pose().translate(-this.width / 2.0f, -this.height / 2.0f);

        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        int right = left + PANEL_WIDTH;

        fillRounded(graphics, left, top, PANEL_WIDTH, PANEL_HEIGHT, 12, PANEL);
        outlineRounded(graphics, left, top, PANEL_WIDTH, PANEL_HEIGHT, 12, BORDER);
        fillRounded(graphics, left, top, PANEL_WIDTH, 42, 12, HEADER);
        graphics.fill(left, top + 12, right, top + 42, HEADER);
        graphics.fill(left + 14, top + 12, left + 17, top + 30, PURPLE);
        graphics.text(this.font, "ProVisuals", left + 28, top + 17, TEXT, false);
        String modLabel = "ProVisuals";
        graphics.text(this.font, modLabel, this.width - this.font.width(modLabel) - 12, 12, PURPLE, false);

        drawToggle(graphics, left + 16, top + 58, PANEL_WIDTH - 32,
                "ChestESP", "Highlights nearby containers in singleplayer",
                ProVisualsSettings.chestEsp);
        drawToggle(graphics, left + 16, top + 122, PANEL_WIDTH - 32,
                "X-Ray", "Highlights nearby ores in singleplayer",
                ProVisualsSettings.xray);

        graphics.text(this.font, "Right Shift  •  Close: ESC", left + 16, top + 194, MUTED, false);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.pose().popMatrix();
    }

    private void drawToggle(GuiGraphicsExtractor graphics, int x, int y, int width,
                            String name, String description, boolean enabled) {
        fillRounded(graphics, x, y, width, 54, 8, CARD);
        outlineRounded(graphics, x, y, width, 54, 8, BORDER);
        graphics.text(this.font, name, x + 12, y + 9, TEXT, false);
        graphics.text(this.font, description, x + 12, y + 28, MUTED, false);

        String state = enabled ? "ON" : "OFF";
        int stateColor = enabled ? GREEN : MUTED;
        int buttonWidth = 54;
        int buttonX = x + width - buttonWidth - 12;
        int buttonY = y + 15;
        fillRounded(graphics, buttonX, buttonY, buttonWidth, 25, 7,
                enabled ? 0xFF203B31 : 0xFF30213F);
        outlineRounded(graphics, buttonX, buttonY, buttonWidth, 25, 7, BORDER);
        graphics.text(this.font, state, buttonX + (buttonWidth - this.font.width(state)) / 2,
                buttonY + 8, stateColor, false);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = (event.x() - this.width / 2.0) / UI_SCALE + this.width / 2.0;
        double mouseY = (event.y() - this.height / 2.0) / UI_SCALE + this.height / 2.0;
        net.minecraft.client.input.MouseButtonEvent adjusted =
                new net.minecraft.client.input.MouseButtonEvent(mouseX, mouseY, event.buttonInfo());
        if (super.mouseClicked(adjusted, doubleClick)) {
            return true;
        }
        if (event.button() != 0) {
            return false;
        }

        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        int rowX = left + 16;
        int rowWidth = PANEL_WIDTH - 32;
        if (inside(mouseX, mouseY, rowX, top + 58, rowWidth, 54)) {
            ProVisualsSettings.toggle("ChestESP");
            return true;
        }
        if (inside(mouseX, mouseY, rowX, top + 122, rowWidth, 54)) {
            ProVisualsSettings.toggle("X-Ray");
            return true;
        }
        return false;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static void fillRounded(GuiGraphicsExtractor graphics, int x, int y, int width,
                                    int height, int radius, int color) {
        int r = Math.max(0, Math.min(radius, Math.min(width / 2, height / 2)));
        for (int row = 0; row < height; row++) {
            int inset = 0;
            if (row < r) {
                double dy = r - row - 0.5;
                inset = r - (int) Math.floor(Math.sqrt(Math.max(0, r * r - dy * dy)));
            } else if (row >= height - r) {
                double dy = row - (height - r) + 0.5;
                inset = r - (int) Math.floor(Math.sqrt(Math.max(0, r * r - dy * dy)));
            }
            graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
        }
    }

    private static void outlineRounded(GuiGraphicsExtractor graphics, int x, int y, int width,
                                        int height, int radius, int color) {
        int r = Math.max(0, Math.min(radius, Math.min(width / 2, height / 2)));
        graphics.fill(x + r, y, x + width - r, y + 1, color);
        graphics.fill(x + r, y + height - 1, x + width - r, y + height, color);
        for (int row = 0; row < height; row++) {
            int inset = 0;
            if (row < r) {
                double dy = r - row - 0.5;
                inset = r - (int) Math.floor(Math.sqrt(Math.max(0, r * r - dy * dy)));
            } else if (row >= height - r) {
                double dy = row - (height - r) + 0.5;
                inset = r - (int) Math.floor(Math.sqrt(Math.max(0, r * r - dy * dy)));
            }
            graphics.fill(x + inset, y + row, x + inset + 1, y + row + 1, color);
            graphics.fill(x + width - inset - 1, y + row, x + width - inset, y + row + 1, color);
        }
    }
}
