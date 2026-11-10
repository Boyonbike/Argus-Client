package dev.argus.client.events;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;

public abstract sealed class PacketEvent extends CancellableEvent permits PacketEvent.Send, PacketEvent.Receive, PacketEvent.PostSend {
   private final Packet packet;
   private final Connection connection;

   public Packet getPacket() {
      return this.packet;
   }

   /** The connection the packet travels on, or null when the event was not posted by a connection (e.g. a synthetic packet). */
   public Connection getConnection() {
      return this.connection;
   }

   protected PacketEvent(Packet packet, Connection connection) {
      this.packet = packet;
      this.connection = connection;
   }

   public static final class PostSend extends PacketEvent {
      public PostSend(Packet packet) {
         this(packet, null);
      }

      public PostSend(Packet packet, Connection connection) {
         super(packet, connection);
      }
   }

   public static final class Receive extends PacketEvent {
      public Receive(Packet packet) {
         this(packet, null);
      }

      public Receive(Packet packet, Connection connection) {
         super(packet, connection);
      }
   }

   public static final class Send extends PacketEvent {
      public Send(Packet packet) {
         this(packet, null);
      }

      public Send(Packet packet, Connection connection) {
         super(packet, connection);
      }
   }
}
