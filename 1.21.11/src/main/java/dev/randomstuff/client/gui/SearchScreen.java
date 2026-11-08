package dev.randomstuff.client.gui;

import dev.randomstuff.client.modules.Module;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import java.util.*;

/** Opens directly from the Search tab and always focuses the query field. */
final class SearchScreen extends UiScreen {
    private UiInput input;
    private List<GuiSearch.Entry> index=List.of();
    private List<GuiSearch.Hit> results=List.of();
    private int left,top,panelWidth,viewHeight,scroll,selected;
    private static final int ROW=38;
    SearchScreen(Screen parent){super("Search",parent,"Search");}
    @Override protected void buildContent(){
        panelWidth=Math.min(540,width-24);left=(width-panelWidth)/2;top=49;
        viewHeight=Math.max(44,Math.min(280,height-top-83));
        index=GuiState.modules().stream().map(module->new GuiSearch.Entry(module.getName(),module.getCategory().getName(),
                Arrays.asList(module.getAliases()),module.getSettings().stream().filter(setting->!setting.isGroupOnly()).map(setting->setting.getName()).distinct().toList())).toList();
        String query=input==null?"":input.getValue();
        input=new UiInput(font,left+26,top+12,panelWidth-106,16,"Search modules and settings...");input.setValue(query);
        input.setResponder(value->refresh());addRenderableWidget(input);setInitialFocus(input);refresh();
    }
    private void refresh(){results=GuiSearch.search(index,input.getValue());scroll=0;selected=0;}
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        backdrop(g);UiTheme t=GuiState.theme();int fullHeight=44+viewHeight+29;
        UiDraw.panel(g,left,top,panelWidth,fullHeight,t);
        UiDraw.rounded(g,left,top,panelWidth,41,6,t.crust());g.fill(left,top+35,left+panelWidth,top+41,t.crust());
        UiDraw.text(g,font,"⌕",left+10,top+15,t.accent());UiDraw.text(g,font,"Esc to close",left+panelWidth-72,top+15,t.muted());
        int start=top+45;scroll=Math.clamp(scroll,0,Math.max(0,results.size()*ROW-viewHeight));
        g.enableScissor(left+4,start,left+panelWidth-4,start+viewHeight);
        for(int i=0;i<results.size();i++){
            int y=start+i*ROW-scroll;if(y+ROW<start||y>start+viewHeight)continue;
            GuiSearch.Hit hit=results.get(i);boolean hover=UiDraw.over(mx,my,left+7,y,panelWidth-14,ROW)&&my>=start&&my<start+viewHeight;
            if(hover||i==selected)UiDraw.rounded(g,left+7,y+1,panelWidth-14,ROW-3,4,hover?t.surface():UiDraw.alpha(t.accent(),22));
            int colour=hit.isSetting()?t.blue():hit.alias()!=null?t.yellow():t.green();
            UiDraw.rounded(g,left+13,y+8,19,19,4,UiDraw.alpha(colour,38));
            UiDraw.center(g,font,hit.isSetting()?"S":hit.alias()!=null?"A":"M",left+13,y+13,19,colour);
            String title=GuiState.title(hit.isSetting()?hit.setting():hit.alias()!=null?hit.alias():hit.module());
            String path=hit.isSetting()?"Setting  ·  "+GuiState.title(hit.category())+" > "+GuiState.title(hit.module())
                    :hit.alias()!=null?"Alias for "+GuiState.title(hit.module())+"  ·  "+GuiState.title(hit.category()):"Module  ·  "+GuiState.title(hit.category());
            Module module=GuiState.module(hit.module());
            UiDraw.text(g,font,UiDraw.fit(font,title,panelWidth-61),left+42,y+7,module!=null&&module.isToggled()&&!hit.isSetting()?t.accent():t.text());
            UiDraw.text(g,font,UiDraw.fit(font,path,panelWidth-61),left+42,y+21,t.muted());
        }
        if(results.isEmpty()){
            UiDraw.center(g,font,input.getValue().isBlank()?"Find a module or setting":"No matches",left,start+12,panelWidth,t.text());
            UiDraw.center(g,font,UiDraw.fit(font,"Names, aliases and close spellings are supported.",panelWidth-20),left,start+30,panelWidth,t.muted());
        }
        g.disableScissor();
        UiDraw.text(g,font,UiDraw.fit(font,"Left: toggle module   Right: settings   Click a setting to edit",panelWidth-20),left+10,top+fullHeight-17,t.muted());
        chrome(g,mx,my,delta);
    }
    private void activate(int index,boolean settings){
        if(index<0||index>=results.size())return;
        GuiSearch.Hit hit=results.get(index);Module module=GuiState.module(hit.module());if(module==null)return;
        if(settings||hit.isSetting())minecraft.setScreen(new ModuleSettingsScreen(this,module,hit.setting()));else module.toggle();
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubled){
        if(super.mouseClicked(event,doubled))return true;
        int y=top+45;
        if(UiDraw.over(event.x(),event.y(),left+7,y,panelWidth-14,viewHeight)&&(event.button()==0||event.button()==1)){
            selected=((int)event.y()-y+scroll)/ROW;activate(selected,event.button()==1);return true;
        }
        return false;
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){
        if(UiDraw.over(mx,my,left,top,panelWidth,viewHeight+73)){scroll=Math.clamp(scroll-(int)(vertical*ROW),0,Math.max(0,results.size()*ROW-viewHeight));return true;}return false;
    }
    @Override public boolean keyPressed(KeyEvent event){
        if(event.key()==264||event.key()==265){
            selected=Math.clamp(selected+(event.key()==264?1:-1),0,Math.max(0,results.size()-1));
            if(selected*ROW<scroll)scroll=selected*ROW;
            if((selected+1)*ROW>scroll+viewHeight)scroll=(selected+1)*ROW-viewHeight;
            return true;
        }
        if(event.key()==257||event.key()==335){activate(selected,(event.modifiers()&1)!=0);return true;}
        if((event.modifiers()&2)!=0&&event.key()==70){setInitialFocus(input);return true;}
        return super.keyPressed(event);
    }
}
