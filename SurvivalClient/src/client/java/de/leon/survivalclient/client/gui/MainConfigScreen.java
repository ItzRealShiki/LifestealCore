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
    private boolean secretEspUnlocked = false;
    private int logoClicks = 0;
    private EditBox displayNameBox;

    // White + purple palette inspired by the pack's bright sword and purple emblem.
    private static final int PANEL = 0xFF0D0A12;
    private static final int HEADER = 0xFF17121E;
    private static final int SIDEBAR = 0xFF120E18;
    private static final int CARD = 0xFF1C1724;
    private static final int BORDER = 0xFF3A2B4D;
    private static final int PURPLE = 0xFFA875F2;
    private static final int PURPLE_DARK = 0xFFC6A5FF;
    private static final int PURPLE_PALE = 0xFF30213F;
    private static final int TEXT = 0xFFF5F0FC;
    private static final int MUTED = 0xFFAA9BB9;
    private static final int GREEN = 0xFF398A68;

    private final Screen parent;
    private String activeSection = "Overview";
    private String status = "All changes are local to this client";
    private String searchQuery = "";
    private EditBox searchBox;
    private boolean saved = false;

    public MainConfigScreen(Screen parent) {
        super(Component.literal("ProVisuals"));
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
        searchBox.setHint(Component.literal(("Search settings...")));
        searchBox.setMaxLength(64);
        searchBox.setValue(searchQuery);
        searchBox.setResponder(value -> {
            searchQuery = value.toLowerCase(Locale.ROOT);
            saved = false;
        });
        this.addRenderableWidget(searchBox);
        ProVisualsSettings.load();
        displayNameBox = new EditBox(this.font, contentX, top + 160,
                contentRight - contentX, 20, Component.literal("Display name"));
        displayNameBox.setMaxLength(32);
        displayNameBox.setValue(ProVisualsSettings.displayName);
        displayNameBox.setResponder(value -> {
            ProVisualsSettings.displayName = value;
            ProVisualsSettings.save();
        });
        displayNameBox.setVisible(activeSection.equals("Visuals"));
        this.addRenderableWidget(displayNameBox);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {        graphics.pose().pushMatrix();
        graphics.pose().translate(this.width / 2.0f, this.height / 2.0f);
        graphics.pose().scale(0.7f, 0.7f);
        graphics.pose().translate(-this.width / 2.0f, -this.height / 2.0f);
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        int right = left + PANEL_WIDTH;
        int bottom = top + PANEL_HEIGHT;
        int sidebarWidth = 142;
        int sidebarRight = left + sidebarWidth;
        int contentX = sidebarRight + 20;
        int contentRight = right - 18;

        // Compact white window and pale-lavender sidebar.
        fillRounded(graphics, left, top, PANEL_WIDTH, PANEL_HEIGHT, 14, PANEL);
        outlineRounded(graphics, left, top, PANEL_WIDTH, PANEL_HEIGHT, 14, BORDER);
        fillRounded(graphics, left, top, sidebarWidth, PANEL_HEIGHT, 14, SIDEBAR);
        graphics.fill(left + 14, top, sidebarRight, bottom, SIDEBAR);
        graphics.fill(sidebarRight, top + 14, sidebarRight + 1, bottom - 14, BORDER);

        // Header.
        fillRounded(graphics, left, top, PANEL_WIDTH, 54, 14, HEADER);
        graphics.fill(left, top + 14, right, top + 54, HEADER);
        graphics.fill(left, top + 53, right, top + 55, PURPLE);
        graphics.fill(left + 16, top + 13, left + 20, top + 41, PURPLE);
        graphics.text(this.font, "ProVisuals", left + 30, top + 20, TEXT, false);
        
        graphics.text(this.font, ("CONFIGURATION"), left + 168, top + 21, PURPLE_DARK, false);
        graphics.text(this.font, ("26.2  /  CLIENT"), right - 112, top + 22, MUTED, false);

        // Sidebar navigation.
        graphics.text(this.font, ("WORKSPACE"), left + 14, top + 70, MUTED, false);
        graphics.fill(left + 12, top + 85, sidebarRight - 12, top + 86, BORDER);
        List<String> visibleSections = new java.util.ArrayList<>(SECTIONS);
        if (secretEspUnlocked) visibleSections.add("ESP");
        for (int i = 0; i < visibleSections.size(); i++) {
            int y = top + 96 + i * 27;
            boolean active = visibleSections.get(i).equals(activeSection);
            if (active) {
                fillRounded(graphics, left + 8, y - 3, sidebarWidth - 16, 23, 7, colorForSection(visibleSections.get(i)));
                fillRounded(graphics, left + 8, y - 3, 3, 23, 2, colorForSection(SECTIONS.get(i)));
            }
            graphics.text(this.font, sc(SECTIONS.get(i)), left + 19, y + 3,
                    active ? colorForSection(SECTIONS.get(i)) : TEXT, false);
        }

        // Main content heading.
        graphics.text(this.font, (activeSection.toUpperCase(Locale.ROOT)), contentX, top + 69, colorForSection(activeSection), false);
        graphics.text(this.font, ("A compact control center for your client."), contentX, top + 84, MUTED, false);

        // Search field background; EditBox above it handles actual keyboard input.
        int searchY = top + 104;
        fillRounded(graphics, contentX, searchY, contentRight - contentX, 26, 8, CARD);
        outlineRounded(graphics, contentX, searchY, contentRight - contentX, 26, 8, BORDER);
        graphics.text(this.font, "⌕", contentX + 9, searchY + 8, PURPLE, false);
        graphics.text(this.font, ("CTRL + K"), contentRight - 54, searchY + 9, MUTED, false);

        if (activeSection.equals("ESP")) {
            graphics.text(this.font, "ESP MODULES", contentX, top + 126, TEXT, false);
            drawModuleRow(graphics, contentX, top + 145, contentRight - contentX, "ChestESP", "Highlight storage containers");
            drawModuleRow(graphics, contentX, top + 181, contentRight - contentX, "MobESP", "Highlight nearby mobs");
            drawModuleRow(graphics, contentX, top + 217, contentRight - contentX, "PlayerESP", "Highlight other players");
            drawModuleRow(graphics, contentX, top + 253, contentRight - contentX, "ItemESP", "Highlight dropped items");
            graphics.text(this.font, "COLOR MODE  " + ProVisualsSettings.colorMode.name(), contentX, top + 298, PURPLE_DARK, false);
            graphics.text(this.font, "Click to cycle: DEFAULT / CUSTOM / RAINBOW / CHROMA", contentX, top + 315, MUTED, false);
        } else if (activeSection.equals("Visuals")) {
            graphics.text(this.font, "DISPLAY NAME", contentX, top + 132, TEXT, false);
            graphics.text(this.font, "Local preference; your Minecraft account name is unchanged.", contentX, top + 148, MUTED, false);
            graphics.text(this.font, "COLOR MODE  " + ProVisualsSettings.colorMode.name(), contentX, top + 196, PURPLE_DARK, false);
        } else {
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
        graphics.text(this.font, ("QUICK ACCESS"), contentX, listY, TEXT, false);
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
            graphics.text(this.font, ("No matching settings found."), contentX + 2, rowY + 9, MUTED, false);
        }

        // Large, standalone PV monogram.
        graphics.pose().pushMatrix();
        graphics.pose().translate(left + 48, top + 318);
        graphics.pose().scale(3.2f, 3.2f);
        graphics.text(this.font, "PV", 0, 0, PURPLE, false);
        graphics.pose().popMatrix();
        }

        // Footer and custom-styled actions.
        graphics.fill(left, bottom - 48, right, bottom - 47, BORDER);
        graphics.text(this.font, (saved ? "PREVIEW UPDATED" : "LOCAL CLIENT CONFIG"),
                left + 16, bottom - 34, saved ? GREEN : MUTED, false);
        drawAction(graphics, left + PANEL_WIDTH - 224, bottom - 37, 64, 23,
                "APPLY", PURPLE, mouseX, mouseY);
        drawAction(graphics, left + PANEL_WIDTH - 153, bottom - 37, 64, 23,
                "RESET", PURPLE_PALE, mouseX, mouseY);
        drawAction(graphics, left + PANEL_WIDTH - 82, bottom - 37, 66, 23,
                "CLOSE", CARD, mouseX, mouseY);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.pose().popMatrix();
    }

    private void drawModuleRow(GuiGraphicsExtractor graphics, int x, int y, int width,
                               String name, String description) {
        boolean enabled = ProVisualsSettings.isEnabled(name);
        fillRounded(graphics, x, y, width, 30, 7, CARD);
        outlineRounded(graphics, x, y, width, 30, 7, BORDER);
        graphics.text(this.font, name, x + 10, y + 4, enabled ? GREEN : TEXT, false);
        graphics.text(this.font, description, x + 10, y + 17, MUTED, false);
        String state = enabled ? "ON" : "OFF";
        graphics.text(this.font, state, x + width - this.font.width(state) - 10, y + 10, enabled ? GREEN : MUTED, false);
    }

    private int drawFilteredRow(GuiGraphicsExtractor graphics, int x, int y, int width,
                                String title, String description, String query) {
        boolean matches = query.isEmpty()
                || title.toLowerCase(Locale.ROOT).contains(query)
                || description.toLowerCase(Locale.ROOT).contains(query);
        if (!matches) {
            return 0;
        }
        fillRounded(graphics, x, y, width, 30, 7, CARD);
        outlineRounded(graphics, x, y, width, 30, 7, BORDER);
        fillRounded(graphics, x, y, 4, 30, 2, PURPLE);
        graphics.text(this.font, (title), x + 10, y + 4, colorForType(title), false);
        graphics.text(this.font, (description), x + 10, y + 17, MUTED, false);
        graphics.text(this.font, ("SOON"), x + width - 38, y + 10, colorForType(title), false);
        return 1;
    }

    private void drawCard(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                          String title, String value, int valueColor) {
        fillRounded(graphics, x, y, width, height, 9, CARD);
        outlineRounded(graphics, x, y, width, height, 9, BORDER);
        graphics.text(this.font, (title), x + 8, y + 8, MUTED, false);
        graphics.text(this.font, (value), x + 8, y + 27, valueColor, false);
    }

    private void drawAction(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                            String label, int fill, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        int actualFill = hovered ? PURPLE_PALE : fill;
        fillRounded(graphics, x, y, width, height, 8, actualFill);
        outlineRounded(graphics, x, y, width, height, 8, BORDER);
        String smallLabel = (label);
        graphics.text(this.font, smallLabel, x + (width - this.font.width(smallLabel)) / 2,
                y + 8, label.equals("APPLY") ? 0xFFFFFFFF : PURPLE_DARK, false);
    }


    private static String sc(String input) {
        String upper = input.toUpperCase(Locale.ROOT);
        String capitals = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String small = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";
        StringBuilder result = new StringBuilder(upper.length());
        for (int i = 0; i < upper.length(); i++) {
            char c = upper.charAt(i);
            int index = capitals.indexOf(c);
            result.append(index >= 0 ? small.charAt(index) : c);
        }
        return result.toString();
    }

    private static int colorForSection(String section) {
        return switch (section.toLowerCase(Locale.ROOT)) {
            case "pvp" -> 0xFFFF626C;
            case "performance" -> 0xFF65D68A;
            case "hud" -> 0xFF59D8E8;
            case "survival" -> 0xFFFFC45C;
            case "visuals" -> 0xFFB58AFF;
            case "profiles" -> 0xFF78AFFF;
            default -> PURPLE_DARK;
        };
    }

    private static int colorForType(String title) {
        String lower = title.toLowerCase(Locale.ROOT);
        if (lower.contains("pvp") || lower.contains("combat")) return colorForSection("PvP");
        if (lower.contains("performance") || lower.contains("fps")) return colorForSection("Performance");
        if (lower.contains("hud") || lower.contains("coordinate")) return colorForSection("HUD");
        if (lower.contains("survival") || lower.contains("durability")) return colorForSection("Survival");
        if (lower.contains("visual")) return colorForSection("Visuals");
        if (lower.contains("profile")) return colorForSection("Profiles");
        return PURPLE;
    }

    private static void fillRounded(GuiGraphicsExtractor graphics, int x, int y, int width,
                                    int height, int radius, int color) {
        int r = Math.max(0, Math.min(radius, Math.min(width / 2, height / 2)));
        for (int row = 0; row < height; row++) {
            int inset = 0;
            if (r > 0 && row < r) {
                double dy = r - row - 0.5;
                inset = r - (int) Math.floor(Math.sqrt(Math.max(0, r * r - dy * dy)));
            } else if (r > 0 && row >= height - r) {
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
            if (r > 0 && row < r) {
                double dy = r - row - 0.5;
                inset = r - (int) Math.floor(Math.sqrt(Math.max(0, r * r - dy * dy)));
            } else if (r > 0 && row >= height - r) {
                double dy = row - (height - r) + 0.5;
                inset = r - (int) Math.floor(Math.sqrt(Math.max(0, r * r - dy * dy)));
            }
            graphics.fill(x + inset, y + row, x + inset + 1, y + row + 1, color);
            graphics.fill(x + width - inset - 1, y + row, x + width - inset, y + row + 1, color);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = (event.x() - this.width / 2.0) / 0.7 + this.width / 2.0;
        double mouseY = (event.y() - this.height / 2.0) / 0.7 + this.height / 2.0;
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
        int bottom = top + PANEL_HEIGHT;
        int sidebarRight = left + 142;

        if (mouseX >= left + 22 && mouseX <= left + 130 && mouseY >= top + 12 && mouseY <= top + 46) {
            logoClicks++;
            if (logoClicks >= 5) {
                secretEspUnlocked = true;
                status = "Secret ESP workspace unlocked";
            }
            return true;
        }

        List<String> visibleSections = new java.util.ArrayList<>(SECTIONS);
        if (secretEspUnlocked) visibleSections.add("ESP");
        for (int i = 0; i < visibleSections.size(); i++) {
            int y = top + 96 + i * 27;
            if (mouseX >= left + 8 && mouseX < sidebarRight - 8
                    && mouseY >= y - 3 && mouseY < y + 20) {
                activeSection = visibleSections.get(i);
                if (displayNameBox != null) displayNameBox.setVisible(activeSection.equals("Visuals"));
                status = activeSection + " selected";
                saved = false;
                return true;
            }
        }

        int contentX = left + 162;
        int contentWidth = left + PANEL_WIDTH - 18 - contentX;
        if (activeSection.equals("ESP")) {
            String[] modules = {"ChestESP", "MobESP", "PlayerESP", "ItemESP"};
            for (int i = 0; i < modules.length; i++) {
                int y = top + 145 + i * 36;
                if (mouseX >= contentX && mouseX <= contentX + contentWidth && mouseY >= y && mouseY <= y + 30) {
                    ProVisualsSettings.toggle(modules[i]);
                    return true;
                }
            }
            if (mouseY >= top + 292 && mouseY <= top + 330) {
                ProVisualsSettings.colorMode = ProVisualsSettings.ColorMode.values()[(ProVisualsSettings.colorMode.ordinal() + 1) % ProVisualsSettings.ColorMode.values().length];
                ProVisualsSettings.save();
                return true;
            }
        }
        if (activeSection.equals("Visuals") && mouseY >= top + 190 && mouseY <= top + 230) {
            ProVisualsSettings.colorMode = ProVisualsSettings.ColorMode.values()[(ProVisualsSettings.colorMode.ordinal() + 1) % ProVisualsSettings.ColorMode.values().length];
            ProVisualsSettings.save();
            return true;
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
