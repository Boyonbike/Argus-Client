package dev.randomstuff.client.gui;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
final class UiInput extends EditBox {
    UiInput(Font font,int x,int y,int width,int height,String hint) {
        super(font,x+7,y,width-14,height,Component.literal(hint));setBordered(false);setHint(Component.literal(hint));
        setMaxLength(256);
    }
    @Override public void renderWidget(GuiGraphics g,int mx,int my,float delta) {
        UiTheme t=GuiState.theme();
        UiDraw.rounded(g,getX()-7,getY()-3,getWidth()+14,getHeight()+6,5,isFocused()?t.accent():t.overlay());
        UiDraw.rounded(g,getX()-6,getY()-2,getWidth()+12,getHeight()+4,4,t.crust());
        setTextColor(t.text());setTextColorUneditable(t.muted());super.renderWidget(g,mx,my,delta);
    }
}
