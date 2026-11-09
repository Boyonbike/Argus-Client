package dev.argus.client.events;

import net.minecraft.network.protocol.game.ServerboundUseItemPacket;

public final class GameModeUseItemPacketEvent extends CancellableEvent {
   private ServerboundUseItemPacket packet;

   public GameModeUseItemPacketEvent(ServerboundUseItemPacket packet) {
      this.packet = packet;
   }

   public ServerboundUseItemPacket getPacket() {
      return this.packet;
   }

   public void setPacket(ServerboundUseItemPacket packet) {
      this.packet = packet;
   }

   public void setRotations(float[] rotations) {
      this.setPacket(new ServerboundUseItemPacket(this.packet.getHand(), this.packet.getSequence(), rotations[0], rotations[1]));
      this.cancel();
   }
}
