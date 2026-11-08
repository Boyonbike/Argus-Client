package dev.randomstuff.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

final class UiButton extends Button {
    private final boolean selected;
    UiButton(int x,int y,int width,int height,String label,Runnable action) { this(x,y,width,height,label,false,action); }
    UiButton(int x,int y,int width,int height,String label,boolean selected,Runnable action) {
        super(x,y,width,height,Component.literal(label),button->action.run(),DEFAULT_NARRATION);this.selected=selected;
    }
    @Override protected void renderContents(GuiGraphics g,int mx,int my,float delta) {
        UiTheme theme=GuiState.theme(); boolean hover=isHoveredOrFocused();
        UiDraw.rounded(g,getX(),getY(),getWidth(),getHeight(),4,selected?UiDraw.alpha(theme.accent(),55):hover?theme.overlay():theme.surface());
        if(selected)UiDraw.rounded(g,getX()+4,getY()+getHeight()-3,getWidth()-8,2,1,theme.accent());
        UiDraw.center(g,Minecraft.getInstance().font,getMessage().getString(),getX(),getY()+(getHeight()-10)/2,getWidth(),selected?theme.accent():active?theme.text():theme.muted());
    }
}
