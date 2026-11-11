package dev.argus.client.modules.render.targets;

import dev.argus.client.ClientAccess;
import dev.argus.client.modules.Module;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.StringSetting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * A module's specific targets ({@link SpecificRules}) tied to the game: saved as a hidden setting, each with real
 * settings for its kind ({@link RuleSettings}), and looked up while drawing. A matching rule overrides the general cards:
 * the thing is drawn by the rule's settings, and not at all where the rule rejects it.
 */
public final class SpecificTargets implements ClientAccess {
   private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
   private static List<Candidate> catalogue;

   /** Something that can be added: what it is, its id, its readable name and an icon to show. */
   public record Candidate(SpecificRules.Type type, String id, String name, ItemStack icon) {
   }

   private final Setting<String> store;
   private final SpecificRules rules = new SpecificRules();
   private String loaded;
   private String saved = "";
   private int version;
   private final Map<Block, SpecificRules.Rule> blocks = new IdentityHashMap<>();
   private final Map<EntityType<?>, SpecificRules.Rule> entities = new IdentityHashMap<>();
   private final Map<Item, SpecificRules.Rule> items = new IdentityHashMap<>();
   private final Map<String, SpecificRules.Rule> players = new HashMap<>();
   private final Map<String, Candidate> described = new HashMap<>();

   SpecificTargets(Module module) {
      this.store = module.add(new StringSetting("SpecificTargets", "[]", ignored -> false));
      this.store.setConfigName("SpecificTargets");
   }

   /** The rules, re-read if the saved setting changed (config load, reset). Edit them, then call {@link #changed()}. */
   public SpecificRules rules() {
      this.refresh();
      return this.rules;
   }

   /** Saves edits made to {@link #rules()} (added, removed, switched) and updates the lookups. */
   public void changed() {
      this.attach();
      this.save();
      this.rebuild();
   }

   /**
    * Picks up edits made through a rule's settings on the page (set settings change in place, without callbacks).
    * Called every client tick.
    */
   public void poll() {
      this.refresh();
      this.attach();
      String json = this.rules.toJson();
      if (!json.equals(this.saved)) {
         this.save();
         this.rebuild();
      }
   }

   /** Bumps on every change, so caches (block meshes) know to rebuild. */
   public int version() {
      this.refresh();
      return this.version;
   }

   /** A rule's settings. */
   public RuleSettings settings(SpecificRules.Rule rule) {
      this.attach();
      return (RuleSettings)rule.body;
   }

   private void save() {
      String json = this.rules.toJson();
      this.saved = json;
      this.loaded = json;
      this.store.setValue(json);
   }

   private void refresh() {
      String value = this.store.getValue();
      if (value != this.loaded) {
         this.loaded = value;
         this.rules.load(value);
         this.attach();
         this.saved = this.rules.toJson();
         this.rebuild();
      }
   }

   /** Gives every rule still holding plain text its real settings. */
   private void attach() {
      for (SpecificRules.Rule rule : this.rules.all()) {
         if (!(rule.body instanceof RuleSettings)) {
            String json = rule.body.read();
            RuleSettings settings = RuleSettings.of(rule.type, target(rule.type, rule.id), this.colorFor(rule));
            settings.write(json);
            rule.body = settings;
         }
      }
   }

   private int colorFor(SpecificRules.Rule rule) {
      int index = this.rules.all().indexOf(rule);
      int[] palette = {0xFF3CFF6E, 0xFF3CC8FF, 0xFFFF3CC8, 0xFFFFD23C, 0xFFFF6E3C, 0xFFB46EFF};
      return palette[Math.max(0, index) % palette.length];
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

         Object target = target(rule.type, rule.id);
         switch (rule.type) {
            case BLOCK -> {
               if (target instanceof Block block) this.blocks.put(block, rule);
            }
            case ENTITY -> {
               if (target instanceof EntityType<?> type) this.entities.put(type, rule);
            }
            case ITEM -> {
               if (target instanceof Item item) this.items.put(item, rule);
            }
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

   /** Whether any enabled block rule plays a sound for new finds. */
   public boolean hasBlockSound() {
      for (SpecificRules.Rule rule : this.blocks.values()) {
         if (this.settings(rule).sound()) {
            return true;
         }
      }

      return false;
   }

   // ---- names and search ----------------------------------------------------------------------------------------------

   /** What a rule targets, with a readable name and icon. */
   public Candidate describe(SpecificRules.Rule rule) {
      return this.described.computeIfAbsent(rule.key(), ignored -> candidate(rule.type, rule.id));
   }

   public String label(SpecificRules.Rule rule) {
      return this.describe(rule).name();
   }

   /**
    * Things matching what was typed, best first: exact names, then names starting with it, then names with a word
    * starting with it, then names or ids containing it. A prefix ({@code block:}, {@code mob:}/{@code entity:},
    * {@code item:}, {@code player:}) keeps to one kind. Players are the ones on the server's tab list; with
    * {@code player:} any valid name can be added.
    */
   public static List<Candidate> search(String input, int limit) {
      String text = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
      SpecificRules.Type only = null;
      int colon = text.indexOf(':');
      if (colon > 0) {
         only = switch (text.substring(0, colon).trim()) {
            case "block", "b" -> SpecificRules.Type.BLOCK;
            case "entity", "mob", "e", "m" -> SpecificRules.Type.ENTITY;
            case "item", "i" -> SpecificRules.Type.ITEM;
            case "player", "p" -> SpecificRules.Type.PLAYER;
            default -> null;
         };
         if (only != null) {
            text = text.substring(colon + 1).trim();
         }
      }

      if (text.isEmpty() && only == null) {
         return List.of();
      }

      String query = text.replace('_', ' ');
      List<Candidate> pool = new ArrayList<>(catalogue());
      pool.addAll(onlinePlayers());
      record Scored(Candidate candidate, int score) {
      }

      List<Scored> scored = new ArrayList<>();
      for (Candidate candidate : pool) {
         if (only != null && candidate.type() != only) {
            continue;
         }

         int score = score(candidate, query);
         if (score >= 0) {
            scored.add(new Scored(candidate, score));
         }
      }

      scored.sort(Comparator.<Scored>comparingInt(Scored::score)
         .thenComparingInt(item -> item.candidate().name().length())
         .thenComparing(item -> item.candidate().type()));
      List<Candidate> result = new ArrayList<>();
      for (Scored item : scored) {
         if (result.size() >= limit) {
            break;
         }

         result.add(item.candidate());
      }

      if (only == SpecificRules.Type.PLAYER && PLAYER_NAME.matcher(text).matches()
         && result.stream().noneMatch(candidate -> candidate.id().equalsIgnoreCase(input.substring(input.indexOf(':') + 1).trim()))) {
         String name = input.substring(input.indexOf(':') + 1).trim();
         result.add(0, new Candidate(SpecificRules.Type.PLAYER, name, name, Items.PLAYER_HEAD.getDefaultInstance()));
         if (result.size() > limit) {
            result.remove(result.size() - 1);
         }
      }

      return result;
   }

   /** Lower is better; -1 means no match. */
   private static int score(Candidate candidate, String query) {
      if (query.isEmpty()) {
         return 4;
      }

      String name = candidate.name().toLowerCase(Locale.ROOT);
      String id = candidate.id().toLowerCase(Locale.ROOT);
      String path = id.substring(id.indexOf(':') + 1).replace('_', ' ');
      if (name.equals(query) || path.equals(query)) {
         return 0;
      }

      if (name.startsWith(query) || path.startsWith(query)) {
         return 1;
      }

      if (name.contains(" " + query) || path.contains(" " + query)) {
         return 2;
      }

      return name.contains(query) || path.contains(query) ? 3 : -1;
   }

   private static List<Candidate> catalogue() {
      if (catalogue == null) {
         List<Candidate> list = new ArrayList<>();
         for (Block block : BuiltInRegistries.BLOCK) {
            if (block.defaultBlockState().isAir()) {
               continue;
            }

            list.add(candidate(SpecificRules.Type.BLOCK, BuiltInRegistries.BLOCK.getKey(block).toString()));
         }

         for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (type != EntityType.PLAYER) {
               list.add(candidate(SpecificRules.Type.ENTITY, BuiltInRegistries.ENTITY_TYPE.getKey(type).toString()));
            }
         }

         for (Item item : BuiltInRegistries.ITEM) {
            if (item != Items.AIR) {
               list.add(candidate(SpecificRules.Type.ITEM, BuiltInRegistries.ITEM.getKey(item).toString()));
            }
         }

         catalogue = List.copyOf(list);
      }

      return catalogue;
   }

   private static List<Candidate> onlinePlayers() {
      ClientPacketListener connection = argusclientMinecraft.getConnection();
      if (connection == null) {
         return List.of();
      }

      List<Candidate> list = new ArrayList<>();
      String self = argusclientMinecraft.player == null ? "" : argusclientMinecraft.player.getGameProfile().name();
      for (PlayerInfo info : connection.getOnlinePlayers()) {
         String name = info.getProfile().name();
         if (PLAYER_NAME.matcher(name).matches() && !name.equalsIgnoreCase(self)) {
            list.add(new Candidate(SpecificRules.Type.PLAYER, name, name, Items.PLAYER_HEAD.getDefaultInstance()));
         }
      }

      return list;
   }

   private static Candidate candidate(SpecificRules.Type type, String id) {
      Object target = target(type, id);
      return switch (type) {
         case BLOCK -> {
            Block block = (Block)target;
            yield new Candidate(type, id, block == null ? id : block.getName().getString(), block == null || block.asItem() == Items.AIR ? null : block.asItem().getDefaultInstance());
         }
         case ENTITY -> {
            EntityType<?> entity = (EntityType<?>)target;
            Item egg = lookup(BuiltInRegistries.ITEM, id + "_spawn_egg").orElse(null);
            yield new Candidate(type, id, entity == null ? id : entity.getDescription().getString(), egg == null ? null : egg.getDefaultInstance());
         }
         case ITEM -> {
            Item item = (Item)target;
            yield new Candidate(type, id, item == null ? id : item.getDefaultInstance().getHoverName().getString(), item == null ? null : item.getDefaultInstance());
         }
         case PLAYER -> new Candidate(type, id, id, Items.PLAYER_HEAD.getDefaultInstance());
      };
   }

   /** The block, entity type or item a rule names, or null (players, or an id this game doesn't have). */
   static Object target(SpecificRules.Type type, String id) {
      return switch (type) {
         case BLOCK -> lookup(BuiltInRegistries.BLOCK, id).orElse(null);
         case ENTITY -> lookup(BuiltInRegistries.ENTITY_TYPE, id).orElse(null);
         case ITEM -> lookup(BuiltInRegistries.ITEM, id).orElse(null);
         case PLAYER -> null;
      };
   }

   private static <T> Optional<T> lookup(Registry<T> registry, String id) {
      Identifier identifier = Identifier.tryParse(id);
      if (identifier == null) {
         return Optional.empty();
      }

      return registry.get(identifier).map(holder -> holder.value());
   }
}
