package dev.argus.client.commands;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.argus.client.ClientAccess;
import dev.argus.client.utils.chat.ChatUtils;
import dev.argus.mixin.UserAccessor;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

public final class SessionCommand extends Command {
   private String createRandomName() {
      return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
   }

   public SessionCommand() {
      super("session");
   }

   @Override
   public void exec(LiteralArgumentBuilder builder) {
      FancyStringArgument nameArgument = FancyStringArgument.fancyString();
      RequiredArgumentBuilder nameBuilder = Command.argument("name", nameArgument);
      com.mojang.brigadier.Command<Object> namedCommand = context -> {
         String name = (String)context.getArgument("name", String.class);
         this.setSessionName(name);
         return 1;
      };
      ArgumentBuilder nameCommandBuilder = nameBuilder.executes(namedCommand);
      builder.then(nameCommandBuilder);
      com.mojang.brigadier.Command<Object> randomNameCommand = context -> {
         String randomName = this.createRandomName();
         this.setSessionName(randomName);
         return 1;
      };
      builder.executes(randomNameCommand);
   }

   private void setSessionName(String name) {
      if (!name.isEmpty() && name.length() <= 16) {
         Minecraft client = ClientAccess.argusclientMinecraft;
         ClientPacketListener listener = client.getConnection();
         ServerData server = listener == null ? null : listener.getServerData();
         UserAccessor user = (UserAccessor)client.getUser();
         user.setUsername(name);
         user.setUuid(UUID.randomUUID());
         ChatUtils.printChat(Component.literal("Session's name has been set to " + name + "."), -1);
         if (server != null) {
            listener.getConnection().disconnect(Component.empty());
            ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), client, ServerAddress.parseString(server.ip), server, true, null);
         }
      } else {
         ChatUtils.printChat(Component.literal("Session names must contain 1 to 16 characters."), -1);
      }
   }
}
