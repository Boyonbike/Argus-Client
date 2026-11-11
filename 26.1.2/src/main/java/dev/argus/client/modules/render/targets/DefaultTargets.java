package dev.argus.client.modules.render.targets;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/** The target lists ESP and Tracers start with on a new config (or after a reset). */
public final class DefaultTargets {
   private static final String[] DYES = {
      "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
      "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
   };

   private DefaultTargets() {
   }

   /**
    * ESP: Meteor's Storage ESP defaults, one group per Meteor colour. Chests, barrels and shulker boxes orange, trapped
    * chests red, ender chests purple, and furnaces, hoppers, dispensers and the rest grey.
    */
   public static String esp() {
      List<String> chests = new ArrayList<>(List.of("chest"));
      for (String age : new String[]{"", "exposed_", "weathered_", "oxidized_"}) {
         chests.add(age + "copper_chest");
         chests.add("waxed_" + age + "copper_chest");
      }

      List<String> shulkers = new ArrayList<>(List.of("shulker_box"));
      for (String dye : DYES) {
         shulkers.add(dye + "_shulker_box");
      }

      JsonArray rules = new JsonArray();
      rules.add(blocks("default-chests", chests, new Color(255, 160, 0)));
      rules.add(blocks("default-trapped-chests", List.of("trapped_chest"), new Color(255, 0, 0)));
      rules.add(blocks("default-barrels", List.of("barrel"), new Color(255, 160, 0)));
      rules.add(blocks("default-shulker-boxes", shulkers, new Color(255, 160, 0)));
      rules.add(blocks("default-ender-chests", List.of("ender_chest"), new Color(120, 0, 255)));
      rules.add(blocks("default-other-storage", List.of(
         "furnace", "blast_furnace", "smoker", "brewing_stand", "chiseled_bookshelf", "crafter",
         "dispenser", "dropper", "decorated_pot", "hopper"
      ), new Color(140, 140, 140)));
      return rules.toString();
   }

   /** Tracers: friends in green and every other player in red. */
   public static String tracers() {
      JsonArray rules = new JsonArray();
      rules.add(players("default-tracers-friends", SpecificRules.FRIENDS, new Color(60, 255, 110)));
      rules.add(players("default-tracers-players", SpecificRules.ALL_PLAYERS, new Color(255, 60, 60)));
      return rules.toString();
   }

   private static JsonObject blocks(String id, List<String> blocks, Color color) {
      JsonArray members = new JsonArray();
      for (String block : blocks) {
         members.add(member("BLOCK", "minecraft:" + block, "BLOCKS"));
      }

      JsonObject settings = new JsonObject();
      settings.addProperty("TargetColorMode", "Custom");
      settings.add("TargetColor", color(color));
      settings.addProperty("TargetRange", 512.0F);
      return rule(id, members, settings);
   }

   private static JsonObject players(String id, String group, Color color) {
      JsonArray members = new JsonArray();
      members.add(member("GROUP", group, "PLAYERS"));
      JsonObject settings = new JsonObject();
      settings.add("TargetColor", color(color));
      settings.addProperty("TargetIgnoreNaked", false);
      settings.addProperty("TargetRange", 512.0F);
      return rule(id, members, settings);
   }

   /** A colour as {@code ColorSetting} saves it (built by hand: ColorSetting needs the game's Colors module). */
   private static JsonObject color(Color color) {
      JsonArray rgba = new JsonArray();
      rgba.add(color.getRed());
      rgba.add(color.getGreen());
      rgba.add(color.getBlue());
      rgba.add(color.getAlpha());
      JsonObject object = new JsonObject();
      object.add("rgba", rgba);
      object.addProperty("rainbow", false);
      object.addProperty("sync", false);
      return object;
   }

   private static JsonObject member(String type, String id, String category) {
      JsonObject member = new JsonObject();
      member.addProperty("type", type);
      member.addProperty("id", id);
      member.addProperty("category", category);
      return member;
   }

   private static JsonObject rule(String id, JsonArray members, JsonObject settings) {
      JsonObject rule = new JsonObject();
      rule.addProperty("id", id);
      rule.add("members", members);
      rule.addProperty("enabled", true);
      rule.addProperty("mirror", false);
      rule.add("settings", settings);
      return rule;
   }
}
