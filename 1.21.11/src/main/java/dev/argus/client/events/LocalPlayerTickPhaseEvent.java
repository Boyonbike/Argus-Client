package dev.argus.client.events;

public abstract class LocalPlayerTickPhaseEvent extends CancellableEvent {
   public static final class BeforePassengerCheck extends LocalPlayerTickPhaseEvent {
   }

   public static final class LocalPlayerTickEvent extends LocalPlayerTickPhaseEvent {
   }
}
