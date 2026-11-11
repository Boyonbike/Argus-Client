package dev.argus.client.managers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.argus.client.ClientContext;
import dev.argus.client.settings.JsonConfigurable;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.RegistrySetSetting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

public class GlintRegistry implements JsonConfigurable {
   private final RegistrySetSetting<Item> neverItems = new RegistrySetSetting<>("Never", BuiltInRegistries.ITEM);
   private final RegistrySetSetting<Item> alwaysItems = new RegistrySetSetting<>("Always", BuiltInRegistries.ITEM);

   @Override
   public void fromJson(JsonElement element) {
      if (element instanceof JsonObject object) {
         if (object.has("never")) {
            RegistrySetSetting<Item> neverSetting = this.neverItems;
            JsonElement neverValue = object.get("never");
            neverSetting.fromJson(neverValue);
         }

         if (object.has("always")) {
            RegistrySetSetting<Item> alwaysSetting = this.alwaysItems;
            JsonElement alwaysValue = object.get("always");
            alwaysSetting.fromJson(alwaysValue);
         }
      }
   }

   @Override
   public JsonElement toJson() {
      JsonObject object = new JsonObject();
      JsonElement neverValue = this.neverItems.toJson();
      object.add("never", neverValue);
      JsonElement alwaysValue = this.alwaysItems.toJson();
      object.add("always", alwaysValue);
      return object;
   }

   @Override
   public String getConfigName() {
      return "glint.json";
   }

   public static GlintRegistry.GlintMode getOverride(Item item) {
      GlintRegistry registry = ClientContext.glintRegistry;
      if (registry == null) {
         return null;
      }

      RegistrySetSetting<Item> neverSetting = registry.neverItems;
      if (neverSetting.getValue().contains(item)) {
         return GlintRegistry.GlintMode.NEVER;
      }

      RegistrySetSetting<Item> alwaysSetting = registry.alwaysItems;
      return alwaysSetting.getValue().contains(item) ? GlintRegistry.GlintMode.ALWAYS : null;
   }

   public boolean toggle(Item item, GlintRegistry.GlintMode mode) {
      RegistrySetSetting<Item> selectedItems = this.getItems(mode);
      boolean wasSelected = selectedItems.getValue().contains(item);
      RegistrySetSetting<Item> neverSetting = this.neverItems;
      neverSetting.getValue().removeIf(configuredItem -> configuredItem.equals(item));
      RegistrySetSetting<Item> alwaysSetting = this.alwaysItems;
      alwaysSetting.getValue().removeIf(configuredItem -> configuredItem.equals(item));
      if (!wasSelected) {
         RegistrySetSetting<Item> additionItems = this.getItems(mode);
         additionItems.getValue().add(item);
      }

      return !wasSelected;
   }

   public RegistrySetSetting<Item> getItems(GlintRegistry.GlintMode mode) {
      return mode == GlintRegistry.GlintMode.ALWAYS ? this.alwaysItems : this.neverItems;
   }

   public enum GlintMode implements NamedEntry {
      NEVER("never"),
      ALWAYS("always");

      private final String name;

      @Override
      public String getName() {
         return this.name;
      }

      GlintMode(String name) {
         this.name = name;
      }
   }
}
