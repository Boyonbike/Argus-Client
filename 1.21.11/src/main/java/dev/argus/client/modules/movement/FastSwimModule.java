package dev.argus.client.modules.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerMoveEvent;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.player.SpeedMineModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.movement.MovementPhysics;
import dev.argus.client.utils.world.WorldUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;

public class FastSwimModule extends Module {
   private static SpeedMineModule speedMineModule;
   private final Setting<Float> glideSetting = this.add(new NumberSetting<>("Glide", 0.05F, 0.0F, 5.0F));
   private final Setting<Boolean> waterSetting = this.add(new BooleanSetting("Water", false).markVisibilityDependency());
   private final Setting<Float> waterSpeedSetting = this.add(new NumberSetting<>("Speed", 1.0F, 0.1F, 3.0F).dependsOn(this.waterSetting));
   private final Setting<Boolean> lavaSetting = this.add(new BooleanSetting("Lava", true).markVisibilityDependency());
   private final Setting<Float> lavaSpeedSetting = this.add(new NumberSetting<>("Speed", 1.0F, 0.1F, 3.0F).dependsOn(this.lavaSetting));
   private final Setting<Boolean> verticalSetting = this.add(new BooleanSetting("Vertical", false).markVisibilityDependency());
   private final Setting<Float> vSpeedSetting = this.add(new NumberSetting<>("VSpeed", 1.0F, 0.1F, 2.0F).dependsOn(this.verticalSetting));
   public final Setting<Boolean> accelerateSetting = this.add(new BooleanSetting("Accelerate", false).markVisibilityDependency());
   public final Setting<Float> accelMinSetting = this.add(new NumberSetting<>("AccelMin", 1.0F, 0.0F, 5.0F).dependsOn(this.accelerateSetting));
   public final Setting<Float> accelTimeSetting;
   private long accelerationStartMillis;

   private boolean shouldSkipSwimming() {
      if (!ClientAccess.argusclientMinecraft.player.isInLava() && !PlayerStateUtils.isInWater(ClientAccess.argusclientMinecraft.player)) {
         return true;
      }

      if (ClientAccess.argusclientMinecraft.player.isInLava() && !this.lavaSetting.getValue()) {
         return true;
      }

      if (PlayerStateUtils.isInWater(ClientAccess.argusclientMinecraft.player) && !this.waterSetting.getValue()) {
         return true;
      }

      if (ClientAccess.argusclientMinecraft.player.isFallFlying()) {
         return true;
      }

      if (!ClientContext.serverStateManager.getSetbackTimer().hasElapsed(500L)) {
         return true;
      }

      if (speedMineModule.isToggled()) {
         double miningProgress = speedMineModule.getMiningProgress();
         double nextMiningProgress = miningProgress + speedMineModule.getTickBreakProgress();
         double threshold = speedMineModule.damageSetting.getValue().floatValue();
         if (nextMiningProgress >= threshold - 0.1 && speedMineModule.getMiningOrNearlyBrokenPos() != null) {
            BlockPos miningTargetSnapshot = speedMineModule.getMiningOrNearlyBrokenPos();
            if (WorldUtils.canMineBlock(miningTargetSnapshot)) {
               return true;
            }
         }
      }

      return ClientContext.baritoneBridge.isPathingOrCalculating();
   }

   @EventHandler
   private void onPlayerTick(LocalPlayerTickEvent event) {
      if (!this.shouldSkipSwimming()) {
         ClientAccess.argusclientMinecraft.player.avatarState().walkDist = 0.0F;
      }
   }

   @EventHandler
   private void onMove(LocalPlayerMoveEvent event) {
      if (this.shouldSkipSwimming()) {
         this.accelerationStartMillis = System.currentTimeMillis();
      } else {
         if (!MovementPhysics.isLocalPlayerMoving()) {
            this.accelerationStartMillis = System.currentTimeMillis();
         }

         float verticalSpeed = this.verticalSetting.getValue() ? 0.12F * this.vSpeedSetting.getValue() : 0.0F;
         float minimumSpeed = 0.12F * this.accelMinSetting.getValue();
         double horizontalSpeed = 0.12F * this.lavaSpeedSetting.getValue();
         if (this.waterSetting.getValue() && PlayerStateUtils.isInWater(ClientAccess.argusclientMinecraft.player)) {
            horizontalSpeed = 0.12F * this.waterSpeedSetting.getValue();
         }

         if (this.accelerateSetting.getValue() && minimumSpeed < horizontalSpeed) {
            float elapsedMillis = (float)(System.currentTimeMillis() - this.accelerationStartMillis);
            float accelerationSeconds = this.accelTimeSetting.getValue();
            float progress = Mth.clamp(elapsedMillis / (accelerationSeconds * 1000.0F), 0.0F, 1.0F);
            horizontalSpeed = Math.min(minimumSpeed + (horizontalSpeed - minimumSpeed) * progress, horizontalSpeed);
         }

         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         if (player.input.keyPresses.jump()) {
            event.setY(verticalSpeed);
            player.setDeltaMovement(player.getDeltaMovement().with(Axis.Y, verticalSpeed));
         } else if (player.input.keyPresses.shift()) {
            event.setY(-verticalSpeed);
            player.setDeltaMovement(player.getDeltaMovement().with(Axis.Y, -verticalSpeed));
            horizontalSpeed /= 2.0;
         } else {
            event.setY(0.0);
            player.setDeltaMovement(player.getDeltaMovement().with(Axis.Y, 0.0));
            if (!player.verticalCollision && this.glideSetting.getValue() != 0.0F) {
               player.setDeltaMovement(player.getDeltaMovement().add(0.0, -this.glideSetting.getValue(), 0.0));
               event.setY(player.getDeltaMovement().y);
            }
         }

         double[] horizontal = MovementPhysics.applyHorizontalMotion(event, horizontalSpeed);
         player.setDeltaMovement(horizontal[0], player.getDeltaMovement().y, horizontal[1]);
      }
   }

   public static void bindReferencedModules(ModuleRegistry modules) {
      speedMineModule = (SpeedMineModule)modules.getModule(SpeedMineModule.class);
   }

   public FastSwimModule() {
      super("FastSwim", "Makes you move faster while in liquids.", ModuleCategory.MOVEMENT);
      NumberSetting<Float> accelTime = new NumberSetting<>("AccelTime", 1.0F, 0.1F, 10.0F);
      accelTime.withUnit("s");
      this.accelTimeSetting = this.add(accelTime.dependsOn(this.accelerateSetting));
      Setting<Float> identitySetting = this.waterSpeedSetting;
      identitySetting.setConfigName("WaterSpeed");
      identitySetting = this.lavaSpeedSetting;
      identitySetting.setConfigName("LavaSpeed");
   }
}
