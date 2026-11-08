package dev.randomstuff.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

final class AppearanceScreen extends UiScreen {
    private int left,top,panelWidth;
    AppearanceScreen(Screen parent){super("Appearance",parent,"Theme");}
    @Override protected void buildContent(){
        panelWidth=Math.min(360,width-24);left=(width-panelWidth)/2;top=42;
        var p=GuiState.preferences();
        addRenderableWidget(new UiButton(left+14,top+158,29,23,"−",()->{p.uiScale=Math.max(.65,Math.round((p.uiScale-.1)*100)/100.0);GuiState.save();rebuildWidgets();}));
        addRenderableWidget(new UiButton(left+48,top+158,panelWidth-96,23,"GUI scale: "+Math.round(p.uiScale*100)+"%",()->{p.uiScale=1;GuiState.save();rebuildWidgets();}));
        addRenderableWidget(new UiButton(left+panelWidth-43,top+158,29,23,"+",()->{p.uiScale=Math.min(1.4,Math.round((p.uiScale+.1)*100)/100.0);GuiState.save();rebuildWidgets();}));
        addRenderableWidget(new UiButton(left+14,top+28,(panelWidth-34)/2,24,"Dark",p.mode==GuiPreferences.Mode.DARK,()->setMode(GuiPreferences.Mode.DARK)));
        addRenderableWidget(new UiButton(left+20+(panelWidth-34)/2,top+28,(panelWidth-34)/2,24,"Light",p.mode==GuiPreferences.Mode.LIGHT,()->setMode(GuiPreferences.Mode.LIGHT)));
        int itemWidth=(panelWidth-38)/3;
        for(int i=0;i<GuiPreferences.Accent.values().length;i++){
            GuiPreferences.Accent accent=GuiPreferences.Accent.values()[i];
            addRenderableWidget(new UiButton(left+14+(i%3)*(itemWidth+5),top+78+(i/3)*24,itemWidth,24,GuiState.title(accent.name().toLowerCase()),p.accent==accent,()->{p.accent=accent;GuiState.save();rebuildWidgets();}));
        }
        addRenderableWidget(new UiButton(left+14,top+131,(panelWidth-34)/2,23,"Grid snap: "+(p.snap?"On":"Off"),()->{p.snap=!p.snap;GuiState.save();rebuildWidgets();}));
        addRenderableWidget(new UiButton(left+20+(panelWidth-34)/2,top+131,(panelWidth-34)/2,23,"Reset positions",()->{p.windows.clear();GuiState.save();minecraft.setScreen(new ModulesScreen(rootParent()));}));
    }
    private void setMode(GuiPreferences.Mode mode){GuiState.preferences().mode=mode;GuiState.save();rebuildWidgets();}
    @Override protected void renderUi(GuiGraphics g,int mx,int my,float delta){
        backdrop(g);UiTheme t=GuiState.theme();UiDraw.panel(g,left,top,panelWidth,184,t);
        UiDraw.text(g,font,"Appearance",left+14,top+13,t.text());

        UiDraw.text(g,font,"Accent colour",left+14,top+62,t.muted());
        chrome(g,mx,my,delta);
    }
}
