package dev.argus.client.utils.network;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import io.netty.buffer.Unpooled;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;

public final class PacketSender implements ClientAccess {
   private PacketSender() {
   }

   public static void sendUseItemOn(InteractionHand hand, BlockHitResult hitResult) {
      send(new ServerboundUseItemOnPacket(hand, hitResult, nextPredictionSequence()));
   }

   public static void sendUseItem(InteractionHand hand) {
      float yaw = ClientAccess.argusclientMinecraft.player.getYRot();
      float pitch = ClientAccess.argusclientMinecraft.player.getXRot();
      sendUseItem(hand, yaw, pitch);
   }

   public static void sendUseItem(InteractionHand hand, float yaw, float pitch) {
      send(new ServerboundUseItemPacket(hand, nextPredictionSequence(), yaw, pitch));
   }

   public static void sendSwing(InteractionHand hand) {
      send(new ServerboundSwingPacket(hand));
   }

   public static void sendRotation(float yaw, float pitch, boolean onGround) {
      send(new Rot(yaw, pitch, onGround, ClientAccess.argusclientMinecraft.player.horizontalCollision));
   }

   public static void sendRotation(float[] rotation, boolean onGround) {
      if (rotation != null) {
         int angleCount = rotation.length;
         if (angleCount >= 2) {
            float yaw = rotation[0];
            float pitch = rotation[1];
            sendRotation(yaw, pitch, onGround);
         }
      }
   }

   public static void sendPosition(double x, double y, double z, boolean onGround) {
      send(new Pos(x, y, z, onGround, ClientAccess.argusclientMinecraft.player.horizontalCollision));
   }

   public static void sendPositionRotation(double x, double y, double z, float yaw, float pitch, boolean onGround) {
      send(new PosRot(x, y, z, yaw, pitch, onGround, ClientAccess.argusclientMinecraft.player.horizontalCollision));
   }

   public static void sendAttack(int entityId) {
      FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
      buffer.writeVarInt(entityId);
      buffer.writeVarInt(1);
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      boolean sneaking = player.isShiftKeyDown();
      buffer.writeBoolean(sneaking);
      ServerboundInteractPacket packet = new ServerboundInteractPacket(buffer);
      send(packet);
   }

   public static void sendPlayerAction(Action action, BlockPos position, Direction direction) {
      int sequenceSnapshot;
      if (action == Action.ABORT_DESTROY_BLOCK) {
         sequenceSnapshot = 0;
      } else {
         sequenceSnapshot = nextPredictionSequence();
      }

      send(new ServerboundPlayerActionPacket(action, position, direction, sequenceSnapshot));
   }

   public static void markImportant(Packet packet) {
      if (packet instanceof ImportantPacket importantPacket) {
         importantPacket.argusclient$markImportant();
      }
   }

   public static void sendPlayerCommand(net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action action) {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      sendPlayerCommand(player, action);
   }

   public static void sendPlayerCommand(Entity entity, net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action action) {
      send(new ServerboundPlayerCommandPacket(entity, action, 0));
   }

   public static void sendStartFallFlying() {
      net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action action = net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.START_FALL_FLYING;
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      sendPlayerCommand(player, action);
   }

   public static void sendSwapOffhand() {
      send(new ServerboundPlayerActionPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
   }

   public static void sendSelectedSlot(int slot) {
      send(new ServerboundSetCarriedItemPacket(slot));
   }

   public static void send(Packet packet) {
      ClientAccess.argusclientMinecraft.player.connection.send(packet);
   }

   public static int nextPredictionSequence() {
      return ClientContext.worldStateManager.nextPredictionSequence();
   }

   public static void sendDirect(Packet packet) {
      if (packet != null) {
         Connection connection = ClientAccess.argusclientMinecraft.player.connection.getConnection();
         if (connection.isConnected()) {
            connection.sentPackets++;
            if (connection.channel.eventLoop().inEventLoop()) {
               connection.channel.writeAndFlush(packet);
            } else {
               connection.channel.eventLoop().execute(() -> connection.channel.writeAndFlush(packet));
            }
         }
      }
   }
}
