package dev.randomstuff.client.gui;

import dev.randomstuff.client.ClientContext;
import dev.randomstuff.client.modules.misc.SocialEntry;
import dev.randomstuff.client.settings.options.SocialRole;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import java.util.*;

/** Friends uses the same palette and navigation while editing the existing social registry. */
public final class FriendsScreen extends UiScreen {
    private int left,top,panelWidth,scroll,rows;
    private UiInput nameBox;
    private List<String> friends=List.of();
    private String status="";
    public FriendsScreen(Screen parent){super("Friends",parent,"Friends");}
    @Override protected void buildContent(){
        panelWidth=Math.min(360,width-24);left=(width-panelWidth)/2;top=46;
        rows=Math.max(1,(height-top-100)/24);
        List<String> names=new ArrayList<>();
        for(Object object:ClientContext.socialRegistry.getRegistry())if(object instanceof SocialEntry entry&&entry.role()==SocialRole.FRIEND)names.add(entry.name());
        names.sort(String.CASE_INSENSITIVE_ORDER);friends=List.copyOf(names);scroll=Math.clamp(scroll,0,Math.max(0,friends.size()-rows));
        String draft=nameBox==null?"":nameBox.getValue();
        nameBox=new UiInput(font,left+12,top+37,panelWidth-91,18,"Player name...");nameBox.setMaxLength(16);nameBox.setValue(draft);
        addRenderableWidget(nameBox);setInitialFocus(nameBox);
        addRenderableWidget(new UiButton(left+panelWidth-70,top+34,58,24,"Add",this::addFriend));
        for(int i=0;i<rows&&scroll+i<friends.size();i++){
            String name=friends.get(scroll+i);
            addRenderableWidget(new UiButton(left+panelWidth-75,top+71+i*24,61,19,"Remove",()->{ClientContext.socialRegistry.removeSocial(name);status="Removed "+name+".";rebuildWidgets();}));
        }
    }
    private void addFriend(){
        String name=nameBox.getValue().trim();
        if(!name.matches("[A-Za-z0-9_]{1,16}")){status="Enter a player name using letters, numbers or underscores.";return;}
        if(ClientContext.socialRegistry.isFriendName(name)){status=name+" is already a friend.";return;}
        ClientContext.socialRegistry.addFriend(name);status="Added "+name+".";nameBox.setValue("");rebuildWidgets();
    }
    @Override protected void renderUi(GuiGraphics g,int mx,int my,float delta){
        backdrop(g);UiTheme t=GuiState.theme();UiDraw.panel(g,left,top,panelWidth,height-top-24,t);
        UiDraw.text(g,font,"Friends",left+13,top+13,t.text());
        UiDraw.text(g,font,Integer.toString(friends.size()),left+panelWidth-28,top+13,t.accent());
        for(int i=0;i<rows&&scroll+i<friends.size();i++){
            int y=top+69+i*24;UiDraw.rounded(g,left+10,y,panelWidth-20,22,4,t.mantle());
            UiDraw.rounded(g,left+14,y+7,8,8,4,t.green());
            UiDraw.text(g,font,UiDraw.fit(font,friends.get(scroll+i),panelWidth-112),left+30,y+7,t.text());
        }
        if(friends.isEmpty())UiDraw.center(g,font,"No friends yet. Add one above.",left,top+83,panelWidth,t.muted());
        UiDraw.text(g,font,UiDraw.fit(font,status.isEmpty()?"Scroll to see more friends.":status,panelWidth-24),left+12,height-36,t.muted());
        chrome(g,mx,my,delta);
    }
    @Override public boolean keyPressed(KeyEvent event){
        if((event.key()==257||event.key()==335)&&nameBox.isFocused()){addFriend();return true;}return super.keyPressed(event);
    }
    @Override protected boolean mouseScrolledUi(double mx,double my,double horizontal,double vertical){
        if(UiDraw.over(mx,my,left,top+65,panelWidth,height-top-90)){
            int next=Math.clamp(scroll-(int)Math.signum(vertical),0,Math.max(0,friends.size()-rows));
            if(next!=scroll){scroll=next;rebuildWidgets();}return true;
        }
        return false;
    }
}
