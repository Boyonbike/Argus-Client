package dev.argus.client.commands;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.argus.client.ClientAccess;
import dev.argus.client.utils.chat.ChatUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;

public final class PitchCommand extends Command {
   public PitchCommand() {
      super("pitch");
   }

   @Override
   public void exec(LiteralArgumentBuilder builder) {
      String rotationArgumentName = "pitch";
      RelativeFloatArgument argumentType = RelativeFloatArgument.relativeFloatArg();
      RequiredArgumentBuilder argumentBuilder = Command.argument(rotationArgumentName, argumentType);
      com.mojang.brigadier.Command<Object> rotationAction = commandContext -> {
         RelativeFloatArgument.RelativeFloat argument = (RelativeFloatArgument.RelativeFloat)commandContext.getArgument(
            "pitch", RelativeFloatArgument.RelativeFloat.class
         );
         float pitch = argument.value();
         if (argument.relative()) {
            LocalPlayer playerForPitch = ClientAccess.argusclientMinecraft.player;
            pitch += playerForPitch.getXRot();
         }

         pitch = Mth.clamp(pitch, -90.0F, 90.0F);
         float appliedPitch = pitch;
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         player.setXRot(appliedPitch);
         String messageText = "Player's pitch has been set to " + pitch + ".";
         MutableComponent message = Component.literal(messageText);
         MessageSignature signature = ChatUtils.createSignature(-1);
         ChatUtils.printChat(message, signature);
         return 1;
      };
      ArgumentBuilder rotation = argumentBuilder.executes(rotationAction);
      builder.then(rotation);
   }
}
