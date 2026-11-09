package dev.argus.client.events;

public final class MinecraftTickEvent extends CancellableEvent {
   public static final MinecraftTickEvent INSTANCE = new MinecraftTickEvent();

   private MinecraftTickEvent() {
   }
}
