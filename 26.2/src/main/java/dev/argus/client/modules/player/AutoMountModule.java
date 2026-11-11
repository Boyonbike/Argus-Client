package dev.argus.client.modules.player;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.events.RotationRequestEvent;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.world.BlockInteractionUtils;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PlayerRideable;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.phys.Vec3;

public final class AutoMountModule extends Module {
   private final Setting<Double> rangeSetting = this.add(new NumberSetting<>("Range", 3.0, 0.1, 6.0).withUnit("m"));
   private final Setting<Float> delaySetting = this.add(new NumberSetting<>("Delay", 1.0F, 0.0F, 5.0F).withUnit("s"));
   private final Setting<Boolean> rotateSetting;
   private final ElapsedTimer mountTimer;

   @EventHandler
   private void onRotationRequest(RotationRequestEvent event) {
      if (!ClientAccess.argusclientMinecraft.player.isPassenger()) {
         ElapsedTimer timerSnapshot = this.mountTimer;
         float configuredDelaySnapshot = this.delaySetting.getValue();
         float minimumDelay = 0.1F;
         double minimumElapsedDelay = Math.max(configuredDelaySnapshot, minimumDelay);
         TimeUnit delayUnit = TimeUnit.SECONDS;
         if (timerSnapshot.hasElapsed(minimumElapsedDelay, delayUnit)) {
            if (!ClientAccess.argusclientMinecraft.player.isShiftKeyDown()) {
               ClientLevel level = ClientAccess.argusclientMinecraft.level;

               for (Entity entity : level.entitiesForRendering()) {
                  double distance = ClientAccess.argusclientMinecraft.player.distanceTo(entity);
                  Double range = this.rangeSetting.getValue();
                  if (!(distance > range)
                     && !(entity instanceof LivingEntity livingEntity && livingEntity.isBaby())
                     && !(entity instanceof Mob mob && !mob.isSaddled() && !(entity instanceof AbstractHorse))
                     && (entity instanceof PlayerRideable || entity instanceof VehicleEntity || entity instanceof Strider)) {
                     InteractionHand interactionHand = InteractionHand.MAIN_HAND;
                     BlockInteractionUtils.interactWithEntity(entity, interactionHand);
                     if (this.rotateSetting.getValue()) {
                        RotationManager rotationManagerSnapshot = ClientContext.rotationManager;
                        Vec3 boundingBoxCenter = entity.getBoundingBox().getCenter();
                        float[] rotations = RotationManager.getRotationsToPosition(boundingBoxCenter);
                        rotationManagerSnapshot.requestRotation(rotations, 5);
                     }

                     timerSnapshot = this.mountTimer;
                     timerSnapshot.reset();
                     break;
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundSetPassengersPacket passengersPacket) {
         Setting<Float> delayOption = this.delaySetting;
         if (delayOption.isAtMinimum()) {
            if (ClientAccess.argusclientMinecraft.player.getVehicle() != null) {
               int packetVehicleId = passengersPacket.getVehicle();
               Entity vehicle = ClientAccess.argusclientMinecraft.player.getVehicle();
               if (packetVehicleId == vehicle.getId()) {
                  for (int passengerId : passengersPacket.getPassengers()) {
                     LocalPlayer passengerPlayer = ClientAccess.argusclientMinecraft.player;
                     if (passengerId == passengerPlayer.getId()) {
                        return;
                     }
                  }

                  this.mountTimer.clear();
               }
            }
         }
      }
   }

   public AutoMountModule() {
      super("AutoMount", "Mounts rideable entities automatically.", ModuleCategory.PLAYER);
      Boolean defaultRotate = true;
      this.rotateSetting = this.add(new BooleanSetting("Rotate", defaultRotate));
      this.mountTimer = new ElapsedTimer();
   }
}
