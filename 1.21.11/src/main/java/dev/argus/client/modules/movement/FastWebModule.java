package dev.argus.client.modules.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.EventStage;
import dev.argus.client.events.LocalPlayerAiStepEvent;
import dev.argus.client.events.LocalPlayerMoveEvent;
import dev.argus.client.events.LocalPlayerSendPositionEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.client.AntiCheatModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.movement.MovementPhysics;
import dev.argus.client.utils.network.PacketSender;
import java.util.function.Predicate;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class FastWebModule extends Module {
   private static AntiCheatModule antiCheatModule;
   final Setting<FastWebModule.ModeChoice> modeSetting = this.add(new EnumSetting<>("Mode", FastWebModule.ModeChoice.PLAIN));
   private final Setting<Boolean> horizontalSetting;
   private final Setting<Float> speedSetting;
   private final Setting<Boolean> verticalSetting;
   private final Setting<Float> vSpeedSetting;
   private final Setting<Boolean> upwardsSetting;
   private boolean usingStrictTimer;

   public FastWebModule() {
      super("FastWeb", "Makes you faster in cobwebs.", ModuleCategory.MOVEMENT);
      Predicate<Object> showMovement = ignored -> this.modeSetting.getValue() != FastWebModule.ModeChoice.GRIM;
      this.horizontalSetting = this.add(new BooleanSetting("Horizontal", false, showMovement).markVisibilityDependency());
      this.speedSetting = this.add(new NumberSetting<>("Speed", 1.0F, 1.0F, 2.0F, showMovement).dependsOn(this.horizontalSetting));
      this.verticalSetting = this.add(new BooleanSetting("Vertical", false, showMovement).markVisibilityDependency());
      this.vSpeedSetting = this.add(new NumberSetting<>("VSpeed", 1.0F, 1.0F, 2.0F, showMovement).dependsOn(this.verticalSetting));
      Predicate<Object> showUpwards = ignored -> this.modeSetting.getValue() == FastWebModule.ModeChoice.PLAIN;
      this.upwardsSetting = this.add(new BooleanSetting("Upwards", false, showUpwards).dependsOn(this.verticalSetting));
      this.usingStrictTimer = false;
      this.setDrawn(false);
   }

   public void reset() {
      ClientContext.playerTickController.releaseMultiplier(this);
      this.usingStrictTimer = false;
   }

   public boolean isUsingStrictTimer() {
      return this.usingStrictTimer;
   }

   private boolean isStrictMode() {
      return this.modeSetting.getValue() == FastWebModule.ModeChoice.STRICT;
   }

   public boolean isGrimEnabled() {
      return !this.isToggled() ? false : this.modeSetting.getValue() == FastWebModule.ModeChoice.GRIM;
   }

   @EventHandler(priority = -100)
   private void onAiStep(LocalPlayerAiStepEvent event) {
      if (this.isGrimEnabled()) {
         if (ClientAccess.argusclientMinecraft.player.input.keyPresses.jump()) {
            if (PlayerStateUtils.isInCobweb(ClientAccess.argusclientMinecraft.player)) {
               ClientAccess.argusclientMinecraft.player.setSprinting(false);
            }
         }
      }
   }

   @EventHandler
   private void onMove(LocalPlayerMoveEvent event) {
      if (!ClientContext.serverStateManager.getSetbackTimer().hasElapsed(500L)) {
         this.reset();
      } else if (!PlayerStateUtils.isInCobweb(ClientAccess.argusclientMinecraft.player)) {
         this.reset();
      } else if (this.modeSetting.getValue() == FastWebModule.ModeChoice.GRIM) {
         this.reset();
      } else {
         float horizontalMultiplier = this.speedSetting.getValue();
         if (this.verticalSetting.getValue()) {
            horizontalMultiplier = this.applyVerticalDescent(event, horizontalMultiplier);
            if (this.upwardsSetting.getValue()) {
               Input jumpKeys = ClientAccess.argusclientMinecraft.player.input.keyPresses;
               if (jumpKeys.jump() && !this.isStrictMode()) {
                  LocalPlayer ascentPlayer = ClientAccess.argusclientMinecraft.player;
                  Vec3 ascentVelocity = ascentPlayer.getDeltaMovement();
                  Axis ascentAxis = Axis.Y;
                  double upwardVelocity = this.vSpeedSetting.getValue().floatValue();
                  Vec3 updatedAscentVelocity = ascentVelocity.with(ascentAxis, upwardVelocity);
                  ascentPlayer.setDeltaMovement(updatedAscentVelocity);
                  double upwardEventY = this.vSpeedSetting.getValue().floatValue();
                  event.setY(upwardEventY);
               }
            }
         }

         if (MovementPhysics.isLocalPlayerMoving() && this.horizontalSetting.getValue()) {
            boolean includeEffects = true;
            double horizontalSpeed = MovementPhysics.getBaseMoveSpeed(includeEffects) * horizontalMultiplier;
            LocalPlayerMoveEvent horizontalEvent = event;
            double[] horizontalMotion = MovementPhysics.applyHorizontalMotion(horizontalEvent, horizontalSpeed);
            LocalPlayer horizontalReceiverSnapshot = ClientAccess.argusclientMinecraft.player;
            double horizontalXSnapshot = horizontalMotion[0];
            LocalPlayer yVelocityPlayer = ClientAccess.argusclientMinecraft.player;
            double horizontalZ = horizontalMotion[1];
            double preservedY = yVelocityPlayer.getDeltaMovement().y;
            horizontalReceiverSnapshot.setDeltaMovement(horizontalXSnapshot, preservedY, horizontalZ);
         }
      }
   }

   private float applyVerticalDescent(LocalPlayerMoveEvent event, float horizontalMultiplier) {
      if (!ClientAccess.argusclientMinecraft.player.input.keyPresses.shift() || ClientAccess.argusclientMinecraft.player.onGround()) {
         this.reset();
         return horizontalMultiplier;
      } else if (this.isStrictMode()) {
         this.usingStrictTimer = true;
         ClientContext.playerTickController.requestMultiplier(this, this.vSpeedSetting.getValue() * 6.0F);
         return horizontalMultiplier;
      } else {
         this.reset();
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         player.setDeltaMovement(player.getDeltaMovement().with(Axis.Y, -this.vSpeedSetting.getValue()));
         event.setY(-this.vSpeedSetting.getValue());
         return Math.min(horizontalMultiplier, 1.2F);
      }
   }

   @EventHandler
   private void onSendPosition(LocalPlayerSendPositionEvent event) {
      if (EventStage.Pre == event.getStage()) {
         Vec3 displacement = new Vec3(
            currentXDisplacement(),
            ClientAccess.argusclientMinecraft.player.getY() - ClientAccess.argusclientMinecraft.player.yo,
            ClientAccess.argusclientMinecraft.player.getZ() - ClientAccess.argusclientMinecraft.player.zo
         );
         if (this.isGrimEnabled() && displacement.lengthSqr() > 0.0) {
            AABB bounds = ClientAccess.argusclientMinecraft.player.getBoundingBox();
            BlockPos.betweenClosedStream(bounds)
               .map(BlockPos::immutable)
               .filter(webPos -> ClientAccess.argusclientMinecraft.level.getBlockState(webPos).is(Blocks.COBWEB))
               .forEach(webPos -> {
                  if (!antiCheatModule.is2b2tEnabled()) {
                     PacketSender.sendPlayerAction(Action.ABORT_DESTROY_BLOCK, webPos, Direction.UP);
                  }

                  PacketSender.sendPlayerAction(Action.STOP_DESTROY_BLOCK, webPos, Direction.UP);
               });
         }

         if (this.modeSetting.getValue() == FastWebModule.ModeChoice.GRIM
            && ClientAccess.argusclientMinecraft.player.input.keyPresses.shift()
            && !ClientAccess.argusclientMinecraft.player.onGround()
            && PlayerStateUtils.isInCobweb(ClientAccess.argusclientMinecraft.player)) {
            double playerY = ClientAccess.argusclientMinecraft.player.getY();
            double randomizedY = playerY + Math.random();
            double spoofedY = randomizedY + 1.0;
            event.setY(spoofedY);
         }
      }
   }

   private static double currentXDisplacement() {
      return ClientAccess.argusclientMinecraft.player.getX() - ClientAccess.argusclientMinecraft.player.xo;
   }

   public static void bindModules(ModuleRegistry registry) {
      antiCheatModule = (AntiCheatModule)registry.getModule(AntiCheatModule.class);
   }

   public enum ModeChoice implements NamedEntry {
      PLAIN("Plain"),
      STRICT("Strict"),
      LAGBACK("LagBack"),
      GRIM("Grim");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      ModeChoice(String displayName) {
         this.displayName = displayName;
      }
   }
}
