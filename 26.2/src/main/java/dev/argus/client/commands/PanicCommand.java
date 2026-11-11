package dev.argus.client.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.access.ChatLineAccess;
import dev.argus.client.access.VisibleChatLinesAccess;
import dev.argus.client.managers.CommandRegistry;
import dev.argus.client.managers.ConfigurationManager;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.Keybind;
import dev.argus.client.modules.Module;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

public final class PanicCommand extends Command {
   public static boolean unloaded = false;
   private static final String HIDDEN_PREFIX = "ㅤ";

   public PanicCommand() {
      super("panic");
   }

   private void unload() {
      ConfigurationManager configuration = ClientContext.configurationManager;
      configuration.save();
      unloaded = true;
      String previousPrefix = CommandRegistry.getPrefix();
      CommandRegistry.setPrefix("ㅤ");
      ModuleRegistry modules = ClientContext.moduleRegistry;

      for (Module module : (List<Module>)modules.getRegistry()) {
         Function<Keybind, Keybind> unbind = binding -> binding.withCode(-1);
         module.modifyKeybind(unbind);
         if (module.isToggled()) {
            module.toggle();
         }
      }

      Gui recentChatGui = ClientAccess.argusclientMinecraft.gui;
      ChatComponent recentChat = recentChatGui.hud.getChat();
      recentChat.getRecentChat().removeIf(message -> message.startsWith(previousPrefix));
      Gui visibleChatGui = ClientAccess.argusclientMinecraft.gui;
      ((VisibleChatLinesAccess)visibleChatGui.hud.getChat()).getVisible().removeIf(line -> {
         ChatLineAccess access = (ChatLineAccess)line;
         if (access.getSignature() != null) {
            MessageSignature signature = access.getSignature();
            ByteBuffer signatureBytes = signature.asByteBuffer();
            if (signatureBytes.getInt() < 0) {
               return true;
            }
         }

         return false;
      });
      Gui remainingChatGui = ClientAccess.argusclientMinecraft.gui;
      ChatComponent remainingChat = remainingChatGui.hud.getChat();
      remainingChat.getRecentChat().removeIf(message -> message.startsWith(previousPrefix));
   }

   @Override
   public void exec(LiteralArgumentBuilder builder) {
      com.mojang.brigadier.Command<Object> command = context -> {
         this.scheduleRotationTask(() -> {
            Minecraft minecraft = ClientAccess.argusclientMinecraft;
            ConfirmScreen confirmationScreen = new ConfirmScreen(confirmed -> {
               if (confirmed) {
                  this.unload();
               }

               Minecraft currentMinecraft = ClientAccess.argusclientMinecraft;
               currentMinecraft.gui.setScreen(null);
            }, Component.literal("Unload the client."), Component.literal("Continue?"));
            minecraft.gui.setScreen(confirmationScreen);
         });
         return 1;
      };
      builder.executes(command);
   }
}
