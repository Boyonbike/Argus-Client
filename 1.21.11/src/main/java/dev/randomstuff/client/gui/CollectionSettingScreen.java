package dev.randomstuff.client.gui;

import dev.randomstuff.client.settings.SetSetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import java.util.*;

/** Entry-level selection for block/item/entity/string sets; never parses a set's display text. */
final class CollectionSettingScreen extends UiScreen {
    private final SetSetting<?> setting;
    private UiInput query;
    private List<String> entries=List.of();
    private Set<String> selected=Set.of();
    private int left,top,panelWidth,viewHeight,scroll;
    private String message="";
    CollectionSettingScreen(Screen parent,SetSetting<?> setting){super(setting.getName(),parent,"Modules");this.setting=setting;}
    @Override protected void buildContent(){
        panelWidth=Math.min(430,width-24);left=(width-panelWidth)/2;top=46;viewHeight=Math.max(32,height-top-112);
        String text=query==null?GuiSettings.editText(setting):query.getValue();
        query=new UiInput(font,left+12,top+38,panelWidth-108,18,"Search or enter one entry...");query.setValue(text);
        query.setResponder(value->{scroll=0;refresh();});addRenderableWidget(query);setInitialFocus(query);
        addRenderableWidget(new UiButton(left+panelWidth-92,top+35,80,24,"Add / remove",this::toggleTyped));refresh();
    }
    private void refresh(){
        selected=new HashSet<>(setting.getFormattedValues());Set<String> all=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        all.addAll(setting.getFormattedAvailableEntries());all.addAll(selected);String filter=query.getValue().toLowerCase(Locale.ROOT);
        entries=all.stream().filter(name->name.toLowerCase(Locale.ROOT).contains(filter)).toList();
    }
    private void toggle(String entry){
        try{GuiSettings.toggleCollectionEntry(setting,entry);message="";refresh();}
        catch(RuntimeException failure){message="That entry is not recognised. Use a name from the list.";}
    }
    private void toggleTyped(){toggle(query.getValue());}
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        backdrop(g);UiTheme t=GuiState.theme();UiDraw.panel(g,left,top,panelWidth,height-top-24,t);
        UiDraw.text(g,font,UiDraw.fit(font,GuiState.title(setting.getName()),panelWidth-100),left+12,top+13,t.text());
        UiDraw.text(g,font,selected.size()+" selected",left+panelWidth-85,top+13,t.accent());
        int start=top+70;scroll=Math.clamp(scroll,0,Math.max(0,entries.size()*23-viewHeight));
        g.enableScissor(left+6,start,left+panelWidth-6,start+viewHeight);
        for(int i=0;i<entries.size();i++){
            int y=start+i*23-scroll;if(y+23<start||y>start+viewHeight)continue;String entry=entries.get(i);
            boolean on=selected.contains(entry),hover=UiDraw.over(mx,my,left+8,y,panelWidth-16,23);
            if(on||hover)UiDraw.rounded(g,left+8,y+1,panelWidth-16,21,4,on?UiDraw.alpha(t.accent(),42):t.surface());
            UiDraw.rounded(g,left+14,y+7,9,9,2,on?t.accent():t.overlay());
            UiDraw.text(g,font,UiDraw.fit(font,entry,panelWidth-48),left+32,y+7,on?t.accent():t.text());
        }
        if(entries.isEmpty())UiDraw.center(g,font,"No matching entries. You can enter one above.",left,start+12,panelWidth,t.muted());
        g.disableScissor();
        UiDraw.text(g,font,UiDraw.fit(font,message.isEmpty()?"Click an entry to add or remove it.":message,panelWidth-24),left+12,height-35,message.isEmpty()?t.muted():t.red());
        chrome(g,mx,my,delta);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubled){
        if(super.mouseClicked(event,doubled))return true;
        if(event.button()!=0||!UiDraw.over(event.x(),event.y(),left+8,top+70,panelWidth-16,viewHeight))return false;
        int row=((int)event.y()-top-70+scroll)/23;if(row>=0&&row<entries.size())toggle(entries.get(row));return true;
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){
        if(UiDraw.over(mx,my,left,top+70,panelWidth,viewHeight)){scroll=Math.clamp(scroll-(int)(vertical*46),0,Math.max(0,entries.size()*23-viewHeight));return true;}return false;
    }
    @Override public boolean keyPressed(KeyEvent event){if(event.key()==257||event.key()==335){toggleTyped();return true;}return super.keyPressed(event);}
}
