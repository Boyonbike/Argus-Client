package dev.argus.client.managers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.argus.client.ClientAccess;
import dev.argus.client.config.JsonUtils;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.InputPressEvent;
import dev.argus.client.events.MacroTriggerEvent;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.modules.Keybind;
import dev.argus.client.modules.misc.HoldMacro;
import dev.argus.client.modules.misc.Macro;
import dev.argus.client.settings.JsonConfigurable;
import dev.argus.client.settings.options.MacroType;
import dev.argus.client.utils.KeyUtils;
import java.util.List;

public final class MacroRegistry extends Registry<Macro> implements ClientAccess, JsonConfigurable {
   public MacroRegistry() {
      ClientAccess.eventBus.subscribe(this);
   }

   @Override
   public void fromJson(JsonElement json) {
      if (json.isJsonObject()) {
         JsonObject rootObject = json.getAsJsonObject();
         if (rootObject.has("macros")) {
            rootObject = json.getAsJsonObject();

            for (JsonElement macroElement : rootObject.getAsJsonArray("macros")) {
               if (macroElement.isJsonObject()) {
                  JsonObject macroJson = macroElement.getAsJsonObject();
                  String[] propertyNames = new String[]{"name", "type", "key", "mouse", "commands"};
                  if (JsonUtils.hasProperties(macroJson, propertyNames)) {
                     try {
                        JsonElement typeElement = macroJson.get("type");
                        String typeName = typeElement.getAsString();
                        MacroType type = MacroType.fromName(typeName);
                        JsonElement nameElement = macroJson.get("name");
                        String name = nameElement.getAsString();
                        Keybind keybind = new Keybind(macroJson.get("key").getAsInt(), Keybind.Mode.TOGGLE, macroJson.get("mouse").getAsBoolean());
                        Macro macro = type.create(name, keybind);
                        if (macroJson.has("toggled")) {
                           JsonElement toggleElement = macroJson.get("toggled");
                           boolean toggled = toggleElement.getAsBoolean();
                           macro.setToggled(toggled);
                        }

                        JsonElement commandsElement = macroJson.get("commands");

                        for (JsonElement commandElement : commandsElement.getAsJsonArray()) {
                           List commandsList = macro.getCommands();
                           commandsList.add(commandElement.getAsString());
                        }

                        if (type == MacroType.HOLD) {
                           while (true) {
                              int commandCount = macro.getCommands().size();
                              if (commandCount <= 2) {
                                 break;
                              }

                              macro.getCommands().removeLast();
                           }
                        }

                        this.registerEntry(macro);
                     } catch (Exception var20) {
                     }
                  }
               }
            }
         }
      }
   }

   @Override
   public JsonElement toJson() {
      JsonArray macrosArray = new JsonArray();
      JsonObject rootObject = new JsonObject();

      for (Macro macro : this.entries) {
         JsonObject macroJson = new JsonObject();
         macroJson.addProperty("name", macro.getName());
         macroJson.addProperty("toggled", macro.isToggled());
         MacroType type = macro.getType();
         macroJson.addProperty("type", type.getName());
         Keybind keybind = macro.getKeybind();
         macroJson.addProperty("key", keybind.code());
         keybind = macro.getKeybind();
         macroJson.addProperty("mouse", keybind.mouse());
         JsonArray commandsArray = new JsonArray();

         for (String command : macro.getCommands()) {
            commandsArray.add(command);
         }

         macroJson.add("commands", commandsArray);
         macrosArray.add(macroJson);
      }

      rootObject.add("macros", macrosArray);
      return rootObject;
   }

   @Override
   public String getConfigName() {
      return "macros.json";
   }

   @EventHandler
   private void onInputPress(InputPressEvent event) {
      if (ClientAccess.argusclientMinecraft.screen == null && !this.isWorldUnavailable()) {
         for (Macro macro : this.entries) {
            if (macro.isToggled()) {
               Keybind keybind = macro.getKeybind();
               boolean mouse = keybind.mouse();
               if (mouse == event.isMouse()) {
                  keybind = macro.getKeybind();
                  int keyCode = keybind.code();
                  if (keyCode == event.getCode()) {
                     macro.run();
                     ClientAccess.eventBus.post(new MacroTriggerEvent(macro));
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private void onTick(WorldRenderEvent.Main event) {
      if (ClientAccess.argusclientMinecraft.screen == null && !this.isWorldUnavailable()) {
         for (Macro macro : this.entries) {
            if (macro instanceof HoldMacro holdMacro && holdMacro.isToggled()) {
               Keybind keybind = holdMacro.getKeybind();
               boolean physicallyDown = KeyUtils.isPhysicallyDown(keybind);
               holdMacro.updatePressed(physicallyDown);
            }
         }
      }
   }
}
