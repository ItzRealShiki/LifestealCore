package de.leon.survivalclient.client.gui;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** Floating, draggable music-bar shell. */
public final class FloatingMusicBar {
    private static final int WIDTH = 286, HEIGHT = 54;
    private static final int PANEL = 0xF20D0A12, HEADER = 0xFF17121E, BORDER = 0xFF3A2B4D, PURPLE = 0xFFA875F2;
    private static boolean wasLeftDown, dragging;
    private static int dragOffsetX, dragOffsetY;
    private FloatingMusicBar() {}

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("survivalclient", "floating_music_bar"), FloatingMusicBar::render);
    }

    private static void render(GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker delta) {
        if (!ProVisualsSettings.musicBarEnabled) return;
        int scale = Math.round(ProVisualsSettings.musicBarScale * 100);
        int w = WIDTH * scale / 100, h = HEIGHT * scale / 100;
        int x = ProVisualsSettings.musicBarX < 0 ? (g.guiWidth() - w) / 2 : ProVisualsSettings.musicBarX;
        int y = Math.max(0, ProVisualsSettings.musicBarY);
        Minecraft mc = Minecraft.getInstance();
        boolean down = mc.mouseHandler.isLeftPressed();
        double mx = mc.mouseHandler.xpos() * g.guiWidth() / mc.getWindow().getWidth();
        double my = mc.mouseHandler.ypos() * g.guiHeight() / mc.getWindow().getHeight();

        if (ProVisualsSettings.musicBarMoveMode && down && !wasLeftDown && mx >= x && mx <= x+w && my >= y && my <= y+h) {
            dragging = true; dragOffsetX = (int)mx-x; dragOffsetY = (int)my-y;
        }
        if (!down) dragging = false;
        if (dragging && ProVisualsSettings.musicBarMoveMode) {
            ProVisualsSettings.musicBarX = Math.max(0, Math.min(g.guiWidth()-w, (int)mx-dragOffsetX));
            ProVisualsSettings.musicBarY = Math.max(0, Math.min(g.guiHeight()-h, (int)my-dragOffsetY));
        }
        if (wasLeftDown && !down && dragging) ProVisualsSettings.save();
        wasLeftDown = down;

        g.fill(x,y,x+w,y+h,PANEL); g.fill(x,y,x+w,y+17*scale/100,HEADER); g.outline(x,y,w,h,BORDER);
        g.fill(x+9*scale/100,y+8*scale/100,x+12*scale/100,y+44*scale/100,PURPLE);
        g.text(mc.font,"♫  NOW PLAYING",x+19*scale/100,y+6*scale/100,PURPLE,false);
        String line = ProVisualsSettings.musicBarMoveMode ? "Drag to move • release to save" : "Waiting for device media session";
        g.text(mc.font,line,x+19*scale/100,y+27*scale/100,0xFFF5F0FC,false);
        g.text(mc.font,"ProVisuals",x+w-mc.font.width("ProVisuals")-9,y+6*scale/100,0xFFAA9BB9,false);
    }

    public static void toggleMoveMode() { ProVisualsSettings.musicBarMoveMode = !ProVisualsSettings.musicBarMoveMode; }
}
