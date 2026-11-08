package dev.randomstuff.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;

/** Keeps the original GUI module's bind/lifecycle while replacing its default screen. */
public final class GuiNavigation {
    private static boolean deferred;
    private GuiNavigation() {}
    public static boolean isOpen(){return deferred||Minecraft.getInstance().screen instanceof UiScreen;}
    public static void openFromBind(){
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null)return;
        if(mc.screen instanceof ChatScreen){deferred=true;return;}
        mc.setScreen(new ModulesScreen(mc.screen));
    }
    public static void closeFromBind(){
        Minecraft mc=Minecraft.getInstance();
        if(mc.screen instanceof UiScreen screen)mc.setScreen(screen.rootParent());
        deferred=false;
    }
    static void tick(){
        if(deferred&&!(Minecraft.getInstance().screen instanceof ChatScreen)){deferred=false;openFromBind();}
    }
}
