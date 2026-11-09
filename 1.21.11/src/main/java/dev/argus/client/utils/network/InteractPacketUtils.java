package dev.argus.client.utils.network;

import dev.argus.client.ClientAccess;
import io.netty.buffer.Unpooled;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.entity.Entity;

public final class InteractPacketUtils implements ClientAccess {
   private InteractPacketUtils() {
   }

   public static Entity getEntity(ServerboundInteractPacket packet) {
      FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
      packet.write(buffer);
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      int entityId = buffer.readVarInt();
      return level.getEntity(entityId);
   }

   public static InteractPacketUtils.Action getAction(ServerboundInteractPacket packet) {
      FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
      packet.write(buffer);
      buffer.readVarInt();
      Class<InteractPacketUtils.Action> actionType = InteractPacketUtils.Action.class;
      return buffer.readEnum(actionType);
   }

   public enum Action {
      INTERACT,
      ATTACK,
      INTERACT_AT;
   }
}
