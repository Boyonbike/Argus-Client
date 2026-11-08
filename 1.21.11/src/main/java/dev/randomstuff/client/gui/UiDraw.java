package dev.randomstuff.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.*;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;

final class UiDraw {
    private static final FontDescription FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("randomstuffclient","ui"));
    private UiDraw() {}
    static MutableComponent component(String text) { return Component.literal(text).withStyle(Style.EMPTY.withFont(FONT)); }
    static int width(Font font,String text) { return font.width(component(text)); }
    static void text(GuiGraphics g,Font font,String text,int x,int y,int colour) { g.drawString(font,component(text),x,y,colour,false); }
    static void center(GuiGraphics g,Font font,String text,int x,int y,int width,int colour) { text(g,font,text,x+(width-width(font,text))/2,y,colour); }
    static String fit(Font font,String text,int available) {
        if (width(font,text)<=available) return text;
        while (!text.isEmpty() && width(font,text+"…")>available) text=text.substring(0,text.length()-1);
        return text+"…";
    }
    static int alpha(int colour,int alpha) { return (colour&0x00ffffff)|(Math.clamp(alpha,0,255)<<24); }
    static int mix(int first,int second,float progress) {
        float t=Math.clamp(progress,0,1);int result=0;
        for(int shift=0;shift<=24;shift+=8) result|=((int)(((first>>>shift)&255)*(1-t)+((second>>>shift)&255)*t))<<shift;
        return result;
    }
    static boolean over(double mx,double my,int x,int y,int w,int h) { return mx>=x&&mx<x+w&&my>=y&&my<y+h; }
    static void rounded(GuiGraphics g,int x,int y,int width,int height,int radius,int colour) {
        if(width<=0||height<=0)return;
        if(radius>=3&&width>=radius*2&&height>=radius*2){
            g.blitSprite(RenderPipelines.GUI_TEXTURED,Identifier.fromNamespaceAndPath("randomstuffclient","rounded_"+Math.clamp(radius,3,7)),x,y,width,height,colour);return;
        }
        int r=Math.max(0,Math.min(radius,Math.min(width,height)/2));
        if(r==0){g.fill(x,y,x+width,y+height,colour);return;}
        g.fill(x,y+r,x+width,y+height-r,colour);
        for(int row=0;row<r;row++){
            double dy=r-row-0.5;int inset=(int)Math.ceil(r-Math.sqrt(r*r-dy*dy));
            g.fill(x+inset,y+row,x+width-inset,y+row+1,colour);
            g.fill(x+inset,y+height-row-1,x+width-inset,y+height-row,colour);
        }
    }
    static void icon(GuiGraphics g,String name,int x,int y,int size,int colour){g.blitSprite(RenderPipelines.GUI_TEXTURED,Identifier.fromNamespaceAndPath("randomstuffclient",name),x,y,size,size,colour);}
    static void panel(GuiGraphics g,int x,int y,int width,int height,UiTheme theme) {
        rounded(g,x-1,y+2,width+2,height+2,7,0x30000000);
        rounded(g,x,y,width,height,6,theme.base());
    }
}
