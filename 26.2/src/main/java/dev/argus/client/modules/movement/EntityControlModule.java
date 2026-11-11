package dev.argus.client.modules.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.events.MinecraftDisconnectEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.events.VehicleMoveEvent;
import dev.argus.client.gui.screens.ClientScreen;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.render.RenderUtils;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.KeyUtils;
import dev.argus.client.utils.movement.MovementPhysics;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.phys.Vec3;

public class EntityControlModule extends Module {
   private final Setting<Boolean> copyYawSetting = this.add(new BooleanSetting("CopyYaw", false));
   public final Setting<Boolean> horseJumpSetting = this.add(new BooleanSetting("HorseJump", true));
   private final Setting<Boolean> grimOverflowSetting = this.add(new BooleanSetting("GrimOverflow", false));
   final Setting<Boolean> entitySpeedSetting = this.add(new BooleanSetting("EntitySpeed", true).markVisibilityDependency());
   private final Setting<Double> speedSetting;
   private final Setting<Boolean> noCollisionSetting;
   private final Setting<Boolean> pathFinderSetting;
   private final Setting<Boolean> glideSetting;
   private final Setting<Boolean> accelerateSetting;
   private final Setting<Boolean> flightSetting;
   private final Setting<Float> verticalSetting;
   private boolean useNegativeFlightJitter;
   private long accelerationStartMillis;
   private volatile boolean grimOverflowActive;
   private final Supplier<Vec3> pathfinderMotion;

   public EntityControlModule() {
      super("EntityControl", "Helps with riding entities.", ModuleCategory.MOVEMENT);
      Predicate<Object> speedVisibility = ignored -> this.entitySpeedSetting.isExpanded();
      this.speedSetting = this.add(new NumberSetting<>("Speed", 1.0, 0.1, 20.0, speedVisibility));
      Predicate<Object> noCollisionVisibility = ignored -> this.entitySpeedSetting.isExpanded();
      this.noCollisionSetting = this.add(new BooleanSetting("NoCollision", true, noCollisionVisibility));
      this.pathFinderSetting = this.add(new BooleanSetting("Baritone", false).dependsOn(this.entitySpeedSetting));
      this.pathFinderSetting.hideWithoutBaritone();
      Predicate<Object> glideVisibility = ignored -> this.entitySpeedSetting.isExpanded();
      this.glideSetting = this.add(new BooleanSetting("Glide", false, glideVisibility));
      Predicate<Object> accelerateVisibility = ignored -> this.entitySpeedSetting.isExpanded();
      this.accelerateSetting = this.add(new BooleanSetting("Accelerate", false, accelerateVisibility));
      Predicate<Object> flightVisibility = ignored -> this.entitySpeedSetting.isExpanded();
      this.flightSetting = this.add(new BooleanSetting("Flight", false, flightVisibility).markVisibilityDependency());
      this.verticalSetting = this.add(new NumberSetting<>("Vertical", 10.0F, 0.0F, 20.0F).dependsOn(this.flightSetting, this.entitySpeedSetting));
      this.speedSetting.setChangeCallback(() -> this.accelerationStartMillis = System.currentTimeMillis());
      this.pathfinderMotion = ClientContext.baritoneBridge.isAvailable() ? new EntityControlPathfinder(this)::getMotion : null;
   }

   @Override
   public void onDisable() {
      this.grimOverflowActive = false;
      if (!this.isWorldUnavailable() && ClientAccess.argusclientMinecraft.player.getVehicle() instanceof Boat boat) {
         boat.setNoGravity(false);
      }
   }

   @EventHandler
   private void onVehicleMoveEvent(VehicleMoveEvent event) {
      if (!this.isWorldUnavailable()) {
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         Entity vehicle = player.getVehicle();
         if (vehicle != null) {
            if (!MovementPhysics.isLocalPlayerMoving()) {
               this.accelerationStartMillis = System.currentTimeMillis();
            }

            if (this.copyYawSetting.getValue()) {
               vehicle.setYRot(player.getViewYRot(RenderUtils.getPartialTick()));
            }

            if (!(vehicle instanceof Mob mob && !mob.isSaddled())) {
               double speed = this.speedSetting.getValue();
               double verticalSpeed = this.verticalSetting.getValue().floatValue();
               if (speed >= 20.0) {
                  speed = 19.99;
               }

               if (this.accelerateSetting.getValue()) {
                  speed = MovementPhysics.accelerateTo(this.speedSetting.getValue(), 0.1, this.speedSetting.getValue(), this.accelerationStartMillis);
               }

               Vec3 pathMotion = this.pathfinderMotion == null ? null : this.pathfinderMotion.get();
               if (this.entitySpeedSetting.getValue() && (pathMotion != null || !this.shouldPauseVehicleSpeed())) {
                  double[] horizontalVelocity = pathMotion == null
                     ? MovementPhysics.getHorizontalMotion(player.getViewYRot(RenderUtils.getPartialTick()), player.input, speed)
                     : new double[]{pathMotion.x, pathMotion.z};
                  double velocityY;
                  if (this.flightSetting.getValue()) {
                     if (vehicle instanceof Boat boat) {
                        boat.setNoGravity(true);
                     }

                     boolean descend = KeyUtils.isPhysicallyDown(ClientAccess.argusclientMinecraft.options.keySprint);
                     if (ClientAccess.argusclientMinecraft.screen instanceof ChatScreen || ClientAccess.argusclientMinecraft.screen instanceof ClientScreen) {
                        descend = false;
                     }

                     if (pathMotion != null) {
                        velocityY = Math.abs(pathMotion.y) > 0.04125F ? pathMotion.y : (this.useNegativeFlightJitter ? -0.04125F : 0.04125F);
                     } else if (ClientAccess.argusclientMinecraft.options.keyJump.isDown()) {
                        velocityY = verticalSpeed;
                     } else if (!this.glideSetting.getValue() && !descend) {
                        velocityY = this.useNegativeFlightJitter ? -0.04125F : 0.04125F;
                     } else {
                        velocityY = -verticalSpeed;
                     }
                  } else {
                     velocityY = event.getY();
                  }

                  event.setVelocity(new Vec3(horizontalVelocity[0], velocityY, horizontalVelocity[1]));
                  this.useNegativeFlightJitter = !this.useNegativeFlightJitter;
               }
            }
         }
      }
   }

   boolean isEntityPathfindingEnabled() {
      return this.isToggled() && this.pathFinderSetting.getValue() && this.entitySpeedSetting.getValue() && this.flightSetting.getValue();
   }

   double getEntityPathSpeed() {
      return Math.min(this.speedSetting.getValue(), 19.99);
   }

   double getEntityPathVerticalSpeed() {
      return this.verticalSetting.getValue().floatValue();
   }

   private boolean shouldPauseVehicleSpeed() {
      Entity vehicle = ClientAccess.argusclientMinecraft.player.getVehicle();
      return vehicle != null && vehicle.horizontalCollision && this.noCollisionSetting.getValue();
   }

   @EventHandler
   private void updateGrimOverflow(LocalPlayerPreTickEvent event) {
      Entity vehicle = ClientAccess.argusclientMinecraft.player.getVehicle();
      this.grimOverflowActive = this.grimOverflowSetting.getValue() && vehicle instanceof AbstractHorse && this.isControlledByLocalPlayer(vehicle);
   }

   private boolean isControlledByLocalPlayer(Entity entity) {
      return entity == null ? false : entity.getControllingPassenger() == ClientAccess.argusclientMinecraft.player;
   }

   @EventHandler
   private void resetGrimOverflow(MinecraftDisconnectEvent event) {
      this.grimOverflowActive = false;
   }

   public boolean isGrimOverflowActive() {
      return this.grimOverflowActive;
   }

   @EventHandler
   public void onPacketReceive(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
         this.accelerationStartMillis = System.currentTimeMillis();
      }
   }
}
