package de.leon.survivalclient.client.gui;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** Floating widget showing metadata from the Windows system media session. */
public final class FloatingMusicBar {
    private static final int WIDTH=360,HEIGHT=66;
    private static final int PANEL=0xF20D0A12,HEADER=0xFF17121E,BORDER=0xFF49345F,PURPLE=0xFFA875F2,TEXT=0xFFF5F0FC,MUTED=0xFFAA9BB9;
    private static boolean wasLeftDown,dragging;
    private static int dragOffsetX,dragOffsetY;
    private FloatingMusicBar(){}

    public static void register(){
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("survivalclient","floating_music_bar"),FloatingMusicBar::render);
    }

    private static void render(GuiGraphicsExtractor g,net.minecraft.client.DeltaTracker delta){
        if(!ProVisualsSettings.musicBarEnabled)return;
        int scale=Math.round(ProVisualsSettings.musicBarScale*100),w=WIDTH*scale/100,h=HEIGHT*scale/100;
        int x=ProVisualsSettings.musicBarX<0?(g.guiWidth()-w)/2:ProVisualsSettings.musicBarX;
        int y=Math.max(0,ProVisualsSettings.musicBarY);
        Minecraft mc=Minecraft.getInstance();
        boolean down=mc.mouseHandler.isLeftPressed();
        double mx=mc.mouseHandler.xpos()*g.guiWidth()/mc.getWindow().getWidth();
        double my=mc.mouseHandler.ypos()*g.guiHeight()/mc.getWindow().getHeight();

        if(ProVisualsSettings.musicBarMoveMode&&down&&!wasLeftDown&&mx>=x&&mx<=x+w&&my>=y&&my<=y+h){
            dragging=true;dragOffsetX=(int)mx-x;dragOffsetY=(int)my-y;
        } else if(!ProVisualsSettings.musicBarMoveMode&&down&&!wasLeftDown&&MediaSessionBridge.available()){
            int controlsX=x+w-92*scale/100,controlsY=y+40*scale/100;
            if(my>=controlsY-8&&my<=controlsY+14){
                if(mx>=controlsX&&mx<controlsX+27*scale/100)MediaSessionBridge.control("PREVIOUS");
                else if(mx>=controlsX+29*scale/100&&mx<controlsX+58*scale/100)MediaSessionBridge.control("PLAYPAUSE");
                else if(mx>=controlsX+60*scale/100&&mx<=controlsX+90*scale/100)MediaSessionBridge.control("NEXT");
            }
        }
        boolean wasDragging=dragging;
        if(!down)dragging=false;
        if(dragging&&ProVisualsSettings.musicBarMoveMode){
            ProVisualsSettings.musicBarX=Math.max(0,Math.min(g.guiWidth()-w,(int)mx-dragOffsetX));
            ProVisualsSettings.musicBarY=Math.max(0,Math.min(g.guiHeight()-h,(int)my-dragOffsetY));
        }
        if(wasLeftDown&&!down&&wasDragging)ProVisualsSettings.save();
        wasLeftDown=down;

        g.fill(x,y,x+w,y+h,PANEL);g.fill(x,y,x+w,y+19*scale/100,HEADER);g.outline(x,y,w,h,BORDER);
        g.fill(x+10*scale/100,y+8*scale/100,x+13*scale/100,y+57*scale/100,PURPLE);
        g.text(mc.font,"♫  NOW PLAYING",x+21*scale/100,y+6*scale/100,PURPLE,false);
        String title=MediaSessionBridge.available()?MediaSessionBridge.title():"Waiting for device media session";
        String artist=MediaSessionBridge.available()?MediaSessionBridge.artist():"Play music in a supported Windows app";
        g.text(mc.font,title,x+21*scale/100,y+25*scale/100,TEXT,false);
        g.text(mc.font,artist,x+21*scale/100,y+40*scale/100,MUTED,false);
        int barX=x+21*scale/100,barY=y+58*scale/100,barW=w-42*scale/100;
        g.fill(barX,barY,barX+barW,barY+2*scale/100,0xFF392D48);
        if(MediaSessionBridge.duration()>0){
            int progress=(int)Math.min(barW,barW*(double)MediaSessionBridge.position()/MediaSessionBridge.duration());
            g.fill(barX,barY,barX+progress,barY+2*scale/100,PURPLE);
        }
        int controlsX=x+w-92*scale/100,controlsY=y+39*scale/100;
        g.text(mc.font,"|◀",controlsX,controlsY,TEXT,false);
        g.text(mc.font,MediaSessionBridge.playing()?"Ⅱ":"▶",controlsX+31*scale/100,controlsY,PURPLE,false);
        g.text(mc.font,"▶|",controlsX+62*scale/100,controlsY,TEXT,false);
        if(ProVisualsSettings.musicBarMoveMode)g.text(mc.font,"MOVE",x+w-mc.font.width("MOVE")-10,y+6*scale/100,0xFFFFD58A,false);
    }

    public static void toggleMoveMode(){ProVisualsSettings.musicBarMoveMode=!ProVisualsSettings.musicBarMoveMode;ProVisualsSettings.save();}
}
