package dev.argus.client.modules.player.blockcoat;

import dev.argus.client.ClientAccess;
import dev.argus.client.utils.network.PacketSender;
import dev.argus.client.utils.world.BlockInteractionUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResult.Success;
import net.minecraft.world.InteractionResult.SwingSource;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Block Coat's placement interactions, including the packet sequence that sneak-uses containers. */
public final class BlockCoatInteraction {
    private BlockCoatInteraction() {}

    /** Whether using this block opens or toggles it, so it needs a sneak-use to act as support. */
    public static boolean clickable(BlockState state) {
        return BlockInteractionUtils.isInteractiveBlock(state.getBlock());
    }

    /** Whether the support is a container (sneak-use) rather than a plain block. */
    public static boolean clickable(BlockHitResult hit) {
        return clickable(ClientAccess.argusclientMinecraft.level.getBlockState(hit.getBlockPos()));
    }

    /**
     * Sneak-use containers so they act as support instead of opening their screen. The shift state is
     * carried by the ordinary player input packet.
     */
    public static void use(BlockHitResult hit, InteractionHand hand, boolean containerSupports) {
        Minecraft mc = ClientAccess.argusclientMinecraft;
        if (!clickable(hit)) {
            boolean wasSneaking = mc.player.isShiftKeyDown();
            mc.player.setShiftKeyDown(false);
            BlockInteractionUtils.isPlacingBlock = true;
            try {
                InteractionResult result = mc.gameMode.useItemOn(mc.player, hand, hit);
                if (result instanceof Success success && success.swingSource() == SwingSource.PREDICTED) mc.player.swing(hand, mc.player.getItemInHand(hand).getAttackAnimation(), false);
            } finally {
                BlockInteractionUtils.isPlacingBlock = false;
                mc.player.setShiftKeyDown(wasSneaking);
            }
            return;
        }
        if (!containerSupports || mc.getConnection() == null) return;
        Input original = mc.player.input.keyPresses;
        Input sneaking = new Input(original.forward(), original.backward(), original.left(), original.right(),
            original.jump(), true, original.sprint());
        PacketSender.send(new ServerboundPlayerInputPacket(sneaking));
        try {
            // Send the sequenced interaction without client-side container prediction or local crouching.
            PacketSender.sendUseItemOn(hand, hit);
            mc.player.swing(hand, mc.player.getItemInHand(hand).getAttackAnimation(), false);
        } finally {
            PacketSender.send(new ServerboundPlayerInputPacket(original));
        }
    }
}
