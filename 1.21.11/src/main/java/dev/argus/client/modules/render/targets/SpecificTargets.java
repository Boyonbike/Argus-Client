package dev.argus.client.modules.render.targets;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.SocialRegistry;
import dev.argus.client.modules.Module;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.StringSetting;
import java.util.ArrayList;
import java.util.Collection;
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
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * A module's target groups ({@link SpecificRules}) tied to the game: saved as a hidden setting, each group with real
 * settings for its category ({@link RuleSettings}), and looked up while drawing.
 * <p>
 * Players: a named player's group wins over the Friends or Enemies group, which wins over the All players group.
 * Entities: a dropped item's item group wins over its entity type's group.
 */
public final class SpecificTargets implements ClientAccess {
   private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
   private static final int[] PALETTE = {0xFF3CFF6E, 0xFF3CC8FF, 0xFFFF3CC8, 0xFFFFD23C, 0xFFFF6E3C, 0xFFB46EFF};
   private static List<Candidate> catalogue;

   /** Something that can be added: what it is, its id, its readable name, its category, an icon and search words. */
   public record Candidate(SpecificRules.Type type, String id, String name, SpecificRules.Category category, ItemStack icon, String aliases) {
      public SpecificRules.Member member() {
         return new SpecificRules.Member(this.type, this.id, this.category);
      }

      public String key() {
         return SpecificRules.key(this.type, this.id);
      }

      /** What to call its kind in a list: Block, Mob, Entity, Item, Player or Players. */
      public String kind() {
         return switch (this.type) {
            case ENTITY -> this.category == SpecificRules.Category.MOBS ? "Mob" : "Entity";
            case ITEM -> "Dropped item";
            case GROUP -> "Players";
            case EXTRA -> "Extra";
            default -> this.type.title();
         };
      }
   }

   private final Setting<String> store;
   private final SpecificRules rules = new SpecificRules();
   private String loaded;
   private String saved = "";
   private int version;
   private int structure;
   private final Map<Block, SpecificRules.Rule> blocks = new IdentityHashMap<>();
   private final Map<EntityType<?>, SpecificRules.Rule> entities = new IdentityHashMap<>();
   private final Map<Item, SpecificRules.Rule> items = new IdentityHashMap<>();
   private final Map<String, SpecificRules.Rule> players = new HashMap<>();
   private SpecificRules.Rule allPlayers;
   private SpecificRules.Rule friends;
   private SpecificRules.Rule enemies;
   private final Map<String, SpecificRules.Rule> extras = new HashMap<>();
   /** Whether this is ESP's list (its groups get ESP's label settings). */
   private boolean esp;
   private final Map<String, Candidate> described = new HashMap<>();

   SpecificTargets(Module module) {
      this.store = module.add(new StringSetting("SpecificTargets", "[]", ignored -> false));
      this.store.setConfigName("SpecificTargets");
   }

   /** Marks this as ESP's list, so its groups get ESP's label settings. Call before the list is first used. */
   public void forEsp() {
      this.esp = true;
   }

   /** The groups, re-read if the saved setting changed (config load, reset). Edit them, then call {@link #changed()}. */
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
    * Picks up edits made through a group's settings on the page (set settings change in place, without callbacks).
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

   /** Bumps when groups are added, removed or remade (not when a setting changes), so the page can rebuild its rows. */
   public int structure() {
      this.refresh();
      this.attach();
      return this.structure;
   }

   /** A group's settings. */
   public RuleSettings settings(SpecificRules.Rule rule) {
      if (!(rule.body instanceof RuleSettings settings) || !settings.builtFor.equals(signature(rule))) {
         this.attach();
      }

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

   private static String signature(SpecificRules.Rule rule) {
      StringBuilder text = new StringBuilder(rule.category().name());
      for (SpecificRules.Member member : rule.members()) {
         text.append('|').append(member.key());
      }

      return text.toString();
   }

   /** Gives every group real settings for its category and members (remade, keeping values, if its members changed). */
   private void attach() {
      List<SpecificRules.Rule> all = this.rules.all();
      for (int i = 0; i < all.size(); i++) {
         SpecificRules.Rule rule = all.get(i);
         String signature = signature(rule);
         if (rule.body instanceof RuleSettings existing && existing.builtFor.equals(signature)) {
            continue;
         }

         String json = rule.body.read();
         List<Object> targets = new ArrayList<>();
         for (SpecificRules.Member member : rule.members()) {
            Object target = target(member.type(), member.id());
            if (target != null) {
               targets.add(target);
            }
         }

         RuleSettings settings = RuleSettings.of(rule.category(), targets, PALETTE[i % PALETTE.length], this.esp);
         settings.write(json);
         settings.builtFor = signature;
         rule.body = settings;
         this.structure++;
      }
   }

   private void rebuild() {
      this.version++;
      this.structure++;
      this.blocks.clear();
      this.entities.clear();
      this.items.clear();
      this.players.clear();
      this.allPlayers = this.friends = this.enemies = null;
      this.extras.clear();
      for (SpecificRules.Rule rule : this.rules.all()) {
         if (!rule.enabled) {
            continue;
         }

         for (SpecificRules.Member member : rule.members()) {
            Object target = target(member.type(), member.id());
            switch (member.type()) {
               case BLOCK -> {
                  if (target instanceof Block block) this.blocks.put(block, rule);
               }
               case ENTITY -> {
                  if (target instanceof EntityType<?> type) this.entities.put(type, rule);
               }
               case ITEM -> {
                  if (target instanceof Item item) this.items.put(item, rule);
               }
               case PLAYER -> this.players.put(member.id().toLowerCase(Locale.ROOT), rule);
               case EXTRA -> this.extras.put(member.id(), rule);
               case GROUP -> {
                  switch (member.id()) {
                     case SpecificRules.ALL_PLAYERS -> this.allPlayers = rule;
                     case SpecificRules.FRIENDS -> this.friends = rule;
                     case SpecificRules.ENEMIES -> this.enemies = rule;
                     default -> {
                     }
                  }
               }
            }
         }
      }
   }

   // ---- lookups while drawing -----------------------------------------------------------------------------------------

   /**
    * The enabled group for an entity, or null. Players: their name's group, else Friends or Enemies, else All players.
    * Dropped items: their item's group, else the item entity type's.
    */
   public SpecificRules.Rule forEntity(Entity entity) {
      this.refresh();
      if (entity instanceof Player player) {
         if (!this.players.isEmpty()) {
            SpecificRules.Rule rule = this.players.get(player.getGameProfile().name().toLowerCase(Locale.ROOT));
            if (rule != null) {
               return rule;
            }
         }

         SocialRegistry social = ClientContext.socialRegistry;
         if (this.friends != null && social != null && social.isFriendPlayer(player)) {
            return this.friends;
         }

         if (this.enemies != null && social != null && social.isEnemyPlayer(player)) {
            return this.enemies;
         }

         return this.allPlayers;
      }

      if (!this.items.isEmpty() && entity instanceof ItemEntity item) {
         SpecificRules.Rule rule = this.items.get(item.getItem().getItem());
         if (rule != null) {
            return rule;
         }
      }

      return this.entities.isEmpty() ? null : this.entities.get(entity.getType());
   }

   /** The enabled group holding an extra ({@link SpecificRules#LIGHT_LEVELS}, {@link SpecificRules#CHORUS_TELEPORTS}), or null. */
   public SpecificRules.Rule extra(String id) {
      this.refresh();
      return this.extras.get(id);
   }

   /** The enabled group for a block, or null. */
   public SpecificRules.Rule forBlock(Block block) {
      this.refresh();
      return this.blocks.isEmpty() ? null : this.blocks.get(block);
   }

   /** Blocks in an enabled group (the block index scans for these). */
   public Set<Block> blocks() {
      this.refresh();
      return Collections.unmodifiableSet(this.blocks.keySet());
   }

   public boolean hasBlocks() {
      this.refresh();
      return !this.blocks.isEmpty();
   }

   /** Whether any enabled group with blocks plays a sound for new finds. */
   public boolean hasBlockSound() {
      for (SpecificRules.Rule rule : this.blocks.values()) {
         if (this.settings(rule).sound()) {
            return true;
         }
      }

      return false;
   }

   // ---- names and search ----------------------------------------------------------------------------------------------

   /** What a member is, with a readable name and icon. */
   public Candidate describe(SpecificRules.Member member) {
      return this.described.computeIfAbsent(member.key(), ignored -> candidate(member.type(), member.id()));
   }

   /** A group's members as text, e.g. "Diamond Ore, Ancient Debris +2". */
   public String label(SpecificRules.Rule rule, int shown) {
      List<SpecificRules.Member> members = rule.members();
      StringBuilder text = new StringBuilder();
      for (int i = 0; i < Math.min(shown, members.size()); i++) {
         if (i > 0) {
            text.append(", ");
         }

         text.append(this.describe(members.get(i)).name());
      }

      if (members.size() > shown) {
         text.append(" +").append(members.size() - shown);
      }

      return text.toString();
   }

   /**
    * Things matching what was typed, best first: exact names, then names starting with it, then names with a word
    * starting with it, then names, ids or search words containing it. {@code only} keeps to one category (null for all).
    * Players are the ones on the server's tab list, plus the All players, Friends and Enemies groups; with a
    * {@code player:} prefix any valid name can be added.
    */
   public static List<Candidate> search(String input, SpecificRules.Category only, int limit) {
      String raw = input == null ? "" : input.trim();
      String text = raw.toLowerCase(Locale.ROOT);
      boolean namedPlayer = false;
      if (text.startsWith("player:")) {
         namedPlayer = true;
         raw = raw.substring(7).trim();
         text = text.substring(7).trim();
      }

      if (text.isEmpty()) {
         return List.of();
      }

      String query = text.replace('_', ' ');
      List<Candidate> pool = new ArrayList<>(catalogue());
      pool.addAll(onlinePlayers());
      record Scored(Candidate candidate, int score) {
      }

      List<Scored> scored = new ArrayList<>();
      for (Candidate candidate : pool) {
         if (only != null && candidate.category() != only || namedPlayer && candidate.type() != SpecificRules.Type.PLAYER) {
            continue;
         }

         int score = score(candidate, query);
         if (score >= 0) {
            scored.add(new Scored(candidate, score));
         }
      }

      scored.sort(Comparator.<Scored>comparingInt(Scored::score)
         .thenComparingInt(item -> item.candidate().type() == SpecificRules.Type.GROUP ? 0 : 1)
         .thenComparingInt(item -> item.candidate().name().length())
         .thenComparing(item -> item.candidate().type()));
      List<Candidate> result = new ArrayList<>();
      for (Scored item : scored) {
         if (result.size() >= limit) {
            break;
         }

         result.add(item.candidate());
      }

      String name = raw;
      if (namedPlayer && PLAYER_NAME.matcher(name).matches() && result.stream().noneMatch(candidate -> candidate.id().equalsIgnoreCase(name))) {
         result.add(0, playerCandidate(name));
         if (result.size() > limit) {
            result.remove(result.size() - 1);
         }
      }

      return result;
   }

   // ---- generic terms -------------------------------------------------------------------------------------------------

   /** A generic term's matches, e.g. "Storage blocks" with every chest, barrel and shulker box. */
   public record TagMatch(String name, SpecificRules.Category category, List<Candidate> members) {
   }

   private record Tag(String name, SpecificRules.Category category, String words, java.util.function.Predicate<Object> test) {
   }

   private static boolean itemIs(Object target, ItemRules.Category category) {
      return target instanceof Item item && ItemRules.categoryOf(item.getDefaultInstance()) == category;
   }

   private static String path(Object target) {
      String id = target instanceof Block block ? idOf(block) : target instanceof Item item ? idOf(item)
         : target instanceof EntityType<?> type ? idOf(type) : "";
      return id.substring(id.indexOf(':') + 1);
   }

   private static final List<Tag> TAGS = List.of(
      new Tag("Storage blocks", SpecificRules.Category.BLOCKS, "storage storages container containers chest chests stash stashes loot inventory",
         target -> target instanceof net.minecraft.world.level.block.ChestBlock || target instanceof net.minecraft.world.level.block.BarrelBlock
            || target instanceof net.minecraft.world.level.block.EnderChestBlock || target instanceof net.minecraft.world.level.block.ShulkerBoxBlock
            || target instanceof net.minecraft.world.level.block.HopperBlock || target instanceof net.minecraft.world.level.block.DispenserBlock
            || target instanceof net.minecraft.world.level.block.AbstractFurnaceBlock || target instanceof net.minecraft.world.level.block.BrewingStandBlock
            || target instanceof net.minecraft.world.level.block.DecoratedPotBlock || target instanceof net.minecraft.world.level.block.CrafterBlock
            || target instanceof net.minecraft.world.level.block.ChiseledBookShelfBlock),
      new Tag("Shulker boxes", SpecificRules.Category.BLOCKS, "shulker shulkers box boxes",
         target -> target instanceof net.minecraft.world.level.block.ShulkerBoxBlock),
      new Tag("Dropped shulker boxes", SpecificRules.Category.ENTITIES, "shulker shulkers box boxes dropped",
         target -> target instanceof Item && path(target).endsWith("shulker_box")),
      new Tag("Ores", SpecificRules.Category.BLOCKS, "ore ores mining minerals mineral debris",
         target -> target instanceof Block && (path(target).endsWith("_ore") || path(target).equals("ancient_debris"))),
      new Tag("Beds", SpecificRules.Category.BLOCKS, "bed beds spawn",
         target -> target instanceof net.minecraft.world.level.block.BedBlock),
      new Tag("Spawners", SpecificRules.Category.BLOCKS, "spawner spawners dungeon dungeons trial",
         target -> target instanceof Block && (path(target).equals("spawner") || path(target).equals("trial_spawner") || path(target).equals("vault"))),
      new Tag("Weapons", SpecificRules.Category.ENTITIES, "weapon weapons sword swords pvp combat",
         target -> itemIs(target, ItemRules.Category.SWORD) || itemIs(target, ItemRules.Category.MACE) || itemIs(target, ItemRules.Category.TRIDENT)
            || target == Items.BOW || target == Items.CROSSBOW || target instanceof Item && path(target).endsWith("_spear")),
      new Tag("Armour", SpecificRules.Category.ENTITIES, "armour armor gear helmet helmets chestplate chestplates leggings boots",
         target -> itemIs(target, ItemRules.Category.ARMOR)),
      new Tag("Tools", SpecificRules.Category.ENTITIES, "tool tools pickaxe pickaxes axe axes shovel shovels hoe hoes",
         target -> itemIs(target, ItemRules.Category.TOOL)),
      new Tag("Valuables", SpecificRules.Category.ENTITIES, "valuable valuables loot gear rare",
         target -> target instanceof Item item && java.util.Arrays.asList(TargetSettings.Items.valuableItems()).contains(item)),
      new Tag("Hostile mobs", SpecificRules.Category.MOBS, "hostile hostiles monster monsters mob mobs enemy",
         target -> target instanceof EntityType<?> type && type.getCategory() == MobCategory.MONSTER),
      new Tag("Passive mobs", SpecificRules.Category.MOBS, "passive animal animals mob mobs friendly",
         target -> target instanceof EntityType<?> type && type.getCategory() != MobCategory.MONSTER && type.getCategory() != MobCategory.MISC),
      new Tag("Vehicles", SpecificRules.Category.ENTITIES, "vehicle vehicles boat boats raft rafts minecart minecarts cart carts",
         target -> target instanceof EntityType<?> && (path(target).contains("boat") || path(target).contains("raft") || path(target).contains("minecart")))
   );

   /**
    * Generic terms matching what was typed (at least three letters), each with every thing it covers, e.g. "storage"
    * gives Storage blocks. {@code only} keeps to one category (null for all).
    */
   public static List<TagMatch> searchTags(String input, SpecificRules.Category only) {
      String query = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
      if (query.length() < 3 || query.contains(":")) {
         return List.of();
      }

      List<TagMatch> found = new ArrayList<>();
      for (Tag tag : TAGS) {
         if (only != null && tag.category() != only) {
            continue;
         }

         boolean hit = tag.name().toLowerCase(Locale.ROOT).contains(query);
         for (String word : tag.words().split(" ")) {
            hit |= word.startsWith(query);
         }

         if (!hit) {
            continue;
         }

         List<Candidate> members = new ArrayList<>();
         for (Candidate candidate : catalogue()) {
            if (candidate.category() == tag.category() && tag.test().test(target(candidate.type(), candidate.id()))) {
               members.add(candidate);
            }
         }

         if (!members.isEmpty()) {
            found.add(new TagMatch(tag.name(), tag.category(), members));
         }
      }

      return found;
   }

   /** Lower is better; -1 means no match. */
   private static int score(Candidate candidate, String query) {
      String name = candidate.name().toLowerCase(Locale.ROOT);
      String id = candidate.id().toLowerCase(Locale.ROOT);
      String path = id.substring(id.indexOf(':') + 1).replace('_', ' ');
      if (name.equals(query) || path.equals(query)) {
         return 0;
      }

      if (name.startsWith(query) || path.startsWith(query) || (" " + candidate.aliases()).contains(" " + query)) {
         return 1;
      }

      if (name.contains(" " + query) || path.contains(" " + query)) {
         return 2;
      }

      return name.contains(query) || path.contains(query) || candidate.aliases().contains(query) ? 3 : -1;
   }

   private static List<Candidate> catalogue() {
      if (catalogue == null) {
         List<Candidate> list = new ArrayList<>();
         list.add(groupCandidate(SpecificRules.ALL_PLAYERS));
         list.add(groupCandidate(SpecificRules.FRIENDS));
         list.add(groupCandidate(SpecificRules.ENEMIES));
         list.add(extraCandidate(SpecificRules.LIGHT_LEVELS));
         list.add(extraCandidate(SpecificRules.CHORUS_TELEPORTS));
         for (Block block : BuiltInRegistries.BLOCK) {
            if (!block.defaultBlockState().isAir()) {
               list.add(candidate(SpecificRules.Type.BLOCK, BuiltInRegistries.BLOCK.getKey(block).toString()));
            }
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
            list.add(playerCandidate(name));
         }
      }

      return list;
   }

   private static Candidate playerCandidate(String name) {
      return new Candidate(SpecificRules.Type.PLAYER, name, name, SpecificRules.Category.PLAYERS, Items.PLAYER_HEAD.getDefaultInstance(), "player");
   }

   private static Candidate groupCandidate(String id) {
      return switch (id) {
         case SpecificRules.FRIENDS -> new Candidate(SpecificRules.Type.GROUP, id, "Friends", SpecificRules.Category.PLAYERS,
            Items.PLAYER_HEAD.getDefaultInstance(), "friend friends player players social");
         case SpecificRules.ENEMIES -> new Candidate(SpecificRules.Type.GROUP, id, "Enemies", SpecificRules.Category.PLAYERS,
            Items.PLAYER_HEAD.getDefaultInstance(), "enemy enemies player players social");
         default -> new Candidate(SpecificRules.Type.GROUP, SpecificRules.ALL_PLAYERS, "All players", SpecificRules.Category.PLAYERS,
            Items.PLAYER_HEAD.getDefaultInstance(), "player players everyone all");
      };
   }

   private static Candidate extraCandidate(String id) {
      return id.equals(SpecificRules.LIGHT_LEVELS)
         ? new Candidate(SpecificRules.Type.EXTRA, id, "Light levels", SpecificRules.Category.EXTRAS, Items.TORCH.getDefaultInstance(),
            "light level levels spawn spawnable dark mob spawning")
         : new Candidate(SpecificRules.Type.EXTRA, SpecificRules.CHORUS_TELEPORTS, "Chorus teleports", SpecificRules.Category.EXTRAS,
            Items.CHORUS_FRUIT.getDefaultInstance(), "chorus teleport teleports fruit");
   }

   private static Candidate candidate(SpecificRules.Type type, String id) {
      Object target = target(type, id);
      return switch (type) {
         case BLOCK -> {
            Block block = (Block)target;
            ItemStack icon = block == null || block.asItem() == Items.AIR ? null : block.asItem().getDefaultInstance();
            yield new Candidate(type, id, block == null ? id : block.getName().getString(), SpecificRules.Category.BLOCKS, icon, "block");
         }
         case ENTITY -> {
            EntityType<?> entity = (EntityType<?>)target;
            Item egg = lookup(BuiltInRegistries.ITEM, id + "_spawn_egg").orElse(null);
            boolean mob = entity != null && entity.getCategory() != MobCategory.MISC;
            yield new Candidate(type, id, entity == null ? id : entity.getDescription().getString(),
               mob ? SpecificRules.Category.MOBS : SpecificRules.Category.ENTITIES, egg == null ? null : egg.getDefaultInstance(), mob ? "mob" : "entity");
         }
         case ITEM -> {
            Item item = (Item)target;
            yield new Candidate(type, id, item == null ? id : item.getDefaultInstance().getHoverName().getString(), SpecificRules.Category.ENTITIES,
               item == null ? null : item.getDefaultInstance(), "item dropped");
         }
         case PLAYER -> playerCandidate(id);
         case GROUP -> groupCandidate(id);
         case EXTRA -> extraCandidate(id);
      };
   }

   /** Members for what an old config chose (see {@link TargetLinks#migrate}). */
   static List<SpecificRules.Member> members(SpecificRules.Type type, Collection<String> ids) {
      List<SpecificRules.Member> members = new ArrayList<>();
      for (String id : ids) {
         members.add(candidate(type, id).member());
      }

      return members;
   }

   static String idOf(Block block) {
      return BuiltInRegistries.BLOCK.getKey(block).toString();
   }

   static String idOf(Item item) {
      return BuiltInRegistries.ITEM.getKey(item).toString();
   }

   static String idOf(EntityType<?> type) {
      return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
   }

   /** The block, entity type or item a member names, or null (players and player groups, or an unknown id). */
   static Object target(SpecificRules.Type type, String id) {
      return switch (type) {
         case BLOCK -> lookup(BuiltInRegistries.BLOCK, id).orElse(null);
         case ENTITY -> lookup(BuiltInRegistries.ENTITY_TYPE, id).orElse(null);
         case ITEM -> lookup(BuiltInRegistries.ITEM, id).orElse(null);
         case PLAYER, GROUP, EXTRA -> null;
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
