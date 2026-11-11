package dev.argus.client.modules.render.targets;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.audio.SoundClip;
import dev.argus.client.managers.BlockColorRegistry;
import dev.argus.client.managers.SocialRegistry;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.settings.options.ColorsSchemeChoice;
import dev.argus.client.settings.options.ListFilterMode;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.chat.ChatUtils;
import dev.argus.client.utils.entity.EntityUtils;
import dev.argus.client.utils.entity.PlayerStateUtils;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.awt.Color;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.MapColor.Brightness;
import net.minecraft.world.phys.Vec3;

/** Which entities and blocks a module's target cards accept, and in what colour. Shared by ESP and Tracers. */
public final class TargetMatcher implements ClientAccess {
   private static final ElapsedTimer SOUND_TIMER = new ElapsedTimer();
   private static final IntOpenHashSet ANNOUNCED_ITEMS = new IntOpenHashSet();
   private static ClientLevel announcedLevel;

   private TargetMatcher() {
   }

   /** The card an entity falls under for these settings (in range and accepted), or null. */
   public static TargetSettings.Kind kindOf(TargetSettings targets, Entity entity, Vec3 camera) {
      return entity == null ? null : kindOf(targets, entity, camera, targets.specific.forEntity(entity));
   }

   /** {@link #kindOf(TargetSettings, Entity, Vec3)} with the entity's specific target already looked up. */
   public static TargetSettings.Kind kindOf(TargetSettings targets, Entity entity, Vec3 camera, SpecificRules.Rule rule) {
      if (entity == null || entity == argusclientMinecraft.player || entity == argusclientMinecraft.getCameraEntity() || entity.isRemoved()) {
         return null;
      }

      double distanceSquared = entity.position().distanceToSqr(camera);
      if (rule != null) {
         // A specific target overrides the cards: shown inside its own range, hidden outside it.
         return targets.specific.settings(rule).acceptsEntity(entity, distanceSquared) ? naturalKind(entity) : null;
      }

      if (entity instanceof Player player) {
         TargetSettings.Players card = targets.players;
         return card.active() && card.inRange(distanceSquared) && acceptsPlayer(card, player) ? TargetSettings.Kind.PLAYERS : null;
      }

      if (entity instanceof ItemEntity item) {
         TargetSettings.Items card = targets.items;
         return card.active() && card.inRange(distanceSquared) && acceptsItem(card, item.getItem()) ? TargetSettings.Kind.ITEMS : null;
      }

      TargetSettings.Mobs card = targets.mobs;
      return card.active() && card.inRange(distanceSquared) && acceptsMob(card, entity) ? TargetSettings.Kind.MOBS : null;
   }

   /** The card an entity would fall under by what it is. */
   private static TargetSettings.Kind naturalKind(Entity entity) {
      if (entity instanceof Player) {
         return TargetSettings.Kind.PLAYERS;
      }

      return entity instanceof ItemEntity ? TargetSettings.Kind.ITEMS : TargetSettings.Kind.MOBS;
   }

   public static boolean acceptsPlayer(TargetSettings.Players card, Player player) {
      SocialRegistry social = ClientContext.socialRegistry;
      TargetSettings.Visibility visibility;
      if (social.isFriendPlayer(player)) {
         visibility = card.friends.getValue();
      } else if (social.isEnemyPlayer(player)) {
         visibility = card.enemies.getValue();
      } else {
         visibility = card.others.getValue();
      }

      if (visibility == TargetSettings.Visibility.HIDE) {
         return false;
      }

      return !card.ignoreNaked.getValue() || PlayerStateUtils.hasArmor(player);
   }

   public static boolean acceptsItem(TargetSettings.Items card, ItemStack stack) {
      if (!ListFilterMode.matches(card.filter.getValue(), stack.getItem(), card.list.getValue())) {
         return false;
      }

      ItemRules.Category category = ItemRules.categoryOf(stack);
      var required = switch (category) {
         case TOOL -> card.toolEnchants.getValue();
         case SWORD -> card.swordEnchants.getValue();
         case ARMOR -> card.armorEnchants.getValue();
         case MACE -> card.maceEnchants.getValue();
         case TRIDENT -> card.tridentEnchants.getValue();
         case OTHER -> java.util.Set.<String>of();
      };
      return ItemRules.passesEnchants(card.enchants.getValue(), category, ItemRules.enchantIds(stack), required);
   }

   private static boolean listed(TargetSettings.Mobs card, Entity entity) {
      return card.list.getValue().contains(entity.getType());
   }

   public static boolean acceptsMob(TargetSettings.Mobs card, Entity entity) {
      if (card.ignoreVehicles.getValue() && entity.isVehicle()) {
         return false;
      }

      ListFilterMode filter = card.filter.getValue();
      if (filter == ListFilterMode.WHITELIST) {
         // A whitelist names exactly what to show, mobs or not, whatever the category toggles say.
         return listed(card, entity);
      }

      if (filter == ListFilterMode.BLACKLIST && listed(card, entity)) {
         return false;
      }

      if (!(entity instanceof LivingEntity) || entity instanceof ArmorStand) {
         return false;
      }

      if (EntityUtils.isHostile(entity)) {
         return card.hostile.getValue();
      }

      return EntityUtils.isNeutral(entity) ? card.neutral.getValue() : card.passive.getValue();
   }

   /** The colour for an accepted entity (before any module-specific alpha). */
   public static Color entityColor(TargetSettings targets, TargetSettings.Kind kind, Entity entity, Vec3 from) {
      return entityColor(targets, kind, entity, from, targets.specific.forEntity(entity));
   }

   /** {@link #entityColor(TargetSettings, TargetSettings.Kind, Entity, Vec3)} with the specific target already looked up. */
   public static Color entityColor(TargetSettings targets, TargetSettings.Kind kind, Entity entity, Vec3 from, SpecificRules.Rule rule) {
      if (rule != null) {
         return targets.specific.settings(rule).entityColor(entity, from);
      }

      switch (kind) {
         case PLAYERS: {
            TargetSettings.Players card = targets.players;
            SocialRegistry social = ClientContext.socialRegistry;
            if (entity instanceof Player player && social.isFriendPlayer(player)) {
               return card.socialColors.getValue() ? social.getFriendColor() : card.friendColor.getValue();
            }

            if (entity instanceof Player player && social.isEnemyPlayer(player)) {
               return card.socialColors.getValue() ? social.getEnemyColor() : card.enemyColor.getValue();
            }

            return card.color.getValue();
         }
         case ITEMS: {
            TargetSettings.Items card = targets.items;
            if (!card.distanceColor.getValue()) {
               return card.color.getValue();
            }

            float fraction = (float)Mth.clamp(Math.sqrt(entity.position().distanceToSqr(from)) / card.farDistance.getValue(), 0.0, 1.0);
            return ColorUtils.blend(card.farColor.getValue(), card.color.getValue(), fraction);
         }
         default: {
            TargetSettings.Mobs card = targets.mobs;
            if (EntityUtils.isHostile(entity)) {
               return card.hostileColor.getValue();
            }

            return EntityUtils.isNeutral(entity) ? card.neutralColor.getValue() : card.passiveColor.getValue();
         }
      }
   }

   // ---- blocks --------------------------------------------------------------------------------------------------------

   /** Whether a found block passes this card's own filters (list, height, ominous vaults, natural chests, opened). */
   public static boolean acceptsBlock(TargetSettings.Blocks card, int ignoredStructures, long packed, BlockTargetIndex.Entry entry) {
      BlockState state = entry.state();
      if (!card.list.getValue().contains(state.getBlock())) {
         return false;
      }

      if (card.height.getValue() == TargetSettings.HeightChoice.OLD && CellIndex.unpackY(packed) < 0) {
         return false;
      }

      if (card.onlyOminous.getValue() && state.is(Blocks.VAULT) && !state.getValueOrElse(VaultBlock.OMINOUS, false)) {
         return false;
      }

      if ((entry.structures() & ignoredStructures) != 0) {
         return false;
      }

      return !(entry.opened() && card.hideOpened.getValue());
   }

   /** The colour for an accepted block, after the Colors module's scheme. */
   public static int blockColor(TargetSettings.Blocks card, ColorsSchemeChoice scheme, BlockTargetIndex.Entry entry) {
      Color color;
      if (entry.opened() && card.markOpened.getValue()) {
         color = card.openedColor.getValue();
      } else if (card.colorMode.getValue() == TargetSettings.ColorMode.CUSTOM) {
         color = card.color.getValue();
      } else {
         color = autoColor(entry.state());
      }

      return scheme == null ? color.getRGB() : scheme.transform(color).getRGB();
   }

   /**
    * Whether a newly found block should play the alert: a specific target's own Sound setting decides for its block,
    * otherwise the Blocks card's.
    */
   public static boolean alertsForBlock(TargetSettings targets, int ignoredStructures, long packed, BlockTargetIndex.Entry entry, Vec3 eye) {
      double distanceSquared = eye.distanceToSqr(CellIndex.unpackX(packed) + 0.5, CellIndex.unpackY(packed) + 0.5, CellIndex.unpackZ(packed) + 0.5);
      SpecificRules.Rule rule = targets.specific.forBlock(entry.state().getBlock());
      if (rule != null) {
         RuleSettings settings = targets.specific.settings(rule);
         return settings.sound() && settings.acceptsBlock(packed, entry) && settings.inRange(distanceSquared);
      }

      TargetSettings.Blocks card = targets.blocks;
      return card.active() && card.sound.getValue() && acceptsBlock(card, ignoredStructures, packed, entry) && card.inRange(distanceSquared);
   }

   /** Whether any block find could alert, so callers can skip walking new finds. */
   public static boolean blockAlertsOn(TargetSettings targets) {
      return targets.blocks.active() && targets.blocks.sound.getValue() || targets.specific.hasBlockSound();
   }

   /** Whether a newly spawned entity should play the alert (a specific target's Sound, otherwise the Mobs card's). */
   public static boolean alertsForSpawn(TargetSettings targets, Entity entity, Vec3 eye) {
      TargetSettings.Kind kind = kindOf(targets, entity, eye);
      if (kind == null) {
         return false;
      }

      SpecificRules.Rule rule = targets.specific.forEntity(entity);
      if (rule != null) {
         return targets.specific.settings(rule).sound();
      }

      return kind == TargetSettings.Kind.MOBS && targets.mobs.sound.getValue();
   }

   /** Announces a shown dropped item in chat if its specific target, or else the Items card, asks for it. */
   public static void alertItem(TargetSettings targets, ItemEntity item) {
      SpecificRules.Rule rule = targets.specific.forEntity(item);
      if (rule != null) {
         RuleSettings settings = targets.specific.settings(rule);
         if (settings.chatAlert()) {
            announceItem(settings.coords(), item);
         }
      } else if (targets.items.chatAlert.getValue()) {
         announceItem(targets.items.coords.getValue(), item);
      }
   }

   /** The block colour registry's colour if set, otherwise a colour that reads like the block. */
   public static Color autoColor(BlockState state) {
      Block block = state.getBlock();
      BlockColorRegistry registry = ClientContext.blockColorRegistry;
      Color custom = registry == null ? null : registry.getColor(block);
      if (custom != null) {
         return custom;
      }

      if (block == Blocks.NETHER_PORTAL) {
         return new Color(128, 0, 255);
      }

      if (block == Blocks.ENDER_CHEST) {
         return new Color(120, 0, 255);
      }

      if (block == Blocks.SHULKER_BOX) {
         return new Color(163, 64, 163);
      }

      if (block == Blocks.TRAPPED_CHEST) {
         return new Color(255, 40, 40);
      }

      if (block == Blocks.CHEST || block == Blocks.BARREL) {
         return new Color(255, 160, 0);
      }

      MapColor mapColor = block instanceof BedBlock bed ? bed.getColor().getMapColor() : state.getMapColor(argusclientMinecraft.level, BlockPos.ZERO);
      if (block instanceof ShulkerBoxBlock box && box.getColor() != null) {
         mapColor = box.getColor().getMapColor();
      }

      if (mapColor == MapColor.NONE) {
         return Color.WHITE;
      }

      return new Color(mapColor.calculateARGBColor(Brightness.HIGH), false).brighter();
   }

   // ---- alerts --------------------------------------------------------------------------------------------------------

   /** Plays the alert sound, at most ten times a second across every card. */
   public static void playAlert() {
      if (SOUND_TIMER.hasElapsedAndReset(100L)) {
         SoundClip sound = ClientContext.soundManager.getSound(dev.argus.client.audio.BuiltinSound.WARNING.getIdentifier());
         if (sound != null) {
            sound.play(1.0F);
         }
      }
   }

   /** Announces a matching dropped item in chat once (shared, so ESP and Tracers don't both announce it). */
   public static void announceItem(boolean coords, ItemEntity item) {
      if (argusclientMinecraft.level != announcedLevel) {
         ANNOUNCED_ITEMS.clear();
         announcedLevel = argusclientMinecraft.level;
      }

      if (!ANNOUNCED_ITEMS.add(item.getId())) {
         return;
      }

      ItemStack stack = item.getItem();
      String text = stack.getHoverName().getString() + (stack.getCount() > 1 ? " x" + stack.getCount() : "") + " found";
      if (coords) {
         text += " at " + item.getBlockX() + ", " + item.getBlockY() + ", " + item.getBlockZ();
      }

      ChatUtils.printChat(Component.literal(text), ChatUtils.objectSignature(item));
   }

   /** Forgets announced items that are gone, so the set doesn't grow forever. */
   public static void pruneAnnounced() {
      ClientLevel level = argusclientMinecraft.level;
      if (level == null) {
         ANNOUNCED_ITEMS.clear();
         return;
      }

      ANNOUNCED_ITEMS.removeIf(id -> level.getEntity(id) == null);
   }
}
