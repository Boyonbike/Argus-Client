package dev.argus.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;

/**
 * Keeps the original GUI module's bind/lifecycle while replacing its default screen.
 * The base client's legacy ClickGuiScreen is never shown: every path that would open it
 * (in-game bind, title/multiplayer/disconnected screen binds, direct setScreen calls) lands here.
 */
public final class GuiNavigation {
    private static boolean deferred;
    private GuiNavigation() {}
    public static boolean isOpen(){return deferred||Minecraft.getInstance().gui.screen() instanceof UiScreen;}
    public static void openFromBind(){
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null)return;
        if(mc.gui.screen() instanceof ChatScreen){deferred=true;return;}
        mc.gui.setScreen(new ModulesScreen(mc.gui.screen()));
    }
    /** Opens the Argus GUI over whatever screen is showing, with or without a world loaded. */
    public static void openAnywhere(){
        Minecraft mc=Minecraft.getInstance();
        if(mc.gui.screen() instanceof UiScreen)return;
        mc.gui.setScreen(replacementFor(mc.gui.screen()));
    }
    /** The Argus screen to show in place of the legacy GUI, returning to {@code current} on close. */
    public static Screen replacementFor(Screen current){
        return new ModulesScreen(current instanceof UiScreen screen?screen.rootParent():current);
    }
    public static void closeFromBind(){
        Minecraft mc=Minecraft.getInstance();
        if(mc.gui.screen() instanceof UiScreen screen)screen.beginClose(screen.rootParent());
        deferred=false;
    }
    static void tick(){
        if(deferred&&!(Minecraft.getInstance().gui.screen() instanceof ChatScreen)){deferred=false;openFromBind();}
    }
}
