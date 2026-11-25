package dev.argus.client.modules.movement.elytratrip;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Turns off the standalone modules that would fight a trip and turns exactly those back on afterwards. Only switches them
 * on and off; their code and settings are untouched. Pure: module access is passed in.
 */
public final class CompetingModules {
   /** Modules that would fight the trip for the flight, the inventory, the chat or the connection. */
   public static final List<String> CANDIDATES = List.of("BaritoneElytra", "ElytraFly", "AutoEat", "Offhand", "AutoArmor", "AutoRespawn",
      "AutoReconnect", "AutoLobby", "AutoSetHome", "FreeLook", "Replenish", "AutoRekit", "InventoryCleaner", "ChestStealer");

   private final Function<String, Boolean> isOn;
   private final BiConsumer<String, Boolean> set;
   private final List<String> turnedOff = new ArrayList<>();

   public CompetingModules(Function<String, Boolean> isOn, BiConsumer<String, Boolean> set) {
      this.isOn = isOn;
      this.set = set;
   }

   /** Turns off each candidate that is on and returns their names. */
   public List<String> switchOff(List<String> candidates) {
      for (String name : candidates) {
         if (Boolean.TRUE.equals(isOn.apply(name))) {
            set.accept(name, false);
            if (!turnedOff.contains(name)) turnedOff.add(name);
         }
      }
      return List.copyOf(turnedOff);
   }

   /** Turns back on exactly the modules this switched off; ones already back on are left alone. */
   public void restore() {
      for (String name : turnedOff) {
         if (!Boolean.TRUE.equals(isOn.apply(name))) set.accept(name, true);
      }
      turnedOff.clear();
   }

   /** Restores all but {@code later}, which stay on the list for a later {@link #restore}. */
   public void restoreExcept(java.util.Set<String> later) {
      List<String> kept = new ArrayList<>();
      for (String name : turnedOff) {
         if (later.contains(name)) kept.add(name);
         else if (!Boolean.TRUE.equals(isOn.apply(name))) set.accept(name, true);
      }
      turnedOff.clear();
      turnedOff.addAll(kept);
   }

   public List<String> turnedOff() {
      return List.copyOf(turnedOff);
   }

   /** A saved list from before a restart, so the modules still come back on. */
   public void load(List<String> saved) {
      turnedOff.clear();
      if (saved != null) turnedOff.addAll(saved);
   }
}
