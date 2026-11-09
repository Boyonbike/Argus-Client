package dev.argus.client.modules.render;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.MinecraftRunTickEvent;
import dev.argus.client.events.bus.EventBus;
import net.fabricmc.loader.api.FabricLoader;

public final class EntityCullingOverrides implements ClientAccess {
   private static final EntityCullingAdapter CULLING_ACCESS = FabricLoader.getInstance().isModLoaded("entityculling") ? new EntityCullingAdapter() : null;
   private static boolean cullingOverrideActive;
   private static boolean savedCullingEnabled;
   private static boolean savedTickCullingEnabled;
   private static int tickCullingRestoreDelay;

   public static void restoreCulling() {
      if (cullingOverrideActive && CULLING_ACCESS != null) {
         cullingOverrideActive = false;
         CULLING_ACCESS.setCullingEnabled(savedCullingEnabled);
      }
   }

   @EventHandler
   public static void onMinecraftRunTickEvent(MinecraftRunTickEvent event) {
      MinecraftRunTickEvent tickEvent = event;
      if (tickEvent.hasPendingTicks() && tickCullingRestoreDelay > 0) {
         tickCullingRestoreDelay--;
         if (tickCullingRestoreDelay == 0) {
            CULLING_ACCESS.setTickCullingEnabled(savedTickCullingEnabled);
         }
      }
   }

   public static void disableCulling() {
      if (!cullingOverrideActive && CULLING_ACCESS != null) {
         cullingOverrideActive = true;
         savedCullingEnabled = CULLING_ACCESS.isCullingEnabled();
         CULLING_ACCESS.setCullingEnabled(false);
         disableTickCulling();
      }
   }

   private static void disableTickCulling() {
      if (tickCullingRestoreDelay == 0) {
         savedTickCullingEnabled = CULLING_ACCESS.isTickCullingEnabled();
      }

      tickCullingRestoreDelay = 2;
      CULLING_ACCESS.setTickCullingEnabled(false);
   }

   static {
      Class<EntityCullingOverrides> subscriber = EntityCullingOverrides.class;
      EventBus eventBus = ClientAccess.eventBus;
      eventBus.subscribeStatic(subscriber);
   }
}
