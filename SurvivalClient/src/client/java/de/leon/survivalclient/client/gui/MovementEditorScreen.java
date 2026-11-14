package de.leon.survivalclient.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** First screen opened by Right Shift: grid-based HUD placement editor. */
public final class MovementEditorScreen extends Screen {
    private static final int PANEL=0xF20D0A12, CARD=0xE91C1724, BORDER=0xFF49345F, PURPLE=0xFFA875F2, TEXT=0xFFF5F0FC, MUTED=0xFFAA9BB9;
    private final Screen parent;
    private boolean dragging;
    private int dragX, dragY;
    private static final int BAR_W=360, BAR_H=66;

    public MovementEditorScreen(Screen parent) { super(Component.literal("ProVisuals • Layout Editor")); this.parent=parent; }

    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick) {
        g.fill(0,0,width,height,0x99000000);
        int panelW=Math.min(760,width-36), panelH=Math.min(470,height-36), px=(width-panelW)/2, py=(height-panelH)/2;
        g.fill(px,py,px+panelW,py+panelH,PANEL); g.outline(px,py,panelW,panelH,BORDER);
        g.fill(px,py,px+panelW,py+48,0xFF17121E);
        g.fill(px+18,py+12,px+21,py+36,PURPLE);
        g.text(font,"PROVISUALS  /  MOVEMENT EDITOR",px+32,py+18,TEXT,false);
        g.text(font,"GRID SNAP PREVIEW",px+panelW-130,py+18,MUTED,false);

        int gx=px+18, gy=py+64, gw=panelW-36, gh=panelH-142;
        g.fill(gx,gy,gx+gw,gy+gh,0xFF100D16);
        for(int x=gx;x<=gx+gw;x+=16) g.fill(x,gy,x+1,gy+gh,0xFF211A2B);
        for(int y=gy;y<=gy+gh;y+=16) g.fill(gx,y,gx+gw,y+1,0xFF211A2B);
        g.outline(gx,gy,gw,gh,0xFF392A49);

        int scale=Math.round(ProVisualsSettings.musicBarScale*100);
        int bw=BAR_W*scale/100,bh=BAR_H*scale/100;
        int bx=ProVisualsSettings.musicBarX<0?gx+(gw-bw)/2:Math.max(gx,Math.min(gx+gw-bw,ProVisualsSettings.musicBarX));
        int by=Math.max(gy,Math.min(gy+gh-bh,ProVisualsSettings.musicBarY));
        g.fill(bx,by,bx+bw,by+bh,0xF20D0A12); g.fill(bx,by,bx+bw,by+19,0xFF17121E); g.outline(bx,by,bw,bh,PURPLE);
        g.fill(bx+9,by+8,bx+12,by+bh-8,PURPLE);
        g.text(font,"♫  FLOATING MUSIC BAR",bx+20,by+7,TEXT,false);
        g.text(font,"Drag me to reposition",bx+20,by+39,MUTED,false);

        int footerY=py+panelH-60;
        g.fill(px+18,footerY,pX2(px,panelW),footerY+42,CARD);
        g.outline(px+18,footerY,panelW-36,42,BORDER);
        g.text(font,"Drag the bar on the grid  •  Position is saved when released",px+30,footerY+15,MUTED,false);
        int btnW=190,btnH=34,btnX=px+(panelW-btnW)/2,btnY=footerY+4;
        g.fill(btnX,btnY,btnX+btnW,btnY+btnH,0xFF6F42A8); g.outline(btnX,btnY,btnW,btnH,0xFFD6B7FF);
        g.text(font,"OPEN MAIN MENU  →",btnX+(btnW-font.width("OPEN MAIN MENU  →"))/2,btnY+12,TEXT,false);
        g.text(font,"RIGHT SHIFT  •  LAYOUT FIRST",px+20,py+panelH-12,0xFF756782,false);
        super.extractRenderState(g,mouseX,mouseY,partialTick);
    }
    private static int pX2(int x,int w){return x+w-18;}

    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e,boolean doubleClick) {
        if(e.button()!=0) return super.mouseClicked(e,doubleClick);
        int panelW=Math.min(760,width-36),panelH=Math.min(470,height-36),px=(width-panelW)/2,py=(height-panelH)/2;
        int footerY=py+panelH-60,btnX=px+(panelW-190)/2,btnY=footerY+4;
        if(e.x()>=btnX&&e.x()<=btnX+190&&e.y()>=btnY&&e.y()<=btnY+34){Minecraft.getInstance().gui.setScreen(new MainConfigScreen(this));return true;}
        int gx=px+18,gy=py+64,gw=panelW-36,gh=panelH-142;
        int scale=Math.round(ProVisualsSettings.musicBarScale*100),bw=BAR_W*scale/100,bh=BAR_H*scale/100;
        int bx=ProVisualsSettings.musicBarX<0?gx+(gw-bw)/2:Math.max(gx,Math.min(gx+gw-bw,ProVisualsSettings.musicBarX));
        int by=Math.max(gy,Math.min(gy+gh-bh,ProVisualsSettings.musicBarY));
        if(e.x()>=bx&&e.x()<=bx+bw&&e.y()>=by&&e.y()<=by+bh){dragging=true;dragX=(int)e.x()-bx;dragY=(int)e.y()-by;return true;}
        return true;
    }
    @Override public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent e,double dx,double dy) {
        if(!dragging) return super.mouseDragged(e,dx,dy);
        int panelW=Math.min(760,width-36),panelH=Math.min(470,height-36),px=(width-panelW)/2,py=(height-panelH)/2;
        int gx=px+18,gy=py+64,gw=panelW-36,gh=panelH-142;
        int scale=Math.round(ProVisualsSettings.musicBarScale*100),bw=BAR_W*scale/100,bh=BAR_H*scale/100;
        ProVisualsSettings.musicBarX=Math.max(gx,Math.min(gx+gw-bw,(int)e.x()-dragX));
        ProVisualsSettings.musicBarY=Math.max(gy,Math.min(gy+gh-bh,(int)e.y()-dragY));
        return true;
    }
    @Override public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent e) {
        if(dragging){dragging=false;ProVisualsSettings.save();return true;}
        return super.mouseReleased(e);
    }
    @Override public void onClose(){Minecraft.getInstance().gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
