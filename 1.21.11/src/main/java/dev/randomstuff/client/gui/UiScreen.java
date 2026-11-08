package dev.randomstuff.client.gui;

import dev.randomstuff.client.ClientContext;
import dev.randomstuff.client.modules.client.UIModule;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** Shared navigation for the module menu, search, friends and theme/settings pages. */
public abstract class UiScreen extends Screen {
    protected final Screen parent;
    private final String activeTab;
    protected UiScreen(String title,Screen parent,String activeTab) { super(Component.literal(title));this.parent=parent;this.activeTab=activeTab; }
    protected Screen rootParent() { return parent instanceof UiScreen screen?screen.rootParent():parent; }
    @Override protected final void init() {
        buildContent();
        String[] labels={"Modules","Search","Friends","HUD","Theme","Classic"};
        int[] widths=new int[labels.length];int total=0;
        for(int i=0;i<labels.length;i++){widths[i]=Math.max(30,UiDraw.width(font,labels[i])+16);total+=widths[i]+3;}
        int x=Math.max(5,(width-total)/2);
        for(int i=0;i<labels.length;i++){
            String label=labels[i];
            addRenderableWidget(new UiButton(x,8,widths[i],23,label,label.equals(activeTab),()->navigate(label)));
            x+=widths[i]+3;
        }
    }
    protected abstract void buildContent();
    protected void navigate(String label) {
        Screen root=rootParent();
        switch(label){
            case "Modules"->minecraft.setScreen(new ModulesScreen(root));
            case "Search"->minecraft.setScreen(new SearchScreen(this instanceof SearchScreen?parent:this));
            case "Friends"->minecraft.setScreen(new FriendsScreen(this instanceof FriendsScreen?parent:this));
            case "HUD"->ClientContext.getHudEditor().open();
            case "Theme"->minecraft.setScreen(new AppearanceScreen(this instanceof AppearanceScreen?parent:this));
            case "Classic"->ClientContext.getClickGui().open();
        }
    }
    protected void backdrop(GuiGraphics g) { g.fill(0,0,width,height,GuiState.preferences().mode==GuiPreferences.Mode.DARK?0x8a090910:0x60000000); }
    protected void chrome(GuiGraphics g,int mx,int my,float delta) {
        super.render(g,mx,my,delta);
        UiTheme t=GuiState.theme();
        UiDraw.text(g,font,GuiState.saveError.isEmpty()?"Random Stuff Client":GuiState.saveError,9,height-13,GuiState.saveError.isEmpty()?t.muted():t.red());
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if(UIModule.INSTANCE!=null){
            var bind=UIModule.INSTANCE.getKeybind();
            if(!bind.mouse()&&!bind.isUnbound()&&event.key()==bind.code()&&(!(getFocused() instanceof EditBox)||bind.code()>=340)){
                minecraft.setScreen(rootParent());return true;
            }
        }
        if((event.modifiers()&2)!=0 && event.key()==70){minecraft.setScreen(new SearchScreen(this instanceof SearchScreen?parent:this));return true;}
        return super.keyPressed(event);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void removed() { GuiState.save();super.removed(); }
    @Override public boolean isPauseScreen() { return false; }
}
