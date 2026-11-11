package dev.argus.client.modules.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.ClientPacketListenerHandleExplosionEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.EventStage;
import dev.argus.client.events.LivingEntityJumpFromGroundEvent;
import dev.argus.client.events.LivingEntityRotationEvent;
import dev.argus.client.events.LocalPlayerAiStepEvent;
import dev.argus.client.events.LocalPlayerMoveEvent;
import dev.argus.client.events.LocalPlayerMoveTowardsClosestSpaceEvent;
import dev.argus.client.events.LocalPlayerSendPositionEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.events.StepHeightEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.combat.DamageUtils;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.movement.GrimSpeedHandler;
import dev.argus.client.utils.movement.MovementPhysics;
import dev.argus.client.utils.movement.StrafeSpeedHandler;
import dev.argus.client.utils.movement.StrictStrafeSpeedHandler;
import java.util.function.Predicate;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class SpeedModule extends Module {
   private static ObstaclePasserModule obstaclePasserModule;
   public final Setting<SpeedModule.ModeChoice> modeSetting = this.add(new EnumSetting<>("Mode", SpeedModule.ModeChoice.Strafe));
   public final Setting<Float> speedSetting;
   public final Setting<Boolean> boostSetting;
   public final Setting<Boolean> inLiquidSetting;
   public final Setting<Boolean> useTimerSetting;
   public final Setting<Boolean> pauseInBlocksSetting;
   private boolean skipNextMoveAfterStep;
   public final ElapsedTimer explosionBoostTimer;
   private final EnumSelectionMap<SpeedModeHandler> modeHandlers;

   public void reset() {
      SpeedModeHandler handler = this.modeHandlers.getSelected();
      handler.lastHorizontalDistance = 0.0;
      handler.moveSpeed = 0.0;
      handler.movementStage = this.modeSetting.getValue() == SpeedModule.ModeChoice.OnGround ? 2 : 4;
   }

   @Override
   public String getInfo() {
      SpeedModule.ModeChoice selectedMode = this.modeSetting.getValue();
      return selectedMode.getName();
   }

   @Override
   public void onEnable() {
      this.modeHandlers.getSelected().onEnable();
   }

   @EventHandler(priority = 100)
   private void onLocalPlayerSendPositionEvent(LocalPlayerSendPositionEvent event) {
      if (ClientContext.serverStateManager.getTeleportAckTimer().hasElapsed(10L)) {
         this.modeHandlers.getSelected().onSendPosition(event);
      }
   }

   @EventHandler
   private void onLocalPlayerAiStepEvent(LocalPlayerAiStepEvent event) {
      if (this.modeSetting.getValue() == SpeedModule.ModeChoice.StrafeStrict && !this.shouldPauseSpeed()) {
         ClientAccess.argusclientMinecraft.player.setSprinting(true);
      }
   }

   @EventHandler
   private void onStepHeight(StepHeightEvent event) {
      if (EventStage.Post == event.getStage() && ClientAccess.argusclientMinecraft.player.onGround() && event.getHeight() > 0.6) {
         this.skipNextMoveAfterStep = true;
      }
   }

   @EventHandler
   private void onLocalPlayerMoveTowardsClosestSpaceEvent(LocalPlayerMoveTowardsClosestSpaceEvent event) {
      if (this.modeSetting.getValue() == SpeedModule.ModeChoice.Grim) {
         event.cancel();
      }
   }

   @EventHandler
   private void onClientPacketListenerHandleExplosionEvent(ClientPacketListenerHandleExplosionEvent event) {
      Vec3 explosionPosition = new Vec3(event.getCenterX(), event.getCenterY(), event.getCenterZ());
      double explosionDamage = DamageUtils.getCrystalDamage(explosionPosition, ClientAccess.argusclientMinecraft.player);
      if (MovementPhysics.isLocalPlayerMoving()) {
         if (ClientContext.serverStateManager.getSetbackTimer().hasElapsed(500L)) {
            if (this.boostSetting.getValue()) {
               double explosionDistance = Math.sqrt(ClientAccess.argusclientMinecraft.player.distanceToSqr(explosionPosition));
               if (!(explosionDistance > 6.0) && !(explosionDamage < 4.0)) {
                  this.explosionBoostTimer.reset();
               }
            }
         }
      }
   }

   @EventHandler
   private void onLivingEntityJumpFromGroundEvent(LivingEntityJumpFromGroundEvent event) {
      if (this.modeSetting.getValue() == SpeedModule.ModeChoice.StrafeStrict && MovementPhysics.isLocalPlayerMoving() && !this.shouldPauseSpeed()) {
         event.cancel();
      }
   }

   @EventHandler
   private void onLocalPlayerMoveEvent(LocalPlayerMoveEvent event) {
      if (ClientContext.serverStateManager.getTeleportAckTimer().hasElapsed(10L)) {
         if (this.skipNextMoveAfterStep) {
            this.skipNextMoveAfterStep = false;
         } else {
            this.modeHandlers.getSelected().onMove(event);
         }
      }
   }

   @EventHandler
   private void onRotationEvent(LivingEntityRotationEvent event) {
      if (!event.isJump()) {
         this.modeHandlers.getSelected().onRotationUpdate();
      }
   }

   public boolean shouldYieldToObstaclePasser() {
      return obstaclePasserModule != null && obstaclePasserModule.isPassingObstacles();
   }

   public boolean shouldPauseInBlocks() {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      AABB playerBounds = player.getBoundingBox();
      BlockPos position = player.blockPosition();
      AABB column = new AABB(position.getX(), playerBounds.minY, position.getZ(), position.getX() + 1.0, playerBounds.maxY, position.getZ() + 1.0)
         .deflate(1.0E-7);
      return ClientAccess.argusclientMinecraft.level.collidesWithSuffocatingBlock(player, column) && this.pauseInBlocksSetting.getValue();
   }

   public boolean shouldPauseInLiquid() {
      return PlayerStateUtils.isInLiquid(ClientAccess.argusclientMinecraft.player) && !this.inLiquidSetting.getValue();
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
         this.reset();
      }
   }

   public boolean shouldPauseSpeed() {
      return this.isWorldUnavailable()
         || this.shouldPauseInBlocks()
         || this.shouldPauseInLiquid()
         || PlayerStateUtils.isHungerLimited()
         || ClientAccess.argusclientMinecraft.player.isFallFlying()
         || ClientAccess.argusclientMinecraft.player.isSpectator()
         || ClientContext.baritoneBridge.isPathingOrCalculating();
   }

   public static void bindReferencedModules(ModuleRegistry registry) {
      obstaclePasserModule = (ObstaclePasserModule)registry.getModule(ObstaclePasserModule.class);
      StrafeSpeedHandler.bindModules(registry);
      StrictStrafeSpeedHandler.bindModules(registry);
      GrimSpeedHandler.bindModules(registry);
   }

   public SpeedModule() {
      super("Speed", "Makes you move faster.", ModuleCategory.MOVEMENT);
      Predicate<Object> speedVisibility = ignored -> this.modeSetting.getValue() != SpeedModule.ModeChoice.StrafeStrict
         && this.modeSetting.getValue() != SpeedModule.ModeChoice.Grim;
      this.speedSetting = this.add(new NumberSetting<>("Speed", 1.0F, 0.0F, 10.0F, speedVisibility));
      Predicate<Object> boostVisibility = ignored -> this.modeSetting.getValue() == SpeedModule.ModeChoice.StrafeStrict
         || this.modeSetting.getValue() == SpeedModule.ModeChoice.Strafe;
      this.boostSetting = this.add(new BooleanSetting("Boost", false, boostVisibility));
      Predicate<Object> liquidVisibility = ignored -> this.modeSetting.getValue() != SpeedModule.ModeChoice.Grim;
      this.inLiquidSetting = this.add(new BooleanSetting("InLiquid", false, liquidVisibility));
      Predicate<Object> timerVisibility = ignored -> this.modeSetting.getValue() != SpeedModule.ModeChoice.Grim;
      this.useTimerSetting = this.add(new BooleanSetting("UseTimer", true, timerVisibility));
      Predicate<Object> blocksVisibility = ignored -> this.modeSetting.getValue() != SpeedModule.ModeChoice.Grim;
      this.pauseInBlocksSetting = this.add(new BooleanSetting("PauseInBlocks", true, blocksVisibility));
      this.explosionBoostTimer = new ElapsedTimer();
      this.modeHandlers = new EnumSelectionMap<>(this.modeSetting);
      this.modeHandlers.register(SpeedModule.ModeChoice.Strafe, new StrafeSpeedHandler(this));
      this.modeHandlers.register(SpeedModule.ModeChoice.StrafeStrict, new StrictStrafeSpeedHandler(this));
      this.modeHandlers.register(SpeedModule.ModeChoice.Vanilla, new VanillaSpeedHandler(this));
      this.modeHandlers.register(SpeedModule.ModeChoice.OnGround, new OnGroundSpeedHandler(this));
      this.modeHandlers.register(SpeedModule.ModeChoice.Grim, new GrimSpeedHandler(this));
   }

   public enum ModeChoice implements NamedEntry {
      Strafe,
      StrafeStrict,
      Vanilla,
      OnGround,
      Grim;

      @Override
      public String getName() {
         return this.name();
      }
   }
}
