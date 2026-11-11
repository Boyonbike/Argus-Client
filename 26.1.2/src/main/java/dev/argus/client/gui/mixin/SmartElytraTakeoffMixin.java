package dev.argus.client.gui.mixin;

import dev.argus.client.ClientContext;
import dev.argus.client.modules.movement.ElytraFlyModule;
import dev.argus.client.utils.movement.VolytraElytraFlyHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Argus smart elytra takeoff: equip before vanilla checks the second Jump press, so vanilla opens the wings in
 * the same attempt and sends its normal flight packet.
 */
@Mixin(LocalPlayer.class)
public abstract class SmartElytraTakeoffMixin {
    @Inject(method = "aiStep", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;tryToStartFallFlying()Z"))
    private void argus$equipForFlight(CallbackInfo ci) {
        if (ClientContext.moduleRegistry == null) return;
        ElytraFlyModule fly = ClientContext.getModule(ElytraFlyModule.class);
        if (fly != null && fly.isToggled() && fly.getActiveMode() instanceof VolytraElytraFlyHandler volytra) {
            volytra.prepareSmartTakeoff();
        }
    }
}
