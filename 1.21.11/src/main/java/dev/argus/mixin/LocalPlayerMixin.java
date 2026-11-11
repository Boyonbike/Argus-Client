package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.ItemUseEvent;
import dev.argus.client.modules.movement.SprintModule;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {
   private static final SprintModule sprintModule = ClientContext.getModule(SprintModule.class);
   @Unique
   private boolean aiStepInputActive;
   @Unique
   private boolean itemUseSlowdownCancelled;

   @Inject(
      method = "aiStep",
      at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;input:Lnet/minecraft/client/player/ClientInput;", ordinal = 4)
   )
   private void onAiStepBeforeInput(CallbackInfo callback) {
      ItemUseEvent.Slowdown event = new ItemUseEvent.Slowdown();
      ClientAccess.eventBus.post(event);
      this.itemUseSlowdownCancelled = event.isCancelled();
      this.aiStepInputActive = true;
      if (sprintModule.isToggled() && sprintModule.shouldDeferToVanillaSprinting()) {
         SprintModule.forceSprintKeyDown = true;
      }
   }

   @Inject(method = "aiStep", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Abilities;mayfly:Z"))
   private void clearSprintKeyOverride(CallbackInfo callback) {
      SprintModule.forceSprintKeyDown = false;
   }

   @Inject(method = "aiStep", at = @At("TAIL"))
   private void finishAiStepInput(CallbackInfo callback) {
      this.aiStepInputActive = false;
   }

   @ModifyReturnValue(method = "isUsingItem", at = @At("RETURN"))
   private boolean suppressItemUseDuringAiStep(boolean original) {
      return original && !(this.itemUseSlowdownCancelled && this.aiStepInputActive);
   }
}
