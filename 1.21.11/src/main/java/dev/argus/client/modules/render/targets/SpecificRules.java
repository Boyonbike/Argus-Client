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
 * A module's specific targets: one block, entity type, dropped item type or named player, each with its own colour and
 * range. A specific target always shows that one thing, whatever the general cards say. Plain Java (no game classes),
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

   /** The largest range; a rule at this range has no limit. */
   public static final float UNLIMITED = 512.0F;
   /** The ranges the GUI cycles through. */
   static final float[] RANGES = {16.0F, 32.0F, 64.0F, 128.0F, 256.0F, UNLIMITED};

   public static final class Rule {
      public final Type type;
      /** A registry id ({@code minecraft:diamond_ore}) or a player name. */
      public final String id;
      public int color;
      public float range = UNLIMITED;
      public boolean enabled = true;
      public boolean mirror;

      public Rule(Type type, String id, int color) {
         this.type = type;
         this.id = id;
         this.color = color;
      }

      public String key() {
         return SpecificRules.key(this.type, this.id);
      }

      public boolean unlimited() {
         return this.range >= UNLIMITED;
      }

      public boolean inRange(double distanceSquared) {
         return this.unlimited() || distanceSquared <= (double)this.range * this.range;
      }

      /** The next range in the GUI's cycle (after the largest comes the smallest). */
      public float nextRange() {
         for (float candidate : RANGES) {
            if (candidate > this.range + 0.01F) {
               return candidate;
            }
         }

         return RANGES[0];
      }

      public String rangeLabel() {
         return this.unlimited() ? "Any range" : Math.round(this.range) + "m";
      }

      /** The values pairing copies (everything but the mirror flag), as text. */
      public String values() {
         return this.color + "|" + this.range + "|" + this.enabled;
      }

      public void copyValues(Rule from) {
         this.color = from.color;
         this.range = from.range;
         this.enabled = from.enabled;
      }

      JsonObject toJson() {
         JsonObject object = new JsonObject();
         object.addProperty("type", this.type.name());
         object.addProperty("id", this.id);
         object.addProperty("color", this.color);
         object.addProperty("range", this.range);
         object.addProperty("enabled", this.enabled);
         object.addProperty("mirror", this.mirror);
         return object;
      }

      static Rule fromJson(JsonObject object) {
         Type type = Type.valueOf(object.get("type").getAsString());
         Rule rule = new Rule(type, object.get("id").getAsString(), object.has("color") ? object.get("color").getAsInt() : 0xFF00FF00);
         if (object.has("range")) {
            rule.range = Math.max(RANGES[0], Math.min(UNLIMITED, object.get("range").getAsFloat()));
         }

         rule.enabled = !object.has("enabled") || object.get("enabled").getAsBoolean();
         rule.mirror = object.has("mirror") && object.get("mirror").getAsBoolean();
         return rule;
      }
   }

   /** Colours handed to new rules in turn. */
   private static final int[] PALETTE = {0xFF3CFF6E, 0xFF3CC8FF, 0xFFFF3CC8, 0xFFFFD23C, 0xFFFF6E3C, 0xFFB46EFF};

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

   /** Adds a rule (on, unlimited range, next palette colour) or returns the one already there. */
   public Rule add(Type type, String id) {
      Rule existing = this.find(type, id);
      if (existing != null) {
         return existing;
      }

      Rule rule = new Rule(type, id, PALETTE[this.rules.size() % PALETTE.length]);
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
