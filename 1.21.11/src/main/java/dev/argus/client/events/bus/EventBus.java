package dev.argus.client.events.bus;

import dev.argus.client.events.Cancellable;
import dev.argus.client.events.EventHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventBus implements EventDispatcher {
   private final Map<Object, List<EventListener>> instanceListenerCache = Collections.synchronizedMap(new IdentityHashMap<>());
   private final Map<Class<?>, List<EventListener>> staticListenerCache = new ConcurrentHashMap<>();
   private final Map<Class<?>, CopyOnWriteArrayList<EventListener>> listenersByEventClass = new ConcurrentHashMap<>();

   @Override
   public void subscribe(Object subscriber) {
      this.subscribeListeners(this.getOrCreateListeners(subscriber.getClass(), subscriber), false);
   }

   @Override
   public void unsubscribe(Object subscriber) {
      this.unsubscribeListeners(this.getOrCreateListeners(subscriber.getClass(), subscriber), false);
   }

   @Override
   public void subscribeStatic(Class<?> listenerClass) {
      this.subscribeListeners(this.getOrCreateListeners(listenerClass, null), true);
   }

   @Override
   public void unsubscribeStatic(Class<?> listenerClass) {
      this.unsubscribeListeners(this.getOrCreateListeners(listenerClass, null), true);
   }

   private List<EventListener> getOrCreateListeners(Class<?> listenerClass, Object subscriber) {
      if (subscriber == null) {
         return this.staticListenerCache.computeIfAbsent(listenerClass, type -> this.collectListeners((Class<?>)type, null));
      }

      synchronized (this.instanceListenerCache) {
         return this.instanceListenerCache.computeIfAbsent(subscriber, key -> this.collectListeners(listenerClass, subscriber));
      }
   }

   private List<EventListener> collectListeners(Class<?> listenerClass, Object subscriber) {
      List<EventListener> listeners = new ArrayList<>();

      for (Class<?> type = listenerClass; type != null; type = type.getSuperclass()) {
         for (Method method : type.getDeclaredMethods()) {
            if (method.isAnnotationPresent(EventHandler.class)
               && method.getReturnType() == void.class
               && method.getParameterCount() == 1
               && !method.getParameterTypes()[0].isPrimitive()) {
               listeners.add(new MethodEventListener(type, subscriber, method));
            }
         }
      }

      return List.copyOf(listeners);
   }

   private void subscribeListeners(List<EventListener> listeners, boolean staticOnly) {
      for (EventListener listener : listeners) {
         if (!staticOnly || listener.isStatic()) {
            this.subscribeListener(listener);
         }
      }
   }

   private void unsubscribeListeners(List<EventListener> listeners, boolean staticOnly) {
      for (EventListener listener : listeners) {
         if (!staticOnly || listener.isStatic()) {
            this.unsubscribeListener(listener);
         }
      }
   }

   @Override
   public void subscribeListener(EventListener listener) {
      CopyOnWriteArrayList<EventListener> listeners = this.listenersByEventClass
         .computeIfAbsent(listener.getEventType(), ignored -> new CopyOnWriteArrayList<>());
      synchronized (listeners) {
         int insertionIndex = 0;

         while (insertionIndex < listeners.size() && listener.getPriority() <= listeners.get(insertionIndex).getPriority()) {
            insertionIndex++;
         }

         listeners.add(insertionIndex, listener);
      }
   }

   @Override
   public void unsubscribeListener(EventListener listener) {
      List<EventListener> listeners = this.listenersByEventClass.get(listener.getEventType());
      if (listeners != null) {
         listeners.remove(listener);
      }
   }

   @Override
   public Object post(Object event) {
      List<EventListener> listeners = this.listenersByEventClass.get(event.getClass());
      if (listeners != null) {
         for (EventListener listener : listeners) {
            listener.call(event);
         }
      }

      return event;
   }

   @Override
   public Cancellable postCancellable(Cancellable event) {
      List<EventListener> listeners = this.listenersByEventClass.get(event.getClass());
      if (listeners != null) {
         event.setCancelled(false);

         for (EventListener listener : listeners) {
            listener.call(event);
            if (event.isCancelled()) {
               break;
            }
         }
      }

      return event;
   }
}
