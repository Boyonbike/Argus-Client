package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.client.AntiCheatModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.inventory.ItemUtils;
import dev.argus.client.utils.movement.MovementPhysics;
import dev.argus.client.utils.movement.WallsVelocityHandler;
import dev.argus.client.utils.network.InteractPacketUtils;
import dev.argus.client.utils.network.PacketSender;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class CriticalsModule extends Module {
   private static AuraModule auraModule = ClientContext.getModule(AuraModule.class);
   private final Setting<CriticalsModule.ModeChoice> modeSetting = this.add(new EnumSetting<>("Mode", CriticalsModule.ModeChoice.PACKET));
   private final Setting<Boolean> pauseOnMoveSetting = this.add(new BooleanSetting("PauseOnMove", false));
   private final Setting<Boolean> onlyFullSetting = this.add(new BooleanSetting("OnlyFull", false));
   private final Setting<Boolean> onlyWeaponSetting = this.add(new BooleanSetting("OnlyWeapon", true));
   private boolean pendingPositionCorrection;

   private boolean isCriticalWeapon(ItemStack stack) {
      Item mace = Items.MACE;
      return stack.is(mace) ? true : ItemUtils.isSword(stack) || ItemUtils.isAxe(stack) || stack.is(ItemTags.PICKAXES);
   }

   private float clampPitch(float pitch) {
      float lowerBound = -90.0F;
      float upperBound = 90.0F;
      return Math.clamp(pitch, lowerBound, upperBound);
   }

   @Override
   public String getInfo() {
      return this.modeSetting.getValue().getName();
   }

   protected boolean canPerformCritical() {
      if (this.onlyWeaponSetting.getValue()) {
         ItemStack heldStack = ClientAccess.argusclientMinecraft.player.getMainHandItem();
         if (!this.isCriticalWeapon(heldStack)) {
            return false;
         }
      }

      if ((this.pauseOnMoveSetting.getValue() || this.modeSetting.getValue() == CriticalsModule.ModeChoice.GRIMV3) && MovementPhysics.isLocalPlayerMoving()) {
         return false;
      }

      if (this.modeSetting.getValue() == CriticalsModule.ModeChoice.GRIMV3) {
         if (this.onlyFullSetting.getValue()) {
            double eyeY = ClientAccess.argusclientMinecraft.player.getEyeY();
            if (!WallsVelocityHandler.hasPlayerCollisionAtHeight(eyeY)) {
               return false;
            }
         }

         return ClientAccess.argusclientMinecraft.player.onGround() && WallsVelocityHandler.hasUsableGroundCollision();
      } else {
         return this.modeSetting.getValue() == CriticalsModule.ModeChoice.GRIM && !ClientAccess.argusclientMinecraft.player.onGround()
            ? true
            : ClientAccess.argusclientMinecraft.player.onGround()
               && ClientAccess.argusclientMinecraft.player.verticalCollision
               && !ClientAccess.argusclientMinecraft.player.isInLava()
               && !ClientAccess.argusclientMinecraft.player.isInWater();
      }
   }

   public CriticalsModule.ModeChoice getMode() {
      return this.modeSetting.getValue();
   }

   @EventHandler
   private void onLocalPlayerPreTick(LocalPlayerPreTickEvent ignoredEvent) {
      float[] rotations = new float[2];
      LocalPlayer yawSource = ClientAccess.argusclientMinecraft.player;
      rotations[0] = yawSource.getYRot();
      LocalPlayer pitchSource = ClientAccess.argusclientMinecraft.player;
      rotations[1] = pitchSource.getXRot();
      if (auraModule.getTarget() != null && auraModule.isToggled()) {
         rotations = RotationManager.getRotationsToEntity(auraModule.getTarget());
      }

      if (this.pendingPositionCorrection) {
         double playerX = ClientAccess.argusclientMinecraft.player.getX();
         double x = playerX + 1.0E-4F;
         double y = ClientAccess.argusclientMinecraft.player.getY();
         double z = ClientAccess.argusclientMinecraft.player.getZ();
         float yaw = rotations[0];
         float pitch = rotations[1];
         float pitchAdjustment = AntiCheatModule.ROTATION_JITTER_STEP;
         pitch -= pitchAdjustment * 2.0F;
         float clampedPitch = this.clampPitch(pitch);
         PacketSender.sendPositionRotation(x, y, z, yaw, clampedPitch, true);
         this.pendingPositionCorrection = false;
      }
   }

   @EventHandler
   private void onConnectionSendPacketEvent(PacketEvent.Send event) {
      Entity attackedTarget = event.getPacket() instanceof ServerboundAttackPacket attack ? InteractPacketUtils.getEntity(attack)
         : event.getPacket() instanceof ServerboundInteractPacket interact && InteractPacketUtils.getAction(interact) == InteractPacketUtils.Action.ATTACK ? InteractPacketUtils.getEntity(interact)
         : null;
      if (attackedTarget != null && this.shouldApplyCritical(attackedTarget)) {
         Entity target = attackedTarget;
         if (target == null) {
            return;
         }

         if (!event.isCancelled()) {
            this.performCritical();
         }

         BlockableEventLoop executor = ClientAccess.argusclientMinecraft;
         Runnable criticalEffect = () -> ClientAccess.argusclientMinecraft.player.crit(target);
         executor.execute(criticalEffect);
      }
   }

   protected void performCritical() {
      float[] rotations = new float[2];
      LocalPlayer yawSource = ClientAccess.argusclientMinecraft.player;
      rotations[0] = yawSource.getYRot();
      LocalPlayer pitchSource = ClientAccess.argusclientMinecraft.player;
      rotations[1] = pitchSource.getXRot();
      if (auraModule.getTarget() != null && auraModule.isToggled()) {
         rotations = RotationManager.getRotationsToEntity(auraModule.getTarget());
      }

      switch ((CriticalsModule.ModeChoice)this.modeSetting.getValue()) {
         case PACKET:
            double[] offsets = new double[]{0.0625101, 0.0};
            this.sendPositionOffsets(offsets);
            break;
         case NCP:
            LocalPlayer ncpPlayer = ClientAccess.argusclientMinecraft.player;
            if (PlayerStateUtils.isInCobweb(ncpPlayer)) {
               double[] offsetsx = new double[]{1.1E-7F, 1.1E-8F, 0.0};
               this.sendPositionOffsets(offsetsx);
            } else {
               double[] offsetsx = new double[]{1.1E-7F, 1.1E-8F};
               this.sendPositionOffsets(offsetsx);
            }
            break;
         case GRIM:
            LocalPlayer grimXSource = ClientAccess.argusclientMinecraft.player;
            double grimX = grimXSource.getX();
            LocalPlayer grimYSource = ClientAccess.argusclientMinecraft.player;
            double grimPlayerY = grimYSource.getY();
            double grimY = grimPlayerY - 1.0E-6;
            LocalPlayer grimZSource = ClientAccess.argusclientMinecraft.player;
            double grimZ = grimZSource.getZ();
            float grimYaw = rotations[0];
            float grimPitch = rotations[1];
            boolean grimOnGround = false;
            PacketSender.sendPositionRotation(grimX, grimY, grimZ, grimYaw, grimPitch, grimOnGround);
            break;
         case GRIMV3:
            LocalPlayer firstXSource = ClientAccess.argusclientMinecraft.player;
            double firstX = firstXSource.getX();
            LocalPlayer firstYSource = ClientAccess.argusclientMinecraft.player;
            double firstPlayerY = firstYSource.getY();
            double firstY = firstPlayerY + 0.0626;
            LocalPlayer firstZSource = ClientAccess.argusclientMinecraft.player;
            double firstZ = firstZSource.getZ();
            float firstYaw = rotations[0];
            float firstPitch = rotations[1] + AntiCheatModule.ROTATION_JITTER_STEP;
            float firstClampedPitch = this.clampPitch(firstPitch);
            boolean firstOnGround = false;
            PacketSender.sendPositionRotation(firstX, firstY, firstZ, firstYaw, firstClampedPitch, firstOnGround);
            LocalPlayer secondXSource = ClientAccess.argusclientMinecraft.player;
            double secondX = secondXSource.getX();
            LocalPlayer secondYSource = ClientAccess.argusclientMinecraft.player;
            double secondPlayerY = secondYSource.getY();
            double secondY = secondPlayerY + 0.0455;
            LocalPlayer secondZSource = ClientAccess.argusclientMinecraft.player;
            double secondZ = secondZSource.getZ();
            float secondYaw = rotations[0];
            float secondPitch = rotations[1] - AntiCheatModule.ROTATION_JITTER_STEP;
            float secondClampedPitch = this.clampPitch(secondPitch);
            boolean secondOnGround = false;
            PacketSender.sendPositionRotation(secondX, secondY, secondZ, secondYaw, secondClampedPitch, secondOnGround);
            this.pendingPositionCorrection = true;
      }
   }

   private void sendPositionOffsets(double... offsets) {
      for (double offset : offsets) {
         double x = ClientAccess.argusclientMinecraft.player.getX();
         double y = ClientAccess.argusclientMinecraft.player.getY() + offset;
         double z = ClientAccess.argusclientMinecraft.player.getZ();
         PacketSender.sendPosition(x, y, z, false);
      }
   }

   private boolean shouldApplyCritical(Entity target) {
      if (!this.canPerformCritical()) {
         return false;
      }
      return auraModule.isToggled() && auraModule.criticalAttackFlag && this.modeSetting.getValue() != CriticalsModule.ModeChoice.GRIM
         ? false
         : target != null && target.isAlive() && !(target instanceof EndCrystal);
   }

   public static void bindModules(ModuleRegistry registry) {
      auraModule = (AuraModule)registry.getModule(AuraModule.class);
   }

   public CriticalsModule() {
      super("Criticals", "Turns your hits into critical ones.", ModuleCategory.COMBAT);
      this.pauseOnMoveSetting.addVisibilityPredicate(unused -> {
         Setting<CriticalsModule.ModeChoice> modeSource = this.modeSetting;
         return modeSource.getValue() != CriticalsModule.ModeChoice.GRIMV3;
      });
      this.onlyFullSetting.addVisibilityPredicate(unused -> {
         Setting<CriticalsModule.ModeChoice> modeSource = this.modeSetting;
         return modeSource.getValue() == CriticalsModule.ModeChoice.GRIMV3;
      });
   }

   public enum ModeChoice implements NamedEntry {
      PACKET("Packet"),
      NCP("NCP"),
      GRIM("Grim"),
      GRIMV3("2b2t");

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
