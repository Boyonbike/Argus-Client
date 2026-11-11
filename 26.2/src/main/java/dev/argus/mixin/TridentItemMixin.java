package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.exploit.TridentModule;
import net.minecraft.world.item.TridentItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(TridentItem.class)
public class TridentItemMixin {
   private static TridentModule tridentModule = ClientContext.getModule(TridentModule.class);

   @ModifyExpressionValue(method = "releaseUsing", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isInWaterOrRain()Z"))
   private boolean allowRiptideOnRelease(boolean inWaterOrRain) {
      return inWaterOrRain || tridentModule.isAlwaysRiptideEnabled();
   }

   @ModifyExpressionValue(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isInWaterOrRain()Z"))
   private boolean allowRiptideOnUse(boolean inWaterOrRain) {
      return inWaterOrRain || tridentModule.isAlwaysRiptideEnabled();
   }

   @ModifyArgs(method = "releaseUsing", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;push(DDD)V"))
   private void scaleRiptidePush(Args arguments) {
      arguments.set(0, (Double)arguments.get(0) * tridentModule.getBoostMultiplier());
      arguments.set(1, (Double)arguments.get(1) * tridentModule.getBoostMultiplier());
      arguments.set(2, (Double)arguments.get(2) * tridentModule.getBoostMultiplier());
   }

   @ModifyExpressionValue(method = "releaseUsing", at = @At(value = "CONSTANT", args = "intValue=10"))
   private int removeMinimumChargeTime(int originalChargeTime) {
      return tridentModule.isToggled() ? 0 : originalChargeTime;
   }

   @Unique
   private static void argusclient$bindTridentItemModules(ModuleRegistry moduleRegistry) {
      tridentModule = (TridentModule)moduleRegistry.getModule(TridentModule.class);
   }
}
