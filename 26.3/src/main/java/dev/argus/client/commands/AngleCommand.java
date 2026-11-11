package dev.argus.client.commands;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.modules.player.RotationLockModule;
import dev.argus.client.utils.chat.ChatUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public final class AngleCommand extends Command {
   private static final RotationLockModule rotationLock = ClientContext.getModule(RotationLockModule.class);

   public AngleCommand() {
      super("angle");
   }

   @Override
   public void exec(LiteralArgumentBuilder builder) {
      String positionArgumentName = "pos";
      PositionArgument positionArgument = new PositionArgument();
      RequiredArgumentBuilder positionBuilder = Command.argument(positionArgumentName, positionArgument);
      LiteralArgumentBuilder yawLockBuilder = Command.literal("yawlock");
      com.mojang.brigadier.Command<Object> yawLockAction = commandContext -> {
         Vec3 position = (Vec3)commandContext.getArgument("pos", Vec3.class);
         float[] rotations = RotationManager.getRotationsToPosition(position);
         rotationLock.yawValueSetting.setValue(rotations[0]);
         return 1;
      };
      ArgumentBuilder yawLock = yawLockBuilder.executes(yawLockAction);
      RequiredArgumentBuilder positionWithYawLock = (RequiredArgumentBuilder)positionBuilder.then(yawLock);
      com.mojang.brigadier.Command<Object> angleAction = commandContext -> {
         Vec3 position = (Vec3)commandContext.getArgument("pos", Vec3.class);
         float[] rotations = RotationManager.getRotationsToPosition(position);
         String messageText = "Calculated angle. Yaw: %.1f, Pitch: %.1f.".formatted(rotations[0], rotations[1]);
         Component message = Component.literal(messageText);
         ChatUtils.printChat(message, -1);
         return 1;
      };
      ArgumentBuilder angle = positionWithYawLock.executes(angleAction);
      builder.then(angle);
   }
}
