package de.leon.survivalclient.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class MainConfigScreen extends Screen {
    private static final int PANEL_WIDTH = 700;
    private static final int PANEL_HEIGHT = 420;

    private static final int BACKGROUND = 0xE90B0D14;
    private static final int PANEL = 0xFF111520;
    private static final int PANEL_LIGHT = 0xFF171D2A;
    private static final int BORDER = 0xFF29243D;
    private static final int PURPLE = 0xFF9B72FF;
    private static final int TEXT = 0xFFF2F0FA;
    private static final int MUTED = 0xFF9295A8;
    private static final int GREEN = 0xFF67D6A3;

    private final Screen parent;
    private String activeSection = "Overview";
    private String status = "All changes are local to this client";
    private boolean saved = false;

    public MainConfigScreen(Screen parent) {
        super(Component.literal("Survival Client"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        int sidebarWidth = 158;

        List<String> sections = List.of("Overview", "PvP", "HUD", "Survival", "Visuals", "Performance", "Profiles");
        int navY = top + 112;
        for (String section : sections) {
            int y = navY;
            this.addRenderableWidget(Button.builder(Component.literal(section), button -> {
                activeSection = section;
                saved = false;
                status = section + " section selected";
            }).bounds(left + 16, y, sidebarWidth - 30, 25).build());
            navY += 34;
        }

        this.addRenderableWidget(Button.builder(Component.literal("APPLY"), button -> {
            saved = true;
            status = "Design preview: nothing to save yet";
        }).bounds(left + PANEL_WIDTH - 230, top + PANEL_HEIGHT - 48, 88, 28).build());

        this.addRenderableWidget(Button.builder(Component.literal("RESET"), button -> {
            activeSection = "Overview";
            saved = false;
            status = "Preview reset";
        }).bounds(left + PANEL_WIDTH - 136, top + PANEL_HEIGHT - 48, 76, 28).build());

        this.addRenderableWidget(Button.builder(Component.literal("CLOSE"), button -> onClose())
                .bounds(left + PANEL_WIDTH - 52, top + PANEL_HEIGHT - 48, 40, 28).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xB9000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        int right = left + PANEL_WIDTH;
        int bottom = top + PANEL_HEIGHT;
        int sidebarRight = left + 158;

        // Main window and sidebar
        graphics.fill(left, top, right, bottom, PANEL);
        graphics.outline(left, top, PANEL_WIDTH, PANEL_HEIGHT, BORDER);
        graphics.fill(left, top, sidebarRight, bottom, 0xFF0E121C);
        graphics.fill(sidebarRight, top + 1, sidebarRight + 1, bottom - 1, BORDER);

        // Header
        graphics.fill(left, top, right, top + 68, 0xFF141927);
        graphics.fill(left, top + 67, right, top + 69, 0xFF30264A);
        graphics.fill(left + 20, top + 18, left + 24, top + 48, PURPLE);
        graphics.text(this.font, "SURVIVAL", left + 36, top + 18, TEXT, false);
        graphics.text(this.font, "CLIENT", left + 36, top + 33, PURPLE, false);
        graphics.text(this.font, "MAIN CONFIGURATION", left + 190, top + 25, TEXT, false);
        graphics.text(this.font, "CLIENT-SIDE CONTROL CENTER  /  BUILD 0.1.0", right - 235, top + 27, MUTED, false);

        // Sidebar identity
        graphics.text(this.font, "WORKSPACE", left + 18, top + 82, MUTED, false);
        graphics.fill(left + 16, top + 99, sidebarRight - 16, top + 100, BORDER);

        // Active navigation marker and label
        int sectionIndex = List.of("Overview", "PvP", "HUD", "Survival", "Visuals", "Performance", "Profiles").indexOf(activeSection);
        if (sectionIndex >= 0) {
            int activeY = top + 112 + sectionIndex * 34;
            graphics.fill(left + 12, activeY + 1, left + 15, activeY + 24, PURPLE);
        }

        // Main content
        int contentX = sidebarRight + 24;
        int contentRight = right - 22;
        graphics.text(this.font, activeSection.toUpperCase(), contentX, top + 88, TEXT, false);
        graphics.text(this.font, "Configure your client experience. Modules will be added later.", contentX, top + 104, MUTED, false);

        // Search field visual
        int searchY = top + 128;
        graphics.fill(contentX, searchY, contentRight, searchY + 29, PANEL_LIGHT);
        graphics.outline(contentX, searchY, contentRight - contentX, 29, BORDER);
        graphics.text(this.font, "⌕", contentX + 10, searchY + 9, PURPLE, false);
        graphics.text(this.font, "Search settings...", contentX + 28, searchY + 10, MUTED, false);
        graphics.text(this.font, "CTRL + K", contentRight - 58, searchY + 10, MUTED, false);

        // Overview cards
        int cardY = top + 174;
        int gap = 12;
        int cardWidth = (contentRight - contentX - gap * 2) / 3;
        drawCard(graphics, contentX, cardY, cardWidth, 78, "CLIENT STATUS", "READY", "Design preview", GREEN);
        drawCard(graphics, contentX + cardWidth + gap, cardY, cardWidth, 78, "ACTIVE MODULES", "0", "Nothing enabled yet", PURPLE);
        drawCard(graphics, contentX + (cardWidth + gap) * 2, cardY, cardWidth, 78, "CONFIG PROFILE", "DEFAULT", "Local profile", TEXT);

        // Placeholder module list
        int listY = cardY + 94;
        graphics.text(this.font, "QUICK ACCESS", contentX, listY, TEXT, false);
        graphics.text(this.font, "Your future modules will appear here.", contentRight - 195, listY, MUTED, false);

        drawModuleRow(graphics, contentX, listY + 22, contentRight - contentX, "PvP utilities", "Combat HUD, CPS and status widgets", "COMING SOON");
        drawModuleRow(graphics, contentX, listY + 64, contentRight - contentX, "Survival tools", "Coordinates, durability and quality-of-life options", "COMING SOON");
        drawModuleRow(graphics, contentX, listY + 106, contentRight - contentX, "Visual settings", "Crosshair, interface and visual preferences", "COMING SOON");

        // Footer
        graphics.fill(left, bottom - 66, right, bottom - 65, BORDER);
        graphics.text(this.font, saved ? "PREVIEW UPDATED" : "LOCAL CLIENT CONFIG", left + 20, bottom - 49, saved ? GREEN : MUTED, false);
        graphics.text(this.font, status, left + 20, bottom - 34, MUTED, false);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawCard(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                          String title, String value, String subtitle, int valueColor) {
        graphics.fill(x, y, x + width, y + height, PANEL_LIGHT);
        graphics.outline(x, y, width, height, BORDER);
        graphics.text(this.font, title, x + 10, y + 10, MUTED, false);
        graphics.text(this.font, value, x + 10, y + 29, valueColor, false);
        graphics.text(this.font, subtitle, x + 10, y + 56, MUTED, false);
    }

    private void drawModuleRow(GuiGraphicsExtractor graphics, int x, int y, int width,
                               String title, String description, String badge) {
        graphics.fill(x, y, x + width, y + 36, PANEL_LIGHT);
        graphics.fill(x, y, x + 2, y + 36, 0xFF5A477E);
        graphics.text(this.font, title, x + 12, y + 6, TEXT, false);
        graphics.text(this.font, description, x + 12, y + 20, MUTED, false);
        graphics.text(this.font, badge, x + width - 88, y + 13, PURPLE, false);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
