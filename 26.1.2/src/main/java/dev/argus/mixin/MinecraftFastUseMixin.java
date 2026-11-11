package dev.argus.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.argus.client.ClientContext;
import dev.argus.client.modules.player.FastUseModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftFastUseMixin {
   @Shadow
   private int rightClickDelay;

   @Inject(
      method = "startUseItem",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/item/ItemStack;isItemEnabled(Lnet/minecraft/world/flag/FeatureFlagSet;)Z"
      ),
      require = 1
   )
   private void argusclient$applyFastUseCooldown(CallbackInfo ci, @Local(ordinal = 0) ItemStack heldItem) {
      FastUseModule fastUse = ClientContext.getModule(FastUseModule.class);
      if (fastUse != null && fastUse.isToggled() && fastUse.appliesTo(heldItem)) {
         this.rightClickDelay = fastUse.cooldownFor(heldItem);
      }
   }
}
