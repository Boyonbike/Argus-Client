package dev.randomstuff.client.gui;

import dev.randomstuff.client.modules.Module;
import dev.randomstuff.client.modules.ModuleCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import java.util.*;

/** Floating, clipped, individually scrollable module windows with a shared search tab. */
public final class ModulesScreen extends UiScreen {
    private static final int WIDTH=112, HEADER=21, ROW=16;
    private static final class Window {
        String name; ModuleCategory category; int x,y,scroll; boolean collapsed;
        Window(String name,ModuleCategory category){this.name=name;this.category=category;}
    }
    private final List<Window> windows=new ArrayList<>();
    private final Map<String,Float> highlights=new HashMap<>();
    private List<Module> all=List.of();
    private Window dragging;
    private int dragX,dragY,canvasScroll;
    private long lastFrame=System.nanoTime();
    public ModulesScreen(Screen parent){super("Random Stuff Client",parent,"Modules");}
    @Override protected void buildContent(){
        all=GuiState.modules();windows.clear();
        for(ModuleCategory category:ModuleCategory.values()) if(category!=ModuleCategory.HUD) windows.add(new Window(GuiState.title(category.getName()),category));
        windows.add(new Window("Favourites",null));
        int[] heights=new int[windows.size()];
        for(int i=0;i<heights.length;i++)heights[i]=Math.min(HEADER+modules(windows.get(i)).size()*ROW+8,Math.max(76,height-90));
        int[][] positions=CategoryLayout.arrange(width,heights);
        for(int i=0;i<windows.size();i++){
            Window w=windows.get(i);GuiPreferences.Window saved=GuiState.preferences().windows.get(w.name);
            w.x=saved==null?positions[i][0]:Math.clamp(saved.x(),10,Math.max(10,width-WIDTH-10));
            w.y=saved==null?positions[i][1]:Math.max(48,saved.y());
            w.collapsed=saved!=null&&saved.collapsed();w.scroll=saved==null?0:saved.scroll();
        }
    }
    private List<Module> modules(Window window){
        return all.stream().filter(module->window.category==null?GuiState.preferences().favourites.contains(module.getName()):module.getCategory()==window.category).toList();
    }
    private int height(Window window){return window.collapsed?HEADER:HEADER+Math.min(Math.max(ROW,modules(window).size()*ROW),Math.max(48,height-115))+7;}
    private int y(Window w){return w.y-canvasScroll;}
    private Window at(double mx,double my){
        if(my<42||my>=height-20)return null;
        for(int i=windows.size()-1;i>=0;i--){Window w=windows.get(i);if(UiDraw.over(mx,my,w.x,y(w),WIDTH,height(w)))return w;}
        return null;
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        backdrop(g);UiTheme t=GuiState.theme();
        int bottom=windows.stream().mapToInt(w->w.y+height(w)).max().orElse(height);
        canvasScroll=Math.clamp(canvasScroll,0,Math.max(0,bottom-height+28));
        float elapsed=(float)Math.min(.1,(System.nanoTime()-lastFrame)/1e9);lastFrame=System.nanoTime();
        Window over=at(mx,my);Module hovered=null;
        g.enableScissor(0,42,width,height-19);
        for(Window w:windows){
            int y=y(w),h=height(w);List<Module> modules=modules(w);
            if(y+h<42||y>height-19)continue;
            UiDraw.panel(g,w.x,y,WIDTH,h,t);
            UiDraw.rounded(g,w.x,y,WIDTH,HEADER,6,t.crust());
            if(!w.collapsed)g.fill(w.x,y+HEADER-6,w.x+WIDTH,y+HEADER,t.crust());
            // Category mark and disclosure triangle, drawn as geometry rather than borrowed icons.
            UiDraw.rounded(g,w.x+7,y+7,7,7,2,UiDraw.alpha(t.accent(),160));
            UiDraw.text(g,font,w.name,w.x+19,y+6,t.text());
            UiDraw.text(g,font,w.collapsed?"+":"−",w.x+WIDTH-12,y+6,t.muted());
            if(w.collapsed||y+HEADER>=height-19)continue;
            int view=h-HEADER-7;w.scroll=Math.clamp(w.scroll,0,Math.max(0,modules.size()*ROW-view));
            g.enableScissor(w.x+3,Math.max(42,y+HEADER),w.x+WIDTH-3,Math.min(height-19,y+h-4));
            for(int i=0;i<modules.size();i++){
                Module module=modules.get(i);int ry=y+HEADER+i*ROW-w.scroll;
                if(ry+ROW<y+HEADER||ry>y+h-4)continue;
                boolean hover=over==w&&my>=y+HEADER&&UiDraw.over(mx,my,w.x+3,ry,WIDTH-6,ROW);
                float current=highlights.getOrDefault(module.getName(),module.isToggled()?1f:0f);
                current+=( (module.isToggled()?1:0)-current)*Math.min(1,elapsed*14);highlights.put(module.getName(),current);
                if(current>.005f||hover)UiDraw.rounded(g,w.x+3,ry+1,WIDTH-6,ROW-1,3,UiDraw.alpha(t.accent(),(int)(current*58)+(hover?22:0)));
                if(current>.01f)UiDraw.rounded(g,w.x+5,ry+3,2,ROW-5,1,UiDraw.alpha(t.accent(),(int)(255*current)));
                String label=UiDraw.fit(font,GuiState.title(module.getName()),WIDTH-20);
                UiDraw.center(g,font,label,w.x+7,ry+4,WIDTH-14,UiDraw.mix(t.text(),t.accent(),current));
                if(hover)hovered=module;
            }
            if(modules.isEmpty())UiDraw.center(g,font,"Middle click to add",w.x+2,y+HEADER+5,WIDTH-4,t.muted());
            g.disableScissor();
            if(modules.size()*ROW>view){
                int track=Math.max(4,view*view/(modules.size()*ROW));int offset=(view-track)*w.scroll/Math.max(1,modules.size()*ROW-view);
                UiDraw.rounded(g,w.x+WIDTH-3,y+HEADER+offset,2,track,1,UiDraw.alpha(t.accent(),100));
            }
        }
        g.disableScissor();chrome(g,mx,my,delta);
        if(hovered!=null){
            String hint=hovered.getDescription();if(hint==null)hint="";
            String[] lines={GuiState.title(hovered.getName()),hint,"Left: toggle   Right: settings   Middle: favourite"};
            int tw=Math.min(width-20,Math.max(180,Arrays.stream(lines).mapToInt(s->UiDraw.width(font,s)).max().orElse(180)))+12;
            tw=Math.min(width-12,tw);int tx=Math.clamp(mx+12,6,Math.max(6,width-tw-6));int ty=Math.min(my+15,height-61);
            UiDraw.panel(g,tx,ty,tw,49,t);
            for(int i=0;i<lines.length;i++)UiDraw.text(g,font,UiDraw.fit(font,lines[i],tw-12),tx+6,ty+6+i*13,i==0?t.accent():t.muted());
        }
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubled){
        if(super.mouseClicked(event,doubled))return true;
        Window w=at(event.x(),event.y());if(w==null)return false;
        if(event.y()<y(w)+HEADER){
            if(event.button()==1||event.x()>=w.x+WIDTH-17)w.collapsed=!w.collapsed;
            else if(event.button()==0){dragging=w;dragX=(int)event.x()-w.x;dragY=(int)event.y()-y(w);windows.remove(w);windows.add(w);}
            remember();return true;
        }
        if(!w.collapsed){
            int row=((int)event.y()-y(w)-HEADER+w.scroll)/ROW;List<Module> list=modules(w);
            if(row>=0&&row<list.size()){
                Module module=list.get(row);
                if(event.button()==0)module.toggle();
                else if(event.button()==1)minecraft.setScreen(new ModuleSettingsScreen(this,module,null));
                else if(event.button()==2){var favourites=GuiState.preferences().favourites;if(!favourites.add(module.getName()))favourites.remove(module.getName());GuiState.save();}
            }
        }
        return true;
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy){
        if(dragging==null)return super.mouseDragged(event,dx,dy);
        int x=(int)event.x()-dragX,y=(int)event.y()-dragY+canvasScroll;
        if(GuiState.preferences().snap){x=Math.round(x/8f)*8;y=Math.round(y/8f)*8;}
        dragging.x=Math.clamp(x,10,Math.max(10,width-WIDTH-10));dragging.y=Math.max(48,y);remember();return true;
    }
    @Override public boolean mouseReleased(MouseButtonEvent event){dragging=null;remember();GuiState.save();return super.mouseReleased(event);}
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){
        Window w=at(mx,my);
        if(w!=null&&!w.collapsed&&modules(w).size()*ROW>height(w)-HEADER-7){
            int previous=w.scroll;w.scroll=Math.clamp(w.scroll-(int)(vertical*ROW*2),0,Math.max(0,modules(w).size()*ROW-(height(w)-HEADER-7)));
            if(previous==w.scroll)canvasScroll=Math.max(0,canvasScroll-(int)(vertical*24));
        }else canvasScroll=Math.max(0,canvasScroll-(int)(vertical*24));
        remember();return true;
    }
    private void remember(){for(Window w:windows)GuiState.preferences().windows.put(w.name,new GuiPreferences.Window(w.x,w.y,w.collapsed,w.scroll));}
    @Override public void removed(){remember();super.removed();}
}
