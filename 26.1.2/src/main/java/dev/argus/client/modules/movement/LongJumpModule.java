package dev.argus.client.modules.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.EventStage;
import dev.argus.client.events.KeyboardInputTickEvent;
import dev.argus.client.events.LocalPlayerMoveEvent;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.events.LocalPlayerSendPositionEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.render.RenderUtils;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.inventory.EnchantmentUtils;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.inventory.ItemUtils;
import dev.argus.client.utils.math.Vec3Utils;
import dev.argus.client.utils.movement.MovementPhysics;
import java.util.function.Predicate;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;

public class LongJumpModule extends Module {
   public final Setting<LongJumpModule.ModeChoice> modeSetting = this.add(new EnumSetting<>("Mode", LongJumpModule.ModeChoice.PLAIN));
   public final Setting<Float> jumpSpeedSetting;
   public final Setting<Boolean> inLiquidSetting;
   public final Setting<Integer> amountSetting;
   public final Setting<Boolean> autoJumpSetting;
   public final Setting<Boolean> useTimerSetting;
   public final Setting<Boolean> autoDisableSetting;
   protected double moveSpeed;
   protected double previousHorizontalDistance;
   protected int movementStage;
   private boolean alternateBoost;
   private boolean jumpRequested;
   private int spearUses;

   private InventoryUtils.SwapSlot findLungeWeapon() {
      Predicate<ItemStack> weaponPredicate = stack -> ItemUtils.isSpear(stack) && EnchantmentUtils.hasEnchantment(Enchantments.LUNGE, stack);
      return InventoryUtils.findSwapSlot(weaponPredicate, true);
   }

   private void handleSpearLanding() {
      boolean completedJump = this.autoDisableSetting.getValue() ? this.jumpRequested : this.spearUses > 0;
      if (this.autoDisableSetting.getValue() && completedJump) {
         this.disable();
      } else {
         if (this.autoJumpSetting.getValue()) {
            ClientContext.inputOverrides.requestJump(true);
            this.jumpRequested = true;
         } else {
            this.jumpRequested = false;
         }

         this.spearUses = 0;
      }
   }

   @Override
   public void onEnable() {
      this.jumpRequested = false;
      this.spearUses = 0;
   }

   @EventHandler
   private void onSpearTick(LocalPlayerPreTickEvent event) {
      if (this.modeSetting.getValue() == LongJumpModule.ModeChoice.SPEAR) {
         int selectedSlot = InventoryUtils.getSelectedSlot();
         InventoryUtils.SwapSlot weapon = this.findLungeWeapon();
         if (weapon.isFound()) {
            if (ClientAccess.argusclientMinecraft.player.onGround()) {
               this.handleSpearLanding();
            } else if (this.jumpRequested) {
               if (!ClientAccess.argusclientMinecraft.player.isFallFlying()) {
                  if (!PlayerStateUtils.isHungerLimited()) {
                     int usesBeforeLunge = this.spearUses;
                     Integer useLimit = this.amountSetting.getValue();
                     if (usesBeforeLunge < useLimit) {
                        if (!PlayerStateUtils.isInLiquid(ClientAccess.argusclientMinecraft.player)) {
                           float attackStrength = ClientAccess.argusclientMinecraft.player.getAttackStrengthScale(1.0F);
                           if (!(attackStrength < 1.0F)) {
                              this.performSpearLunge(weapon, selectedSlot);
                              this.spearUses++;
                              if (this.autoDisableSetting.getValue()) {
                                 int usesAfterLunge = this.spearUses;
                                 Integer completedUseLimit = this.amountSetting.getValue();
                                 if (usesAfterLunge >= completedUseLimit) {
                                    this.disable();
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void performSpearLunge(InventoryUtils.SwapSlot weapon, int selectedSlot) {
      ItemStack componentStack = weapon.getStack();
      PiercingWeapon piercingWeapon = componentStack.get(DataComponents.PIERCING_WEAPON);
      if (weapon.hand() == InteractionHand.OFF_HAND) {
         int containerSlot = InventoryUtils.toMenuSlot(selectedSlot);
         InventoryUtils.clickSlot(containerSlot, 40, ContainerInput.SWAP);
      } else {
         InventoryUtils.swapTo(weapon);
      }

      ClientAccess.argusclientMinecraft.gameMode.piercingAttack(piercingWeapon);
      ClientAccess.argusclientMinecraft.player.swing(InteractionHand.MAIN_HAND);
      ClientAccess.argusclientMinecraft.player.resetOnlyAttackStrengthTicker();
      if (weapon.hand() == InteractionHand.OFF_HAND) {
         int convertedRestoreSlot = InventoryUtils.toMenuSlot(selectedSlot);
         InventoryUtils.clickSlot(convertedRestoreSlot, 40, ContainerInput.SWAP);
      } else {
         InventoryUtils.swapBack(selectedSlot, weapon);
      }
   }

   @EventHandler(priority = 100)
   public void onJumpInput(KeyboardInputTickEvent event) {
      if (event.getInput().keyPresses.jump()) {
         this.jumpRequested = true;
      }
   }

   @EventHandler(priority = 100)
   private void measureHorizontalDistance(LocalPlayerSendPositionEvent event) {
      if (event.getStage() != EventStage.Post && this.modeSetting.getValue() == LongJumpModule.ModeChoice.PLAIN) {
         if (!MovementPhysics.isLocalPlayerMoving()) {
            this.movementStage = 4;
         }

         double deltaX = ClientAccess.argusclientMinecraft.player.getX() - ClientAccess.argusclientMinecraft.player.xo;
         double deltaZ = ClientAccess.argusclientMinecraft.player.getZ() - ClientAccess.argusclientMinecraft.player.zo;
         double distanceSquared = deltaX * deltaX + deltaZ * deltaZ;
         this.previousHorizontalDistance = Math.sqrt(distanceSquared);
      }
   }

   @EventHandler
   private void applyPlainMovement(LocalPlayerMoveEvent event) {
      if (this.modeSetting.getValue() == LongJumpModule.ModeChoice.PLAIN
         && !this.collidesWithSuffocatingColumn()
         && !this.isLiquidBlocked()
         && !ClientAccess.argusclientMinecraft.player.isFallFlying()
         && !ClientAccess.argusclientMinecraft.player.isSpectator()) {
         double motionY = event.getY();
         if (this.useTimerSetting.getValue()) {
            if (ClientContext.serverStateManager.getSetbackTimer().hasElapsed(250L)) {
               ClientContext.tickRateController.requestMultiplier(this, 1.0888F);
            } else {
               ClientContext.tickRateController.releaseMultiplier(this);
            }
         }

         if (!ClientContext.serverStateManager.getSetbackTimer().hasElapsed(250L) && this.autoDisableSetting.getValue()) {
            this.toggle();
         }

         double jumpSpeedScale = 2.0F * this.jumpSpeedSetting.getValue();
         double baseSpeed = 0.2873 * jumpSpeedScale;
         if (this.movementStage == 1 && MovementPhysics.isLocalPlayerMoving()) {
            this.moveSpeed = 1.35 * MovementPhysics.applySpeedEffects(false, baseSpeed) - 0.01;
         } else if (this.movementStage == 2 && MovementPhysics.isLocalPlayerMoving()) {
            motionY = 0.42 + MovementPhysics.getJumpBoost();
            this.moveSpeed = this.moveSpeed * (this.alternateBoost ? 1.6835 : 1.395);
         } else if (this.movementStage == 3) {
            this.moveSpeed = this.previousHorizontalDistance - 0.66F * (this.previousHorizontalDistance - MovementPhysics.applySpeedEffects(true, baseSpeed));
            this.alternateBoost = !this.alternateBoost;
         } else {
            LocalPlayer player = ClientAccess.argusclientMinecraft.player;
            if ((
                  player.verticalCollision
                     || !ClientAccess.argusclientMinecraft.level.noCollision(player.getBoundingBox().move(0.0, player.getDeltaMovement().y, 0.0))
               )
               && this.movementStage > 0) {
               this.movementStage = MovementPhysics.isLocalPlayerMoving() ? 1 : 0;
            }

            this.moveSpeed = this.previousHorizontalDistance - this.previousHorizontalDistance / 159.0;
         }

         this.moveSpeed = Math.max(this.moveSpeed, MovementPhysics.applySpeedEffects(false, baseSpeed));
         double motionX = 0.0;
         double motionZ = 0.0;
         if (MovementPhysics.isLocalPlayerMoving()) {
            LocalPlayer player = ClientAccess.argusclientMinecraft.player;
            float yaw = player.getViewYRot(RenderUtils.getPartialTick());
            double[] horizontal = MovementPhysics.getHorizontalMotion(yaw, player.input, this.moveSpeed);
            motionX = horizontal[0];
            motionZ = horizontal[1];
         }

         Vec3Utils.setComponents(event.getVelocity(), motionX, motionY, motionZ);
         if (MovementPhysics.isLocalPlayerMoving()) {
            this.movementStage++;
         }
      }
   }

   public boolean collidesWithSuffocatingColumn() {
      AABB playerBounds = ClientAccess.argusclientMinecraft.player.getBoundingBox();
      AABB columnBox = new AABB(
         ClientAccess.argusclientMinecraft.player.blockPosition().getX(),
         playerBounds.minY,
         ClientAccess.argusclientMinecraft.player.blockPosition().getZ(),
         ClientAccess.argusclientMinecraft.player.blockPosition().getX() + 1.0,
         playerBounds.maxY,
         ClientAccess.argusclientMinecraft.player.blockPosition().getZ() + 1.0
      );
      double inset = 1.0E-7;
      AABB collisionBox = columnBox.deflate(inset);
      return ClientAccess.argusclientMinecraft.level.collidesWithSuffocatingBlock(ClientAccess.argusclientMinecraft.player, collisionBox);
   }

   public boolean isLiquidBlocked() {
      return PlayerStateUtils.isInLiquid(ClientAccess.argusclientMinecraft.player) && !this.inLiquidSetting.getValue();
   }

   public LongJumpModule() {
      super("LongJump", "Makes you jump fast 'n far.", ModuleCategory.MOVEMENT);
      Predicate<Object> jumpSpeedVisible = ignored -> this.modeSetting.getValue() == LongJumpModule.ModeChoice.PLAIN;
      this.jumpSpeedSetting = this.add(new NumberSetting<>("JumpSpeed", 1.0F, 0.0F, 5.0F, jumpSpeedVisible));
      Predicate<Object> inLiquidVisible = ignored -> this.modeSetting.getValue() == LongJumpModule.ModeChoice.PLAIN;
      this.inLiquidSetting = this.add(new BooleanSetting("InLiquid", false, inLiquidVisible));
      Predicate<Object> amountVisible = ignored -> this.modeSetting.getValue() == LongJumpModule.ModeChoice.SPEAR;
      this.amountSetting = this.add(new NumberSetting<>("Amount", 2, 1, 4, amountVisible));
      Predicate<Object> autoJumpVisible = ignored -> this.modeSetting.getValue() == LongJumpModule.ModeChoice.SPEAR;
      this.autoJumpSetting = this.add(new BooleanSetting("AutoJump", true, autoJumpVisible));
      this.useTimerSetting = this.add(new BooleanSetting("UseTimer", true));
      this.autoDisableSetting = this.add(new BooleanSetting("AutoDisable", true));
      this.movementStage = 4;
   }

   public enum ModeChoice implements NamedEntry {
      PLAIN("Plain"),
      SPEAR("Spear");

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
