package dev.argus.client.managers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.config.EnumJsonConverter;
import dev.argus.client.modules.client.ColorsModule;
import dev.argus.client.modules.misc.SocialEntry;
import dev.argus.client.settings.JsonConfigurable;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.SocialRole;
import dev.argus.client.utils.entity.PlayerState;
import java.awt.Color;
import java.util.Optional;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class SocialRegistry extends Registry<SocialEntry> implements ClientAccess, JsonConfigurable {
   private static final ColorsModule colorsModule = ClientContext.getModule(ColorsModule.class);
   private static final EnumJsonConverter<SocialRole> roleConverter = new EnumJsonConverter<>(SocialRole.class);

   public boolean isFriendName(String name) {
      return this.getRole(name) == SocialRole.FRIEND;
   }

   public void addFriend(String name) {
      this.removeSocial(name);
      this.registerSocial(new SocialEntry(name, SocialRole.FRIEND));
   }

   public Color getEnemyColor() {
      Setting<Color> colorSetting = colorsModule.enemyColorSetting;
      return colorSetting.getValue();
   }

   public Color getFriendColor() {
      Setting<Color> colorSetting = colorsModule.friendColorSetting;
      return colorSetting.getValue();
   }

   public boolean isFriendEntity(Entity entity) {
      return entity instanceof Player player ? this.isFriendPlayer(player) : false;
   }

   @Override
   public boolean register(Object entry) {
      return this.registerSocial((SocialEntry)entry);
   }

   public boolean registerSocial(SocialEntry entry) {
      String capturedName = entry.getName();
      SocialRole role = entry.role();
      this.updatePlayerRole(capturedName, role);
      return super.registerEntry(entry);
   }

   @Override
   public void fromJson(JsonElement json) {
      JsonObject root = json.getAsJsonObject();
      if (root.has("socials")) {
         root = json.getAsJsonObject();

         for (JsonElement entryElement : root.getAsJsonArray("socials")) {
            JsonObject entryObject = entryElement.getAsJsonObject();
            if (entryObject.has("name") && entryObject.has("role")) {
               JsonElement nameElement = entryObject.get("name");
               String name = nameElement.getAsString();
               EnumJsonConverter<SocialRole> capturedConverter = roleConverter;
               JsonElement roleElement = entryObject.get("role");
               SocialRole role = capturedConverter.fromJson(roleElement);
               SocialEntry allocatedSocial = new SocialEntry(name, role);
               this.registerSocial(allocatedSocial);
            }
         }
      }
   }

   @Override
   public JsonElement toJson() {
      JsonObject root = new JsonObject();
      JsonArray socials = new JsonArray();

      for (SocialEntry social : this.entries) {
         JsonObject entryObject = new JsonObject();
         String name = social.getName();
         entryObject.addProperty("name", name);
         EnumJsonConverter<SocialRole> capturedConverter = roleConverter;
         SocialRole capturedRole = social.role();
         JsonElement roleElement = capturedConverter.toJson(capturedRole);
         entryObject.add("role", roleElement);
         socials.add(entryObject);
      }

      root.add("socials", socials);
      return root;
   }

   public void addEnemy(String name) {
      this.removeSocial(name);
      this.registerSocial(new SocialEntry(name, SocialRole.ENEMY));
   }

   public boolean isEnemyName(String name) {
      return this.getRole(name) == SocialRole.ENEMY;
   }

   @Override
   public String getConfigName() {
      return "socials.json";
   }

   public SocialRole getRole(String name) {
      Optional<SocialRole> mappedRole = this.findFirst(entry -> {
         String capturedName = entry.getName();
         return capturedName.equalsIgnoreCase(name);
      }).map(SocialEntry::role);
      return mappedRole.orElse(null);
   }

   public boolean isEnemyEntity(Entity entity) {
      return entity instanceof Player player ? this.isEnemyPlayer(player) : false;
   }

   public Color getColor(String name, Color defaultColor) {
      if (this.isFriendName(name)) {
         return this.getFriendColor();
      } else {
         return this.isEnemyName(name) ? this.getEnemyColor() : defaultColor;
      }
   }

   private void updatePlayerRole(String name, SocialRole role) {
      if (!this.isWorldUnavailable()) {
         ClientLevel level = ClientAccess.argusclientMinecraft.level;

         for (AbstractClientPlayer player : level.players()) {
            GameProfile profile = player.getGameProfile();
            String capturedPlayerName = profile.name();
            if (capturedPlayerName.equalsIgnoreCase(name) && player instanceof PlayerState roleHolder) {
               roleHolder.argusclient$setRole(role);
               return;
            }
         }
      }
   }

   @Override
   public boolean registerEntry(NamedEntry entry) {
      return this.registerSocial((SocialEntry)entry);
   }

   public boolean removeSocial(String name) {
      this.updatePlayerRole(name, null);
      return this.entries.removeIf(entry -> {
         String capturedName = entry.getName();
         return capturedName.equalsIgnoreCase(name);
      });
   }

   public boolean isEnemyPlayer(Player player) {
      PlayerState roleHolder = (PlayerState)player;
      return roleHolder.argusclient$getRole() == SocialRole.ENEMY;
   }

   public boolean isFriendPlayer(Player player) {
      PlayerState roleHolder = (PlayerState)player;
      return roleHolder.argusclient$getRole() == SocialRole.FRIEND;
   }
}
