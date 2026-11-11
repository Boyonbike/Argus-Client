package dev.argus.client.modules.render.targets;

import dev.argus.client.modules.Module;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.StringSetting;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * A module's specific targets ({@link SpecificRules}) tied to the game: saved as a hidden setting, resolved to blocks,
 * entity types, items and player names, and looked up while drawing. A matching rule overrides the general cards: the
 * thing is drawn in the rule's colour inside the rule's range, and not at all outside it.
 */
public final class SpecificTargets {
   private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

   /** What a typed name resolved to. */
   public record Parsed(SpecificRules.Type type, String id, String label) {
   }

   private final Setting<String> store;
   private final SpecificRules rules = new SpecificRules();
   private String loaded;
   private int version;
   private final Map<Block, SpecificRules.Rule> blocks = new IdentityHashMap<>();
   private final Map<EntityType<?>, SpecificRules.Rule> entities = new IdentityHashMap<>();
   private final Map<Item, SpecificRules.Rule> items = new IdentityHashMap<>();
   private final Map<String, SpecificRules.Rule> players = new HashMap<>();
   private final Map<String, String> labels = new HashMap<>();

   SpecificTargets(Module module) {
      this.store = module.add(new StringSetting("SpecificTargets", "[]", ignored -> false));
      this.store.setConfigName("SpecificTargets");
   }

   /** The rules, re-read if the saved setting changed (config load, reset). Edit them, then call {@link #changed()}. */
   public SpecificRules rules() {
      this.refresh();
      return this.rules;
   }

   /** Saves edits made to {@link #rules()} and updates the lookups. */
   public void changed() {
      String json = this.rules.toJson();
      this.loaded = json;
      this.store.setValue(json);
      this.rebuild();
   }

   /** Bumps on every change, so caches (block meshes) know to rebuild. */
   public int version() {
      this.refresh();
      return this.version;
   }

   private void refresh() {
      String value = this.store.getValue();
      if (value != this.loaded) {
         this.loaded = value;
         this.rules.load(value);
         this.rebuild();
      }
   }

   private void rebuild() {
      this.version++;
      this.blocks.clear();
      this.entities.clear();
      this.items.clear();
      this.players.clear();
      for (SpecificRules.Rule rule : this.rules.all()) {
         if (!rule.enabled) {
            continue;
         }

         switch (rule.type) {
            case BLOCK -> lookup(BuiltInRegistries.BLOCK, rule.id).ifPresent(block -> this.blocks.put(block, rule));
            case ENTITY -> lookup(BuiltInRegistries.ENTITY_TYPE, rule.id).ifPresent(type -> this.entities.put(type, rule));
            case ITEM -> lookup(BuiltInRegistries.ITEM, rule.id).ifPresent(item -> this.items.put(item, rule));
            case PLAYER -> this.players.put(rule.id.toLowerCase(Locale.ROOT), rule);
         }
      }
   }

   // ---- lookups while drawing -----------------------------------------------------------------------------------------

   /** The enabled rule for an entity (a named player first, then a dropped item's type, then the entity type), or null. */
   public SpecificRules.Rule forEntity(Entity entity) {
      this.refresh();
      if (!this.players.isEmpty() && entity instanceof Player player) {
         SpecificRules.Rule rule = this.players.get(player.getGameProfile().name().toLowerCase(Locale.ROOT));
         if (rule != null) {
            return rule;
         }
      }

      if (!this.items.isEmpty() && entity instanceof ItemEntity item) {
         SpecificRules.Rule rule = this.items.get(item.getItem().getItem());
         if (rule != null) {
            return rule;
         }
      }

      return this.entities.isEmpty() ? null : this.entities.get(entity.getType());
   }

   /** The enabled rule for a block, or null. */
   public SpecificRules.Rule forBlock(Block block) {
      this.refresh();
      return this.blocks.isEmpty() ? null : this.blocks.get(block);
   }

   /** Blocks with an enabled rule (the block index scans for these too). */
   public Set<Block> blocks() {
      this.refresh();
      return Collections.unmodifiableSet(this.blocks.keySet());
   }

   public boolean hasBlocks() {
      this.refresh();
      return !this.blocks.isEmpty();
   }

   // ---- names ---------------------------------------------------------------------------------------------------------

   /** A readable name for a rule, e.g. "Diamond Ore". */
   public String label(SpecificRules.Rule rule) {
      return this.labels.computeIfAbsent(rule.key(), ignored -> labelOf(rule.type, rule.id));
   }

   private static String labelOf(SpecificRules.Type type, String id) {
      return switch (type) {
         case BLOCK -> lookup(BuiltInRegistries.BLOCK, id).map(block -> block.getName().getString()).orElse(id);
         case ENTITY -> lookup(BuiltInRegistries.ENTITY_TYPE, id).map(entity -> entity.getDescription().getString()).orElse(id);
         case ITEM -> lookup(BuiltInRegistries.ITEM, id).map(item -> item.getDefaultInstance().getHoverName().getString()).orElse(id);
         case PLAYER -> id;
      };
   }

   /**
    * Resolves what a player typed. A prefix picks the kind ({@code block:}, {@code entity:} or {@code mob:},
    * {@code item:}, {@code player:}); without one, blocks are tried first, then entities, then items, then player names.
    */
   public static Parsed parse(String input) {
      String text = input == null ? "" : input.trim();
      if (text.isEmpty()) {
         throw new IllegalArgumentException("Type a block, entity, item or player name.");
      }

      SpecificRules.Type forced = null;
      int colon = text.indexOf(':');
      if (colon > 0) {
         String prefix = text.substring(0, colon).toLowerCase(Locale.ROOT).trim();
         forced = switch (prefix) {
            case "block", "b" -> SpecificRules.Type.BLOCK;
            case "entity", "mob", "e" -> SpecificRules.Type.ENTITY;
            case "item", "i" -> SpecificRules.Type.ITEM;
            case "player", "p" -> SpecificRules.Type.PLAYER;
            default -> null;
         };
         if (forced != null) {
            text = text.substring(colon + 1).trim();
         }
      }

      if (forced == SpecificRules.Type.PLAYER) {
         if (!PLAYER_NAME.matcher(text).matches()) {
            throw new IllegalArgumentException("\"" + text + "\" isn't a valid player name.");
         }

         return new Parsed(SpecificRules.Type.PLAYER, text, text);
      }

      String id = text.toLowerCase(Locale.ROOT).replace(' ', '_');
      SpecificRules.Type[] order = forced == null
         ? new SpecificRules.Type[]{SpecificRules.Type.BLOCK, SpecificRules.Type.ENTITY, SpecificRules.Type.ITEM}
         : new SpecificRules.Type[]{forced};
      for (SpecificRules.Type type : order) {
         Optional<Identifier> found = switch (type) {
            case BLOCK -> lookup(BuiltInRegistries.BLOCK, id).map(BuiltInRegistries.BLOCK::getKey);
            case ENTITY -> lookup(BuiltInRegistries.ENTITY_TYPE, id).map(BuiltInRegistries.ENTITY_TYPE::getKey);
            case ITEM -> lookup(BuiltInRegistries.ITEM, id).map(BuiltInRegistries.ITEM::getKey);
            case PLAYER -> Optional.empty();
         };
         if (found.isPresent()) {
            String key = found.get().toString();
            return new Parsed(type, key, labelOf(type, key));
         }
      }

      if (forced == null && PLAYER_NAME.matcher(text).matches()) {
         return new Parsed(SpecificRules.Type.PLAYER, text, text);
      }

      throw new IllegalArgumentException(
         forced == null ? "No block, entity or item called \"" + text + "\"." : "No " + forced.title().toLowerCase(Locale.ROOT) + " called \"" + text + "\"."
      );
   }

   private static <T> Optional<T> lookup(Registry<T> registry, String id) {
      Identifier identifier = Identifier.tryParse(id);
      if (identifier == null) {
         return Optional.empty();
      }

      return registry.get(identifier).map(holder -> holder.value());
   }
}
