package dev.argus.client.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import dev.argus.client.modules.Keybind;
import dev.argus.client.utils.KeyUtils;
import java.util.function.Predicate;
import net.minecraft.client.KeyMapping;

/**
 * A key or mouse button picked in the settings GUI. The default ({@link Keybind#UNBOUND}) follows a vanilla
 * key mapping, so it keeps working when that mapping is rebound in Minecraft's own controls.
 */
public final class KeyBindSetting extends Setting<Keybind> {
   private final String followsName;

   /** @param followsName what the default follows, shown in the GUI (for example "Sprint"). */
   public KeyBindSetting(String name, String followsName, Predicate<? super Keybind> visibility) {
      super(name, Keybind.UNBOUND, visibility);
      this.followsName = followsName;
   }

   /** The name of the vanilla mapping the default follows. */
   public String followsName() { return this.followsName; }

   /** True when no custom key is picked and the setting follows its vanilla mapping. */
   public boolean followsDefault() {
      return this.getValue() == null || this.getValue().isUnbound();
   }

   /** The key's name, or the followed mapping's name for the default. */
   public String displayName() {
      return this.followsDefault() ? this.followsName : this.getValue().getDisplayName();
   }

   /** Whether the picked key, or {@code fallback} for the default, is physically held right now. */
   public boolean isPhysicallyDown(KeyMapping fallback) {
      return this.followsDefault() ? KeyUtils.isPhysicallyDown(fallback) : KeyUtils.isPhysicallyDown(this.getValue());
   }

   /** Binds a keyboard key (GLFW key code). */
   public void bindKey(int key) { this.setValue(new Keybind(key, Keybind.Mode.TOGGLE, false)); }

   /** Binds a mouse button (GLFW button index). */
   public void bindMouse(int button) { this.setValue(new Keybind(button, Keybind.Mode.TOGGLE, true)); }

   /** Returns to following the vanilla mapping. */
   public void followDefault() { this.setValue(Keybind.UNBOUND); }

   @Override
   public void parseValue(String text) {
      if (text == null || text.isBlank() || text.trim().equalsIgnoreCase(this.followsName)) {
         this.followDefault();
         return;
      }
      throw new IllegalArgumentException("Pick the key in the settings screen.");
   }

   @Override
   public JsonElement toJson() {
      if (this.followsDefault()) return JsonNull.INSTANCE;
      JsonObject json = new JsonObject();
      json.addProperty("code", this.getValue().code());
      json.addProperty("mouse", this.getValue().mouse());
      return json;
   }

   @Override
   public void fromJson(JsonElement json) {
      if (json instanceof JsonObject object && object.has("code")) {
         int code = object.get("code").getAsInt();
         boolean mouse = object.has("mouse") && object.get("mouse").getAsBoolean();
         this.setValue(code < 0 ? Keybind.UNBOUND : new Keybind(code, Keybind.Mode.TOGGLE, mouse));
      } else {
         this.followDefault();
      }
   }
}
