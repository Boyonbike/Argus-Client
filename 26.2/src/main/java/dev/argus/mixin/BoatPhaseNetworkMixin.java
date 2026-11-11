package dev.argus.mixin;

import dev.argus.client.modules.movement.BoatPhaseModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells Boat Phase about server corrections (vehicle and player) just before and after they are applied. */
@Mixin(ClientPacketListener.class)
public abstract class BoatPhaseNetworkMixin {
    @Inject(method = "handleMoveVehicle", at = @At("HEAD"))
    private void argus$boatCorrectionBefore(ClientboundMoveVehiclePacket packet, CallbackInfo info) {
        if (!Minecraft.getInstance().isSameThread()) return;
        BoatPhaseModule module = BoatPhaseModule.active();
        if (module != null) module.beforeVehicleCorrection((ClientPacketListener) (Object) this, packet);
    }

    @Inject(method = "handleMoveVehicle", at = @At("RETURN"))
    private void argus$boatCorrectionAfter(ClientboundMoveVehiclePacket packet, CallbackInfo info) {
        if (!Minecraft.getInstance().isSameThread()) return;
        BoatPhaseModule module = BoatPhaseModule.active();
        if (module != null) module.afterVehicleCorrection((ClientPacketListener) (Object) this, packet);
    }

    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void argus$playerCorrectionBefore(ClientboundPlayerPositionPacket packet, CallbackInfo info) {
        if (!Minecraft.getInstance().isSameThread()) return;
        BoatPhaseModule module = BoatPhaseModule.active();
        if (module != null) module.beforePlayerCorrection(packet);
    }

    @Inject(method = "handleMovePlayer", at = @At("RETURN"))
    private void argus$playerCorrectionAfter(ClientboundPlayerPositionPacket packet, CallbackInfo info) {
        if (!Minecraft.getInstance().isSameThread()) return;
        BoatPhaseModule module = BoatPhaseModule.active();
        if (module != null) module.afterPlayerCorrection();
    }
}
