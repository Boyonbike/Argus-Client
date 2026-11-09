package dev.argus.client.modules.player;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.movement.NoSlowModule;
import dev.argus.client.utils.KeyUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;

public class FreecamInput extends ClientInput implements ClientAccess {
   private static NoSlowModule noSlow = ClientContext.getModule(NoSlowModule.class);

   private boolean isMovementKeyDown(KeyMapping mapping) {
      return noSlow.isToggled() && noSlow.isGuiMoveAllowed() && noSlow.guiMoveSetting.getValue() ? KeyUtils.isPhysicallyDown(mapping) : mapping.isDown();
   }

   @Override
   public void tick() {
      KeyMapping forwardKey = ClientAccess.argusclientMinecraft.options.keyUp;
      boolean positiveDown = this.isMovementKeyDown(forwardKey);
      KeyMapping backKey = ClientAccess.argusclientMinecraft.options.keyDown;
      boolean negativeDown = this.isMovementKeyDown(backKey);
      float forward = this.getAxisInput(positiveDown, negativeDown);
      KeyMapping leftKey = ClientAccess.argusclientMinecraft.options.keyLeft;
      positiveDown = this.isMovementKeyDown(leftKey);
      KeyMapping rightKey = ClientAccess.argusclientMinecraft.options.keyRight;
      negativeDown = this.isMovementKeyDown(rightKey);
      float sideways = this.getAxisInput(positiveDown, negativeDown);
      this.moveVector = new Vec2(sideways, forward);
   }

   private float getAxisInput(boolean positiveDown, boolean negativeDown) {
      return positiveDown == negativeDown ? 0.0F : (positiveDown ? 1.0F : -1.0F);
   }

   public static void bindModules(ModuleRegistry registry) {
      noSlow = (NoSlowModule)registry.getModule(NoSlowModule.class);
   }
}
