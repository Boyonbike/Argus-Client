package dev.argus.client.modules.render.targets;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A module's targets: groups of things (blocks, entity types, dropped items, players or player groups) added together,
 * each group with its own settings for its category. A thing can be in only one group. Plain Java (no game classes), so
 * the list, its JSON form and the mirror sync between ESP and Tracers can be unit-tested.
 */
public final class SpecificRules {
   /** What a member names. */
   public enum Type {
      BLOCK("Block"),
      ENTITY("Entity"),
      ITEM("Item"),
      PLAYER("Player"),
      /** All players, friends or enemies (ids {@link #ALL_PLAYERS}, {@link #FRIENDS}, {@link #ENEMIES}). */
      GROUP("Players");

      private final String title;

      Type(String title) {
         this.title = title;
      }

      public String title() {
         return this.title;
      }
   }

   public static final String ALL_PLAYERS = "players";
   public static final String FRIENDS = "friends";
   public static final String ENEMIES = "enemies";

   /** A group's category, which picks its settings. A group mixing categories is {@link #ALL}. */
   public enum Category {
      ALL("All"),
      BLOCKS("Blocks"),
      PLAYERS("Players"),
      MOBS("Mobs"),
      ENTITIES("Entities");

      private final String title;

      Category(String title) {
         this.title = title;
      }

      public String title() {
         return this.title;
      }

      public static Category of(Collection<Member> members) {
         Category found = null;
         for (Member member : members) {
            if (found == null) {
               found = member.category();
            } else if (found != member.category()) {
               return ALL;
            }
         }

         return found == null ? ALL : found;
      }
   }

   /** One thing in a group. {@code category} is decided when it's added (a mob or not needs the game). */
   public record Member(Type type, String id, Category category) {
      public String key() {
         return SpecificRules.key(this.type, this.id);
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
      /** Identifies the group on both pages, so ESP's and Tracers' copies pair up. */
      public final String id;
      private final List<Member> members = new ArrayList<>();
      public boolean enabled = true;
      public boolean mirror;
      /** This group's settings (colour, range and the rest for its category). */
      public Body body = new TextBody("{}");

      public Rule(String id, Collection<Member> members) {
         this.id = id;
         this.members.addAll(members);
      }

      public List<Member> members() {
         return Collections.unmodifiableList(this.members);
      }

      public Category category() {
         return Category.of(this.members);
      }

      /** The values pairing copies (everything but the mirror flag), as text. */
      public String values() {
         return this.enabled + "|" + membersArray(this.members) + "|" + this.body.read();
      }

      JsonObject toJson() {
         JsonObject object = new JsonObject();
         object.addProperty("id", this.id);
         object.add("members", membersArray(this.members));
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
         List<Member> members = new ArrayList<>();
         if (object.get("members") instanceof JsonArray array) {
            for (JsonElement element : array) {
               JsonObject member = element.getAsJsonObject();
               members.add(new Member(
                  Type.valueOf(member.get("type").getAsString()), member.get("id").getAsString(), Category.valueOf(member.get("category").getAsString())
               ));
            }
         }

         Rule rule = new Rule(object.get("id").getAsString(), members);
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

   private static JsonArray membersArray(List<Member> members) {
      JsonArray array = new JsonArray();
      for (Member member : members) {
         JsonObject object = new JsonObject();
         object.addProperty("type", member.type().name());
         object.addProperty("id", member.id());
         object.addProperty("category", member.category().name());
         array.add(object);
      }

      return array;
   }

   public List<Rule> all() {
      return Collections.unmodifiableList(this.rules);
   }

   public boolean isEmpty() {
      return this.rules.isEmpty();
   }

   public Rule find(String id) {
      for (Rule rule : this.rules) {
         if (rule.id.equals(id)) {
            return rule;
         }
      }

      return null;
   }

   /** The group a thing is in, or null. */
   public Rule owner(Type type, String id) {
      String key = key(type, id);
      for (Rule rule : this.rules) {
         for (Member member : rule.members) {
            if (member.key().equals(key)) {
               return rule;
            }
         }
      }

      return null;
   }

   /**
    * Adds a group of the given things, leaving out any already in a group (each thing can be added once). Returns the
    * new group, or null if nothing was left to add.
    */
   public Rule add(Collection<Member> members) {
      List<Member> fresh = new ArrayList<>();
      Set<String> seen = new HashSet<>();
      for (Member member : members) {
         if (this.owner(member.type(), member.id()) == null && seen.add(member.key())) {
            fresh.add(member);
         }
      }

      if (fresh.isEmpty()) {
         return null;
      }

      Rule rule = new Rule(newId(), fresh);
      this.rules.add(rule);
      return rule;
   }

   /**
    * Makes this list hold a copy of another page's group (same id): its members, on/off and settings. Things in the
    * copy are taken out of any other group here, and groups left empty are removed. Returns the local copy.
    */
   public Rule put(Rule from) {
      Rule local = this.find(from.id);
      if (local == null) {
         local = new Rule(from.id, from.members);
         this.rules.add(local);
      } else {
         local.members.clear();
         local.members.addAll(from.members);
      }

      Set<String> taken = new HashSet<>();
      for (Member member : from.members) {
         taken.add(member.key());
      }

      Rule keep = local;
      this.rules.removeIf(rule -> {
         if (rule == keep) {
            return false;
         }

         rule.members.removeIf(member -> taken.contains(member.key()));
         return rule.members.isEmpty();
      });
      local.enabled = from.enabled;
      String json = from.body.read();
      if (!json.equals(local.body.read())) {
         local.body.write(json);
      }

      return local;
   }

   public boolean remove(Rule rule) {
      return this.rules.removeIf(candidate -> candidate.id.equals(rule.id));
   }

   private static String newId() {
      return Long.toString(System.currentTimeMillis(), 36) + Integer.toString(ThreadLocalRandom.current().nextInt(1 << 20), 36);
   }

   public String toJson() {
      JsonArray array = new JsonArray();
      for (Rule rule : this.rules) {
         array.add(rule.toJson());
      }

      return array.toString();
   }

   /** Replaces the list with a saved one; unreadable entries are skipped, and so are things already in a group. */
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

         Set<String> ids = new HashSet<>();
         Set<String> things = new HashSet<>();
         for (JsonElement element : parsed.getAsJsonArray()) {
            try {
               Rule rule = Rule.fromJson(element.getAsJsonObject());
               rule.members.removeIf(member -> !things.add(member.key()));
               if (!rule.members.isEmpty() && ids.add(rule.id)) {
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
    * Keeps mirrored groups identical between two lists (ESP on the left). A group counts as mirrored only while both
    * sides have it with the mirror flag set; otherwise the flag is cleared on whichever side still has it. For each link,
    * the side that differs from the last agreed value is copied across; if both changed, the left wins.
    *
    * @param agreed the last agreed values by group id, kept by the caller between syncs
    * @return {@link #LEFT_CHANGED} and/or {@link #RIGHT_CHANGED}
    */
   public static int sync(SpecificRules left, SpecificRules right, Map<String, String> agreed) {
      int changed = 0;
      changed |= dropHalfLinks(left, right) ? LEFT_CHANGED : 0;
      changed |= dropHalfLinks(right, left) ? RIGHT_CHANGED : 0;
      Set<String> linked = new HashSet<>();
      for (Rule rule : List.copyOf(left.rules)) {
         if (!rule.mirror) {
            continue;
         }

         Rule other = right.find(rule.id);
         if (other == null) {
            continue;
         }

         linked.add(rule.id);
         String leftValues = rule.values();
         String rightValues = other.values();
         if (leftValues.equals(rightValues)) {
            agreed.put(rule.id, leftValues);
            continue;
         }

         if (!leftValues.equals(agreed.get(rule.id))) {
            right.put(rule).mirror = true;
            agreed.put(rule.id, leftValues);
            changed |= RIGHT_CHANGED;
         } else {
            left.put(other).mirror = true;
            agreed.put(rule.id, rightValues);
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
            Rule other = theirs.find(rule.id);
            if (other == null || !other.mirror) {
               rule.mirror = false;
               changed = true;
            }
         }
      }

      return changed;
   }
}
