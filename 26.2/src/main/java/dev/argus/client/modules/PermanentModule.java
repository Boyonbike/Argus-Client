package dev.argus.client.modules;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public abstract class PermanentModule extends Module {
   public PermanentModule(String name, String description, ModuleCategory category, String... aliases) {
      super(name, description, category, aliases);
   }

   @Override
   public boolean isToggled() {
      return true;
   }

   @Override
   public boolean isDrawn() {
      return false;
   }

   @Override
   public Keybind getKeybind() {
      return Keybind.UNBOUND;
   }

   @Override
   public JsonElement toJson() {
      JsonElement serialized = super.toJson();
      JsonObject properties = serialized.getAsJsonObject();
      properties.remove("enabled");
      properties.remove("key");
      properties.remove("state");
      return properties;
   }

   @Override
   public void disable() {
   }
}
