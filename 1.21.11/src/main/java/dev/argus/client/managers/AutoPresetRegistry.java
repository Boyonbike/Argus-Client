package dev.argus.client.managers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.ConnectScreenConnectEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.settings.JsonConfigurable;
import dev.argus.client.settings.options.PresetCategory;
import dev.argus.client.utils.chat.ChatUtils;
import dev.argus.client.utils.network.AutoPresetServerEntry;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

public final class AutoPresetRegistry implements ClientAccess, JsonConfigurable {
   private final Map<String, AutoPresetServerEntry> serverSettings = new HashMap();
   private String lastServer;

   public Collection getServers() {
      return this.serverSettings.keySet();
   }

   public AutoPresetRegistry() {
      ClientAccess.eventBus.subscribe(this);
   }

   @Override
   public void fromJson(JsonElement json) {
      JsonObject root = json.getAsJsonObject();

      for (Entry serverEntry : root.entrySet()) {
         String server = (String)serverEntry.getKey();
         JsonElement serverJson = (JsonElement)serverEntry.getValue();
         JsonObject settingsJson = serverJson.getAsJsonObject();

         for (Entry settingEntry : settingsJson.entrySet()) {
            String categoryName = (String)settingEntry.getKey();
            PresetCategory category = PresetCategory.findByName(categoryName);
            JsonElement presetJson = (JsonElement)settingEntry.getValue();
            String presetName = presetJson.getAsString();
            if (category != null) {
               this.setPreset(server, category, presetName);
            }
         }

         if (settingsJson.has("kit")) {
            JsonElement kitJson = settingsJson.get("kit");
            this.setKit(server, kitJson.getAsString());
         }
      }
   }

   @Override
   public JsonElement toJson() {
      JsonObject root = new JsonObject();

      for (AutoPresetServerEntry serverSettings : this.serverSettings.values()) {
         JsonObject settingsJson = new JsonObject();

         for (Entry presetEntry : serverSettings.getPresets().entrySet()) {
            PresetCategory category = (PresetCategory)presetEntry.getKey();
            String categoryKey = category.getName().toLowerCase(Locale.ROOT);
            String presetName = (String)presetEntry.getValue();
            settingsJson.addProperty(categoryKey, presetName);
         }

         if (serverSettings.hasKit()) {
            String kitName = serverSettings.getKitName();
            settingsJson.addProperty("kit", kitName);
         }

         String serverName = serverSettings.getServer();
         root.add(serverName, settingsJson);
      }

      return root;
   }

   public boolean removeKit(String server) {
      AutoPresetServerEntry settings = (AutoPresetServerEntry)this.serverSettings.get(server);
      if (settings == null) {
         return false;
      }

      settings.setKitName(null);
      return true;
   }

   @Override
   public String getConfigName() {
      Path presetDirectoryName = ConfigurationManager.PRESETS_DIRECTORY.getFileName();
      return presetDirectoryName.resolve("auto.json").toString();
   }

   public boolean removePreset(String server, PresetCategory category) {
      AutoPresetServerEntry settings = (AutoPresetServerEntry)this.serverSettings.get(server);
      return settings == null ? false : settings.getPresets().remove(category) != null;
   }

   public void setPreset(String server, PresetCategory category, String presetName) {
      AutoPresetServerEntry settings = this.serverSettings.computeIfAbsent(server, ignoredServer -> new AutoPresetServerEntry(server));
      settings.getPresets().put(category, presetName);
   }

   @EventHandler
   public void onConnectScreenConnectEvent(ConnectScreenConnectEvent event) {
      String serverAddress = event.getServerAddress();
      String previousServer = this.lastServer;
      if (!serverAddress.equalsIgnoreCase(previousServer)) {
         this.lastServer = event.getServerAddress();
         AutoPresetServerEntry settings = (AutoPresetServerEntry)this.serverSettings.get(this.lastServer);
         if (settings != null) {
            AtomicInteger messageCounter = new AtomicInteger(-2);
            settings.getPresets().forEach((category, presetName) -> {
               ConfigurationManager configurationManager = ClientContext.configurationManager;
               PresetRegistry presetRegistry = configurationManager.getPresetRegistry(category);
               presetRegistry.applyPreset(presetName);
               TaskScheduler schedulerForPreset = ClientContext.taskScheduler;
               Runnable messageForPreset = () -> {
                  int messageId = messageCounter.getAndDecrement();
                  MessageSignature signature = ChatUtils.createSignature(messageId);
                  Object[] messageArguments = new Object[]{presetName};
                  String messageFormat = "Preset %s has been loaded.";
                  String messageText = messageFormat.formatted(messageArguments);
                  ChatUtils.printChat(Component.literal(messageText), signature);
               };
               schedulerForPreset.scheduleRotationTask(messageForPreset, 0);
            });
            if (settings.hasKit()) {
               String kitName = settings.getKitName();
               ClientContext.kitRegistry.findFirst(kit -> kit.getName().equalsIgnoreCase(kitName)).ifPresent(matchingKit -> {
                  KitRegistry kitRegistry = ClientContext.kitRegistry;
                  kitRegistry.setCurrentKitName(kitName);
                  TaskScheduler schedulerForKit = ClientContext.taskScheduler;
                  Runnable messageForKit = () -> {
                     int messageId = messageCounter.getAndDecrement();
                     MessageSignature signature = ChatUtils.createSignature(messageId);
                     Object[] messageArguments = new Object[]{kitName};
                     String messageFormat = "Kit %s has been loaded.";
                     String messageText = messageFormat.formatted(messageArguments);
                     ChatUtils.printChat(Component.literal(messageText), signature);
                  };
                  schedulerForKit.scheduleRunTickTask(messageForKit);
               });
            }
         }
      }
   }

   public boolean removeServer(String server) {
      return this.serverSettings.remove(server) != null;
   }

   public void setKit(String server, String kitName) {
      AutoPresetServerEntry settings = this.serverSettings.computeIfAbsent(server, ignoredServer -> new AutoPresetServerEntry(server));
      settings.setKitName(kitName);
   }
}
