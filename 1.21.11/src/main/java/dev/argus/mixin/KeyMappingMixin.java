package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.argus.client.modules.movement.SprintModule;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyMapping.class)
public class KeyMappingMixin {
   // Every key's isDown() goes through here, so check the cheap static flag first and avoid a callback object.
   @ModifyReturnValue(method = "isDown", at = @At("RETURN"))
   public boolean overrideSprintKeyState(boolean original) {
      return original || SprintModule.forceSprintKeyDown && (Object)this == Minecraft.getInstance().options.keySprint;
   }
}
