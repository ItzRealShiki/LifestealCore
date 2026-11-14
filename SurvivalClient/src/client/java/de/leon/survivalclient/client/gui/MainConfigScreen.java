package de.leon.survivalclient.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class MainConfigScreen extends Screen {
    private static final int PANEL=0xFF0D0A12, HEADER=0xFF17121E, CARD=0xFF1C1724, BORDER=0xFF3A2B4D, PURPLE=0xFFA875F2, TEXT=0xFFF5F0FC, MUTED=0xFFAA9BB9;
    private final Screen parent;
    public MainConfigScreen(Screen parent) { super(Component.literal("ProVisuals")); this.parent=parent; }

    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick) {
        int w=360,h=214,x=(width-w)/2,y=(height-h)/2;
        g.fill(x,y,x+w,y+h,PANEL); g.outline(x,y,w,h,BORDER); g.fill(x,y,x+w,y+38,HEADER);
        g.fill(x+14,y+10,x+17,y+28,PURPLE); g.text(font,"ProVisuals",x+27,y+14,TEXT,false);
        g.text(font,"FLOATING MUSIC BAR",x+16,y+53,PURPLE,false);
        row(g,x+16,y+72,w-32,"Music Bar",ProVisualsSettings.musicBarEnabled?"ON":"OFF");
        row(g,x+16,y+112,w-32,"Size  (left: smaller / right: larger)",String.format("%.0f%%",ProVisualsSettings.musicBarScale*100));
        row(g,x+16,y+152,w-32,"Position edit",ProVisualsSettings.musicBarMoveMode?"ON":"OFF");
        g.text(font,"Drag the bar with left mouse while edit is ON",x+16,y+190,MUTED,false);
        g.text(font,"ProVisuals",width-font.width("ProVisuals")-12,12,PURPLE,false);
        super.extractRenderState(g,mouseX,mouseY,partialTick);
    }
    private void row(GuiGraphicsExtractor g,int x,int y,int w,String label,String value) {
        g.fill(x,y,x+w,y+32,CARD); g.outline(x,y,w,32,BORDER); g.text(font,label,x+10,y+11,TEXT,false);
        g.text(font,value,x+w-font.width(value)-10,y+11,PURPLE,false);
    }
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e,boolean doubleClick) {
        if(e.button()!=0) return super.mouseClicked(e,doubleClick);
        int x=(width-360)/2,y=(height-214)/2;
        if(inside(e.x(),e.y(),x+16,y+72,328,32)){ProVisualsSettings.musicBarEnabled=!ProVisualsSettings.musicBarEnabled;ProVisualsSettings.save();return true;}
        if(inside(e.x(),e.y(),x+16,y+112,328,32)){ProVisualsSettings.musicBarScale+=e.x()>width/2.0?0.05f:-0.05f;ProVisualsSettings.musicBarScale=Math.max(.75f,Math.min(1.5f,ProVisualsSettings.musicBarScale));ProVisualsSettings.save();return true;}
        if(inside(e.x(),e.y(),x+16,y+152,328,32)){FloatingMusicBar.toggleMoveMode();return true;}
        return super.mouseClicked(e,doubleClick);
    }
    private static boolean inside(double mx,double my,int x,int y,int w,int h){return mx>=x&&mx<=x+w&&my>=y&&my<=y+h;}
    @Override public void onClose(){Minecraft.getInstance().gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
