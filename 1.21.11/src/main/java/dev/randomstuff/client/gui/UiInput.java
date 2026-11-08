package dev.randomstuff.client.gui;

import dev.randomstuff.client.gui.mixin.EditBoxAccessor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Uses the high-density font for both text and cursor/selection hit testing. */
final class UiInput extends EditBox {
    private final Font uiFont;
    private final String placeholder;
    private int displayStart;
    UiInput(Font font,int x,int y,int width,int height,String hint){
        super(font,x+7,y,width-14,height,Component.literal(hint));uiFont=font;placeholder=hint;
        setBordered(false);setMaxLength(256);
    }
    private void alignCursor(){
        String text=getValue();int cursor=getCursorPosition();displayStart=Math.clamp(displayStart,0,cursor);
        if(UiDraw.width(uiFont,text.substring(displayStart,cursor))<=getWidth()-2)return;
        int low=displayStart,high=cursor;
        while(low<high){int middle=(low+high)/2;if(UiDraw.width(uiFont,text.substring(middle,cursor))>getWidth()-2)low=middle+1;else high=middle;}
        displayStart=low;
    }
    private int displayEnd(){
        String text=getValue();int low=displayStart,high=text.length();
        while(low<high){int middle=(low+high+1)/2;if(UiDraw.width(uiFont,text.substring(displayStart,middle))<=getWidth()-2)low=middle;else high=middle-1;}
        return low;
    }
    @Override public void renderWidget(GuiGraphics g,int mx,int my,float delta){
        UiTheme theme=GuiState.theme();alignCursor();String text=getValue();int end=displayEnd();
        UiDraw.rounded(g,getX()-7,getY()-3,getWidth()+14,getHeight()+6,5,isFocused()?theme.accent():theme.overlay());
        UiDraw.rounded(g,getX()-6,getY()-2,getWidth()+12,getHeight()+4,4,theme.crust());
        g.enableScissor(getX(),getY(),getX()+getWidth(),getY()+getHeight());
        if(text.isEmpty())UiDraw.text(g,uiFont,UiDraw.fit(uiFont,placeholder,getWidth()),getX(),getY()+3,theme.muted());
        else{
            int highlight=((EditBoxAccessor)(Object)this).randomstuffclient$getHighlightPosition();
            int first=Math.max(displayStart,Math.min(getCursorPosition(),highlight)),last=Math.min(end,Math.max(getCursorPosition(),highlight));
            if(isFocused()&&first<last){int x=getX()+UiDraw.width(uiFont,text.substring(displayStart,first));int w=UiDraw.width(uiFont,text.substring(first,last));g.fill(x,getY()+1,x+w,getY()+13,UiDraw.alpha(theme.accent(),80));}
            UiDraw.text(g,uiFont,text.substring(displayStart,end),getX(),getY()+3,active?theme.text():theme.muted());
        }
        if(isFocused()&&System.currentTimeMillis()/500%2==0){int cursor=Math.clamp(getCursorPosition(),displayStart,end);int x=getX()+UiDraw.width(uiFont,text.substring(displayStart,cursor));g.fill(x,getY()+2,x+1,getY()+13,theme.accent());}
        g.disableScissor();
    }
    @Override public boolean isMouseOver(double mx,double my){return UiDraw.over(mx,my,getX()-7,getY()-3,getWidth()+14,getHeight()+6);}
    private int cursorAt(double mx){
        alignCursor();String text=getValue();int end=displayEnd();double point=mx-getX();
        if(point<0)return Math.max(0,displayStart-1);if(point>=getWidth())return Math.min(text.length(),end+1);
        int previous=0;
        for(int i=displayStart;i<end;i++){int next=UiDraw.width(uiFont,text.substring(displayStart,i+1));if(point<(previous+next)/2.0)return i;previous=next;}
        return end;
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubled){
        if(!visible||!active||event.button()!=0||!isMouseOver(event.x(),event.y()))return false;
        setFocused(true);moveCursorTo(cursorAt(event.x()),(event.modifiers()&1)!=0);
        if(doubled){int first=getWordPosition(-1),last=getWordPosition(1);moveCursorTo(first,false);moveCursorTo(last,true);}
        return true;
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy){
        if(!isFocused()||event.button()!=0)return false;moveCursorTo(cursorAt(event.x()),true);return true;
    }
}
