package dev.argus.client.modules.basehunting;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.MinecraftDisconnectEvent;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.RegistrySetSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.DimensionType;
import dev.argus.client.utils.world.WorldUtils;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Highlights mobs that are in the wrong dimension, such as cows in the Nether or piglins in the Overworld. They can only
 * get there through portals, which means players and bases nearby. Ported from bephax's ODMob.
 */
public class ODMobModule extends Module {
   private final Setting<Set<EntityType<?>>> netherMobsSetting = this.add(new RegistrySetSetting<>("NetherMobs", BuiltInRegistries.ENTITY_TYPE, HuntBlocks.entities(
      "piglin", "zombified_piglin", "piglin_brute", "hoglin", "zoglin", "blaze", "ghast", "magma_cube", "wither_skeleton", "strider"
   )));
   private final Setting<Set<EntityType<?>>> overworldMobsSetting = this.add(new RegistrySetSetting<>("OverworldMobs", BuiltInRegistries.ENTITY_TYPE, HuntBlocks.entities(
      "cow", "pig", "sheep", "chicken", "horse", "donkey", "mule", "llama", "fox", "wolf", "cat", "ocelot", "parrot", "rabbit", "polar_bear", "panda",
      "bee", "villager", "iron_golem", "snow_golem", "squid", "glow_squid", "dolphin", "turtle", "cod", "salmon", "pufferfish", "tropical_fish",
      "axolotl", "bat", "frog", "tadpole", "goat", "allay", "camel", "sniffer", "armadillo"
   )));
   private final Setting<Set<EntityType<?>>> endMobsSetting = this.add(new RegistrySetSetting<>("EndMobs", BuiltInRegistries.ENTITY_TYPE, HuntBlocks.entities(
      "endermite", "shulker"
   )));
   private final Setting<Boolean> inOverworldSetting = this.add(new BooleanSetting("InOverworld", true));
   private final Setting<Boolean> inNetherSetting = this.add(new BooleanSetting("InNether", true));
   private final Setting<Boolean> inEndSetting = this.add(new BooleanSetting("InEnd", true));
   private final Setting<Boolean> ignoreTamedSetting = this.add(new BooleanSetting("IgnoreFollowing", false));
   private final HuntMarkers markers = new HuntMarkers(this, new Color(255, 80, 255, 50), new Color(255, 80, 255, 230), true).withoutMemory();
   private final List<Entity> found = new ArrayList<>();
   private int ticks;

   public ODMobModule() {
      super("ODMob", "Highlights mobs in the wrong dimension, which only get there through players' portals.", ModuleCategory.BASE_HUNTING);
   }

   @Override
   public void onDisable() {
      this.markers.clear();
      this.found.clear();
   }

   @Override
   public String getInfo() {
      return this.found.isEmpty() ? null : String.valueOf(this.found.size());
   }

   @EventHandler
   private void onDisconnect(MinecraftDisconnectEvent event) {
      this.markers.clear();
      this.found.clear();
   }

   @EventHandler
   private void onTick(LocalPlayerTickEvent event) {
      if (this.isWorldUnavailable() || ++this.ticks < 10) {
         return;
      }

      this.ticks = 0;
      this.found.clear();
      DimensionType dimension = WorldUtils.getDimension();
      for (Entity entity : ClientAccess.argusclientMinecraft.level.entitiesForRendering()) {
         if (!(entity instanceof LivingEntity) || entity instanceof Player) {
            continue;
         }

         String origin = this.origin(entity, dimension);
         if (origin != null) {
            this.found.add(entity);
            this.markers.alertOnce(entity.getUUID(), entity.blockPosition(), "a " + entity.getType().getDescription().getString() + " from the " + origin);
         }
      }
   }

   private String origin(Entity entity, DimensionType dimension) {
      EntityType<?> type = entity.getType();
      boolean nether = this.netherMobsSetting.getValue().contains(type);
      boolean overworld = this.overworldMobsSetting.getValue().contains(type);
      boolean end = this.endMobsSetting.getValue().contains(type);
      boolean watching = switch (dimension) {
         case OVERWORLD -> this.inOverworldSetting.getValue();
         case THE_NETHER -> this.inNetherSetting.getValue();
         case THE_END -> this.inEndSetting.getValue();
      };
      if (!watching || this.ignoreTamedSetting.getValue() && (entity.isPassenger() || entity instanceof net.minecraft.world.entity.Mob mob && mob.isLeashed())) {
         // A mob on a lead or in a boat or minecart may simply be travelling with the player you can already see.
         return null;
      }

      return switch (dimension) {
         // Chicken jockeys spawn naturally in the Nether with a baby zombified piglin riding them.
         case THE_NETHER -> overworld && !isChickenJockey(entity) ? "Overworld" : end ? "End" : null;
         case OVERWORLD -> nether ? "Nether" : end ? "End" : null;
         case THE_END -> overworld ? "Overworld" : nether ? "Nether" : null;
      };
   }

   private static boolean isChickenJockey(Entity entity) {
      if (!"chicken".equals(EntityType.getKey(entity.getType()).getPath())) {
         return false;
      }

      for (Entity passenger : entity.getPassengers()) {
         if ("zombified_piglin".equals(EntityType.getKey(passenger.getType()).getPath())) {
            return true;
         }
      }

      return false;
   }

   @EventHandler
   private void onWorldRender(WorldRenderEvent.Main event) {
      List<AABB> boxes = new ArrayList<>(this.found.size());
      for (Entity entity : this.found) {
         if (!entity.isRemoved()) {
            boxes.add(HuntEntities.interpolatedBox(entity, event.getPartialTick()));
         }
      }

      this.markers.renderBoxes(event, boxes);
   }
}
