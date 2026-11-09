package dev.argus.client.settings.options;

import dev.argus.client.ClientAccess;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.settings.NamedEntry;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public enum ChorusControlYawChoice implements ClientAccess, NamedEntry {
   KEEP("Keep"),
   INVERT("Invert"),
   POS("Pos");

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   ChorusControlYawChoice(String displayName) {
      this.displayName = displayName;
   }

   public float resolveYaw(Vec3 destination, Vec3 eyePosition) {
      if (this == KEEP) {
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         return player.getYRot();
      }

      float yaw = RotationManager.calculateRotations(destination, eyePosition)[0];
      if (this == INVERT) {
         yaw += 180.0F;
      }

      return yaw;
   }
}
