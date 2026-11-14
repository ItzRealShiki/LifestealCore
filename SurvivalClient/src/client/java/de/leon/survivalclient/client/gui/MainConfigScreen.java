package de.leon.survivalclient.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

public final class MainConfigScreen extends Screen {
    private static final int PANEL_WIDTH = 640;
    private static final int PANEL_HEIGHT = 380;
    private static final List<String> SECTIONS = List.of(
            "Overview", "PvP", "HUD", "Survival", "Visuals", "Performance", "Profiles"
    );

    // White + purple palette inspired by the pack's bright sword and purple emblem.
    private static final int PANEL = 0xFFFFFCFF;
    private static final int HEADER = 0xFFF8F3FF;
    private static final int SIDEBAR = 0xFFF3ECFC;
    private static final int CARD = 0xFFFBF8FF;
    private static final int BORDER = 0xFFE2D5F2;
    private static final int PURPLE = 0xFF8754D8;
    private static final int PURPLE_DARK = 0xFF633A9C;
    private static final int PURPLE_PALE = 0xFFEDE2FC;
    private static final int TEXT = 0xFF342545;
    private static final int MUTED = 0xFF8B7D9D;
    private static final int GREEN = 0xFF398A68;

    private final Screen parent;
    private String activeSection = "Overview";
    private String status = "All changes are local to this client";
    private String searchQuery = "";
    private EditBox searchBox;
    private boolean saved = false;

    public MainConfigScreen(Screen parent) {
        super(Component.literal("Survival Client"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        int sidebarWidth = 142;
        int contentX = left + sidebarWidth + 20;
        int contentRight = left + PANEL_WIDTH - 18;
        int searchY = top + 104;

        searchBox = new EditBox(this.font, contentX + 28, searchY + 5,
                contentRight - contentX - 112, 16, Component.literal("Search settings"));
        searchBox.setBordered(false);
        searchBox.setTextColor(TEXT);
        searchBox.setTextColorUneditable(MUTED);
        searchBox.setHint(Component.literal("Search settings..."));
        searchBox.setMaxLength(64);
        searchBox.setValue(searchQuery);
        searchBox.setResponder(value -> {
            searchQuery = value.toLowerCase(Locale.ROOT);
            saved = false;
        });
        this.addRenderableWidget(searchBox);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xA9000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        int right = left + PANEL_WIDTH;
        int bottom = top + PANEL_HEIGHT;
        int sidebarWidth = 142;
        int sidebarRight = left + sidebarWidth;
        int contentX = sidebarRight + 20;
        int contentRight = right - 18;

        // Compact white window and pale-lavender sidebar.
        graphics.fill(left, top, right, bottom, PANEL);
        graphics.outline(left, top, PANEL_WIDTH, PANEL_HEIGHT, BORDER);
        graphics.fill(left, top, sidebarRight, bottom, SIDEBAR);
        graphics.fill(sidebarRight, top + 1, sidebarRight + 1, bottom - 1, BORDER);

        // Header.
        graphics.fill(left, top, right, top + 54, HEADER);
        graphics.fill(left, top + 53, right, top + 55, PURPLE);
        graphics.fill(left + 16, top + 13, left + 20, top + 41, PURPLE);
        graphics.text(this.font, "SURVIVAL", left + 30, top + 13, TEXT, false);
        graphics.text(this.font, "CLIENT", left + 30, top + 28, PURPLE, false);
        graphics.text(this.font, "CONFIGURATION", left + 168, top + 21, PURPLE_DARK, false);
        graphics.text(this.font, "26.2  /  CLIENT", right - 112, top + 22, MUTED, false);

        // Sidebar navigation.
        graphics.text(this.font, "WORKSPACE", left + 14, top + 70, MUTED, false);
        graphics.fill(left + 12, top + 85, sidebarRight - 12, top + 86, BORDER);
        for (int i = 0; i < SECTIONS.size(); i++) {
            int y = top + 96 + i * 27;
            boolean active = SECTIONS.get(i).equals(activeSection);
            if (active) {
                graphics.fill(left + 8, y - 3, sidebarRight - 8, y + 20, PURPLE_PALE);
                graphics.fill(left + 8, y - 3, left + 11, y + 20, PURPLE);
            }
            graphics.text(this.font, SECTIONS.get(i), left + 19, y + 3,
                    active ? PURPLE_DARK : TEXT, false);
        }

        // Main content heading.
        graphics.text(this.font, activeSection.toUpperCase(Locale.ROOT), contentX, top + 69, TEXT, false);
        graphics.text(this.font, "A compact control center for your client.", contentX, top + 84, MUTED, false);

        // Search field background; EditBox above it handles actual keyboard input.
        int searchY = top + 104;
        graphics.fill(contentX, searchY, contentRight, searchY + 26, CARD);
        graphics.outline(contentX, searchY, contentRight - contentX, 26, BORDER);
        graphics.text(this.font, "⌕", contentX + 9, searchY + 8, PURPLE, false);
        graphics.text(this.font, "CTRL + K", contentRight - 54, searchY + 9, MUTED, false);

        // Compact status cards.
        int cardY = top + 142;
        int gap = 8;
        int cardWidth = (contentRight - contentX - gap * 2) / 3;
        drawCard(graphics, contentX, cardY, cardWidth, 56, "CLIENT STATUS", "READY", GREEN);
        drawCard(graphics, contentX + cardWidth + gap, cardY, cardWidth, 56,
                "ACTIVE MODULES", "0", PURPLE);
        drawCard(graphics, contentX + (cardWidth + gap) * 2, cardY, cardWidth, 56,
                "PROFILE", "DEFAULT", PURPLE_DARK);

        // Search filters the quick-access rows.
        int listY = top + 211;
        graphics.text(this.font, "QUICK ACCESS", contentX, listY, TEXT, false);
        String query = searchQuery.trim();
        int rowY = listY + 17;
        int shown = 0;
        shown += drawFilteredRow(graphics, contentX, rowY + shown * 35, contentRight - contentX,
                "PvP utilities", "Combat HUD, CPS and status widgets", query);
        shown += drawFilteredRow(graphics, contentX, rowY + shown * 35, contentRight - contentX,
                "Survival tools", "Coordinates, durability and quality-of-life options", query);
        shown += drawFilteredRow(graphics, contentX, rowY + shown * 35, contentRight - contentX,
                "Visual settings", "Crosshair, interface and visual preferences", query);
        if (shown == 0) {
            graphics.text(this.font, "No matching settings found.", contentX + 2, rowY + 9, MUTED, false);
        }

        // Footer and custom-styled actions.
        graphics.fill(left, bottom - 48, right, bottom - 47, BORDER);
        graphics.text(this.font, saved ? "PREVIEW UPDATED" : "LOCAL CLIENT CONFIG",
                left + 16, bottom - 34, saved ? GREEN : MUTED, false);
        drawAction(graphics, left + PANEL_WIDTH - 224, bottom - 37, 64, 23,
                "APPLY", PURPLE, mouseX, mouseY);
        drawAction(graphics, left + PANEL_WIDTH - 153, bottom - 37, 64, 23,
                "RESET", PURPLE_PALE, mouseX, mouseY);
        drawAction(graphics, left + PANEL_WIDTH - 82, bottom - 37, 66, 23,
                "CLOSE", CARD, mouseX, mouseY);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private int drawFilteredRow(GuiGraphicsExtractor graphics, int x, int y, int width,
                                String title, String description, String query) {
        boolean matches = query.isEmpty()
                || title.toLowerCase(Locale.ROOT).contains(query)
                || description.toLowerCase(Locale.ROOT).contains(query);
        if (!matches) {
            return 0;
        }
        graphics.fill(x, y, x + width, y + 30, CARD);
        graphics.outline(x, y, width, 30, BORDER);
        graphics.fill(x, y, x + 3, y + 30, PURPLE);
        graphics.text(this.font, title, x + 10, y + 4, TEXT, false);
        graphics.text(this.font, description, x + 10, y + 17, MUTED, false);
        graphics.text(this.font, "SOON", x + width - 38, y + 10, PURPLE, false);
        return 1;
    }

    private void drawCard(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                          String title, String value, int valueColor) {
        graphics.fill(x, y, x + width, y + height, CARD);
        graphics.outline(x, y, width, height, BORDER);
        graphics.text(this.font, title, x + 8, y + 8, MUTED, false);
        graphics.text(this.font, value, x + 8, y + 27, valueColor, false);
    }

    private void drawAction(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                            String label, int fill, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        int actualFill = hovered ? PURPLE_PALE : fill;
        graphics.fill(x, y, x + width, y + height, actualFill);
        graphics.outline(x, y, width, height, BORDER);
        graphics.text(this.font, label, x + (width - this.font.width(label)) / 2,
                y + 8, label.equals("APPLY") ? 0xFFFFFFFF : PURPLE_DARK, false);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        double mouseX = event.x();
        double mouseY = event.y();
        if (event.button() != 0) {
            return false;
        }

        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        int bottom = top + PANEL_HEIGHT;
        int sidebarRight = left + 142;

        for (int i = 0; i < SECTIONS.size(); i++) {
            int y = top + 96 + i * 27;
            if (mouseX >= left + 8 && mouseX < sidebarRight - 8
                    && mouseY >= y - 3 && mouseY < y + 20) {
                activeSection = SECTIONS.get(i);
                status = activeSection + " selected";
                saved = false;
                return true;
            }
        }

        if (mouseY >= bottom - 37 && mouseY < bottom - 14) {
            if (mouseX >= left + PANEL_WIDTH - 224 && mouseX < left + PANEL_WIDTH - 160) {
                saved = true;
                status = "Design preview: nothing to save yet";
                return true;
            }
            if (mouseX >= left + PANEL_WIDTH - 153 && mouseX < left + PANEL_WIDTH - 89) {
                activeSection = "Overview";
                searchQuery = "";
                if (searchBox != null) searchBox.setValue("");
                saved = false;
                status = "Preview reset";
                return true;
            }
            if (mouseX >= left + PANEL_WIDTH - 82 && mouseX < left + PANEL_WIDTH - 16) {
                onClose();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (searchBox != null && searchBox.isFocused()
                && searchBox.keyPressed(event)) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (searchBox != null && searchBox.isFocused()
                && searchBox.charTyped(event)) {
            return true;
        }
        return super.charTyped(event);
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
