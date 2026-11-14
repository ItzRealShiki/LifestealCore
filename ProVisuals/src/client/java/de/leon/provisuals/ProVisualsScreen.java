package de.leon.provisuals;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Purple pixel-style ProVisuals settings screen with a grid-only placement overlay. */
public final class ProVisualsScreen extends Screen {
    private static final String[] CATEGORIES = {
        "PvP Mods", "Inv & HUD", "Animations", "Potions", "Status Effects", "Misc Options"
    };
    private int left, top, panelW, panelH, sidebarW;
    private int category;
    private boolean gridOverlay = true;
    private boolean editingHex;
    private String hex = "A855F7";
    private int accent = 0xFFA855F7;

    public ProVisualsScreen() { super(Component.literal("ProVisuals")); }

    @Override protected void init() {
        panelW = Math.min((int)(width * .84), 900);
        panelH = Math.min((int)(height * .78), 560);
        panelW = Math.max(360, panelW);
        panelH = Math.max(260, panelH);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        sidebarW = Math.max(132, Math.min(220, panelW / 4));
    }

    private static boolean hit(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    private void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0xAA170B35);
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF190B3A);
        g.fill(x, y, x + w, y + h, 0xFF7B42C9);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0xFF35166F);
        g.fill(x + 6, y + 6, x + w - 6, y + h - 6, 0xFF51269B);
        g.fill(x + 6, y + 6, x + w - 6, y + 9, 0xFFB879FF);
        g.fill(x + 6, y + h - 9, x + w - 6, y + h - 6, 0xFF24104D);
    }

    private void header(GuiGraphicsExtractor g, int x, int y, int w, String text) {
        g.fill(x, y, x + w, y + 34, 0xFF7A3FC3);
        g.fill(x, y + 31, x + w, y + 34, 0xFFB879FF);
        g.text(font, text, x + 14, y + 12, 0xFFFFFFFF, false);
    }

    private void button(GuiGraphicsExtractor g, int x, int y, int w, int h,
                        String text, int mx, int my, boolean active) {
        boolean hover = hit(mx, my, x, y, w, h);
        int edge = active || hover ? 0xFFE1B7FF : 0xFF211047;
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF1B0B3D);
        g.fill(x, y, x + w, y + h, edge);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3,
            active ? 0xFF9A4BE8 : hover ? 0xFF7940C3 : 0xFF6030A8);
        g.fill(x + 4, y + 4, x + w - 4, y + 6, 0xFFB77AFF);
        g.text(font, text, x + (w - font.width(text)) / 2,
            y + (h - 8) / 2, 0xFFFFFFFF, false);
    }

    private void toggleRow(GuiGraphicsExtractor g, int x, int y, int w, String label,
                           boolean enabled, int mx, int my) {
        g.fill(x, y + 35, x + w, y + 37, 0x665E36A0);
        g.text(font, label, x + 12, y + 13, 0xFFFFFFFF, false);
        int bx = x + w - 92;
        g.fill(bx - 2, y + 4, bx + 86, y + 29, 0xFF1D0B40);
        g.fill(bx, y + 6, bx + 82, y + 27, 0xFF170A36);
        g.fill(bx + 3, y + 8, bx + 41, y + 25, enabled ? 0xFFB65CFF : 0xFF4A3474);
        g.fill(bx + 42, y + 8, bx + 79, y + 25, enabled ? 0xFF6330A0 : 0xFF38265C);
        String state = enabled ? "ON" : "OFF";
        g.text(font, state, bx + (enabled ? 10 : 48), y + 12, 0xFFFFFFFF, false);
    }

    private void drawGridOverlay(GuiGraphicsExtractor g, int mx, int my) {
        g.fill(0, 0, width, height, 0x48060016);
        int step = 32;
        for (int x = 0; x < width; x += step) {
            g.fill(x, 0, x + 1, height, 0x557F45C9);
        }
        for (int y = 0; y < height; y += step) {
            g.fill(0, y, width, y + 1, 0x557F45C9);
        }
        g.fill(width / 2 - 1, 0, width / 2 + 1, height, 0x88D6A2FF);
        g.fill(0, height / 2 - 1, width, height / 2 + 1, 0x88D6A2FF);
        int bw = 170, bh = 34, bx = (width - bw) / 2, by = height - 64;
        panel(g, bx, by, bw, bh);
        button(g, bx + 5, by + 5, bw - 10, bh - 10, "ProVisuals", mx, my, true);
        String hint = "GRID OVERLAY  •  GUI ALIGNMENT";
        g.text(font, hint, (width - font.width(hint)) / 2, by - 24, 0xFFEBD8FF, false);
    }

    private void drawSettings(GuiGraphicsExtractor g, int mx, int my) {
        int x = left + sidebarW + 18;
        int y = top + 58;
        int w = panelW - sidebarW - 36;
        int rowY = y + 54;
        if (category == 0) {
            panel(g, x, y, w, 180);
            header(g, x + 5, y + 5, w - 10, "PvP Mod Settings");
            toggleRow(g, x + 10, rowY, w - 20, "FastPlace", ProVisualsClient.isFastPlaceEnabled(), mx, my);
            toggleRow(g, x + 10, rowY + 42, w - 20, "FastBreak", ProVisualsClient.isFastBreakEnabled(), mx, my);
            if (ProVisualsClient.isServerControlled()) {
                g.text(font, "Server permissions control these settings.", x + 14, y + 154, 0xFFE1CFFF, false);
            } else {
                g.text(font, "Available in singleplayer and supported servers.", x + 14, y + 154, 0xFFE1CFFF, false);
            }
        } else if (category == 5) {
            panel(g, x, y, w, 180);
            header(g, x + 5, y + 5, w - 10, "Misc Options");
            toggleRow(g, x + 10, rowY, w - 20, "Fullbright (TEST)", ProVisualsClient.isFullbrightEnabled(), mx, my);
            g.text(font, "Appearance", x + 14, y + 112, 0xFFFFFFFF, false);
            g.text(font, "HEX accent color", x + 14, y + 137, 0xFFE8D9FF, false);
            g.fill(x + 150, y + 128, x + 250, y + 153, 0xFF1D0B40);
            g.text(font, "#" + hex + (editingHex ? "_" : ""), x + 158, y + 137, 0xFFFFFFFF, false);
            button(g, x + 260, y + 128, 68, 25, "APPLY", mx, my, true);
            g.fill(x + w - 55, y + 126, x + w - 25, y + 156, accent);
        } else {
            panel(g, x, y, w, 120);
            header(g, x + 5, y + 5, w - 10, CATEGORIES[category] + " Settings");
            g.text(font, "There are no configurable options in this category yet.",
                x + 16, y + 66, 0xFFE8D9FF, false);
        }
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        if (gridOverlay) {
            drawGridOverlay(g, mx, my);
            super.extractRenderState(g, mx, my, delta);
            return;
        }
        g.fill(0, 0, width, height, 0x99090020);
        panel(g, left, top, panelW, panelH);
        header(g, left + 5, top + 5, panelW - 10, "PvP Mods");
        button(g, left + panelW - 92, top + 10, 76, 24, "Close", mx, my, false);

        panel(g, left + 7, top + 46, sidebarW, panelH - 54);
        for (int i = 0; i < CATEGORIES.length; i++) {
            int by = top + 58 + i * 48;
            button(g, left + 15, by, sidebarW - 16, 40, CATEGORIES[i], mx, my, category == i);
        }
        drawSettings(g, mx, my);
        super.extractRenderState(g, mx, my, delta);
    }

    @Override public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        double mx = e.x(), my = e.y();
        if (e.button() != 0) return super.mouseClicked(e, doubleClick);
        if (gridOverlay) {
            int bw = 170, bh = 34, bx = (width - bw) / 2, by = height - 64;
            if (hit(mx, my, bx, by, bw, bh)) {
                gridOverlay = false;
                init();
            }
            return true;
        }
        if (hit(mx, my, left + panelW - 92, top + 10, 76, 24)) {
            onClose();
            return true;
        }
        for (int i = 0; i < CATEGORIES.length; i++) {
            if (hit(mx, my, left + 15, top + 58 + i * 48, sidebarW - 16, 40)) {
                category = i;
                return true;
            }
        }
        int x = left + sidebarW + 18;
        int y = top + 58;
        int w = panelW - sidebarW - 36;
        int rowY = y + 54;
        if (category == 0) {
            if (hit(mx, my, x + 10, rowY, w - 20, 38)) ProVisualsClient.toggleFastPlace();
            else if (hit(mx, my, x + 10, rowY + 42, w - 20, 38)) ProVisualsClient.toggleFastBreak();
            return true;
        }
        if (category == 5) {
            if (hit(mx, my, x + 10, rowY, w - 20, 38)) {
                ProVisualsClient.toggleFullbright();
                return true;
            }
            if (hit(mx, my, x + 150, y + 128, 100, 25)) {
                editingHex = true;
                return true;
            }
            if (hit(mx, my, x + 260, y + 128, 68, 25)) {
                try {
                    String value = hex.startsWith("#") ? hex.substring(1) : hex;
                    if (value.matches("[0-9a-fA-F]{6}")) accent = 0xFF000000 | Integer.parseInt(value, 16);
                } catch (NumberFormatException ignored) { }
                editingHex = false;
                return true;
            }
        }
        return true;
    }

    @Override public boolean charTyped(CharacterEvent e) {
        if (editingHex) {
            String value = e.codepointAsString();
            if (value.matches("[0-9a-fA-F]") && hex.length() < 6) hex += value.toUpperCase();
            return true;
        }
        return super.charTyped(e);
    }

    @Override public boolean keyPressed(KeyEvent e) {
        if (editingHex) {
            if (e.key() == 8 && !hex.isEmpty()) {
                hex = hex.substring(0, hex.length() - 1);
                return true;
            }
            if (e.key() == 13) {
                editingHex = false;
                return true;
            }
        }
        return super.keyPressed(e);
    }

    @Override public boolean isPauseScreen() { return false; }
}