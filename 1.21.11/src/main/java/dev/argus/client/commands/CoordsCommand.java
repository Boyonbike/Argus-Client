package dev.argus.client.commands;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.argus.client.ClientAccess;
import dev.argus.client.settings.options.DimensionType;
import dev.argus.client.utils.chat.ChatUtils;
import dev.argus.client.utils.world.WorldUtils;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;

public final class CoordsCommand extends Command {
   private String formatCoordinates() {
      if (ClientAccess.argusclientMinecraft.player == null) {
         return "";
      }

      LocalPlayer playerForX = ClientAccess.argusclientMinecraft.player;
      int x = (int)playerForX.getX();
      LocalPlayer playerForY = ClientAccess.argusclientMinecraft.player;
      int y = (int)playerForY.getY();
      LocalPlayer playerForZ = ClientAccess.argusclientMinecraft.player;
      int z = (int)playerForZ.getZ();
      DimensionType dimension = WorldUtils.getDimension();
      String dimensionName = dimension.getDisplayName();
      double randomValue = Math.random();
      double scaledRandom = randomValue * 10000.0;
      int randomSuffix = (int)Math.floor(scaledRandom);
      return "X: " + x + ", Y: " + y + ", Z: " + z + " in The " + dimensionName + " [" + randomSuffix + "]";
   }

   public CoordsCommand() {
      super("coords");
   }

   @Override
   public void exec(LiteralArgumentBuilder root) {
      PlayerNameArgumentType playerNameType = new PlayerNameArgumentType();
      RequiredArgumentBuilder nameBuilder = Command.argument("name", playerNameType);
      com.mojang.brigadier.Command<Object> whisperAction = context -> {
         String playerName = (String)context.getArgument("name", String.class);
         String coordinates = this.formatCoordinates();
         this.whisperCoordinates(playerName, coordinates);
         return 1;
      };
      ArgumentBuilder nameArgument = nameBuilder.executes(whisperAction);
      LiteralArgumentBuilder clipboardRoot = (LiteralArgumentBuilder)root.then(nameArgument);
      com.mojang.brigadier.Command<Object> clipboardAction = context -> {
         try {
            KeyboardHandler capturedKeyboard = ClientAccess.argusclientMinecraft.keyboardHandler;
            String coordinates = this.formatCoordinates();
            capturedKeyboard.setClipboard(coordinates);
            MutableComponent confirmation = Component.literal("Your position has been copied to the clipboard.");
            MessageSignature signature = ChatUtils.createSignature(-1);
            ChatUtils.printChat(confirmation, signature);
         } catch (Exception var6x) {
         }

         return 1;
      };
      clipboardRoot.executes(clipboardAction);
   }

   private void whisperCoordinates(String recipient, String coordinates) {
      String whisperCommand = "w " + recipient + " " + coordinates;
      ClientPacketListener connection = ClientAccess.argusclientMinecraft.player.connection;
      connection.sendCommand(whisperCommand);
   }
}
