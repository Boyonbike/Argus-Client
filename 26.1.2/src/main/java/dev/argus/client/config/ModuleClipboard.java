package dev.argus.client.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.argus.client.modules.Module;
import dev.argus.client.settings.Setting;
import java.util.HashSet;
import java.util.Set;

/**
 * A module's settings as clipboard text, so they can be copied from one client and pasted into another. Only the
 * settings travel: whether the module is on and its keybind stay as they are on the receiving client.
 *
 * <pre>{"argus-module": "Speed", "settings": {"Mode": "Strafe", ...}}</pre>
 */
public final class ModuleClipboard {
   static final String MODULE_KEY = "argus-module";
   static final String SETTINGS_KEY = "settings";

   private ModuleClipboard() {}

   /** What a paste did: how many settings it set, or why it set none. */
   public record Result(int applied, String problem) {
      public boolean ok() {
         return this.problem == null;
      }

      static Result failed(String problem) {
         return new Result(0, problem);
      }
   }

   public static String copy(Module module) {
      JsonObject saved = module.toJson().getAsJsonObject();
      JsonObject out = new JsonObject();
      out.addProperty(MODULE_KEY, module.getName());
      out.add(SETTINGS_KEY, saved.has(SETTINGS_KEY) ? saved.get(SETTINGS_KEY) : new JsonObject());
      return new GsonBuilder().setPrettyPrinting().create().toJson(out);
   }

   public static Result paste(Module module, String text) {
      JsonObject settings = parse(module, text);
      if (settings == null) return Result.failed(problem(module, text));
      Set<String> matching = matching(module, settings);
      if (matching.isEmpty()) return Result.failed("None of the copied settings belong to " + module.getName());
      JsonObject wrapper = new JsonObject();
      wrapper.add(SETTINGS_KEY, settings);
      module.fromJson(wrapper);
      return new Result(matching.size(), null);
   }

   private static Set<String> matching(Module module, JsonObject settings) {
      Set<String> names = new HashSet<>();
      for (Setting<?> setting : module.getSettings()) {
         if (!setting.isExcludedFromConfig() && settings.has(setting.getConfigName())) names.add(setting.getConfigName());
      }
      return names;
   }

   private static JsonObject root(String text) {
      if (text == null || text.isBlank()) return null;
      try {
         JsonElement parsed = JsonParser.parseString(text.trim());
         return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
      } catch (RuntimeException notJson) {
         return null;
      }
   }

   /** The copied settings, if the text is a config for this module. */
   private static JsonObject parse(Module module, String text) {
      JsonObject root = root(text);
      if (root == null || !root.has(MODULE_KEY) || !(root.get(SETTINGS_KEY) instanceof JsonObject settings)) return null;
      return root.get(MODULE_KEY).getAsString().equalsIgnoreCase(module.getName()) ? settings : null;
   }

   private static String problem(Module module, String text) {
      JsonObject root = root(text);
      if (root == null || !root.has(MODULE_KEY) || !(root.get(SETTINGS_KEY) instanceof JsonObject)) {
         return "The clipboard doesn't hold a copied module config";
      }
      return "That config is for " + root.get(MODULE_KEY).getAsString() + ", not " + module.getName();
   }
}
