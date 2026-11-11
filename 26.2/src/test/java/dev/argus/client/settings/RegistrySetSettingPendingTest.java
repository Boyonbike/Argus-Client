package dev.argus.client.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.mojang.serialization.Lifecycle;
import dev.argus.client.TestBootstrap;
import java.util.Set;
import net.minecraft.core.DefaultedMappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Config now loads at startup, before deferred registries (e.g. AutoEat food) are filled on world join. */
class RegistrySetSettingPendingTest {
   @BeforeAll
   static void bootstrap() {
      TestBootstrap.bootStrap();
   }

   private static DefaultedMappedRegistry<Item> emptyRegistry() {
      ResourceKey<net.minecraft.core.Registry<Item>> key = ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath("argusclient", "test_food"));
      return new DefaultedMappedRegistry<>("air", key, Lifecycle.stable(), false);
   }

   private static void install(DefaultedMappedRegistry<Item> registry, Item... items) {
      for (Item item : items) {
         registry.register(BuiltInRegistries.ITEM.getResourceKey(item).orElseThrow(), item, RegistrationInfo.BUILT_IN);
      }

      registry.freeze(); // as CustomItemRegistries does
   }

   private static JsonArray saved(String... ids) {
      JsonArray json = new JsonArray();
      for (String id : ids) {
         json.add(id);
      }
      return json;
   }

   @Test
   void savedEntriesSurviveSaveBeforeRegistryInstalls() {
      DefaultedMappedRegistry<Item> registry = emptyRegistry();
      RegistrySetSetting<Item> setting = new RegistrySetSetting<>("Items", registry, Items.GOLDEN_APPLE);
      JsonArray userList = saved("minecraft:bread", "minecraft:cooked_beef");

      setting.fromJson(userList);

      assertEquals(userList, setting.toJson(), "a title-screen save must write the user's list back unchanged");
   }

   @Test
   void pendingEntriesResolveOnceRegistryInstalls() {
      DefaultedMappedRegistry<Item> registry = emptyRegistry();
      RegistrySetSetting<Item> setting = new RegistrySetSetting<>("Items", registry, Items.GOLDEN_APPLE);
      setting.fromJson(saved("minecraft:bread", "minecraft:cooked_beef"));

      install(registry, Items.BREAD, Items.COOKED_BEEF, Items.GOLDEN_APPLE);
      RegistrySetSetting.resolvePending();

      assertEquals(Set.of(Items.BREAD, Items.COOKED_BEEF), setting.getValue());
   }

   @Test
   void explicitEditDropsPendingEntries() {
      DefaultedMappedRegistry<Item> registry = emptyRegistry();
      RegistrySetSetting<Item> setting = new RegistrySetSetting<>("Items", registry, Items.GOLDEN_APPLE);
      setting.fromJson(saved("minecraft:bread"));

      setting.modifyEntries(Set::clear);
      install(registry, Items.BREAD);
      RegistrySetSetting.resolvePending();

      assertTrue(setting.getValue().isEmpty());
      assertEquals(new JsonArray(), setting.toJson());
   }

   @Test
   void populatedRegistryLoadsImmediately() {
      DefaultedMappedRegistry<Item> registry = emptyRegistry();
      install(registry, Items.BREAD, Items.GOLDEN_APPLE);
      RegistrySetSetting<Item> setting = new RegistrySetSetting<>("Items", registry, Items.GOLDEN_APPLE);

      setting.fromJson(saved("minecraft:bread", "minecraft:not_an_item"));

      assertEquals(Set.of(Items.BREAD), setting.getValue());
   }
}
