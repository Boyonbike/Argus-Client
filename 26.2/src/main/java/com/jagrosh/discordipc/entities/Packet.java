package com.jagrosh.discordipc.entities;

import com.google.gson.JsonObject;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

public class Packet {
   private final Packet.OpCode op;
   private final JsonObject data;
   private final String encoding;

   public Packet(Packet.OpCode op, JsonObject data, String encoding) {
      this.op = op;
      this.data = data;
      this.encoding = encoding;
   }

   @Deprecated
   public Packet(Packet.OpCode op, JsonObject data) {
      this(op, data, "UTF-8");
   }

   public byte[] toBytes() {
      String json = this.data.toString();

      byte[] bytes;
      try {
         bytes = json.getBytes(this.encoding);
      } catch (UnsupportedEncodingException exception) {
         bytes = json.getBytes();
      }

      ByteBuffer buffer = ByteBuffer.allocate(bytes.length + 8);
      buffer.putInt(Integer.reverseBytes(this.op.ordinal()));
      buffer.putInt(Integer.reverseBytes(bytes.length));
      buffer.put(bytes);
      return buffer.array();
   }

   public Packet.OpCode getOp() {
      return this.op;
   }

   public JsonObject getJson() {
      return this.data;
   }

   @Override
   public String toString() {
      return "Pkt:" + this.getOp() + this.getJson().toString();
   }

   public String toDecodedString() {
      try {
         return "Pkt:" + this.getOp() + new String(this.getJson().toString().getBytes(this.encoding));
      } catch (UnsupportedEncodingException exception) {
         return "Pkt:" + this.getOp() + this.getJson().toString();
      }
   }

   public enum OpCode {
      HANDSHAKE,
      FRAME,
      CLOSE,
      PING,
      PONG;
   }
}
