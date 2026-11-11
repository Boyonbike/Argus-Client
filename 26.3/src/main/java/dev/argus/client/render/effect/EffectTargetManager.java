package dev.argus.client.render.effect;

import java.util.LinkedHashMap;
import java.util.Map;

/** Owns every effect target and keeps them at the framebuffer size. */
public final class EffectTargetManager {
   private static final Map<String, EffectTarget> TARGETS = new LinkedHashMap<>();

   public static EffectTarget target(String name) {
      return TARGETS.computeIfAbsent(name, EffectTarget::new);
   }

   public static void resize(int width, int height) {
      for (EffectTarget target : TARGETS.values()) {
         target.resize(width, height);
      }
   }

   public static void closeAll() {
      for (EffectTarget target : TARGETS.values()) {
         target.close();
      }
      TARGETS.clear();
   }

   private EffectTargetManager() {
   }
}
