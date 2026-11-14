package de.leon.survivalclient.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import de.leon.survivalclient.client.network.ServerFeatureState;

/** Main ProVisuals settings with a left category rail and nested sections. */
public final class MainConfigScreen extends Screen {
    private static final int PANEL=0xF20D0A12, SIDEBAR=0xFF130F1A, HEADER=0xFF191321, CARD=0xFF211A2B, BORDER=0xFF49345F, PURPLE=0xFFA875F2, TEXT=0xFFF5F0FC, MUTED=0xFFAA9BB9;
    private static final String[] CATEGORIES={"Overview","HUD","Music","Visuals","Interface","General","Server"};
    private static final String[][] SUBS={
        {"Dashboard","What's New"},
        {"Elements","Position","Visibility"},
        {"Floating Bar","Playback","Appearance"},
        {"Particles","World","Effects"},
        {"Theme","Layout","Animations"},
        {"Profiles","Performance","About"},
        {"FastPlace","FastBreak","Connection"}
    };
    private final Screen parent;
    private int category=2, subcategory=0;
    public MainConfigScreen(Screen parent){super(Component.literal("ProVisuals"));this.parent=parent;}

    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick){
        int w=Math.min(760,width-32),h=Math.min(470,height-32),x=(width-w)/2,y=(height-h)/2;
        g.fill(0,0,width,height,0x99000000);
        g.fill(x,y,x+w,y+h,PANEL);g.outline(x,y,w,h,BORDER);
        roundedFill(g,x+8,y+58,150,h-70,SIDEBAR,10);g.fill(x,y,x+w,y+52,HEADER);
        g.fill(x+16,y+15,x+20,y+38,PURPLE);
        g.text(font,"PROVISUALS",x+30,y+21,TEXT,false);
        g.text(font,"CLIENT CONFIGURATION",x+w-160,y+22,MUTED,false);
        g.text(font,"WORKSPACE",x+18,y+67,0xFF806D96,false);
        for(int i=0;i<CATEGORIES.length;i++){
            int cy=y+88+i*43;boolean active=i==category;
            if(active){roundedFill(g,x+10,cy-7,146,34,0xFF30223F,8);g.fill(x+14,cy-1,x+17,cy+21,PURPLE);}
            g.text(font,(active?"◆  ":"   ")+CATEGORIES[i],x+22,cy+4,active?TEXT:MUTED,false);
        }
        int cx=x+188,contentW=w-210;
        g.text(font,CATEGORIES[category].toUpperCase(),cx,y+72,PURPLE,false);
        g.text(font,SUBS[category][subcategory],cx,y+91,TEXT,false);
        int tabX=cx,tabY=y+116;
        for(int i=0;i<SUBS[category].length;i++){
            int tw=Math.max(88,font.width(SUBS[category][i])+24);
            boolean active=i==subcategory;
            g.fill(tabX,tabY,tabX+tw,tabY+27,active?0xFF39264E:CARD);
            g.outline(tabX,tabY,tw,27,active?PURPLE:BORDER);
            g.text(font,SUBS[category][i],tabX+12,tabY+9,active?TEXT:MUTED,false);
            tabX+=tw+7;
        }
        int cy=y+160;
        if(category==2){
            card(g,cx,cy,contentW,70,"FLOATING MUSIC BAR","Display and configure the music widget.");
            row(g,cx+14,cy+39,contentW-28,"Show music bar",ProVisualsSettings.musicBarEnabled?"ENABLED":"DISABLED");
            card(g,cx,cy+82,contentW,70,"WIDGET SIZE","Adjust the scale of the floating bar.");
            row(g,cx+14,cy+121,contentW-28,"Scale  •  click left/right side",String.format("%.0f%%",ProVisualsSettings.musicBarScale*100));
            card(g,cx,cy+164,contentW,70,"POSITION EDITING","Move the bar from the grid editor.");
            row(g,cx+14,cy+203,contentW-28,"Drag mode",ProVisualsSettings.musicBarMoveMode?"ON":"OFF");
            if(subcategory==1){
                card(g,cx,cy+246,contentW,58,"DEVICE MEDIA","Windows media-session bridge for supported players.");
                g.text(font,"Track metadata and playback controls are read from Windows.",cx+16,cy+279,MUTED,false);
            } else if(subcategory==2){
                card(g,cx,cy+246,contentW,58,"APPEARANCE","Purple accent • dark surface • compact layout");
            }
        } else if(category==6) {
            String connection=ServerFeatureState.connected()?"Connected to compatible server":"No compatible server connection";
            card(g,cx,cy,contentW,70,"LIFESTEALCORE SERVER",connection);
            if(subcategory==0) {
                card(g,cx,cy+82,contentW,78,"FASTPLACE",ServerFeatureState.connected()?(ServerFeatureState.fastPlace()?"Enabled by /settings":"Disabled by /settings"):"Waiting for server settings");
                g.text(font,"Controlled by the existing server /settings menu.",cx+16,cy+139,MUTED,false);
            } else if(subcategory==1) {
                card(g,cx,cy+82,contentW,78,"FASTBREAK",ServerFeatureState.connected()?(ServerFeatureState.fastBreak()?"Enabled • Creative only":"Disabled by /settings"):"Waiting for server settings");
                g.text(font,"FastBreak only applies while your server-approved mode is Creative.",cx+16,cy+139,MUTED,false);
            } else {
                card(g,cx,cy+82,contentW,78,"SERVER SYNC","Settings are received from LifestealCore.");
                g.text(font,"Change FastPlace and FastBreak in /settings in-game.",cx+16,cy+139,MUTED,false);
            }
        } else {
            card(g,cx,cy,contentW,92,"SETTINGS SECTION",CATEGORIES[category]+" / "+SUBS[category][subcategory]);
            g.text(font,"This section is ready for its settings.",cx+16,cy+62,MUTED,false);
            card(g,cx,cy+104,contentW,78,"PROVISUALS DESIGN","Rounded navigation • nested categories • purple highlights");
            g.text(font,"Choose another category from the left navigation.",cx+16,cy+151,MUTED,false);
        }
        int backX=x+188,backY=y+h-48;
        g.fill(backX,backY,backX+150,backY+30,0xFF30223F);g.outline(backX,backY,150,30,BORDER);
        g.text(font,"←  BACK TO LAYOUT",backX+13,backY+10,TEXT,false);
        g.text(font,"ESC  •  CLOSE",x+w-105,y+h-38,MUTED,false);
        super.extractRenderState(g,mouseX,mouseY,partialTick);
    }
    private void roundedFill(GuiGraphicsExtractor g,int x,int y,int w,int h,int color,int radius){
        int r=Math.min(radius,Math.min(w/2,h/2));
        for(int i=0;i<r;i++){
            int inset=r-i;
            g.fill(x+inset,y+i,x+w-inset,y+i+1,color);
            g.fill(x+inset,y+h-1-i,x+w-inset,y+h-i,color);
        }
        g.fill(x,y+r,x+w,y+h-r,color);
    }
    private void card(GuiGraphicsExtractor g,int x,int y,int w,int h,String title,String subtitle){
        roundedFill(g,x,y,w,h,CARD,8);g.outline(x,y,w,h,BORDER);
        g.text(font,title,x+14,y+12,PURPLE,false);g.text(font,subtitle,x+14,y+31,MUTED,false);
    }
    private void row(GuiGraphicsExtractor g,int x,int y,int w,String label,String value){
        roundedFill(g,x,y,w,26,0xFF2A2234,6);
        g.text(font,label,x+9,y+9,TEXT,false);g.text(font,value,x+w-font.width(value)-10,y+9,PURPLE,false);
    }

    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e,boolean doubleClick){
        if(e.button()!=0)return super.mouseClicked(e,doubleClick);
        int w=Math.min(760,width-32),h=Math.min(470,height-32),x=(width-w)/2,y=(height-h)/2;
        if(e.x()>=x+10&&e.x()<=x+156){
            for(int i=0;i<CATEGORIES.length;i++){int cy=y+88+i*43;if(e.y()>=cy-7&&e.y()<=cy+27){category=i;subcategory=0;return true;}}
        }
        int cx=x+188,tabX=cx,tabY=y+116;
        for(int i=0;i<SUBS[category].length;i++){
            int tw=Math.max(88,font.width(SUBS[category][i])+24);
            if(e.x()>=tabX&&e.x()<=tabX+tw&&e.y()>=tabY&&e.y()<=tabY+27){subcategory=i;return true;}
            tabX+=tw+7;
        }
        int cy=y+160,contentW=w-210;
        if(category==2){
            if(e.y()>=cy+39&&e.y()<=cy+65){ProVisualsSettings.musicBarEnabled=!ProVisualsSettings.musicBarEnabled;ProVisualsSettings.save();return true;}
            if(e.y()>=cy+121&&e.y()<=cy+147){ProVisualsSettings.musicBarScale+=e.x()>width/2.0?0.05f:-0.05f;ProVisualsSettings.musicBarScale=Math.max(.75f,Math.min(1.5f,ProVisualsSettings.musicBarScale));ProVisualsSettings.save();return true;}
            if(e.y()>=cy+203&&e.y()<=cy+229){FloatingMusicBar.toggleMoveMode();ProVisualsSettings.save();return true;}
        }
        int backX=x+188,backY=y+h-48;
        if(e.x()>=backX&&e.x()<=backX+150&&e.y()>=backY&&e.y()<=backY+30){Minecraft.getInstance().gui.setScreen(parent);return true;}
        return super.mouseClicked(e,doubleClick);
    }
    @Override public void onClose(){Minecraft.getInstance().gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
