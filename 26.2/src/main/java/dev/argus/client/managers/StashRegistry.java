package dev.argus.client.managers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.bus.EventBus;
import dev.argus.client.modules.misc.StashEntry;
import dev.argus.client.modules.misc.StashFinderModule;
import dev.argus.client.settings.JsonConfigurable;
import dev.argus.client.settings.Setting;
import java.util.Collection;

public final class StashRegistry extends Registry<StashEntry> implements ClientAccess, JsonConfigurable {
   private static StashFinderModule stashFinderModule = ClientContext.getModule(StashFinderModule.class);

   public StashRegistry() {
      EventBus eventBus = ClientAccess.eventBus;
      eventBus.subscribe(this);
   }

   @Override
   public void fromJson(JsonElement element) {
      if (element.isJsonObject()) {
         JsonObject object = element.getAsJsonObject();
         if (object.has("stashes")) {
            object = element.getAsJsonObject();

            for (JsonElement stashElement : object.getAsJsonArray("stashes")) {
               try {
                  Collection<StashEntry> stashEntries = this.entries;
                  Gson gson = ClientAccess.gson;
                  stashEntries.add((StashEntry)gson.fromJson(stashElement, StashEntry.class));
               } catch (Exception var8) {
               }
            }
         }
      }
   }

   @Override
   public JsonElement toJson() {
      Setting<Boolean> logSetting = stashFinderModule.logSetting;
      Boolean loggingEnabled = logSetting.getValue();
      if (!loggingEnabled) {
         return new JsonObject();
      }

      JsonArray stashes = new JsonArray();
      JsonObject object = new JsonObject();

      for (StashEntry stash : ClientContext.stashRegistry.getRegistry()) {
         Gson gson = ClientAccess.gson;
         stashes.add(gson.toJsonTree(stash));
      }

      object.add("stashes", stashes);
      return object;
   }

   @Override
   public String getConfigName() {
      return "stashfinder.json";
   }
}
