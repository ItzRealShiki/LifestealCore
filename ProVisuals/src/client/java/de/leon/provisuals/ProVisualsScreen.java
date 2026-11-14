package de.leon.provisuals;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * ProVisuals workspace. All labels are rendered with Minecraft's built-in pixel font.
 */
public final class ProVisualsScreen extends Screen {
    private static final String[] CATEGORIES = {
            "Overview", "PvP", "HUD", "Survival", "Visuals", "Performance", "Profiles"
    };
    private static final String[][] SUBCATEGORIES = {
            {"Dashboard", "Quick Access"},
            {"Utilities", "Combat", "Movement"},
            {"Widgets", "Information", "Music Island"},
            {"Tools", "World", "Inventory"},
            {"Effects", "ESP", "Colors"},
            {"Optimization", "Diagnostics"},
            {"My Profiles", "Presets"}
    };
    private static final int[] ACCENTS = {0xFF9B7BFF, 0xFF58C7FF, 0xFF65D69A, 0xFFFFB45E, 0xFFFF78A8};

    private int panelLeft, panelTop, panelWidth, panelHeight;
    private int selectedCategory;
    private int selectedSubcategory;
    private boolean settingsOpen;
    private boolean compactSidebar;
    private int accentIndex;

    public ProVisualsScreen() {
        super(Component.literal("ProVisuals"));
    }

    @Override
    protected void init() {
        panelWidth = Math.min(650, width - 24);
        panelHeight = Math.min(390, height - 24);
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
    }

    private int sidebarWidth() { return compactSidebar ? 92 : 132; }
    private int accent() { return ACCENTS[accentIndex]; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        // Dim the live world without replacing it with a flat background.
        g.fill(0, 0, width, height, 0x99060910);
        int right = panelLeft + panelWidth;
        int bottom = panelTop + panelHeight;
        g.fill(panelLeft, panelTop, right, bottom, 0xF0181B27);
        g.fill(panelLeft, panelTop, right, panelTop + 3, accent());
        g.fill(panelLeft, panelTop, panelLeft + sidebarWidth(), bottom, 0xF010121B);
        g.fill(panelLeft + sidebarWidth(), panelTop + 54, panelLeft + sidebarWidth() + 1, bottom, 0xFF303344);

        g.text(font, "PROVISUALS", panelLeft + 18, panelTop + 18, 0xFFFFFFFF);
        g.text(font, "CLIENT WORKSPACE", panelLeft + 18, panelTop + 34, 0xFF9298AD, false);
        drawButton(g, right - 76, panelTop + 12, 28, 26, "⚙", mouseX, mouseY, settingsOpen);
        drawButton(g, right - 42, panelTop + 12, 28, 26, "X", mouseX, mouseY, false);

        if (settingsOpen) {
            drawSettings(g, mouseX, mouseY);
        } else {
            drawCategories(g, mouseX, mouseY);
            drawContent(g, mouseX, mouseY);
        }
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    private void drawCategories(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x = panelLeft + 10;
        int y = panelTop + 66;
        for (int i = 0; i < CATEGORIES.length; i++) {
            int rowY = y + i * 34;
            boolean selected = selectedCategory == i;
            if (selected) g.fill(x, rowY, x + sidebarWidth() - 20, rowY + 28, 0xFF302A46);
            else if (inside(mouseX, mouseY, x, rowY, sidebarWidth() - 20, 28))
                g.fill(x, rowY, x + sidebarWidth() - 20, rowY + 28, 0xFF252938);
            if (selected) g.fill(x, rowY, x + 3, rowY + 28, accent());
            String label = compactSidebar ? CATEGORIES[i].substring(0, 1) : CATEGORIES[i];
            g.text(font, label, x + 10, rowY + 10, selected ? 0xFFFFFFFF : 0xFFB5B9C8, false);
        }
    }

    private void drawContent(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x = panelLeft + sidebarWidth() + 22;
        int y = panelTop + 68;
        int contentWidth = panelWidth - sidebarWidth() - 42;
        g.text(font, CATEGORIES[selectedCategory].toUpperCase(), x, y, 0xFFFFFFFF);
        g.text(font, "Choose a section", x, y + 17, 0xFF9298AD, false);

        int tabX = x;
        int tabY = y + 38;
        for (int i = 0; i < SUBCATEGORIES[selectedCategory].length; i++) {
            String label = SUBCATEGORIES[selectedCategory][i];
            int tabWidth = Math.max(72, font.width(label) + 20);
            drawButton(g, tabX, tabY, tabWidth, 25, label, mouseX, mouseY, selectedSubcategory == i);
            tabX += tabWidth + 7;
            if (tabX > panelLeft + panelWidth - 100) break;
        }

        int cardY = tabY + 42;
        g.fill(x, cardY, x + contentWidth, cardY + 112, 0xFF202432);
        g.fill(x, cardY, x + 3, cardY + 112, accent());
        String sub = SUBCATEGORIES[selectedCategory][selectedSubcategory];
        g.text(font, sub.toUpperCase(), x + 16, cardY + 14, 0xFFFFFFFF);
        if (selectedCategory == 1 && selectedSubcategory == 0) {
            g.text(font, "FastPlace", x + 16, cardY + 42, 0xFFE6E8F0, false);
            drawButton(g, x + contentWidth - 104, cardY + 34, 88, 25,
                    ProVisualsClient.isFastPlaceEnabled() ? "ON" : "OFF", mouseX, mouseY, ProVisualsClient.isFastPlaceEnabled());
            g.text(font, "FastBreak", x + 16, cardY + 76, 0xFFE6E8F0, false);
            drawButton(g, x + contentWidth - 104, cardY + 68, 88, 25,
                    ProVisualsClient.isFastBreakEnabled() ? "ON" : "OFF", mouseX, mouseY, ProVisualsClient.isFastBreakEnabled());
        } else if (selectedCategory == 0) {
            g.text(font, "Welcome to your client workspace.", x + 16, cardY + 45, 0xFFE6E8F0, false);
            g.text(font, "Select a category on the left to browse modules.", x + 16, cardY + 65, 0xFF9298AD, false);
        } else {
            g.text(font, "This section is ready for its modules.", x + 16, cardY + 48, 0xFFE6E8F0, false);
            g.text(font, "More features can be added here without changing the layout.", x + 16, cardY + 68, 0xFF9298AD, false);
        }

        String state = ProVisualsClient.isServerControlled() ? "SERVER MANAGED" : "LOCAL CLIENT";
        g.text(font, state, x, panelTop + panelHeight - 25, 0xFFB9A6FF, false);
        g.text(font, "RIGHT SHIFT  /  CLOSE", x + contentWidth - 142, panelTop + panelHeight - 25, 0xFF777E92, false);
    }

    private void drawSettings(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x = panelLeft + sidebarWidth() + 22;
        int y = panelTop + 72;
        int w = panelWidth - sidebarWidth() - 44;
        g.text(font, "SETTINGS", x, y, 0xFFFFFFFF);
        g.text(font, "Customize the ProVisuals interface.", x, y + 19, 0xFF9298AD, false);
        g.fill(x, y + 48, x + w, y + 100, 0xFF202432);
        g.text(font, "Accent color", x + 14, y + 68, 0xFFE6E8F0, false);
        drawButton(g, x + w - 128, y + 61, 112, 28, "CHANGE", mouseX, mouseY, false);
        g.fill(x, y + 112, x + w, y + 164, 0xFF202432);
        g.text(font, "Compact sidebar", x + 14, y + 132, 0xFFE6E8F0, false);
        drawButton(g, x + w - 128, y + 125, 112, 28, compactSidebar ? "ON" : "OFF", mouseX, mouseY, compactSidebar);
        g.text(font, "Settings are client-side and do not change server permissions.", x, y + 190, 0xFF9298AD, false);
        drawButton(g, x, panelTop + panelHeight - 54, 100, 28, "BACK", mouseX, mouseY, false);
    }

    private void drawButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String label,
                            int mouseX, int mouseY, boolean active) {
        boolean hovered = inside(mouseX, mouseY, x, y, w, h);
        int fill = active ? 0xFF3A3155 : hovered ? 0xFF34394B : 0xFF292E3D;
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x, y, x + w, y + 1, active ? accent() : 0xFF444A5D);
        g.text(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2,
                active ? 0xFFFFFFFF : 0xFFD9DCE7, false);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int right = panelLeft + panelWidth;
        if (inside(mouseX, mouseY, right - 76, panelTop + 12, 28, 26)) {
            settingsOpen = !settingsOpen;
            return true;
        }
        if (inside(mouseX, mouseY, right - 42, panelTop + 12, 28, 26)) {
            onClose();
            return true;
        }
        if (settingsOpen) {
            int x = panelLeft + sidebarWidth() + 22;
            int y = panelTop + 72;
            int w = panelWidth - sidebarWidth() - 44;
            if (inside(mouseX, mouseY, x + w - 128, y + 61, 112, 28)) {
                accentIndex = (accentIndex + 1) % ACCENTS.length;
                return true;
            }
            if (inside(mouseX, mouseY, x + w - 128, y + 125, 112, 28)) {
                compactSidebar = !compactSidebar;
                init();
                return true;
            }
            if (inside(mouseX, mouseY, x, panelTop + panelHeight - 54, 100, 28)) {
                settingsOpen = false;
                return true;
            }
            return true;
        }

        int categoryX = panelLeft + 10;
        int categoryY = panelTop + 66;
        for (int i = 0; i < CATEGORIES.length; i++) {
            if (inside(mouseX, mouseY, categoryX, categoryY + i * 34, sidebarWidth() - 20, 28)) {
                selectedCategory = i;
                selectedSubcategory = 0;
                return true;
            }
        }

        int x = panelLeft + sidebarWidth() + 22;
        int tabX = x;
        int tabY = panelTop + 68 + 38;
        for (int i = 0; i < SUBCATEGORIES[selectedCategory].length; i++) {
            String label = SUBCATEGORIES[selectedCategory][i];
            int tabWidth = Math.max(72, font.width(label) + 20);
            if (inside(mouseX, mouseY, tabX, tabY, tabWidth, 25)) {
                selectedSubcategory = i;
                return true;
            }
            tabX += tabWidth + 7;
        }

        if (selectedCategory == 1 && selectedSubcategory == 0) {
            int contentWidth = panelWidth - sidebarWidth() - 42;
            int cardY = tabY + 42;
            if (inside(mouseX, mouseY, x + contentWidth - 104, cardY + 34, 88, 25)) {
                ProVisualsClient.toggleFastPlace();
                return true;
            }
            if (inside(mouseX, mouseY, x + contentWidth - 104, cardY + 68, 88, 25)) {
                ProVisualsClient.toggleFastBreak();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
