package dev.argus.client.modules.render.targets;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A module's specific targets: one block, entity type, dropped item type or named player, each with its own settings
 * (colour, range and the rest that suit its kind). A specific target always shows that one thing, whatever the general cards say. Plain Java (no game classes),
 * so the list, its JSON form and the mirror sync between ESP and Tracers can be unit-tested.
 */
public final class SpecificRules {
   public enum Type {
      BLOCK("Block"),
      ENTITY("Entity"),
      ITEM("Item"),
      PLAYER("Player");

      private final String title;

      Type(String title) {
         this.title = title;
      }

      public String title() {
         return this.title;
      }
   }

   /** A rule's own settings, as JSON text. The game side swaps in real settings; tests use plain text. */
   public interface Body {
      String read();

      void write(String json);
   }

   /** Settings kept as text until the game side attaches real settings. */
   public static final class TextBody implements Body {
      private String json;

      public TextBody(String json) {
         this.json = json;
      }

      @Override
      public String read() {
         return this.json;
      }

      @Override
      public void write(String json) {
         this.json = json;
      }
   }

   public static final class Rule {
      public final Type type;
      /** A registry id ({@code minecraft:diamond_ore}) or a player name. */
      public final String id;
      public boolean enabled = true;
      public boolean mirror;
      /** This target's own settings (colour, range and the rest for its kind). */
      public Body body = new TextBody("{}");

      public Rule(Type type, String id) {
         this.type = type;
         this.id = id;
      }

      public String key() {
         return SpecificRules.key(this.type, this.id);
      }

      /** The values pairing copies (everything but the mirror flag), as text. */
      public String values() {
         return this.enabled + "|" + this.body.read();
      }

      public void copyValues(Rule from) {
         this.enabled = from.enabled;
         String json = from.body.read();
         if (!json.equals(this.body.read())) {
            this.body.write(json);
         }
      }

      JsonObject toJson() {
         JsonObject object = new JsonObject();
         object.addProperty("type", this.type.name());
         object.addProperty("id", this.id);
         object.addProperty("enabled", this.enabled);
         object.addProperty("mirror", this.mirror);
         JsonElement settings;
         try {
            settings = JsonParser.parseString(this.body.read());
         } catch (RuntimeException ignored) {
            settings = new JsonObject();
         }

         object.add("settings", settings);
         return object;
      }

      static Rule fromJson(JsonObject object) {
         Type type = Type.valueOf(object.get("type").getAsString());
         Rule rule = new Rule(type, object.get("id").getAsString());
         rule.enabled = !object.has("enabled") || object.get("enabled").getAsBoolean();
         rule.mirror = object.has("mirror") && object.get("mirror").getAsBoolean();
         if (object.get("settings") instanceof JsonObject settings) {
            rule.body = new TextBody(settings.toString());
         }

         return rule;
      }
   }

   private final List<Rule> rules = new ArrayList<>();

   public static String key(Type type, String id) {
      return type.name() + ":" + id.toLowerCase(Locale.ROOT);
   }

   public List<Rule> all() {
      return Collections.unmodifiableList(this.rules);
   }

   public boolean isEmpty() {
      return this.rules.isEmpty();
   }

   public Rule find(Type type, String id) {
      String key = key(type, id);
      for (Rule rule : this.rules) {
         if (rule.key().equals(key)) {
            return rule;
         }
      }

      return null;
   }

   /** Adds a rule (on, default settings) or returns the one already there. */
   public Rule add(Type type, String id) {
      Rule existing = this.find(type, id);
      if (existing != null) {
         return existing;
      }

      Rule rule = new Rule(type, id);
      this.rules.add(rule);
      return rule;
   }

   public boolean remove(Rule rule) {
      return this.rules.removeIf(candidate -> candidate.key().equals(rule.key()));
   }

   public String toJson() {
      JsonArray array = new JsonArray();
      for (Rule rule : this.rules) {
         array.add(rule.toJson());
      }

      return array.toString();
   }

   /** Replaces the list with a saved one; unreadable entries are skipped. */
   public void load(String json) {
      this.rules.clear();
      if (json == null || json.isBlank()) {
         return;
      }

      try {
         JsonElement parsed = JsonParser.parseString(json);
         if (!parsed.isJsonArray()) {
            return;
         }

         Set<String> seen = new HashSet<>();
         for (JsonElement element : parsed.getAsJsonArray()) {
            try {
               Rule rule = Rule.fromJson(element.getAsJsonObject());
               if (seen.add(rule.key())) {
                  this.rules.add(rule);
               }
            } catch (RuntimeException ignored) {
            }
         }
      } catch (RuntimeException ignored) {
      }
   }

   // ---- pairing -------------------------------------------------------------------------------------------------------

   /** Bits returned by {@link #sync}: which side was changed. */
   public static final int LEFT_CHANGED = 1;
   public static final int RIGHT_CHANGED = 2;

   /**
    * Keeps mirrored rules identical between two lists (ESP on the left). A rule counts as mirrored only while both sides
    * have it with the mirror flag set; otherwise the flag is cleared on whichever side still has it. For each link, the
    * side that differs from the last agreed value is copied across; if both changed, the left wins.
    *
    * @param agreed the last agreed values by rule key, kept by the caller between syncs
    * @return {@link #LEFT_CHANGED} and/or {@link #RIGHT_CHANGED}
    */
   public static int sync(SpecificRules left, SpecificRules right, Map<String, String> agreed) {
      int changed = 0;
      changed |= dropHalfLinks(left, right) ? LEFT_CHANGED : 0;
      changed |= dropHalfLinks(right, left) ? RIGHT_CHANGED : 0;
      Set<String> linked = new HashSet<>();
      for (Rule rule : left.rules) {
         if (!rule.mirror) {
            continue;
         }

         Rule other = right.find(rule.type, rule.id);
         String key = rule.key();
         linked.add(key);
         String leftValues = rule.values();
         String rightValues = other.values();
         if (leftValues.equals(rightValues)) {
            agreed.put(key, leftValues);
            continue;
         }

         if (!leftValues.equals(agreed.get(key))) {
            other.copyValues(rule);
            agreed.put(key, leftValues);
            changed |= RIGHT_CHANGED;
         } else {
            rule.copyValues(other);
            agreed.put(key, rightValues);
            changed |= LEFT_CHANGED;
         }
      }

      agreed.keySet().retainAll(linked);
      return changed;
   }

   private static boolean dropHalfLinks(SpecificRules mine, SpecificRules theirs) {
      boolean changed = false;
      for (Rule rule : mine.rules) {
         if (rule.mirror) {
            Rule other = theirs.find(rule.type, rule.id);
            if (other == null || !other.mirror) {
               rule.mirror = false;
               changed = true;
            }
         }
      }

      return changed;
   }
}
