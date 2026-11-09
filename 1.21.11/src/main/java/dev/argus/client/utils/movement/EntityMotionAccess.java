package dev.argus.client.utils.movement;

import net.minecraft.world.phys.Vec3;

public interface EntityMotionAccess {
   float getCameraYaw();

   void setCameraPitch(float var1);

   float getCameraPitch();

   void setCameraYaw(float var1);

   Vec3 argusclient$getPrevVelocity();
}
