package dev.argus.client.events.bus;

import dev.argus.client.events.Cancellable;

public interface EventDispatcher {
   void subscribe(Object var1);

   void unsubscribeStatic(Class<?> var1);

   void unsubscribe(Object var1);

   void subscribeListener(EventListener var1);

   Cancellable postCancellable(Cancellable var1);

   void unsubscribeListener(EventListener var1);

   Object post(Object var1);

   void subscribeStatic(Class<?> var1);
}
