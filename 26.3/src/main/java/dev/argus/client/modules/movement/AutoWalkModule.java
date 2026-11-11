package dev.argus.client.modules.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.audio.BuiltinSound;
import dev.argus.client.audio.SoundClip;
import dev.argus.client.events.AbstractClientPlayerTickEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.player.SpeedMineModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.IdentifierSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.chat.ChatUtils;
import dev.argus.client.utils.collections.NamedIdentifier;
import dev.argus.client.utils.combat.ExtraBreakTarget;
import dev.argus.client.utils.world.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class AutoWalkModule extends Module {
   private static SpeedMineModule speedMineModule;
   private final Setting<Boolean> autoJumpSetting = this.add(new BooleanSetting("AutoJump", false));
   private final Setting<Boolean> stuckNotifySetting = this.add(new BooleanSetting("StuckNotify", false).markVisibilityDependency());
   private final Setting<NamedIdentifier> soundSetting = this.add(
      new IdentifierSetting("Sound", BuiltinSound.WARNING.getIdentifier()).dependsOn(this.stuckNotifySetting)
   );
   private final Setting<Float> volumeSetting = this.add(new NumberSetting<>("Volume", 1.0F, 0.0F, 1.0F).dependsOn(this.stuckNotifySetting));
   private final Setting<Boolean> pauseSetting = this.add(new BooleanSetting("Pause", false).markAsGroup());
   private final Setting<Boolean> miningSetting = this.add(new BooleanSetting("Mining", false).dependsOn(this.pauseSetting));
   private final Setting<Boolean> eatingSetting = this.add(new BooleanSetting("Eating", false).dependsOn(this.pauseSetting));
   private int stuckTicks;

   private boolean isMining() {
      if (!speedMineModule.isToggled()) {
         return ClientAccess.argusclientMinecraft.gameMode.isDestroying();
      }

      BlockPos miningPosition = speedMineModule.getMiningPos();
      ExtraBreakTarget extraTarget = speedMineModule.getExtraBreakTarget();
      return WorldUtils.canMineBlock(miningPosition) || extraTarget != null && WorldUtils.canMineBlock(extraTarget.getPosition());
   }

   private boolean shouldPauseForAction() {
      return this.eatingSetting.getValue() && ClientAccess.argusclientMinecraft.player.isUsingItem() || this.miningSetting.getValue() && this.isMining();
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      if (!this.shouldPauseForAction()) {
         double verticalDistance = Math.abs(ClientAccess.argusclientMinecraft.player.getY() - ClientAccess.argusclientMinecraft.player.yo);
         if (ClientContext.movementSpeedTracker.getCurrentSpeed() == 0.0 && verticalDistance < 0.2) {
            if (this.stuckTicks == 5 && this.stuckNotifySetting.getValue()) {
               ChatUtils.printChat(Component.literal("You are stuck!"), ChatUtils.moduleSignature(this), ChatUtils.MessagePriority.HIGH);
               SoundClip sound = ClientContext.soundManager.getSound(this.soundSetting.getValue());
               sound.play(this.volumeSetting.getValue());
            }

            this.stuckTicks++;
         } else {
            this.stuckTicks = 0;
         }
      }
   }

   @EventHandler
   private void onAbstractClientPlayerTickEvent(AbstractClientPlayerTickEvent event) {
      if (!this.shouldPauseForAction()) {
         ClientContext.inputOverrides.requestForwardMovement(1.0F);
         ClientContext.inputOverrides.requestForward(true);
         if (this.autoJumpSetting.getValue()) {
            ClientContext.inputOverrides.requestJump(true);
         }
      }
   }

   public static void bindReferencedModules(ModuleRegistry registry) {
      speedMineModule = (SpeedMineModule)registry.getModule(SpeedMineModule.class);
   }

   public AutoWalkModule() {
      super("AutoWalk", "Lets your forward key rest.", ModuleCategory.MOVEMENT);
   }

   static {
      ClientAccess.eventBus.subscribeStatic(AutoWalkModule.class);
   }
}
