package dev.argus.client.utils.network;

import dev.argus.client.ClientAccess;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.entity.Entity;

public final class InteractPacketUtils implements ClientAccess {
   private InteractPacketUtils() {
   }

   public static Entity getEntity(ServerboundInteractPacket packet) {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      return level == null ? null : level.getEntity(packet.entityId());
   }

   public static Entity getEntity(ServerboundAttackPacket packet) {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      return level == null ? null : level.getEntity(packet.entityId());
   }

   public static Action getAction(ServerboundInteractPacket packet) {
      return packet.location() != null ? Action.INTERACT_AT : Action.INTERACT;
   }

   public static Action getAction(ServerboundAttackPacket packet) {
      return Action.ATTACK;
   }

   public enum Action {
      INTERACT,
      ATTACK,
      INTERACT_AT;
   }
}
