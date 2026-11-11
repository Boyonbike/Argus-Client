package dev.argus.client.commands;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.argus.client.ClientAccess;
import dev.argus.client.utils.chat.ChatUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public final class YawCommand extends Command {
   public YawCommand() {
      super("yaw");
   }

   @Override
   public void exec(LiteralArgumentBuilder builder) {
      String rotationArgumentName = "yaw";
      RelativeFloatArgument argumentType = RelativeFloatArgument.relativeFloatArg();
      RequiredArgumentBuilder argumentBuilder = Command.argument(rotationArgumentName, argumentType);
      com.mojang.brigadier.Command<Object> rotationAction = commandContext -> {
         RelativeFloatArgument.RelativeFloat argument = (RelativeFloatArgument.RelativeFloat)commandContext.getArgument(
            "yaw", RelativeFloatArgument.RelativeFloat.class
         );
         float yaw = argument.value();
         if (argument.relative()) {
            LocalPlayer playerForRelativeYaw = ClientAccess.argusclientMinecraft.player;
            yaw += playerForRelativeYaw.getYRot();
         }

         if (Float.isNaN(yaw)) {
            LocalPlayer playerForNaNYaw = ClientAccess.argusclientMinecraft.player;
            yaw = playerForNaNYaw.getYRot();
         }

         float yawMagnitude = Math.abs(yaw);
         if (yawMagnitude > 7200.0F) {
            yaw %= 360.0F;
         }

         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         player.setYRot(yaw);
         String suffix = !(yaw > 180.0F) && !(yaw < -180.0F) ? "" : " (%s)".formatted(yaw);
         float wrappedYaw = Mth.wrapDegrees(yaw);
         String formattedMessage = "Player's yaw has been set to %.2f%s.".formatted(wrappedYaw, suffix);
         Component message = Component.literal(formattedMessage);
         ChatUtils.printChat(message, -1);
         return 1;
      };
      ArgumentBuilder rotation = argumentBuilder.executes(rotationAction);
      builder.then(rotation);
   }
}
