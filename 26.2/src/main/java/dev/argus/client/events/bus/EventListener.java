package dev.argus.client.events.bus;

public interface EventListener {
   boolean isStatic();

   Class<?> getEventType();

   int getPriority();

   void call(Object var1);
}
