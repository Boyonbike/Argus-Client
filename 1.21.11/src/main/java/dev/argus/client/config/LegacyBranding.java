package dev.argus.client.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Locale;

public final class LegacyBranding {
   private LegacyBranding() {
   }

   public static String canonicalModuleName(String name) {
      String var1 = name.toLowerCase(Locale.ROOT);
      byte var2 = -1;
      switch (var1.hashCode()) {
         case -1508424849:
         default:
            break;
         case -483355658:
            if (var1.equals("antivoid")) {
               var2 = 0;
            }
            break;
         case 1008060813:
            if (var1.equals("autoupgrade")) {
               var2 = 1;
            }
      }
      return switch (var2) {
         case 0 -> "Avoid";
         case 1 -> "AutoTemplate";
         default -> name;
      };
   }

   public static JsonElement findModuleConfig(JsonObject config, String moduleName) {
      if (config.has(moduleName)) {
         return config.get(moduleName);
      }

      return switch (moduleName) {
         case "Avoid" -> config.get("AntiVoid");
         case "AutoTemplate" -> config.get("AutoUpgrade");
         default -> null;
      };
   }

   public static JsonElement migrateModule(String moduleName, JsonElement input) {
      if (!(input instanceof JsonObject module && module.get("settings") instanceof JsonObject)) {
         return input;
      }

      if (!switch (moduleName) {
         case "Watermark", "Welcomer", "AutoSign", "BetterChat", "FeetPlace", "AutoFarm", "EntityControl", "UI" -> true;
         default -> false;
      }) {
         return input;
      }

      JsonObject migrated = module.deepCopy();
      JsonObject settings = migrated.getAsJsonObject("settings");
      switch (moduleName) {
         case "Watermark", "Welcomer" -> LegacyConfigRenamer.renameSettings(settings, "Text");
         case "BetterChat" -> LegacyConfigRenamer.renameSettings(settings, "Prefix", "Suffix");
         case "AutoSign" -> {
            LegacyConfigRenamer.renameSettings(settings, "Line1", "Line2", "Line3", "Line4");
            LegacyConfigRenamer.updateSign(settings);
         }
         case "UI" -> {
            // The default menu style used to be saved as "Meteor"; an unknown name would fall back to Plain.
            JsonElement style = settings.get("Style");
            if (style != null && style.isJsonPrimitive() && "Meteor".equalsIgnoreCase(style.getAsString())) {
               settings.addProperty("Style", "Argus");
            }
         }
         case "AutoFarm" -> {
            if (!settings.has("Frequency") && settings.has("BPT")) {
               settings.add("Frequency", settings.get("BPT"));
            }
            settings.remove("BPT");
         }
         case "EntityControl" -> {
            if (!settings.has("Baritone") && settings.has("PathFinder")) {
               settings.add("Baritone", settings.get("PathFinder"));
            }
            settings.remove("PathFinder");
         }
         case "FeetPlace" -> {
            JsonElement blockIgnore = settings.get("BlockIgnore");
            JsonElement mineIgnore = settings.get("MineIgnore");
            boolean mining = mineIgnore != null && mineIgnore.isJsonPrimitive() && mineIgnore.getAsJsonPrimitive().isBoolean() && mineIgnore.getAsBoolean();
            if (blockIgnore != null && blockIgnore.isJsonPrimitive() && blockIgnore.getAsJsonPrimitive().isBoolean()) {
               settings.addProperty("BlockIgnore", blockIgnore.getAsBoolean() ? "Both" : (mining ? "Mining" : "None"));
            } else if (blockIgnore == null && mining) {
               settings.addProperty("BlockIgnore", "Mining");
            }
            settings.remove("MineIgnore");
            JsonElement sequential = settings.get("Sequential");
            if (sequential != null && sequential.isJsonPrimitive() && sequential.getAsJsonPrimitive().isBoolean()) {
               settings.addProperty("Sequential", sequential.getAsBoolean() ? "Strict" : "None");
            }
         }
      }
      return migrated;
   }
}
