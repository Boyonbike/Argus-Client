package dev.argus.client.modules.render.targets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.SettingLabelCondition;
import dev.argus.client.settings.options.ColorsSchemeChoice;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The settings of one target group, by its category:
 * <ul>
 *   <li><b>All</b> (mixed): colour, range;</li>
 *   <li><b>Players:</b> colour, ignore naked, range;</li>
 *   <li><b>Mobs:</b> colour, ignore vehicles, sound alert, range;</li>
 *   <li><b>Entities:</b> colour, ignore vehicles, sound alert, and for dropped items enchants (gear only), distance
 *       colour and chat alert; range;</li>
 *   <li><b>Blocks:</b> colour (custom or auto), opened containers and natural structures (containers only), height,
 *       ominous vaults (vaults only), sound alert, range.</li>
 * </ul>
 * They are real settings (so the page draws them like any card), saved inside the rule as JSON.
 */
public abstract class RuleSettings implements SpecificRules.Body {
   private final List<Setting<?>> settings = new ArrayList<>();
   /** The members these settings were made for; the group's settings are remade if they change. */
   String builtFor = "";
   public final Setting<Color> color;
   public final Setting<Float> range;

   RuleSettings(int defaultColor) {
      this.color = new ColorSetting("Color", new Color(defaultColor, true));
      this.color.setDescription("The colour this target is drawn in.");
      this.range = new NumberSetting<>("Range", 512.0F, 8.0F, 512.0F).withUnit("m");
      ((NumberSetting<Float>)this.range).setBoundaryLabel("Unlimited", SettingLabelCondition.MAX);
      this.range.setDescription("How far away this target is shown. Outside it, it's hidden even if a card below would show it.");
   }

   /** Settings for a group of this category; {@code targets} are its resolved blocks, entity types and items. */
   static RuleSettings of(SpecificRules.Category category, List<Object> targets, int defaultColor) {
      return switch (category) {
         case ALL -> new AllRule(defaultColor);
         case PLAYERS -> new PlayerRule(defaultColor);
         case MOBS -> new EntityRule(defaultColor, List.of());
         case ENTITIES -> new EntityRule(defaultColor, targets);
         case BLOCKS -> new BlockRule(defaultColor, targets);
      };
   }

   protected final <S extends Setting<?>> S add(S setting, String description) {
      // Prefixed so the page doesn't show the module's own description for a same-named setting.
      setting.setConfigName("Target" + setting.getName());
      setting.setDescription(description);
      this.settings.add(setting);
      return setting;
   }

   /** Adds colour and range in their usual places (colour first, range last). */
   protected final void addColor() {
      this.color.setConfigName("TargetColor");
      this.settings.add(this.color);
   }

   protected final void addRange() {
      this.range.setConfigName("TargetRange");
      this.settings.add(this.range);
   }

   /** The settings, in display order. */
   public List<Setting<?>> settings() {
      return Collections.unmodifiableList(this.settings);
   }

   public boolean unlimited() {
      return this.range.isAtMaximum();
   }

   public boolean inRange(double distanceSquared) {
      if (this.unlimited()) {
         return true;
      }

      double limit = this.range.getValue();
      return distanceSquared <= limit * limit;
   }

   /** The range as a key for grouping (the maximum means unlimited). */
   public float rangeKey() {
      return this.unlimited() ? 512.0F : this.range.getValue();
   }

   public String rangeLabel() {
      return this.unlimited() ? "any range" : Math.round(this.range.getValue()) + "m";
   }

   public int swatch() {
      return this.color.getValue().getRGB();
   }

   // ---- what each kind accepts (overridden where the kind has filters) ----------------------------------------------

   public boolean acceptsEntity(Entity entity, double distanceSquared) {
      return this.inRange(distanceSquared);
   }

   public Color entityColor(Entity entity, Vec3 from) {
      return this.color.getValue();
   }

   public boolean acceptsBlock(long packed, BlockTargetIndex.Entry entry) {
      return true;
   }

   public int blockColor(BlockTargetIndex.Entry entry, ColorsSchemeChoice scheme) {
      Color color = this.color.getValue();
      return scheme == null ? color.getRGB() : scheme.transform(color).getRGB();
   }

   public boolean sound() {
      return false;
   }

   public boolean chatAlert() {
      return false;
   }

   public boolean coords() {
      return false;
   }

   // ---- JSON (the rule's body) ----------------------------------------------------------------------------------------

   @Override
   public String read() {
      JsonObject object = new JsonObject();
      for (Setting<?> setting : this.settings) {
         object.add(setting.getConfigName(), setting.toJson());
      }

      return object.toString();
   }

   @Override
   public void write(String json) {
      JsonObject object;
      try {
         JsonElement parsed = JsonParser.parseString(json);
         if (!(parsed instanceof JsonObject parsedObject)) {
            return;
         }

         object = parsedObject;
      } catch (RuntimeException ignored) {
         return;
      }

      for (Setting<?> setting : this.settings) {
         JsonElement value = object.get(setting.getConfigName());
         if (value != null) {
            try {
               setting.fromJson(value);
            } catch (RuntimeException ignored) {
            }
         }
      }
   }

   // ---- kinds ---------------------------------------------------------------------------------------------------------

   public static final class AllRule extends RuleSettings {
      AllRule(int defaultColor) {
         super(defaultColor);
         this.addColor();
         this.addRange();
      }
   }

   public static final class PlayerRule extends RuleSettings {
      public final Setting<Boolean> ignoreNaked;

      PlayerRule(int defaultColor) {
         super(defaultColor);
         this.addColor();
         this.ignoreNaked = this.add(new BooleanSetting("IgnoreNaked", false), "Skip players wearing no armour.");
         this.addRange();
      }

      @Override
      public boolean acceptsEntity(Entity entity, double distanceSquared) {
         return this.inRange(distanceSquared)
            && !(this.ignoreNaked.getValue() && entity instanceof net.minecraft.world.entity.player.Player player && !dev.argus.client.utils.entity.PlayerStateUtils.hasArmor(player));
      }
   }

   /** Mobs, and other entities (dropped items get the item settings). */
   public static final class EntityRule extends RuleSettings {
      public final Setting<Boolean> ignoreVehicles;
      public final Setting<Boolean> sound;
      public final Setting<ItemRules.EnchantMode> enchants;
      public final Setting<Set<String>> requiredEnchants;
      public final Setting<Boolean> distanceColor;
      public final Setting<Color> farColor;
      public final Setting<Float> farDistance;
      public final Setting<Boolean> chatAlert;
      public final Setting<Boolean> coords;

      EntityRule(int defaultColor, List<Object> targets) {
         super(defaultColor);
         boolean items = targets.stream().anyMatch(target -> target instanceof Item);
         boolean gear = targets.stream().anyMatch(target -> target instanceof Item item && ItemRules.categoryOf(item.getDefaultInstance()) != ItemRules.Category.OTHER);
         boolean others = targets.isEmpty() || targets.stream().anyMatch(target -> !(target instanceof Item));
         this.enchants = this.add(
            new EnumSetting<>("Enchants", ItemRules.EnchantMode.IGNORE, ignored -> gear),
            "Ignore: any of this item. Any: only enchanted ones. Specific: only ones with every enchant listed below."
         );
         this.requiredEnchants = this.add(
            new EnchantSetSetting("RequiredEnchants", ignored -> gear && this.enchants.getValue() == ItemRules.EnchantMode.SPECIFIC),
            "Enchants the item must have when Enchants is Specific."
         );
         this.addColor();
         this.distanceColor = this.add(new BooleanSetting("DistanceColor", false, ignored -> items), "Fade dropped items from Color nearby to Far color further away.");
         this.farColor = this.add(new ColorSetting("FarColor", new Color(25, 255, 255), ignored -> items && this.distanceColor.getValue()), "The colour far away.");
         this.farDistance = this.add(
            new NumberSetting<>("FarDistance", 128.0F, 8.0F, 512.0F, ignored -> items && this.distanceColor.getValue()).withUnit("m"),
            "Where the colour is fully Far color."
         );
         this.chatAlert = this.add(new BooleanSetting("ChatAlert", false, ignored -> items), "Say in chat when a dropped one is found.");
         this.coords = this.add(new BooleanSetting("Coords", true, ignored -> items && this.chatAlert.getValue()), "Include where it is in the chat alert.");
         this.ignoreVehicles = this.add(new BooleanSetting("IgnoreVehicles", false, ignored -> others), "Skip ones something is riding.");
         this.sound = this.add(new BooleanSetting("Sound", false, ignored -> others), "Play an alert when one appears.");
         this.addRange();
      }

      @Override
      public boolean acceptsEntity(Entity entity, double distanceSquared) {
         if (!this.inRange(distanceSquared)) {
            return false;
         }

         if (!(entity instanceof ItemEntity item)) {
            return !(this.ignoreVehicles.getValue() && entity.isVehicle());
         }

         ItemStack stack = item.getItem();
         return ItemRules.passesEnchants(this.enchants.getValue(), ItemRules.categoryOf(stack), ItemRules.enchantIds(stack), this.requiredEnchants.getValue());
      }

      @Override
      public Color entityColor(Entity entity, Vec3 from) {
         if (!(entity instanceof ItemEntity) || !this.distanceColor.getValue()) {
            return this.color.getValue();
         }

         float fraction = (float)Mth.clamp(Math.sqrt(entity.position().distanceToSqr(from)) / this.farDistance.getValue(), 0.0, 1.0);
         return ColorUtils.blend(this.farColor.getValue(), this.color.getValue(), fraction);
      }

      @Override
      public boolean sound() {
         return this.sound.getValue();
      }

      @Override
      public boolean chatAlert() {
         return this.chatAlert.getValue();
      }

      @Override
      public boolean coords() {
         return this.coords.getValue();
      }
   }

   public static final class BlockRule extends RuleSettings {
      public final Setting<TargetSettings.ColorMode> colorMode;
      public final Setting<Boolean> markOpened;
      public final Setting<Color> openedColor;
      public final Setting<Boolean> hideOpened;
      public final Setting<Boolean> ignoreNatural;
      public final Setting<Boolean> dungeon;
      public final Setting<Boolean> fortress;
      public final Setting<Boolean> bastion;
      public final Setting<Boolean> ancientCity;
      public final Setting<Boolean> trialChambers;
      public final Setting<TargetSettings.HeightChoice> height;
      public final Setting<Boolean> onlyOminous;
      public final Setting<Boolean> sound;

      BlockRule(int defaultColor, List<Object> targets) {
         super(defaultColor);
         boolean container = targets.stream().anyMatch(target -> target instanceof Block block && block.defaultBlockState().hasBlockEntity());
         boolean vault = targets.contains(Blocks.VAULT);
         this.colorMode = this.add(
            new EnumSetting<>("ColorMode", TargetSettings.ColorMode.CUSTOM),
            "Custom: use Color. Auto: the block's own colour (or the one set in Colors)."
         );
         this.color.addVisibilityPredicate(ignored -> this.colorMode.getValue() == TargetSettings.ColorMode.CUSTOM);
         this.addColor();
         this.markOpened = this.add(new BooleanSetting("MarkOpened", false, ignored -> container), "Draw containers you've opened in Opened color.");
         this.openedColor = this.add(
            new ColorSetting("OpenedColor", new Color(217, 199, 255), ignored -> container && this.markOpened.getValue()), "The colour of opened containers."
         );
         this.hideOpened = this.add(new BooleanSetting("HideOpened", false, ignored -> container), "Hide containers you've opened.");
         this.ignoreNatural = this.add(new BooleanSetting("IgnoreNatural", false, ignored -> container), "Skip ones that generated in the structures picked below.");
         this.dungeon = this.add(new BooleanSetting("Dungeon", true, ignored -> container && this.ignoreNatural.getValue()), "Skip dungeon ones.");
         this.fortress = this.add(new BooleanSetting("Fortress", true, ignored -> container && this.ignoreNatural.getValue()), "Skip nether fortress ones.");
         this.bastion = this.add(new BooleanSetting("Bastion", true, ignored -> container && this.ignoreNatural.getValue()), "Skip bastion ones.");
         this.ancientCity = this.add(new BooleanSetting("AncientCity", false, ignored -> container && this.ignoreNatural.getValue()), "Skip ancient city ones.");
         this.trialChambers = this.add(
            new BooleanSetting("TrialChambers", true, ignored -> container && this.ignoreNatural.getValue()), "Skip trial chamber ones."
         );
         this.height = this.add(new EnumSetting<>("Height", TargetSettings.HeightChoice.NEW), "1.12: only show ones at Y 0 and above.");
         this.onlyOminous = this.add(new BooleanSetting("OnlyOminous", false, ignored -> vault), "Only show ominous vaults.");
         this.sound = this.add(new BooleanSetting("Sound", false), "Play an alert when a new one is found.");
         this.addRange();
      }

      private int ignoredStructures() {
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

      @Override
      public boolean acceptsBlock(long packed, BlockTargetIndex.Entry entry) {
         BlockState state = entry.state();
         if (this.height.getValue() == TargetSettings.HeightChoice.OLD && CellIndex.unpackY(packed) < 0) {
            return false;
         }

         if (this.onlyOminous.getValue() && state.is(Blocks.VAULT) && !state.getValueOrElse(VaultBlock.OMINOUS, false)) {
            return false;
         }

         if ((entry.structures() & this.ignoredStructures()) != 0) {
            return false;
         }

         return !(entry.opened() && this.hideOpened.getValue());
      }

      @Override
      public int blockColor(BlockTargetIndex.Entry entry, ColorsSchemeChoice scheme) {
         Color color;
         if (entry.opened() && this.markOpened.getValue()) {
            color = this.openedColor.getValue();
         } else if (this.colorMode.getValue() == TargetSettings.ColorMode.AUTO) {
            color = TargetMatcher.autoColor(entry.state());
         } else {
            color = this.color.getValue();
         }

         return scheme == null ? color.getRGB() : scheme.transform(color).getRGB();
      }

      @Override
      public boolean sound() {
         return this.sound.getValue();
      }
   }
}
