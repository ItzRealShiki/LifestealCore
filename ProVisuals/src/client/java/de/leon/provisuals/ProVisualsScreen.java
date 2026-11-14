package de.leon.provisuals;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Compact, rounded ProVisuals workspace with a draggable module layout editor. */
public final class ProVisualsScreen extends Screen {
    private static final String[] CATEGORIES = {"Overview", "PvP", "HUD", "Survival", "Visuals", "Performance", "Profiles"};
    private static final String[][] SUBCATEGORIES = {
        {"Dashboard", "Quick Access"}, {"Utilities", "Combat", "Movement"},
        {"Widgets", "Information", "Music Island"}, {"Tools", "World", "Inventory"},
        {"Effects", "ESP", "Colors"}, {"Optimization", "Diagnostics"}, {"My Profiles", "Presets"}
    };
    private static final int[] MODULE_X = {0, 1, 0};
    private static final int[] MODULE_Y = {0, 0, 1};
    private static final int[] MODULE_W = {1, 1, 1};
    private static final int[] MODULE_H = {1, 1, 1};
    private static final String[] MODULES = {"FastPlace", "FastBreak", "Fullbright"};
    private int left, top, panelW, panelH, sidebar = 104;
    private int category, subcategory;
    private boolean editor = true, settings, compact, editingHex, dragging, resizing;
    private int dragModule = -1, dragOffsetX, dragOffsetY;
    private String hex = "A855F7";
    private int accent = 0xFFA855F7;

    public ProVisualsScreen() { super(Component.literal("ProVisuals")); }

    @Override protected void init() {
        panelW = Math.min((int)(width * .8), 520);
        panelH = Math.min((int)(height * .8), 312);
        panelW = Math.max(360, panelW); panelH = Math.max(230, panelH);
        left = (width - panelW) / 2; top = (height - panelH) / 2;
        sidebar = compact ? 62 : 104;
    }
    private int accent() { return accent; }
    private static boolean hit(double mx,double my,int x,int y,int w,int h) {
        return mx>=x && my>=y && mx<x+w && my<y+h;
    }
    private void rounded(GuiGraphicsExtractor g,int x,int y,int w,int h,int color,int radius) {
        for(int i=0;i<radius;i++) {
            int inset=radius-i-1;
            g.fill(x+inset,y+i,x+w-inset,y+i+1,color);
            g.fill(x+inset,y+h-i-1,x+w-inset,y+h-i,color);
        }
        g.fill(x,y+radius,x+w,y+h-radius,color);
    }
    private void glow(GuiGraphicsExtractor g,int x,int y,int w,int h,int color) {
        rounded(g,x-3,y-3,w+6,h+6,(color & 0x00FFFFFF) | 0x18000000,10);
        rounded(g,x-1,y-1,w+2,h+2,(color & 0x00FFFFFF) | 0x38000000,9);
    }
    private void button(GuiGraphicsExtractor g,int x,int y,int w,int h,String label,int mx,int my,boolean active) {
        boolean hover=hit(mx,my,x,y,w,h);
        if(active || hover) glow(g,x,y,w,h,accent());
        rounded(g,x,y,w,h,active?0xFF39304F:hover?0xFF303548:0xFF222735,7);
        if(active) rounded(g,x,y+2,3,h-4,accent(),2);
        g.text(font,label,x+(w-font.width(label))/2,y+(h-8)/2,0xFFF4F2FF,false);
    }
    private void shell(GuiGraphicsExtractor g,int mx,int my) {
        g.fill(0,0,width,height,0x99060910);
        glow(g,left,top,panelW,panelH,accent());
        rounded(g,left,top,panelW,panelH,0xF0181B27,12);
        rounded(g,left+1,top+1,panelW-2,panelH-2,0xF0181B27,11);
        rounded(g,left+12,top+12,panelW-24,2,accent(),2);
        rounded(g,left+8,top+48,sidebar,panelH-56,0xFF111521,9);
        g.text(font,"PROVISUALS",left+16,top+17,0xFFFFFFFF);
        g.text(font,editor?"LAYOUT EDITOR":settings?"SETTINGS":"CLIENT WORKSPACE",left+16,top+31,0xFF999FB5,false);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        shell(g,mx,my);
        if(editor) drawEditor(g,mx,my);
        else if(settings) drawSettings(g,mx,my);
        else drawWorkspace(g,mx,my);
        super.extractRenderState(g,mx,my,delta);
    }
    private void drawEditor(GuiGraphicsExtractor g,int mx,int my) {
        g.text(font,"LAYOUT EDITOR",left+sidebar+20,top+65,0xFFFFFFFF);
        g.text(font,"Drag cards to move them. Drag the bottom-right corner to resize.",left+sidebar+20,top+80,0xFF9AA1B5,false);
        int gx=left+sidebar+20, gy=top+105, cell=62;
        for(int x=0;x<5;x++) for(int y=0;y<3;y++) {
            int px=gx+x*cell, py=gy+y*cell;
            rounded(g,px,py,cell-5,cell-5,0x332F3850,5);
            rounded(g,px,py,cell-5,cell-5,0x553C4560,5);
        }
        for(int i=0;i<MODULES.length;i++) {
            int x=gx+MODULE_X[i]*cell, y=gy+MODULE_Y[i]*cell;
            int w=MODULE_W[i]*cell-5, h=MODULE_H[i]*cell-5;
            glow(g,x,y,w,h,accent());
            rounded(g,x,y,w,h,0xEE292E40,8);
            g.text(font,MODULES[i],x+9,y+12,0xFFF5F3FF,false);
            rounded(g,x+w-10,y+h-10,7,7,accent(),3);
            g.text(font,"MOVE",x+9,y+h-19,0xFFB9B1D6,false);
        }
        button(g,left+panelW-112,top+panelH-40,96,25,"ProVisuals",mx,my,true);
    }
    private void drawWorkspace(GuiGraphicsExtractor g,int mx,int my) {
        int x=left+sidebar+16, y=top+62;
        for(int i=0;i<CATEGORIES.length;i++) {
            int yy=top+58+i*29;
            button(g,left+8,yy,sidebar-16,24,compact?CATEGORIES[i].substring(0,1):CATEGORIES[i],mx,my,category==i);
        }
        g.text(font,CATEGORIES[category].toUpperCase(),x,y,0xFFFFFFFF);
        int tx=x, ty=y+24;
        for(int i=0;i<SUBCATEGORIES[category].length;i++) {
            String s=SUBCATEGORIES[category][i]; int w=font.width(s)+18;
            button(g,tx,ty,w,22,s,mx,my,subcategory==i); tx+=w+5;
        }
        int cy=ty+34, cw=panelW-sidebar-32;
        rounded(g,x,cy,cw,panelH-(cy-top)-45,0xFF222735,9);
        if(category==1 && subcategory==0) {
            g.text(font,"FastPlace",x+13,cy+17,0xFFFFFFFF,false);
            button(g,x+cw-75,cy+10,62,22,ProVisualsClient.isFastPlaceEnabled()?"ON":"OFF",mx,my,ProVisualsClient.isFastPlaceEnabled());
            g.text(font,"FastBreak",x+13,cy+49,0xFFFFFFFF,false);
            button(g,x+cw-75,cy+42,62,22,ProVisualsClient.isFastBreakEnabled()?"ON":"OFF",mx,my,ProVisualsClient.isFastBreakEnabled());
        } else if(category==4 && subcategory==0) {
            g.text(font,"Fullbright (TEST)",x+13,cy+17,0xFFFFFFFF,false);
            button(g,x+cw-75,cy+10,62,22,ProVisualsClient.isFullbrightEnabled()?"ON":"OFF",mx,my,ProVisualsClient.isFullbrightEnabled());
            g.text(font,"Client-side brightness boost",x+13,cy+43,0xFFB4B9CA,false);
        } else {
            g.text(font,category==0?"Welcome to ProVisuals":"Module section",x+13,cy+19,0xFFFFFFFF,false);
            g.text(font,"Choose a module category to configure your client.",x+13,cy+39,0xFF9AA1B5,false);
        }
        button(g,left+panelW-74,top+13,25,23,"⚙",mx,my,settings);
        button(g,left+panelW-42,top+13,25,23,"X",mx,my,false);
    }
    private void drawSettings(GuiGraphicsExtractor g,int mx,int my) {
        int x=left+sidebar+18,y=top+70,w=panelW-sidebar-36;
        g.text(font,"APPEARANCE",x,y,0xFFFFFFFF);
        rounded(g,x,y+22,w,75,0xFF222735,8);
        g.text(font,"HEX accent color",x+12,y+34,0xFFECEBFA,false);
        rounded(g,x+12,y+52,105,24,0xFF151A26,5);
        g.text(font,"#"+hex+(editingHex?"_":""),x+20,y+60,0xFFFFFFFF,false);
        button(g,x+126,y+52,66,24,"APPLY",mx,my,true);
        glow(g,x+w-46,y+43,30,30,accent()); rounded(g,x+w-46,y+43,30,30,accent(),8);
        g.text(font,"Type HEX after clicking the field.",x+12,y+82,0xFF9299AE,false);
        button(g,x,y+112,100,24,"COMPACT "+(compact?"ON":"OFF"),mx,my,compact);
        button(g,x,panelH+top-40,90,24,"BACK",mx,my,false);
        button(g,left+panelW-74,top+13,25,23,"X",mx,my,false);
    }
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean doubleClick) {
        double mx=e.x(),my=e.y(); if(e.button()!=0)return super.mouseClicked(e,doubleClick);
        if(editor) {
            int gx=left+sidebar+20,gy=top+105,cell=62;
            if(hit(mx,my,left+panelW-112,top+panelH-40,96,25)){editor=false;return true;}
            for(int i=0;i<MODULES.length;i++){
                int x=gx+MODULE_X[i]*cell,y=gy+MODULE_Y[i]*cell,w=MODULE_W[i]*cell-5,h=MODULE_H[i]*cell-5;
                if(hit(mx,my,x+w-13,y+h-13,13,13)){dragging=true;resizing=true;dragModule=i;return true;}
                if(hit(mx,my,x,y,w,h)){dragging=true;resizing=false;dragModule=i;dragOffsetX=(int)mx-x;dragOffsetY=(int)my-y;return true;}
            }
            return true;
        }
        if(settings) {
            int x=left+sidebar+18,y=top+70,w=panelW-sidebar-36;
            if(hit(mx,my,x+12,y+52,105,24)){editingHex=true;return true;}
            if(hit(mx,my,x+126,y+52,66,24)){try{String h=hex.startsWith("#")?hex.substring(1):hex;if(h.matches("[0-9a-fA-F]{6}"))accent=0xFF000000|Integer.parseInt(h,16);}catch(Exception ignored){}editingHex=false;return true;}
            if(hit(mx,my,x,y+112,100,24)){compact=!compact;init();return true;}
            if(hit(mx,my,x,top+panelH-40,90,24)){settings=false;return true;}
            if(hit(mx,my,left+panelW-74,top+13,25,23)){settings=false;return true;}
            return true;
        }
        if(hit(mx,my,left+panelW-74,top+13,25,23)){settings=true;return true;}
        if(hit(mx,my,left+panelW-42,top+13,25,23)){onClose();return true;}
        for(int i=0;i<CATEGORIES.length;i++)if(hit(mx,my,left+8,top+58+i*29,sidebar-16,24)){category=i;subcategory=0;return true;}
        int x=left+sidebar+16,tx=x,ty=top+86;
        for(int i=0;i<SUBCATEGORIES[category].length;i++){int w=font.width(SUBCATEGORIES[category][i])+18;if(hit(mx,my,tx,ty,w,22)){subcategory=i;return true;}tx+=w+5;}
        int cy=ty+34,cw=panelW-sidebar-32;
        if(category==1&&subcategory==0){if(hit(mx,my,x+cw-75,cy+10,62,22))ProVisualsClient.toggleFastPlace();if(hit(mx,my,x+cw-75,cy+42,62,22))ProVisualsClient.toggleFastBreak();return true;}
        if(category==4&&subcategory==0&&hit(mx,my,x+cw-75,cy+10,62,22)){ProVisualsClient.toggleFullbright();return true;}
        return super.mouseClicked(e,doubleClick);
    }
    @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy) {
        if(editor&&dragging&&dragModule>=0){int gx=left+sidebar+20,gy=top+105,cell=62;
            if(resizing){MODULE_W[dragModule]=Math.max(1,Math.min(2,MODULE_W[dragModule]+(int)Math.signum(dx)));MODULE_H[dragModule]=Math.max(1,Math.min(2,MODULE_H[dragModule]+(int)Math.signum(dy)));}
            else {MODULE_X[dragModule]=Math.max(0,Math.min(4-MODULE_W[dragModule],(int)((e.x()-dragOffsetX-gx+cell/2)/cell)));MODULE_Y[dragModule]=Math.max(0,Math.min(2-MODULE_H[dragModule],(int)((e.y()-dragOffsetY-gy+cell/2)/cell)));}
            return true;
        }return super.mouseDragged(e,dx,dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent e){dragging=false;resizing=false;dragModule=-1;return super.mouseReleased(e);}
    @Override public boolean charTyped(CharacterEvent e) {
        if(editingHex){String s=e.codepointAsString();if(s.matches("[0-9a-fA-F]")&&hex.length()<6)hex+=s.toUpperCase();return true;}
        return super.charTyped(e);
    }
    @Override public boolean keyPressed(KeyEvent e) {
        if(editingHex){if(e.keycode()==8&&!hex.isEmpty()){hex=hex.substring(0,hex.length()-1);return true;}if(e.keycode()==13){editingHex=false;return true;}}
        return super.keyPressed(e);
    }
    @Override public boolean isPauseScreen(){return false;}
}