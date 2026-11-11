package dev.argus.client.modules.render.targets;

import dev.argus.client.modules.Module;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.RegistrySetSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.SettingLabelCondition;
import dev.argus.client.settings.options.ListFilterMode;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;

/**
 * The four target cards (Players, Mobs, Items, Blocks) that ESP and Tracers both show. Each module gets its own copy
 * with identical names and config keys, so the two pages look the same and pairing can match settings by config name.
 * Each card starts with Enabled and ends with Range; the Mirror flag is hidden and saved with the module.
 */
public final class TargetSettings {
   public enum Kind {
      PLAYERS("Players"),
      MOBS("Mobs"),
      ITEMS("Items"),
      BLOCKS("Blocks");

      private final String title;

      Kind(String title) {
         this.title = title;
      }

      public String title() {
         return this.title;
      }
   }

   public enum Visibility implements NamedEntry {
      SHOW("Show"),
      HIDE("Hide");

      private final String name;

      Visibility(String name) {
         this.name = name;
      }

      @Override
      public String getName() {
         return this.name;
      }
   }

   public enum ColorMode implements NamedEntry {
      AUTO("Auto"),
      CUSTOM("Custom");

      private final String name;

      ColorMode(String name) {
         this.name = name;
      }

      @Override
      public String getName() {
         return this.name;
      }
   }

   public enum HeightChoice implements NamedEntry {
      NEW("1.18"),
      OLD("1.12");

      private final String name;

      HeightChoice(String name) {
         this.name = name;
      }

      @Override
      public String getName() {
         return this.name;
      }
   }

   public final Players players;
   public final Mobs mobs;
   public final Items items;
   public final Blocks blocks;
   /** Rules for one specific block, entity type, item or player; they override the cards above for that thing. */
   public final SpecificTargets specific;
   /** Set once the cards' choices have moved into the target list (see {@link TargetLinks#migrate}). */
   public final Setting<Boolean> migrated;
   private final List<Group> groups;

   /** Adds the four cards to the module, in order, at the current end of its settings. */
   public TargetSettings(Module module, Set<Kind> enabledByDefault) {
      this.players = new Players(module, enabledByDefault.contains(Kind.PLAYERS));
      this.mobs = new Mobs(module, enabledByDefault.contains(Kind.MOBS));
      this.items = new Items(module, enabledByDefault.contains(Kind.ITEMS));
      this.blocks = new Blocks(module, enabledByDefault.contains(Kind.BLOCKS));
      this.groups = List.of(this.players, this.mobs, this.items, this.blocks);
      this.specific = new SpecificTargets(module);
      this.migrated = module.add(new BooleanSetting("TargetsMigrated", false, ignored -> false));
      this.migrated.setConfigName("TargetsMigrated");
   }

   public TargetSettings(Module module, Kind... enabledByDefault) {
      this(module, enabledByDefault.length == 0 ? EnumSet.noneOf(Kind.class) : EnumSet.of(enabledByDefault[0], enabledByDefault));
   }

   public List<Group> groups() {
      return this.groups;
   }

   public Group group(Kind kind) {
      return this.groups.get(kind.ordinal());
   }

   /** One target card. Settings in {@link #shared()} are the ones pairing copies; Mirror itself is never copied. */
   public abstract static class Group {
      public final Kind kind;
      public final Setting<Boolean> card;
      public final Setting<Boolean> enabled;
      public final Setting<Boolean> mirror;
      public Setting<Float> range;
      private final Module module;
      private final List<Setting<?>> shared = new ArrayList<>();

      Group(Module module, Kind kind, boolean enabledByDefault) {
         this.module = module;
         this.kind = kind;
         // The cards are no longer shown: the target list replaced them. They stay for old configs (see TargetLinks#migrate).
         this.card = module.add(new BooleanSetting(kind.title(), true, ignored -> false).markAsGroup());
         this.card.setConfigName(kind.title() + "Card");
         this.enabled = this.add("Enabled", new BooleanSetting("Enabled", enabledByDefault));
         this.mirror = module.add(new BooleanSetting("Mirror", false, ignored -> false));
         this.mirror.setConfigName(kind.title() + "Mirror");
      }

      /** Adds a setting to this card with a config key unique to the card, for example {@code PlayersColor}. */
      protected final <S extends Setting<?>> S add(String key, S setting) {
         setting.dependsOn(this.card);
         setting.setConfigName(this.kind.title() + key);
         this.module.add(setting);
         this.shared.add(setting);
         return setting;
      }

      protected final void addRange() {
         NumberSetting<Float> number = new NumberSetting<>("Range", 512.0F, 8.0F, 512.0F).withUnit("m");
         number.setBoundaryLabel("Unlimited", SettingLabelCondition.MAX);
         this.range = this.add("Range", number);
      }

      public final List<Setting<?>> shared() {
         return Collections.unmodifiableList(this.shared);
      }

      public final boolean active() {
         return this.enabled.getValue();
      }

      public final boolean mirrored() {
         return this.mirror.getValue();
      }

      /** Whether a squared distance is inside this card's range (the maximum means unlimited). */
      public final boolean inRange(double distanceSquared) {
         if (this.range.isAtMaximum()) {
            return true;
         }

         double limit = this.range.getValue();
         return distanceSquared <= limit * limit;
      }
   }

   public static final class Players extends Group {
      public final Setting<Visibility> friends;
      public final Setting<Visibility> enemies;
      public final Setting<Visibility> others;
      public final Setting<Boolean> ignoreNaked;
      public final Setting<Color> color;
      public final Setting<Boolean> socialColors;
      public final Setting<Color> friendColor;
      public final Setting<Color> enemyColor;

      Players(Module module, boolean enabledByDefault) {
         super(module, Kind.PLAYERS, enabledByDefault);
         this.friends = this.add("Friends", new EnumSetting<>("Friends", Visibility.SHOW));
         this.enemies = this.add("Enemies", new EnumSetting<>("Enemies", Visibility.SHOW));
         this.others = this.add("Others", new EnumSetting<>("Others", Visibility.SHOW));
         this.ignoreNaked = this.add("IgnoreNaked", new BooleanSetting("IgnoreNaked", false));
         this.color = this.add("Color", new ColorSetting("Color", new Color(-4351489, true)));
         this.socialColors = this.add("SocialColors", new BooleanSetting("SocialColors", true));
         this.friendColor = this.add("FriendColor", new ColorSetting("FriendColor", new Color(-16711681, true), ignored -> !this.socialColors.getValue()));
         this.enemyColor = this.add("EnemyColor", new ColorSetting("EnemyColor", new Color(-4980736, true), ignored -> !this.socialColors.getValue()));
         this.addRange();
      }
   }

   public static final class Mobs extends Group {
      public final Setting<Boolean> hostile;
      public final Setting<Color> hostileColor;
      public final Setting<Boolean> neutral;
      public final Setting<Color> neutralColor;
      public final Setting<Boolean> passive;
      public final Setting<Color> passiveColor;
      public final Setting<ListFilterMode> filter;
      public final Setting<Set<EntityType<?>>> list;
      public final Setting<Boolean> ignoreVehicles;
      public final Setting<Boolean> sound;

      Mobs(Module module, boolean enabledByDefault) {
         super(module, Kind.MOBS, enabledByDefault);
         this.hostile = this.add("Hostile", new BooleanSetting("Hostile", true));
         this.hostileColor = this.add("HostileColor", new ColorSetting("HostileColor", new Color(255, 70, 70), ignored -> this.hostile.getValue()));
         this.neutral = this.add("Neutral", new BooleanSetting("Neutral", true));
         this.neutralColor = this.add("NeutralColor", new ColorSetting("NeutralColor", new Color(255, 190, 60), ignored -> this.neutral.getValue()));
         this.passive = this.add("Passive", new BooleanSetting("Passive", false));
         this.passiveColor = this.add("PassiveColor", new ColorSetting("PassiveColor", new Color(90, 220, 110), ignored -> this.passive.getValue()));
         this.filter = this.add("Filter", new EnumSetting<>("Filter", ListFilterMode.ANY));
         this.list = this.add("List", new RegistrySetSetting<EntityType<?>>("List", BuiltInRegistries.ENTITY_TYPE, ignored -> this.filter.getValue().isFiltering()));
         this.ignoreVehicles = this.add("IgnoreVehicles", new BooleanSetting("IgnoreVehicles", false));
         this.sound = this.add("Sound", new BooleanSetting("Sound", false));
         this.addRange();
      }
   }

   public static final class Items extends Group {
      public final Setting<ListFilterMode> filter;
      public final Setting<Set<Item>> list;
      public final Setting<ItemRules.EnchantMode> enchants;
      public final Setting<Set<String>> toolEnchants;
      public final Setting<Set<String>> swordEnchants;
      public final Setting<Set<String>> armorEnchants;
      public final Setting<Set<String>> maceEnchants;
      public final Setting<Set<String>> tridentEnchants;
      public final Setting<Color> color;
      public final Setting<Boolean> distanceColor;
      public final Setting<Color> farColor;
      public final Setting<Float> farDistance;
      public final Setting<Boolean> chatAlert;
      public final Setting<Boolean> coords;

      Items(Module module, boolean enabledByDefault) {
         super(module, Kind.ITEMS, enabledByDefault);
         this.filter = this.add("Filter", new EnumSetting<>("Filter", ListFilterMode.WHITELIST));
         this.list = this.add("List", new RegistrySetSetting<>("List", BuiltInRegistries.ITEM, ignored -> this.filter.getValue().isFiltering(), valuableItems()));
         this.enchants = this.add("Enchants", new EnumSetting<>("Enchants", ItemRules.EnchantMode.ANY));
         this.toolEnchants = this.add("ToolEnchants", this.enchantList("ToolEnchants", "efficiency", "unbreaking", "mending"));
         this.swordEnchants = this.add("SwordEnchants", this.enchantList("SwordEnchants", "unbreaking", "mending"));
         this.armorEnchants = this.add("ArmorEnchants", this.enchantList("ArmorEnchants", "unbreaking", "mending"));
         this.maceEnchants = this.add("MaceEnchants", this.enchantList("MaceEnchants", "unbreaking", "mending"));
         this.tridentEnchants = this.add("TridentEnchants", this.enchantList("TridentEnchants", "unbreaking", "mending"));
         this.color = this.add("Color", new ColorSetting("Color", new Color(255, 25, 255)));
         this.distanceColor = this.add("DistanceColor", new BooleanSetting("DistanceColor", true));
         this.farColor = this.add("FarColor", new ColorSetting("FarColor", new Color(25, 255, 255), ignored -> this.distanceColor.getValue()));
         this.farDistance = this.add(
            "FarDistance", new NumberSetting<>("FarDistance", 128.0F, 8.0F, 512.0F, ignored -> this.distanceColor.getValue()).withUnit("m")
         );
         this.chatAlert = this.add("ChatAlert", new BooleanSetting("ChatAlert", false));
         this.coords = this.add("Coords", new BooleanSetting("Coords", true, ignored -> this.chatAlert.getValue()));
         this.addRange();
      }

      private EnchantSetSetting enchantList(String name, String... defaults) {
         return new EnchantSetSetting(name, ignored -> this.enchants.getValue() == ItemRules.EnchantMode.SPECIFIC, defaults);
      }

      /** The default whitelist: valuable gear and loot (after Trouser Streak's Advanced Item ESP). */
      static Item[] valuableItems() {
         List<Item> items = new ArrayList<>(List.of(
            net.minecraft.world.item.Items.DIAMOND_HELMET, net.minecraft.world.item.Items.DIAMOND_CHESTPLATE,
            net.minecraft.world.item.Items.DIAMOND_LEGGINGS, net.minecraft.world.item.Items.DIAMOND_BOOTS,
            net.minecraft.world.item.Items.NETHERITE_HELMET, net.minecraft.world.item.Items.NETHERITE_CHESTPLATE,
            net.minecraft.world.item.Items.NETHERITE_LEGGINGS, net.minecraft.world.item.Items.NETHERITE_BOOTS,
            net.minecraft.world.item.Items.ELYTRA, net.minecraft.world.item.Items.MACE, net.minecraft.world.item.Items.TRIDENT,
            net.minecraft.world.item.Items.DIAMOND_SWORD, net.minecraft.world.item.Items.DIAMOND_AXE,
            net.minecraft.world.item.Items.DIAMOND_PICKAXE, net.minecraft.world.item.Items.DIAMOND_SHOVEL,
            net.minecraft.world.item.Items.DIAMOND_HOE, net.minecraft.world.item.Items.NETHERITE_SWORD,
            net.minecraft.world.item.Items.NETHERITE_AXE, net.minecraft.world.item.Items.NETHERITE_PICKAXE,
            net.minecraft.world.item.Items.NETHERITE_SHOVEL, net.minecraft.world.item.Items.NETHERITE_HOE,
            net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE, net.minecraft.world.item.Items.END_CRYSTAL,
            net.minecraft.world.item.Items.ENDER_CHEST, net.minecraft.world.item.Items.TOTEM_OF_UNDYING,
            net.minecraft.world.item.Items.EXPERIENCE_BOTTLE, net.minecraft.world.item.Items.SHULKER_BOX
         ));
         for (DyeColor dye : DyeColor.values()) {
            items.add(ShulkerBoxBlock.getBlockByColor(dye).asItem());
         }

         return items.toArray(Item[]::new);
      }
   }

   public static final class Blocks extends Group {
      public final Setting<Set<Block>> list;
      public final Setting<ColorMode> colorMode;
      public final Setting<Color> color;
      public final Setting<Boolean> markOpened;
      public final Setting<Color> openedColor;
      public final Setting<Boolean> hideOpened;
      public final Setting<Boolean> ignoreNatural;
      public final Setting<Boolean> dungeon;
      public final Setting<Boolean> fortress;
      public final Setting<Boolean> bastion;
      public final Setting<Boolean> ancientCity;
      public final Setting<Boolean> trialChambers;
      public final Setting<HeightChoice> height;
      public final Setting<Boolean> onlyOminous;
      public final Setting<Boolean> sound;

      Blocks(Module module, boolean enabledByDefault) {
         super(module, Kind.BLOCKS, enabledByDefault);
         this.list = this.add("List", new RegistrySetSetting<>("List", BuiltInRegistries.BLOCK, storageBlocks()));
         this.colorMode = this.add("ColorMode", new EnumSetting<>("ColorMode", ColorMode.AUTO));
         this.color = this.add("Color", new ColorSetting("Color", new Color(255, 160, 0), ignored -> this.colorMode.getValue() == ColorMode.CUSTOM));
         this.markOpened = this.add("MarkOpened", new BooleanSetting("MarkOpened", false));
         this.openedColor = this.add("OpenedColor", new ColorSetting("OpenedColor", new Color(217, 199, 255), ignored -> this.markOpened.getValue()));
         this.hideOpened = this.add("HideOpened", new BooleanSetting("HideOpened", false));
         this.ignoreNatural = this.add("IgnoreNatural", new BooleanSetting("IgnoreNatural", false));
         this.dungeon = this.add("Dungeon", new BooleanSetting("Dungeon", true, ignored -> this.ignoreNatural.getValue()));
         this.fortress = this.add("Fortress", new BooleanSetting("Fortress", true, ignored -> this.ignoreNatural.getValue()));
         this.bastion = this.add("Bastion", new BooleanSetting("Bastion", true, ignored -> this.ignoreNatural.getValue()));
         this.ancientCity = this.add("AncientCity", new BooleanSetting("AncientCity", false, ignored -> this.ignoreNatural.getValue()));
         this.trialChambers = this.add("TrialChambers", new BooleanSetting("TrialChambers", true, ignored -> this.ignoreNatural.getValue()));
         this.height = this.add("Height", new EnumSetting<>("Height", HeightChoice.NEW));
         this.onlyOminous = this.add("OnlyOminous", new BooleanSetting("OnlyOminous", false, ignored -> this.list.getValue().contains(net.minecraft.world.level.block.Blocks.VAULT)));
         this.sound = this.add("Sound", new BooleanSetting("Sound", false));
         this.addRange();
      }

      /** Bit mask of the natural structures whose chests this card ignores (see {@link BlockTargetIndex}). */
      public int ignoredStructures() {
         if (!this.ignoreNatural.getValue()) {
            return 0;
         }

         int mask = 0;
         if (this.dungeon.getValue()) mask |= BlockTargetIndex.DUNGEON;
         if (this.fortress.getValue()) mask |= BlockTargetIndex.FORTRESS;
         if (this.bastion.getValue()) mask |= BlockTargetIndex.BASTION;
         if (this.ancientCity.getValue()) mask |= BlockTargetIndex.ANCIENT_CITY;
         if (this.trialChambers.getValue()) mask |= BlockTargetIndex.TRIAL_CHAMBERS;
         return mask;
      }

      /** The default block list: storage containers. */
      static Block[] storageBlocks() {
         List<Block> blocks = new ArrayList<>(List.of(
            net.minecraft.world.level.block.Blocks.CHEST, net.minecraft.world.level.block.Blocks.TRAPPED_CHEST,
            net.minecraft.world.level.block.Blocks.BARREL, net.minecraft.world.level.block.Blocks.ENDER_CHEST,
            net.minecraft.world.level.block.Blocks.SHULKER_BOX
         ));
         for (DyeColor dye : DyeColor.values()) {
            blocks.add(ShulkerBoxBlock.getBlockByColor(dye));
         }

         return blocks.toArray(Block[]::new);
      }
   }
}
