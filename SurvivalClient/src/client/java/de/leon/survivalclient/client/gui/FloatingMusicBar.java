package de.leon.survivalclient.client.gui;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Compact floating music island. It expands on hover; mouse polling keeps it
 * interactive while chat or another screen is open.
 */
public final class FloatingMusicBar {
    private static final int COLLAPSED_W=196, COLLAPSED_H=44;
    private static final int EXPANDED_W=310, EXPANDED_H=72;
    private static final int PANEL=0xF20D0A12, HEADER=0xFF17121E, BORDER=0xFF49345F;
    private static final int PURPLE=0xFFA875F2, TEXT=0xFFF5F0FC, MUTED=0xFFAA9BB9;
    private static boolean wasLeftDown, dragging;
    private static int dragOffsetX, dragOffsetY;
    private static boolean expanded;
    private FloatingMusicBar() {}

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("survivalclient", "floating_music_bar"), FloatingMusicBar::render);
    }

    private static void render(GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker delta) {
        if (!ProVisualsSettings.musicBarEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        int scale = Math.round(ProVisualsSettings.musicBarScale * 100);
        int collapsedW = COLLAPSED_W * scale / 100, collapsedH = COLLAPSED_H * scale / 100;
        int expandedW = EXPANDED_W * scale / 100, expandedH = EXPANDED_H * scale / 100;
        int x = ProVisualsSettings.musicBarX < 0 ? (g.guiWidth() - collapsedW) / 2 : ProVisualsSettings.musicBarX;
        int y = Math.max(0, ProVisualsSettings.musicBarY);
        double mx = mc.mouseHandler.xpos() * g.guiWidth() / mc.getWindow().getWidth();
        double my = mc.mouseHandler.ypos() * g.guiHeight() / mc.getWindow().getHeight();
        boolean down = mc.mouseHandler.isLeftPressed();

        boolean overCollapsed = inside(mx, my, x, y, collapsedW, collapsedH);
        boolean overExpanded = inside(mx, my, x, y, expandedW, expandedH);
        expanded = ProVisualsSettings.musicBarMoveMode || overExpanded;

        if (ProVisualsSettings.musicBarMoveMode && down && !wasLeftDown && overCollapsed) {
            dragging = true;
            dragOffsetX = (int) mx - x;
            dragOffsetY = (int) my - y;
        }

        int w = expanded ? expandedW : collapsedW;
        int h = expanded ? expandedH : collapsedH;
        if (!ProVisualsSettings.musicBarMoveMode && down && !wasLeftDown && expanded && MediaSessionBridge.available()) {
            int controlsY = y + 43 * scale / 100;
            int controlsX = x + 190 * scale / 100;
            int buttonW = 34 * scale / 100;
            if (my >= controlsY - 5 && my <= controlsY + 20 * scale / 100) {
                if (mx >= controlsX && mx < controlsX + buttonW) MediaSessionBridge.control("PREVIOUS");
                else if (mx >= controlsX + buttonW && mx < controlsX + buttonW * 2) MediaSessionBridge.control("PLAYPAUSE");
                else if (mx >= controlsX + buttonW * 2 && mx < controlsX + buttonW * 3) MediaSessionBridge.control("NEXT");
            }
        }

        boolean wasDragging = dragging;
        if (!down) dragging = false;
        if (dragging && ProVisualsSettings.musicBarMoveMode) {
            ProVisualsSettings.musicBarX = Math.max(0, Math.min(g.guiWidth() - w, (int) mx - dragOffsetX));
            ProVisualsSettings.musicBarY = Math.max(0, Math.min(g.guiHeight() - h, (int) my - dragOffsetY));
        }
        if (wasLeftDown && !down && wasDragging) ProVisualsSettings.save();
        wasLeftDown = down;

        g.fill(x, y, x + w, y + h, PANEL);
        g.outline(x, y, w, h, BORDER);

        // Cover-art slot: a consistent fallback tile is shown until Windows exposes a thumbnail.
        int art = 32 * scale / 100;
        int artX = x + 6 * scale / 100, artY = y + (h - art) / 2;
        g.fill(artX, artY, artX + art, artY + art, 0xFF30223F);
        g.outline(artX, artY, art, art, PURPLE);
        g.text(mc.font, "♫", artX + (art - mc.font.width("♫")) / 2, artY + (art - 9) / 2, PURPLE, false);

        String title = MediaSessionBridge.available() ? MediaSessionBridge.title() : "No music playing";
        int textX = artX + art + 9 * scale / 100;
        int maxTextW = (expanded ? 165 : w - (textX - x) - 9) * 100 / scale;
        title = mc.font.plainSubstrByWidth(title, Math.max(30, maxTextW));
        g.text(mc.font, title, textX, y + (collapsedH - 9) / 2, TEXT, false);

        if (expanded) {
            String artist = MediaSessionBridge.available() ? MediaSessionBridge.artist() : "Windows media session";
            artist = mc.font.plainSubstrByWidth(artist, 160 * 100 / scale);
            g.text(mc.font, artist, textX, y + 29 * scale / 100, MUTED, false);
            int controlsX = x + 190 * scale / 100, controlsY = y + 43 * scale / 100;
            drawControl(g, mc, "|◀", controlsX, controlsY, TEXT);
            drawControl(g, MediaSessionBridge.playing() ? "Ⅱ" : "▶", controlsX + 34 * scale / 100, controlsY, PURPLE);
            drawControl(g, "▶|", controlsX + 68 * scale / 100, controlsY, TEXT);
        }
        if (ProVisualsSettings.musicBarMoveMode) {
            g.text(mc.font, "MOVE", x + w - mc.font.width("MOVE") - 7, y + 5, 0xFFFFD58A, false);
        }
    }

    private static void drawControl(GuiGraphicsExtractor g, Minecraft mc, String label, int x, int y, int color) {
        g.fill(x - 3, y - 3, x + 25, y + 17, 0xFF30223F);
        g.text(mc.font, label, x + 3, y + 2, color, false);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    public static void toggleMoveMode() {
        ProVisualsSettings.musicBarMoveMode = !ProVisualsSettings.musicBarMoveMode;
        ProVisualsSettings.save();
    }
}
