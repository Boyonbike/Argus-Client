package dev.randomstuff.client.gui;

import dev.randomstuff.client.modules.Module;
import dev.randomstuff.client.modules.ModuleCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import java.util.*;

/** Category, favourites and search panels share the same draggable dropdown-menu canvas. */
public final class ModulesScreen extends UiScreen {
    private static final int WIDTH=112, HEADER=20, ROW=15, SEARCH_ROW=26, SEARCH_INPUT=30;
    private enum Kind { CATEGORY, FAVOURITES, SEARCH }
    private static final class Window {
        final String name;
        final ModuleCategory category;
        final Kind kind;
        int x,y,scroll,width=WIDTH;
        boolean collapsed;
        Window(String name,ModuleCategory category,Kind kind){this.name=name;this.category=category;this.kind=kind;}
    }
    private final List<Window> windows=new ArrayList<>();
    private final Map<String,Float> highlights=new HashMap<>();
    private List<Module> all=List.of();
    private List<GuiSearch.Entry> searchIndex=List.of();
    private List<GuiSearch.Hit> results=List.of();
    private UiInput searchInput;
    private Window searchWindow,dragging;
    private int dragX,dragY,canvasScroll;
    private long lastFrame=System.nanoTime();
    private boolean focusOnOpen;

    public ModulesScreen(Screen parent){super("Random Stuff Client",parent,"Modules");}
    ModulesScreen(Screen parent,boolean focusSearch){this(parent);focusOnOpen=focusSearch;}

    @Override protected void buildContent(){
        String query=searchInput==null?"":searchInput.getValue();
        all=GuiState.modules();windows.clear();
        for(ModuleCategory category:ModuleCategory.values())if(category!=ModuleCategory.HUD)
            windows.add(new Window(GuiState.title(category.getName()),category,Kind.CATEGORY));
        windows.add(new Window("Favourites",null,Kind.FAVOURITES));
        searchWindow=new Window("Search",null,Kind.SEARCH);
        searchWindow.width=Math.min(192,Math.max(144,(int)(width*.4)));
        int categoryArea=width-searchWindow.width-18;
        int[] heights=new int[windows.size()];
        for(int i=0;i<heights.length;i++)heights[i]=panelHeight(windows.get(i));
        int[][] positions=CategoryLayout.arrange(categoryArea,heights);
        for(int i=0;i<windows.size();i++)restore(windows.get(i),positions[i][0],positions[i][1]);
        restore(searchWindow,width-searchWindow.width-10,64);windows.add(searchWindow);
        searchIndex=all.stream().map(module->new GuiSearch.Entry(module.getName(),module.getCategory().getName(),
                Arrays.asList(module.getAliases()),module.getSettings().stream().filter(setting->!setting.isGroupOnly())
                .map(setting->setting.getName()).distinct().toList())).toList();
        searchInput=new UiInput(font,searchWindow.x+7,searchWindow.y+HEADER+7,searchWindow.width-14,16,"Search modules or settings...");
        searchInput.setValue(query);searchInput.setResponder(value->refreshSearch());addWidget(searchInput);refreshSearch();
        if(focusOnOpen){focusSearch();focusOnOpen=false;}
    }
    private void restore(Window window,int x,int y){
        var saved=GuiState.preferences().windows.get(window.name);
        window.x=saved==null?x:Math.clamp(saved.x(),10,Math.max(10,width-window.width-10));
        window.y=saved==null?y:Math.max(48,saved.y());window.collapsed=saved!=null&&saved.collapsed();window.scroll=saved==null?0:saved.scroll();
    }
    private List<Module> modules(Window window){
        return all.stream().filter(module->window.kind==Kind.FAVOURITES?GuiState.preferences().favourites.contains(module.getName()):module.getCategory()==window.category).toList();
    }
    private void refreshSearch(){results=GuiSearch.search(searchIndex,searchInput.getValue());searchWindow.scroll=0;}
    private int viewHeight(Window window){
        if(window.kind==Kind.SEARCH)return Math.min(Math.max(48,results.size()*SEARCH_ROW),Math.max(48,Math.min(260,height-154)));
        return Math.min(Math.max(ROW,modules(window).size()*ROW),Math.max(48,height-112));
    }
    private int panelHeight(Window window){return window.collapsed?HEADER:HEADER+viewHeight(window)+(window.kind==Kind.SEARCH?SEARCH_INPUT:0)+6;}
    private int y(Window window){return window.y-canvasScroll;}
    private Window at(double mx,double my){
        if(my<42||my>=height-20)return null;
        for(int i=windows.size()-1;i>=0;i--){Window window=windows.get(i);if(UiDraw.over(mx,my,window.x,y(window),window.width,panelHeight(window)))return window;}
        return null;
    }
    void focusSearch(){
        searchWindow.collapsed=false;canvasScroll=Math.max(0,searchWindow.y-64);
        searchInput.visible=true;searchInput.active=true;setInitialFocus(searchInput);
    }
    @Override protected void renderUi(GuiGraphics g,int mx,int my,float delta){
        backdrop(g);UiTheme theme=GuiState.theme();
        int bottom=windows.stream().mapToInt(window->window.y+panelHeight(window)).max().orElse(height);
        canvasScroll=Math.clamp(canvasScroll,0,Math.max(0,bottom-height+28));
        float elapsed=(float)Math.min(.1,(System.nanoTime()-lastFrame)/1e9);lastFrame=System.nanoTime();
        Window over=at(mx,my);Module hovered=null;
        g.enableScissor(0,42,width,height-19);
        searchInput.setX(searchWindow.x+14);searchInput.setY(y(searchWindow)+HEADER+7);
        searchInput.visible=!searchWindow.collapsed&&searchInput.getY()+searchInput.getHeight()+3>42&&searchInput.getY()-3<height-19;
        searchInput.active=searchInput.visible;
        if(!searchInput.visible&&getFocused()==searchInput)setFocused(null);
        for(Window window:windows){
            int top=y(window),panelHeight=panelHeight(window);
            if(top+panelHeight<42||top>height-19)continue;
            UiDraw.panel(g,window.x,top,window.width,panelHeight,theme);
            UiDraw.rounded(g,window.x,top,window.width,HEADER,6,theme.crust());
            if(!window.collapsed)g.fill(window.x,top+HEADER-6,window.x+window.width,top+HEADER,theme.crust());
            String icon=window.kind==Kind.SEARCH?"search":window.kind==Kind.FAVOURITES?"favourites":window.category.name().toLowerCase(Locale.ROOT);
            UiDraw.icon(g,icon,window.x+7,top+6,9,theme.accent());
            UiDraw.text(g,font,window.name,window.x+21,top+6,theme.text());
            UiDraw.text(g,font,window.collapsed?"+":"−",window.x+window.width-12,top+6,theme.muted());
            if(window.collapsed||top+HEADER>=height-19)continue;
            if(window.kind==Kind.SEARCH){drawSearch(g,window,mx,my,delta,over==window,theme);continue;}
            List<Module> modules=modules(window);int view=viewHeight(window);
            window.scroll=Math.clamp(window.scroll,0,Math.max(0,modules.size()*ROW-view));
            g.enableScissor(window.x+3,Math.max(42,top+HEADER),window.x+window.width-3,Math.min(height-19,top+panelHeight-4));
            for(int i=0;i<modules.size();i++){
                Module module=modules.get(i);int rowY=top+HEADER+i*ROW-window.scroll;
                if(rowY+ROW<top+HEADER||rowY>top+panelHeight-4)continue;
                boolean hover=over==window&&my>=top+HEADER&&UiDraw.over(mx,my,window.x+3,rowY,window.width-6,ROW);
                float current=highlights.getOrDefault(module.getName(),module.isToggled()?1f:0f);
                current+=((module.isToggled()?1:0)-current)*Math.min(1,elapsed*14);highlights.put(module.getName(),current);
                if(current>.005f||hover)UiDraw.rounded(g,window.x+3,rowY+1,window.width-6,ROW-1,3,UiDraw.alpha(theme.accent(),(int)(current*58)+(hover?22:0)));
                if(current>.01f)UiDraw.rounded(g,window.x+5,rowY+3,2,ROW-5,1,UiDraw.alpha(theme.accent(),(int)(255*current)));
                UiDraw.center(g,font,UiDraw.fit(font,GuiState.title(module.getName()),window.width-20),window.x+7,rowY+3,window.width-14,UiDraw.mix(theme.text(),theme.accent(),current));
                if(hover)hovered=module;
            }
            if(modules.isEmpty())UiDraw.center(g,font,UiDraw.fit(font,"Middle click to add",window.width-12),window.x+2,top+HEADER+4,window.width-4,theme.muted());
            g.disableScissor();scrollbar(g,window,top+HEADER,view,modules.size()*ROW,theme);
        }
        g.disableScissor();chrome(g,mx,my,delta);
        if(hovered!=null)tooltip(g,hovered,mx,my,theme);
    }
    private void drawSearch(GuiGraphics g,Window window,int mx,int my,float delta,boolean over,UiTheme theme){
        int top=y(window);searchInput.setX(window.x+14);searchInput.setY(top+HEADER+7);searchInput.render(g,mx,my,delta);
        int start=top+HEADER+SEARCH_INPUT,view=viewHeight(window);
        window.scroll=Math.clamp(window.scroll,0,Math.max(0,results.size()*SEARCH_ROW-view));
        if(start>=height-19)return;
        g.enableScissor(window.x+4,Math.max(42,start),window.x+window.width-4,Math.min(height-19,start+view));
        for(int i=0;i<results.size();i++){
            int rowY=start+i*SEARCH_ROW-window.scroll;if(rowY+SEARCH_ROW<start||rowY>start+view)continue;
            var hit=results.get(i);boolean hover=over&&my>=start&&UiDraw.over(mx,my,window.x+4,rowY,window.width-8,SEARCH_ROW);
            if(hover)UiDraw.rounded(g,window.x+4,rowY+1,window.width-8,SEARCH_ROW-2,3,theme.surface());
            int colour=hit.isSetting()?theme.blue():hit.alias()!=null?theme.yellow():theme.green();
            UiDraw.rounded(g,window.x+8,rowY+5,9,9,2,UiDraw.alpha(colour,100));
            String title=GuiState.title(hit.isSetting()?hit.setting():hit.alias()!=null?hit.alias():hit.module());
            UiDraw.text(g,font,UiDraw.fit(font,title,window.width-30),window.x+22,rowY+3,colour);
            String path=hit.isSetting()?GuiState.title(hit.module()):hit.alias()!=null?"Alias: "+GuiState.title(hit.module()):GuiState.title(hit.category());
            UiDraw.text(g,font,UiDraw.fit(font,path,window.width-18),window.x+9,rowY+15,theme.muted());
        }
        if(results.isEmpty()){
            UiDraw.center(g,font,searchInput.getValue().isBlank()?"Type to search":"No matches",window.x,start+8,window.width,theme.muted());
            UiDraw.center(g,font,"Modules · Aliases · Settings",window.x,start+24,window.width,theme.muted());
        }
        g.disableScissor();scrollbar(g,window,start,view,results.size()*SEARCH_ROW,theme);
    }
    private void scrollbar(GuiGraphics g,Window window,int start,int view,int total,UiTheme theme){
        if(total<=view)return;int track=Math.max(4,view*view/total),offset=(view-track)*window.scroll/Math.max(1,total-view);
        UiDraw.rounded(g,window.x+window.width-3,start+offset,2,track,1,UiDraw.alpha(theme.accent(),100));
    }
    private void tooltip(GuiGraphics g,Module module,int mx,int my,UiTheme theme){
        String description=module.getDescription()==null?"":module.getDescription();
        String[] lines={GuiState.title(module.getName()),description,"Left: toggle   Right: settings   Middle: favourite"};
        int tooltipWidth=Math.min(width-12,Math.max(180,Arrays.stream(lines).mapToInt(line->UiDraw.width(font,line)).max().orElse(180))+12);
        int x=Math.clamp(mx+12,6,Math.max(6,width-tooltipWidth-6)),top=Math.min(my+15,height-61);
        UiDraw.panel(g,x,top,tooltipWidth,49,theme);
        for(int i=0;i<lines.length;i++)UiDraw.text(g,font,UiDraw.fit(font,lines[i],tooltipWidth-12),x+6,top+6+i*13,i==0?theme.accent():theme.muted());
    }
    @Override protected boolean mouseClickedUi(MouseButtonEvent event,boolean doubled){
        Window window=at(event.x(),event.y());
        if(window==null){
            // This manually rendered child must never intercept navigation through a clipped/stale box.
            boolean active=searchInput.active;searchInput.active=false;
            try{return super.mouseClickedUi(event,doubled);}finally{searchInput.active=active;}
        }
        if(window==searchWindow&&!window.collapsed&&searchInput.isMouseOver(event.x(),event.y())&&event.y()>=y(window)+HEADER){
            setFocused(searchInput);setDragging(true);return searchInput.mouseClicked(event,doubled);
        }
        if(window!=searchWindow)setFocused(null);
        if(event.y()<y(window)+HEADER){
            if(event.button()==1||event.x()>=window.x+window.width-17){window.collapsed=!window.collapsed;if(window==searchWindow&&window.collapsed)setFocused(null);}
            else if(event.button()==0){dragging=window;dragX=(int)event.x()-window.x;dragY=(int)event.y()-y(window);windows.remove(window);windows.add(window);}
            remember();return true;
        }
        if(window.collapsed)return true;
        if(window.kind==Kind.SEARCH){
            int start=y(window)+HEADER+SEARCH_INPUT;
            if(event.y()<start)return true;
            int row=((int)event.y()-start+window.scroll)/SEARCH_ROW;
            if(row>=0&&row<results.size()){
                var hit=results.get(row);Module module=GuiState.module(hit.module());if(module==null)return true;
                if(event.button()==1||hit.isSetting())minecraft.setScreen(new ModuleSettingsScreen(this,module,hit.setting()));
                else if(event.button()==0)module.toggle();
            }
            return true;
        }
        int row=((int)event.y()-y(window)-HEADER+window.scroll)/ROW;List<Module> list=modules(window);
        if(row>=0&&row<list.size()){
            Module module=list.get(row);
            if(event.button()==0)module.toggle();else if(event.button()==1)minecraft.setScreen(new ModuleSettingsScreen(this,module,null));
            else if(event.button()==2){var favourites=GuiState.preferences().favourites;if(!favourites.add(module.getName()))favourites.remove(module.getName());GuiState.save();}
        }
        return true;
    }
    @Override protected boolean mouseDraggedUi(MouseButtonEvent event,double dx,double dy){
        if(dragging==null)return super.mouseDraggedUi(event,dx,dy);
        int x=(int)event.x()-dragX,top=(int)event.y()-dragY+canvasScroll;
        if(GuiState.preferences().snap){x=Math.round(x/8f)*8;top=Math.round(top/8f)*8;}
        dragging.x=Math.clamp(x,10,Math.max(10,width-dragging.width-10));dragging.y=Math.max(48,top);remember();return true;
    }
    @Override protected boolean mouseReleasedUi(MouseButtonEvent event){dragging=null;remember();GuiState.save();return super.mouseReleasedUi(event);}
    @Override protected boolean mouseScrolledUi(double mx,double my,double horizontal,double vertical){
        Window window=at(mx,my);
        if(window!=null&&!window.collapsed){
            int total=window.kind==Kind.SEARCH?results.size()*SEARCH_ROW:modules(window).size()*ROW;
            int previous=window.scroll;window.scroll=Math.clamp(window.scroll-(int)(vertical*30),0,Math.max(0,total-viewHeight(window)));
            if(previous!=window.scroll){remember();return true;}
        }
        canvasScroll=Math.max(0,canvasScroll-(int)(vertical*24));remember();return true;
    }
    private void remember(){for(Window window:windows)GuiState.preferences().windows.put(window.name,new GuiPreferences.Window(window.x,window.y,window.collapsed,window.scroll));}
    @Override public void removed(){remember();super.removed();}
}
