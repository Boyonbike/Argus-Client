package dev.argus.client.modules.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerMoveEvent;
import dev.argus.client.events.LocalPlayerSendPositionEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.movement.MovementPhysics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Input;

public class FlightModule extends Module {
   private final Setting<FlightModule.ModeChoice> modeSetting = this.add(new EnumSetting<>("Mode", FlightModule.ModeChoice.VANILLA));
   private final Setting<Float> speedSetting = this.add(new NumberSetting<>("Speed", 1.0F, 0.1F, 10.0F));
   private final Setting<Float> verticalSetting = this.add(new NumberSetting<>("Vertical", 1.0F, 0.0F, 5.0F));
   private final Setting<Float> glideSetting = this.add(new NumberSetting<>("Glide", 0.0F, 0.0F, 5.0F));
   private final Setting<Boolean> antiKickSetting = this.add(new BooleanSetting("AntiKick", false));
   public final Setting<Boolean> accelerateSetting = this.add(new BooleanSetting("Accelerate", false).markVisibilityDependency());
   public final Setting<Float> startSpeedSetting = this.add(new NumberSetting<>("StartSpeed", 1.0F, 0.0F, 5.0F).dependsOn(this.accelerateSetting));
   public final Setting<Float> timeSetting = this.add(new NumberSetting<>("Time", 1.0F, 0.1F, 10.0F).withUnit("s").dependsOn(this.accelerateSetting));
   private Abilities flightAbilities;
   private boolean savedFlying;
   private float savedFlyingSpeed;
   private long accelerationStartTimeMillis;

   protected double computeFlightSpeed() {
      return this.accelerateSetting.getValue() && this.modeSetting.getValue() != FlightModule.ModeChoice.JETPACK
         ? MovementPhysics.accelerateTo(
            this.speedSetting.getValue().floatValue(),
            this.startSpeedSetting.getValue().floatValue(),
            this.timeSetting.getValue().floatValue(),
            this.accelerationStartTimeMillis
         )
         : this.speedSetting.getValue().floatValue();
   }

   private void restoreAbilities() {
      if (this.flightAbilities != null) {
         this.flightAbilities.flying = this.savedFlying;
         this.flightAbilities.setFlyingSpeed(this.savedFlyingSpeed);
         this.flightAbilities = null;
      }
   }

   private void applyVanillaFlight() {
      Abilities abilities = ClientAccess.argusclientMinecraft.player.getAbilities();
      if (this.flightAbilities != abilities) {
         this.restoreAbilities();
         this.flightAbilities = abilities;
         this.onServerAbilitiesApplied(abilities);
      }
      abilities.flying = true;
      abilities.setFlyingSpeed((float)(this.computeFlightSpeed() * 0.05F));
   }

   public void onServerAbilitiesApplied(Abilities abilities) {
      if (this.flightAbilities == abilities) {
         this.savedFlying = abilities.flying;
         this.savedFlyingSpeed = abilities.getFlyingSpeed();
      }
   }

   @Override
   public String getInfo() {
      try {
         return FontRenderer.capitalize(this.modeSetting.getValue());
      } catch (Exception failure) {
         return null;
      }
   }

   @Override
   public void onEnable() {
      this.accelerationStartTimeMillis = System.currentTimeMillis();
   }

   @Override
   public void onDisable() {
      this.restoreAbilities();
   }

   @EventHandler
   private void onLocalPlayerSendPositionEvent(LocalPlayerSendPositionEvent event) {
      FlightModule.ModeChoice mode = this.modeSetting.getValue();
      mode.handleSendPosition(this, event);
   }

   @EventHandler
   private void onLocalPlayerMoveEvent(LocalPlayerMoveEvent event) {
      if (!MovementPhysics.isLocalPlayerMoving()) {
         this.accelerationStartTimeMillis = System.currentTimeMillis();
      }

      FlightModule.ModeChoice mode = this.modeSetting.getValue();
      mode.handleMovement(this, event);
      if (this.antiKickSetting.getValue()) {
         if (ClientAccess.argusclientMinecraft.player.onGround()) {
            return;
         }

         double verticalOffset = 0.04;
         int playerTicks = ClientAccess.argusclientMinecraft.player.tickCount;
         if (playerTicks % 2 == 0) {
            verticalOffset = -verticalOffset;
         }

         double eventY = event.getY() + verticalOffset;
         event.setY(eventY);
         ClientAccess.argusclientMinecraft.player.setDeltaMovement(ClientAccess.argusclientMinecraft.player.getDeltaMovement().add(0.0, verticalOffset, 0.0));
      }
   }

   @EventHandler
   public void onPacketReceive(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
         this.accelerationStartTimeMillis = System.currentTimeMillis();
      }
   }

   public FlightModule() {
      super("Flight", "Allows you to fly using magic.", ModuleCategory.MOVEMENT);
      this.verticalSetting.addVisibilityPredicate(ignored -> this.modeSetting.getValue() == FlightModule.ModeChoice.STATIC);
      this.glideSetting.addVisibilityPredicate(ignored -> this.modeSetting.getValue() == FlightModule.ModeChoice.STATIC);
      this.antiKickSetting.addVisibilityPredicate(ignored -> this.modeSetting.getValue() == FlightModule.ModeChoice.STATIC);
      this.accelerateSetting.addVisibilityPredicate(ignored -> this.modeSetting.getValue() != FlightModule.ModeChoice.JETPACK);
      this.startSpeedSetting.addVisibilityPredicate(ignored -> this.modeSetting.getValue() != FlightModule.ModeChoice.JETPACK);
      this.timeSetting.addVisibilityPredicate(ignored -> this.modeSetting.getValue() != FlightModule.ModeChoice.JETPACK);
      this.antiKickSetting.addVisibilityPredicate(ignored -> this.modeSetting.getValue() != FlightModule.ModeChoice.JETPACK);
      this.speedSetting.disableNumericBounds();
      this.verticalSetting.disableNumericBounds();
      this.modeSetting.setChangeCallback(() -> {
         if (this.modeSetting.getValue() != FlightModule.ModeChoice.VANILLA) {
            this.restoreAbilities();
         }
      });
   }

   public enum ModeChoice implements NamedEntry {
      VANILLA("Vanilla") {
         @Override
         public void handleMovement(FlightModule module, LocalPlayerMoveEvent event) {
            module.applyVanillaFlight();
         }
      },
      STATIC("Static") {
         private int jumpTicks;

         @Override
         public void handleMovement(FlightModule module, LocalPlayerMoveEvent event) {
            double verticalSpeed = module.verticalSetting.getValue().floatValue();
            LocalPlayer player = ClientAccess.argusclientMinecraft.player;
            if (player.input.keyPresses.jump()) {
               this.jumpTicks++;
               if (module.antiKickSetting.getValue()) {
                  if (this.jumpTicks >= 17) {
                     verticalSpeed = -0.15;
                  }

                  if (this.jumpTicks >= 21) {
                     this.jumpTicks = 0;
                  }
               }

               event.setY(verticalSpeed);
               player.setDeltaMovement(player.getDeltaMovement().with(Axis.Y, verticalSpeed));
            } else if (player.input.keyPresses.shift()) {
               event.setY(-verticalSpeed);
               player.setDeltaMovement(player.getDeltaMovement().with(Axis.Y, -verticalSpeed));
            } else {
               float idleY = (float)(player.onGround() ? player.getDeltaMovement().y : 0.0);
               event.setY(idleY);
               player.setDeltaMovement(player.getDeltaMovement().with(Axis.Y, idleY));
               if (!player.verticalCollision && module.glideSetting.getValue() != 0.0F) {
                  player.setDeltaMovement(player.getDeltaMovement().add(0.0, -module.glideSetting.getValue(), 0.0));
                  event.setY(player.getDeltaMovement().y);
               }
            }

            double[] horizontal = MovementPhysics.applyHorizontalMotion(event, module.computeFlightSpeed());
            player.setDeltaMovement(horizontal[0], player.getDeltaMovement().y, horizontal[1]);
         }
      },
      JETPACK("JetPack") {
         @Override
         public void handleSendPosition(FlightModule module, LocalPlayerSendPositionEvent event) {
            Input jumpInput = ClientAccess.argusclientMinecraft.player.input.keyPresses;
            if (jumpInput.jump()) {
               ClientAccess.argusclientMinecraft
                  .player
                  .setDeltaMovement(ClientAccess.argusclientMinecraft.player.getDeltaMovement().add(0.0, module.computeFlightSpeed() * 0.1, 0.0));
            }
         }
      },
      SOURCE("Source") {
         @Override
         public void handleMovement(FlightModule module, LocalPlayerMoveEvent event) {
            double speed = module.computeFlightSpeed();
            MovementPhysics.applyHorizontalMotion(event, speed);
            if (MovementPhysics.isLocalPlayerMoving()) {
               float forwardAmount = ClientAccess.argusclientMinecraft.player.input.moveVector.y;
               double forwardDirection = -Math.signum(forwardAmount);
               double pitchDegrees = ClientAccess.argusclientMinecraft.player.getXRot();
               double pitchRadians = Math.toRadians(pitchDegrees);
               double verticalVelocity = forwardDirection * Math.sin(pitchRadians) * speed;
               event.setY(verticalVelocity);
            } else {
               event.setY(0.0);
            }
         }
      };

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      ModeChoice(String displayName) {
         this.displayName = displayName;
      }

      public void handleSendPosition(FlightModule module, LocalPlayerSendPositionEvent event) {
      }

      public void handleMovement(FlightModule module, LocalPlayerMoveEvent event) {
      }
   }
}
