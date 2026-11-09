package dev.argus.client.modules.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.EventStage;
import dev.argus.client.events.LocalPlayerSendPositionEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.client.AntiCheatModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.SettingLabelCondition;
import dev.argus.client.utils.network.PacketSender;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.phys.AABB;

public class FastClimbModule extends Module {
   private static AntiCheatModule antiCheatModule;
   public final Setting<Float> speedSetting = this.add(new NumberSetting<>("Speed", 0.2F, 0.0F, 1.0F));
   public final Setting<Boolean> airborneSetting = this.add(new BooleanSetting("Airborne", true));
   private boolean climbableWhenGrounded;

   public boolean canFastClimb() {
      return !this.isToggled() ? false : this.airborneSetting.getValue() || this.climbableWhenGrounded;
   }

   private boolean isClimbableAtPlayer(BlockPos position) {
      return ClientAccess.argusclientMinecraft.level.getBlockState(position).is(BlockTags.CLIMBABLE)
         && BlockPos.containing(ClientAccess.argusclientMinecraft.player.position()).equals(position);
   }

   private void updateGroundedClimbable() {
      if (ClientAccess.argusclientMinecraft.player.onGround()) {
         boolean foundClimbable = false;
         AABB bounds = ClientAccess.argusclientMinecraft.player.getBoundingBox().inflate(0.1);

         for (BlockPos position : BlockPos.betweenClosed(bounds)) {
            if (this.isClimbableAtPlayer(position)) {
               foundClimbable = true;
               break;
            }
         }

         this.climbableWhenGrounded = foundClimbable;
      }
   }

   @EventHandler
   private void onSendPosition(LocalPlayerSendPositionEvent event) {
      if (EventStage.Pre == event.getStage()) {
         this.updateGroundedClimbable();
      }

      if (event.getStage() != EventStage.Post) {
         if (RotationManager.isMovementSyncEnabled()) {
            if (this.canFastClimb()) {
               AABB scanBounds = ClientAccess.argusclientMinecraft.player.getBoundingBox().inflate(0.1);
               BlockPos.betweenClosedStream(scanBounds).map(BlockPos::immutable).filter(this::isClimbableAtPlayer).forEach(position -> {
                  if (!antiCheatModule.is2b2tEnabled()) {
                     PacketSender.sendPlayerAction(Action.ABORT_DESTROY_BLOCK, position, Direction.UP);
                  }

                  PacketSender.sendPlayerAction(Action.STOP_DESTROY_BLOCK, position, Direction.UP);
               });
            }
         }
      }
   }

   public static void bindModules(ModuleRegistry modules) {
      antiCheatModule = (AntiCheatModule)modules.getModule(AntiCheatModule.class);
   }

   public FastClimbModule() {
      super("FastClimb", "Makes you climb faster.", ModuleCategory.MOVEMENT);
      this.speedSetting.setBoundaryLabel("Ignore", SettingLabelCondition.MIN);
   }
}
