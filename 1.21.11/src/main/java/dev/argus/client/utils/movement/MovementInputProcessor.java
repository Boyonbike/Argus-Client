package dev.argus.client.utils.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.integrations.ProtocolCompatibility;
import dev.argus.client.integrations.ProtocolVersionAccess;
import dev.argus.mixin.LocalPlayerAccessor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;

public final class MovementInputProcessor implements ClientAccess {
   public void applyInputPreservingBobbing() {
      float previousYawBob = ClientAccess.argusclientMinecraft.player.yBobO;
      float previousPitchBob = ClientAccess.argusclientMinecraft.player.xBobO;
      float pitchBob = ClientAccess.argusclientMinecraft.player.xBob;
      float yawBob = ClientAccess.argusclientMinecraft.player.yBob;
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      player.applyInput();
      ClientAccess.argusclientMinecraft.player.yBobO = previousYawBob;
      ClientAccess.argusclientMinecraft.player.xBobO = previousPitchBob;
      ClientAccess.argusclientMinecraft.player.xBob = pitchBob;
      ClientAccess.argusclientMinecraft.player.yBob = yawBob;
   }

   public Vec2 normalizeInput(Vec2 input) {
      ProtocolVersionAccess targetVersion = ProtocolCompatibility.getTargetVersion();
      return targetVersion.olderEq_1_21_4()
         ? ((LocalPlayerAccessor)ClientAccess.argusclientMinecraft.player).argusclient$applyMovementSpeedFactors(input)
         : input.normalized();
   }

   public MovementDirection getRelativeDirection(float movementYaw, float rotationYaw) {
      double difference = (Mth.degreesDifference(movementYaw, rotationYaw) + 360.0) % 360.0;
      MovementDirection[] directions = MovementDirection.values();
      return directions[(int)Math.round(difference / 45.0) % directions.length];
   }
}
