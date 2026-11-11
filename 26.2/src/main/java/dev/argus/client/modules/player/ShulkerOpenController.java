package dev.argus.client.modules.player;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.managers.SwingInteractionManager;
import dev.argus.client.utils.entity.SwingAction;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.world.BlockInteractionUtils;
import dev.argus.client.utils.world.WorldUtils;
import java.util.Comparator;
import java.util.stream.Stream;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class ShulkerOpenController extends ChestStealerController {
   private boolean pendingOpen;

   private boolean isUsableShulker(BlockPos position) {
      boolean ignoreCrystals = false;
      boolean playersOnly = true;
      if (!BlockInteractionUtils.canPlaceAt(position, playersOnly, ignoreCrystals)) {
         return false;
      }

      boolean strictDirection = true;
      Direction supportDirection = BlockInteractionUtils.findSupportDirection(position, strictDirection);
      if (supportDirection == null) {
         return false;
      }

      BlockPos supportPosition = position.relative(supportDirection);
      if (BlockInteractionUtils.requiresSneakForInteraction(supportPosition)) {
         return false;
      }

      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      Direction openDirection = supportDirection.getOpposite();
      BlockPos openPosition = position.relative(openDirection);
      return level.getBlockState(openPosition).isAir();
   }

   public ShulkerOpenController(ChestStealerModule module) {
      super(module);
   }

   @EventHandler
   public void openPendingShulker() {
      Screen screen = ClientAccess.argusclientMinecraft.gui.screen();
      if (!InventoryUtils.isEnderChestScreen(screen)) {
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         ItemStack heldStack = player.getMainHandItem();
         TagKey shulkerTag = ItemTags.SHULKER_BOXES;
         if (heldStack.is(shulkerTag) && this.pendingOpen) {
            BlockPos position = this.findShulkerToOpen();
            if (position != null) {
               boolean strictDirection = true;
               Direction direction = BlockInteractionUtils.findSupportDirection(position, strictDirection);
               BlockPos supportPosition = position.relative(direction);
               Direction hitDirection = direction.getOpposite();
               Vec3 supportCenter = Vec3.atCenterOf(supportPosition);
               double hitOffset = 0.5;
               Vec3 hitPosition = supportCenter.relative(hitDirection, hitOffset);
               MultiPlayerGameMode gameMode = ClientAccess.argusclientMinecraft.gameMode;
               LocalPlayer interactionPlayer = ClientAccess.argusclientMinecraft.player;
               InteractionHand interactionHand = InteractionHand.MAIN_HAND;
               BlockHitResult hitResult = new BlockHitResult(hitPosition, hitDirection, supportPosition, false);
               gameMode.useItemOn(interactionPlayer, interactionHand, hitResult);
               SwingInteractionManager swingManager = ClientContext.swingManager;
               InteractionHand swingHand = InteractionHand.MAIN_HAND;
               SwingAction swingType = SwingAction.INTERACT;
               swingManager.swing(swingHand, swingType);
               RotationManager rotationManager = ClientContext.rotationManager;
               float[] rotations = RotationManager.getRotationsToBlock(supportPosition);
               rotationManager.requestRotation(rotations, 1000);
               this.pendingOpen = false;
            }
         }
      }
   }

   private BlockPos findShulkerToOpen() {
      ChestStealerModule stealerModule = this.module;
      BlockPos center = ChestStealerModule.getLastInteractedBlock(stealerModule);
      Vec3 searchOrigin;
      if (center == null) {
         searchOrigin = ClientAccess.argusclientMinecraft.player.getEyePosition();
      } else {
         searchOrigin = Vec3.atBottomCenterOf(center);
      }

      Vec3 searchCenter = ClientAccess.argusclientMinecraft.player.getEyePosition();
      float searchRadius = 4.0F;
      Stream<BlockPos> usableCandidates = WorldUtils.streamSphereBlocks(searchCenter, searchRadius).filter(this::isUsableShulker);
      Comparator<BlockPos> byDistance = Comparator.comparingDouble(candidate -> searchOrigin.distanceToSqr(Vec3.atCenterOf(candidate)));
      return usableCandidates.min(byDistance).orElse(null);
   }

   public void requestOpen() {
      this.pendingOpen = true;
   }
}
