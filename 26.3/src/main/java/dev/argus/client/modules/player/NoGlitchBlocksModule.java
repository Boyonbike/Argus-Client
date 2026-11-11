package dev.argus.client.modules.player;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.MultiPlayerGameModeDestroyBlockEvent;
import dev.argus.client.events.MultiPlayerGameModeUseItemOnEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.network.PacketSender;
import dev.argus.client.utils.world.BlockInteractionUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;

public class NoGlitchBlocksModule extends Module {
   private final Setting<Boolean> placeSetting;
   public final Setting<Boolean> breakSetting;

   @EventHandler
   private void onMultiPlayerGameModeUseItemOnEvent(MultiPlayerGameModeUseItemOnEvent event) {
      InteractionHand interactionHand = event.getHand();
      if (this.placeSetting.getValue() && ClientAccess.argusclientMinecraft.player.getItemInHand(interactionHand).getItem() instanceof BlockItem blockItem) {
         BlockPos clickedPos = event.getHitResult().getBlockPos();
         Direction clickedFace = event.getHitResult().getDirection();
         BlockPos placementPos = clickedPos.relative(clickedFace);
         Block placedBlock = blockItem.getBlock();
         if (BlockInteractionUtils.canPlaceBlock(placementPos, placedBlock, false)) {
            InteractionHand packetHand = event.getHand();
            BlockHitResult packetHit = event.getHitResult();
            PacketSender.sendUseItemOn(packetHand, packetHit);
            LocalPlayer playerForSwing = ClientAccess.argusclientMinecraft.player;
            InteractionHand swingHand = event.getHand();
            playerForSwing.swing(swingHand, playerForSwing.getItemInHand(swingHand).getAttackAnimation(), false);
            event.cancel();
         }
      }
   }

   @EventHandler
   private void onMultiPlayerGameModeDestroyBlockEvent(MultiPlayerGameModeDestroyBlockEvent event) {
      if (this.breakSetting.getValue()) {
         event.cancel();
      }
   }

   public NoGlitchBlocksModule() {
      super(
         "NoGlitchBlocks",
         "Makes sure there are no ghost blocks as you interact with them. \n" + ChatFormatting.RED + "Not recommended on 2b2t/Grim servers.",
         ModuleCategory.PLAYER
      );
      Boolean defaultPlace = true;
      BooleanSetting placeOption = new BooleanSetting("Place", defaultPlace);
      this.placeSetting = this.add(placeOption);
      Boolean defaultBreak = true;
      BooleanSetting breakOption = new BooleanSetting("Break", defaultBreak);
      this.breakSetting = this.add(breakOption);
   }
}
