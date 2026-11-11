package com.jagrosh.discordipc.entities;

import java.util.function.Consumer;

public class Callback {
   private final Consumer success;
   private final Consumer failure;

   public Callback() {
      this(null, null);
   }

   public Callback(Consumer success) {
      this(success, null);
   }

   public Callback(Consumer success, Consumer failure) {
      this.success = success;
      this.failure = failure;
   }

   public boolean isEmpty() {
      return this.success == null && this.failure == null;
   }

   public void succeed(Packet packet) {
      if (this.success != null) {
         this.success.accept(packet);
      }
   }

   public void fail(String message) {
      if (this.failure != null) {
         this.failure.accept(message);
      }
   }
}
