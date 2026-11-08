package dev.randomstuff.client.gui;

import dev.randomstuff.client.modules.Module;
import dev.randomstuff.client.modules.Keybind;
import dev.randomstuff.client.settings.Setting;
import dev.randomstuff.client.settings.ColorSetting;
import dev.randomstuff.client.settings.SetSetting;
import dev.randomstuff.client.settings.NamedEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import java.awt.Color;
import java.util.*;

/** Uses the actual client Setting API; no shadow copies of module configuration. */
final class ModuleSettingsScreen extends UiScreen {
    private final Module module;
    private final String initialSetting;
    private int left,top,panelWidth,scroll,viewHeight;
    private final List<Setting<?>> visible=new ArrayList<>();
    private UiInput editor;
    private Setting<?> editing;
    private boolean binding;
    private String message="";
    private boolean revealedGroups;
    private static final int ROW=25;
    ModuleSettingsScreen(Screen parent,Module module,String setting){super(module.getName(),parent,"Modules");this.module=module;initialSetting=setting;}
    @Override protected void buildContent(){
        panelWidth=Math.min(430,width-24);left=(width-panelWidth)/2;top=46;viewHeight=Math.max(40,height-top-(editing==null?115:150));
        if(initialSetting!=null&&!revealedGroups){GuiSettings.expandGroups(module.getSettings());revealedGroups=true;}
        visible.clear();visible.addAll(GuiSettings.rows(module.getSettings(),initialSetting));
        if(initialSetting!=null&&scroll==0){for(int i=0;i<visible.size();i++)if(visible.get(i).getName().equals(initialSetting)){scroll=i*ROW;break;}}
        addRenderableWidget(new UiButton(left+panelWidth-100,top+10,88,22,module.isToggled()?"Enabled":"Disabled",module.isToggled(),()->{module.toggle();rebuildWidgets();}));
        addRenderableWidget(new UiButton(left+12,top+43,panelWidth-24,22,binding?"Press a key · Esc cancels · Delete clears":"Keybind: "+module.getKeybind().getDisplayName(),()->{binding=true;rebuildWidgets();}));
        if(editing!=null){
            editor=new UiInput(font,left+12,height-57,panelWidth-85,17,"Value");editor.setMaxLength(1024);editor.setValue(value(editing));addRenderableWidget(editor);setInitialFocus(editor);
            addRenderableWidget(new UiButton(left+panelWidth-68,height-59,56,22,"Apply",this::apply));
        }
    }
    private static String value(Setting<?> setting){
        Object value=setting.getValue();
        if(value instanceof NamedEntry named)return named.getName();
        if(value instanceof Color colour)return String.format("#%08X",colour.getRGB());
        if(setting instanceof SetSetting<?> collection)return collection.getValue().size()+" entries";
        return GuiSettings.editText(setting);
    }
    private int maxScroll(){return Math.max(0,visible.size()*ROW-viewHeight);}
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        backdrop(g);UiTheme t=GuiState.theme();UiDraw.panel(g,left,top,panelWidth,height-top-24,t);
        UiDraw.text(g,font,UiDraw.fit(font,GuiState.title(module.getName()),panelWidth-122),left+12,top+15,t.text());
        UiDraw.text(g,font,UiDraw.fit(font,module.getDescription()==null?"":module.getDescription(),panelWidth-24),left+12,top+31,t.muted());
        scroll=Math.clamp(scroll,0,maxScroll());int start=top+74;
        g.enableScissor(left+6,start,left+panelWidth-6,start+viewHeight);
        for(int i=0;i<visible.size();i++){
            Setting<?> setting=visible.get(i);int y=start+i*ROW-scroll;
            if(y+ROW<start||y>start+viewHeight)continue;
            boolean hover=UiDraw.over(mx,my,left+8,y,panelWidth-16,ROW)&&my>=start&&my<start+viewHeight;
            boolean focus=editing==setting||Objects.equals(initialSetting,setting.getName());
            if(hover||focus)UiDraw.rounded(g,left+8,y+1,panelWidth-16,ROW-3,4,focus?UiDraw.alpha(t.accent(),38):t.surface());
            int half=(panelWidth-28)/2;
            UiDraw.text(g,font,UiDraw.fit(font,(setting.visibilityDependency&&!setting.isGroupOnly()?(setting.isExpanded()?"− ":"+ "):"")+GuiState.title(setting.getName()),half-8),left+14,y+8,t.text());
            Object current=setting.getValue();
            if(setting.isGroupOnly()){
                UiDraw.text(g,font,setting.isExpanded()?"Collapse −":"Expand +",left+panelWidth-80,y+8,t.accent());
            }else if(!setting.isVisible()){
                UiDraw.text(g,font,"Unavailable",left+panelWidth-80,y+8,t.muted());
            }else if(current instanceof Boolean enabled){
                UiDraw.rounded(g,left+panelWidth-41,y+7,24,12,6,enabled?t.accent():t.overlay());
                UiDraw.rounded(g,left+panelWidth-(enabled?26:39),y+9,8,8,4,enabled?t.crust():t.muted());
            }else{
                String text=UiDraw.fit(font,value(setting),half-12);
                UiDraw.text(g,font,text,left+panelWidth-16-UiDraw.width(font,text),y+8,focus?t.accent():t.muted());
            }
        }
        if(visible.isEmpty())UiDraw.center(g,font,"No visible settings for this module.",left,start+10,panelWidth,t.muted());
        g.disableScissor();
        if(!message.isEmpty())UiDraw.text(g,font,UiDraw.fit(font,message,panelWidth-24),left+12,height-32,t.red());
        else if(editing==null)UiDraw.text(g,font,UiDraw.fit(font,"+ labels expand · Click values to edit · Right click resets",panelWidth-24),left+12,height-33,t.muted());
        chrome(g,mx,my,delta);
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private void change(Setting setting,Object next){setting.setValue(next);editing=null;message="";rebuildWidgets();}
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubled){
        if(binding){
            if(event.button()>=0){module.setKeybind(new Keybind(event.button(),module.getKeybind().mode(),true));binding=false;rebuildWidgets();return true;}
        }
        if(super.mouseClicked(event,doubled))return true;
        int start=top+74;
        if(!UiDraw.over(event.x(),event.y(),left+8,start,panelWidth-16,viewHeight))return false;
        int i=((int)event.y()-start+scroll)/ROW;if(i<0||i>=visible.size())return false;
        Setting<?> setting=visible.get(i);Object current=setting.getValue();message="";
        if(setting.isGroupOnly()){setting.expanded=!setting.isExpanded();editing=null;rebuildWidgets();return true;}
        if(!setting.isVisible()){message="Enable the parent option to edit this setting.";return true;}
        if(setting.visibilityDependency&&event.button()==0&&event.x()<left+panelWidth/2){setting.expanded=!setting.isExpanded();editing=null;rebuildWidgets();return true;}
        if(event.button()==1){setting.reset();editing=null;rebuildWidgets();return true;}
        if(event.button()!=0)return false;
        if(setting instanceof SetSetting<?> collection)minecraft.setScreen(new CollectionSettingScreen(this,collection));
        else if(current instanceof Boolean enabled)change(setting,!enabled);
        else if(current instanceof Enum<?> choice){Object[] choices=choice.getDeclaringClass().getEnumConstants();change(setting,choices[(choice.ordinal()+1)%choices.length]);}
        else{editing=setting;rebuildWidgets();}
        return true;
    }
    private void apply(){
        if(editing==null||editor==null)return;String input=editor.getValue().trim();
        try{
            Object old=editing.getValue();
            if(old instanceof Number){
                double number=Double.parseDouble(input);if(!Double.isFinite(number))throw new IllegalArgumentException();
                if(old instanceof Float&&!Float.isFinite(Float.parseFloat(input)))throw new IllegalArgumentException();
                if(editing.isNumericBoundsEnabled()){
                    Object minimum=editing.getMinimumValue(),maximum=editing.getMaximumValue();
                    if(minimum instanceof Number min&&number<min.doubleValue()||maximum instanceof Number max&&number>max.doubleValue())throw new IllegalArgumentException();
                }
            }
            if(editing instanceof ColorSetting colour){
                String hex=input.startsWith("#")?input.substring(1):input;
                if(hex.length()!=6&&hex.length()!=8)throw new IllegalArgumentException();
                long bits=Long.parseUnsignedLong(hex,16);colour.setColor(new Color((int)(hex.length()==6?bits|0xff000000L:bits),true));
            }else editing.parseValue(input);
            editing=null;message="";rebuildWidgets();
        }catch(RuntimeException failure){message="Invalid value. Check the format or allowed range.";}
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){
        if(UiDraw.over(mx,my,left,top+74,panelWidth,viewHeight)){scroll=Math.clamp(scroll-(int)(vertical*ROW*2),0,maxScroll());return true;}return false;
    }
    @Override public boolean keyPressed(KeyEvent event){
        if(binding){
            if(event.key()!=256)module.setKeybind(event.key()==259||event.key()==261?Keybind.UNBOUND:new Keybind(event.key(),module.getKeybind().mode(),false));
            binding=false;rebuildWidgets();return true;
        }
        if(editor!=null&&editing!=null&&(event.key()==257||event.key()==335)){apply();return true;}
        return super.keyPressed(event);
    }
}
