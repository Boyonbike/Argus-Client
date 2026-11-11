package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.GameModeUseItemPacketEvent;
import dev.argus.client.events.MultiPlayerGameModeDestroyBlockEvent;
import dev.argus.client.events.MultiPlayerGameModeReleaseUsingItemEvent;
import dev.argus.client.events.MultiPlayerGameModeStartDestroyBlockEvent;
import dev.argus.client.events.MultiPlayerGameModeUseItemEvent;
import dev.argus.client.events.MultiPlayerGameModeUseItemOnEvent;
import dev.argus.client.modules.client.AntiCheatModule;
import dev.argus.client.modules.combat.AutoClickerModule;
import dev.argus.client.modules.exploit.ReachModule;
import dev.argus.client.modules.player.AutoToolModule;
import dev.argus.client.modules.player.InventoryTweaksModule;
import dev.argus.client.modules.player.NoInteractModule;
import dev.argus.client.modules.player.SpeedMineModule;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.inventory.ItemUtils;
import dev.argus.client.utils.network.PacketSender;
import dev.argus.client.utils.world.BlockBreakingState;
import dev.argus.client.utils.world.BlockInteractionUtils;
import dev.argus.client.utils.world.PlacementFallback;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeInteractionMixin implements ClientAccess, BlockBreakingState {
   private static final AutoToolModule autoToolModule = ClientContext.getModule(AutoToolModule.class);
   private static final AutoClickerModule autoClickerModule = ClientContext.getModule(AutoClickerModule.class);
   private static final NoInteractModule noInteractModule = ClientContext.getModule(NoInteractModule.class);
   private static final InventoryTweaksModule inventoryTweaksModule = ClientContext.getModule(InventoryTweaksModule.class);
   private static final AntiCheatModule antiCheatModule = ClientContext.getModule(AntiCheatModule.class);
   private static final ReachModule reachModule = ClientContext.getModule(ReachModule.class);
   private static SpeedMineModule speedmine = ClientContext.getModule(SpeedMineModule.class);
   @Shadow
   private boolean isDestroying;
   @Shadow
   private float destroyProgress;
   @Shadow
   private BlockPos destroyBlockPos;
   @Shadow
   private int destroyDelay;

   @Shadow
   public abstract void handleInventoryMouseClick(int var1, int var2, int var3, ContainerInput var4, Player var5);

   @Shadow
   protected abstract void startPrediction(ClientLevel var1, PredictiveAction var2);

   @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
   private void postDestroyBlockEvent(BlockPos blockPos, CallbackInfoReturnable<Boolean> callback) {
      MultiPlayerGameModeDestroyBlockEvent event = new MultiPlayerGameModeDestroyBlockEvent(blockPos);
      ClientAccess.eventBus.post(event);
      if (event.isCancelled()) {
         callback.setReturnValue(false);
         callback.cancel();
      }
   }

   @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
   private void postStartDestroyBlockEvent(BlockPos blockPos, Direction face, CallbackInfoReturnable<Boolean> callback) {
      MultiPlayerGameModeStartDestroyBlockEvent event = new MultiPlayerGameModeStartDestroyBlockEvent(blockPos, face);
      ClientAccess.eventBus.post(event);
      if (event.isCancelled()) {
         callback.setReturnValue(true);
         callback.cancel();
      }
   }

   @Inject(method = "handleContainerInput", at = @At("HEAD"), cancellable = true)
   public void applyInventoryClickTweaks(int containerId, int slotId, int mouseButton, ContainerInput clickType, Player actingPlayer, CallbackInfo callback) {
      if (inventoryTweaksModule.isToggled()) {
         AbstractContainerMenu menu = actingPlayer.containerMenu;
         if (inventoryTweaksModule.fastArmorSetting.getValue() && mouseButton == 1 && clickType == ContainerInput.QUICK_MOVE) {
            Slot clickedSlot = menu.getSlot(slotId);
            if (clickedSlot.getItem().has(DataComponents.EQUIPPABLE) || clickedSlot.getItem().is(Items.ELYTRA)) {
               InventoryUtils.swapSlotsWithPickup(
                  slotId, 8 - ClientAccess.argusclientMinecraft.player.getEquipmentSlotForItem(clickedSlot.getItem()).getIndex()
               );
               callback.cancel();
               return;
            }
         }

         if (inventoryTweaksModule.xCarryTweaksSetting.getValue()
            && mouseButton == 1
            && clickType == ContainerInput.QUICK_MOVE
            && slotId > 4
            && menu instanceof InventoryMenu) {
            for (int craftingSlotIndex = 0; craftingSlotIndex < 4; craftingSlotIndex++) {
               ItemStack craftingStack = ClientAccess.argusclientMinecraft.player.inventoryMenu.getCraftSlots().getItems().get(craftingSlotIndex);
               if (craftingStack.isEmpty()) {
                  this.handleInventoryMouseClick(containerId, slotId, 0, ContainerInput.PICKUP, ClientAccess.argusclientMinecraft.player);
                  this.handleInventoryMouseClick(containerId, craftingSlotIndex + 1, 0, ContainerInput.PICKUP, ClientAccess.argusclientMinecraft.player);
                  callback.cancel();
                  return;
               }
            }
         }
      }
   }

   @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
   private void postUseItemOnAndApplyNoInteract(
      LocalPlayer actingPlayer, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> callback
   ) {
      MultiPlayerGameModeUseItemOnEvent event = new MultiPlayerGameModeUseItemOnEvent(hit, hand);
      ClientAccess.eventBus.post(event);
      if (event.isCancelled()) {
         callback.setReturnValue(InteractionResult.FAIL);
         callback.cancel();
      } else {
         hand = NoInteractModule.selectUsableHand();
         ItemStack heldStack = ClientAccess.argusclientMinecraft.player.getItemInHand(NoInteractModule.selectUsableHand());
         if (noInteractModule.shouldBlockInteraction(heldStack, hit.getBlockPos())) {
            if (noInteractModule.modeSetting.getValue() == NoInteractModule.ModeChoice.SHIFT) {
               noInteractModule.shouldStopSneaking = !ClientContext.serverStateManager.isServerSneaking();
               ClientContext.playerStateTracker.setSneaking(true);
            } else {
               callback.setReturnValue(InteractionResult.FAIL);
               callback.cancel();
            }

            ClientAccess.argusclientMinecraft.gameMode.useItem(ClientAccess.argusclientMinecraft.player, hand);
         } else if (tryPlacementFallback(actingPlayer, hand, hit)) {
            callback.setReturnValue(InteractionResult.FAIL);
            callback.cancel();
         }
      }
   }

   @Unique
   private static boolean tryPlacementFallback(LocalPlayer player, InteractionHand hand, BlockHitResult hit) {
      if (reachModule == null
         || !reachModule.isToggled()
         || !reachModule.placementFallbackSetting.getValue()
         || player.isSecondaryUseActive()
         || hit.getType() == HitResult.Type.MISS) {
         return false;
      }

      ItemStack heldStack = player.getItemInHand(hand);
      if (!(heldStack.getItem() instanceof BlockItem)) {
         return false;
      }

      if (BlockInteractionUtils.requiresSneakForInteraction(hit.getBlockPos())) {
         return false;
      }

      BlockState clickedState = player.level().getBlockState(hit.getBlockPos());
      if (!clickedState.isSolidRender()
         || clickedState.isSignalSource()
         || clickedState.getMenuProvider(player.level(), hit.getBlockPos()) != null) {
         return false;
      }

      BlockPlaceContext context = new BlockPlaceContext(player, hand, heldStack, hit);
      BlockPos placementPos = context.getClickedPos();
      BlockPos target = PlacementFallback.resolveTarget(hit.getBlockPos(), placementPos, player.getEyePosition(), player.isCreative());
      if (target == null || !player.level().getBlockState(target).canBeReplaced(context)) {
         return false;
      }

      return BlockInteractionUtils.placeBlockIgnoringCrystals(target, null, true, hand);
   }

   @ModifyReturnValue(
      method = "lambda$useItemOn$0(Lorg/apache/commons/lang3/mutable/MutableObject;Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;I)Lnet/minecraft/network/protocol/Packet;",
      at = @At("RETURN")
   )
   private Packet<?> markImportantUseItemOnPacket(Packet<?> packet, @Local(argsOnly = true) LocalPlayer actingPlayer) {
      if (actingPlayer.isUsingItem()) {
         PacketSender.markImportant(packet);
      }

      return packet;
   }

   @Inject(
      method = "lambda$useItem$0(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/entity/player/Player;Lorg/apache/commons/lang3/mutable/MutableObject;I)Lnet/minecraft/network/protocol/Packet;",
      at = @At("TAIL")
   )
   private void markImportantUseItemPacket(
      InteractionHand hand,
      Player actingPlayer,
      MutableObject result,
      int sequence,
      CallbackInfoReturnable<Packet<?>> callback,
      @Local ServerboundUseItemPacket packet
   ) {
      if (actingPlayer.isUsingItem() || ItemUtils.isFood(actingPlayer.getItemInHand(hand))) {
         PacketSender.markImportant(packet);
      }
   }

   @Inject(method = "useItemOn", at = @At("RETURN"))
   private void restoreSneakingAfterUseItemOn(
      LocalPlayer actingPlayer, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> callback
   ) {
      if (noInteractModule.shouldStopSneaking) {
         ClientContext.playerStateTracker.setSneaking(false);
         noInteractModule.shouldStopSneaking = false;
      }
   }

   @Inject(method = "releaseUsingItem", at = @At("HEAD"), cancellable = true)
   private void postReleaseUsingItemEvent(Player actingPlayer, CallbackInfo callback) {
      MultiPlayerGameModeReleaseUsingItemEvent event = new MultiPlayerGameModeReleaseUsingItemEvent();
      ClientAccess.eventBus.post(event);
      if (event.isCancelled()) {
         callback.cancel();
      }
   }

   @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
   private void postUseItemEvent(Player actingPlayer, InteractionHand hand, CallbackInfoReturnable<InteractionResult> callback) {
      MultiPlayerGameModeUseItemEvent event = new MultiPlayerGameModeUseItemEvent(hand);
      ClientAccess.eventBus.post(event);
      if (event.isCancelled()) {
         callback.cancel();
         callback.setReturnValue(InteractionResult.FAIL);
      }
   }

   @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
   private void protectFriendsFromAttack(Player actingPlayer, Entity attackedEntity, CallbackInfo callback) {
      if (autoClickerModule.shouldProtectFriend(attackedEntity) && actingPlayer == ClientAccess.argusclientMinecraft.player) {
         callback.cancel();
      }
   }

   @WrapWithCondition(
      method = "stopDestroyBlock",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V")
   )
   private boolean allowStopDestroyBlockPacket(ClientPacketListener connection, Packet packet) {
      return !speedmine.isToggled();
   }

   @Inject(method = "continueDestroyBlock", at = @At("HEAD"))
   private void clearDestroyDelayForSpeedMine(BlockPos blockPos, Direction face, CallbackInfoReturnable<Boolean> callback) {
      if (speedmine.isToggled()) {
         this.destroyDelay = 0;
      }
   }

   @ModifyExpressionValue(
      method = "lambda$useItem$0(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/entity/player/Player;Lorg/apache/commons/lang3/mutable/MutableObject;I)Lnet/minecraft/network/protocol/Packet;",
      at = @At(value = "NEW", target = "(Lnet/minecraft/world/InteractionHand;IFF)Lnet/minecraft/network/protocol/game/ServerboundUseItemPacket;")
   )
   private ServerboundUseItemPacket modifyPredictedUseItemPacket(ServerboundUseItemPacket packet) {
      GameModeUseItemPacketEvent event = new GameModeUseItemPacketEvent(packet);
      ClientAccess.eventBus.post(event);
      return event.isCancelled() ? event.getPacket() : packet;
   }

   @ModifyExpressionValue(method = "continueDestroyBlock", at = @At(value = "CONSTANT", args = "floatValue=1.0"), require = 0)
   private float modifyContinueDestroyBlockDamageThreshold(float originalThreshold) {
      return autoToolModule.isBlockDamageOverrideActive() ? autoToolModule.blockDamageSetting.getValue() : originalThreshold;
   }

   @ModifyExpressionValue(
      method = "lambda$startDestroyBlock$1(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;I)Lnet/minecraft/network/protocol/Packet;",
      at = @At(value = "CONSTANT", args = "floatValue=1.0"),
      require = 0
   )
   private float modifyPredictedBlockDamageThreshold(float originalThreshold) {
      return autoToolModule.isBlockDamageOverrideActive() ? autoToolModule.blockDamageSetting.getValue() : originalThreshold;
   }

   @Inject(
      method = "startDestroyBlock",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;startPrediction(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/client/multiplayer/prediction/PredictiveAction;)V",
         ordinal = 1,
         shift = Shift.AFTER
      )
   )
   private void finishBlockAfterStartPrediction(BlockPos blockPos, Direction face, CallbackInfoReturnable<Boolean> callback, @Local BlockState blockState) {
      float damageThreshold = autoToolModule.blockDamageSetting.getValue();
      if (autoToolModule.isBlockDamageOverrideActive()
         && blockState.getDestroyProgress(ClientAccess.argusclientMinecraft.player, ClientAccess.argusclientMinecraft.player.level(), blockPos)
            >= damageThreshold
         && !blockState.isAir()) {
         this.startPrediction(
            ClientAccess.argusclientMinecraft.level, sequence -> new ServerboundPlayerActionPacket(Action.STOP_DESTROY_BLOCK, blockPos, face, sequence)
         );
         if (antiCheatModule.is2b2tEnabled()) {
            this.startPrediction(
               ClientAccess.argusclientMinecraft.level, sequence -> new ServerboundPlayerActionPacket(Action.ABORT_DESTROY_BLOCK, blockPos, face, sequence)
            );
         }
      }
   }

   @ModifyExpressionValue(
      method = "continueDestroyBlock",
      at = @At(value = "FIELD", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;destroyDelay:I", ordinal = 0)
   )
   private int modifyDestroyDelayForAutoTool(int originalDelay) {
      return autoToolModule.isToggled() && autoToolModule.noBreakDelaySetting.getValue() ? 0 : originalDelay;
   }

   @Override
   public boolean isBreakingBlock() {
      return this.isDestroying;
   }

   @Override
   public void setBreakingBlock(boolean breaking) {
      this.isDestroying = breaking;
   }

   @Override
   public float getBreakingProgress() {
      return this.destroyProgress;
   }

   @Override
   public void setBreakingProgress(float progress) {
      this.destroyProgress = progress;
   }

   @Override
   public BlockPos getCurrentBreakingBlock() {
      return this.destroyBlockPos;
   }

   @Override
   public void setCurrentBreakingBlock(BlockPos blockPos) {
      this.destroyBlockPos = blockPos;
   }
}
