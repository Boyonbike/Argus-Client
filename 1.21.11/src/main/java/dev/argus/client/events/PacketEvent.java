package dev.argus.client.events;

import net.minecraft.network.protocol.Packet;

public abstract sealed class PacketEvent extends CancellableEvent permits PacketEvent.Send, PacketEvent.Receive, PacketEvent.PostSend {
   private final Packet packet;

   public Packet getPacket() {
      return this.packet;
   }

   protected PacketEvent(Packet packet) {
      this.packet = packet;
   }

   public static final class PostSend extends PacketEvent {
      public PostSend(Packet packet) {
         super(packet);
      }
   }

   public static final class Receive extends PacketEvent {
      public Receive(Packet packet) {
         super(packet);
      }
   }

   public static final class Send extends PacketEvent {
      public Send(Packet packet) {
         super(packet);
      }
   }
}
