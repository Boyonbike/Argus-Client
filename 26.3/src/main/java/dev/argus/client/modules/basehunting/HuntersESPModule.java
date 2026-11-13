package dev.argus.client.modules.basehunting;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.MinecraftDisconnectEvent;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.RegistrySetSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.StringSetting;
import dev.argus.client.utils.world.WorldUtils;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Every sign of players that you can see from a distance, in one module with a colour per kind. Collectibles: framed
 * maps, banners, valuable gear in frames, on the ground and on armour stands. Pots: decorated pots holding things that
 * never generate in them. Mob gear: mobs wearing or holding player gear. Vanity: framed shulker boxes, filled glow
 * frames, endermen carrying blocks, XP orbs, dropped items and mobs crammed into one block. Merges Trouser Streak's
 * CollectibleESP, PotESP and MobGearESP with bephax's and the stash hunting add-on's VanityESP.
 */
public class HuntersESPModule extends Module {
   private static final Set<Item> NATURAL_POT_ITEMS = Set.of(HuntBlocks.items(
      "air", "string", "emerald", "emerald_block", "raw_iron_block", "iron_ingot", "trial_key", "diamond", "diamond_block", "music_disc_creator_music_box"
   ));
   // Gear: what counts as a player's item, used by the framed, ground, armour stand and mob checks.
   private final Setting<Set<Item>> gearSetting = this.add(new RegistrySetSetting<>("GearItems", BuiltInRegistries.ITEM, HuntBlocks.items(HuntItems.PLAYER_GEAR)));
   private final Setting<Boolean> needEnchantsSetting = this.add(new BooleanSetting("NeedEnchants", true));
   private final Setting<String> enchantListSetting = this.add(new StringSetting("FindEnchants", ""));
   // Collectibles.
   private final Setting<Boolean> collectiblesSetting = this.add(new BooleanSetting("Collectibles", true).markAsGroup());
   private final Setting<Boolean> mapsSetting = this.add(new BooleanSetting("FramedMaps", true).dependsOn(this.collectiblesSetting));
   private final Setting<Boolean> bannersSetting = this.add(new BooleanSetting("Banners", true).dependsOn(this.collectiblesSetting));
   private final Setting<Boolean> framedGearSetting = this.add(new BooleanSetting("FramedGear", true).dependsOn(this.collectiblesSetting));
   private final Setting<Boolean> groundGearSetting = this.add(new BooleanSetting("GroundGear", true).dependsOn(this.collectiblesSetting));
   private final Setting<Boolean> standsSetting = this.add(new BooleanSetting("ArmorStands", true).dependsOn(this.collectiblesSetting));
   private final Setting<Color> collectibleColorSetting = this.add(new ColorSetting("CollectibleColor", new Color(80, 255, 140, 230)).dependsOn(this.collectiblesSetting));
   // Pots.
   private final Setting<Boolean> potsSetting = this.add(new BooleanSetting("Pots", true).markAsGroup());
   private final Setting<Set<Item>> potIgnoreSetting = this.add(new RegistrySetSetting<>("PotIgnore", BuiltInRegistries.ITEM).dependsOn(this.potsSetting));
   private final Setting<Color> potColorSetting = this.add(new ColorSetting("PotColor", new Color(255, 160, 60, 230)).dependsOn(this.potsSetting));
   // Mob gear.
   private final Setting<Boolean> mobGearSetting = this.add(new BooleanSetting("MobGear", true).markAsGroup());
   private final Setting<Boolean> listAllSetting = this.add(new BooleanSetting("ListAllGear", true).dependsOn(this.mobGearSetting));
   private final Setting<Integer> hideWithinSetting = this.add(new NumberSetting<>("HideWithin", 0, 0, 64).withUnit("m").dependsOn(this.mobGearSetting));
   private final Setting<Color> mobGearColorSetting = this.add(new ColorSetting("MobGearColor", new Color(255, 80, 200, 230)).dependsOn(this.mobGearSetting));
   // Vanity.
   private final Setting<Boolean> vanitySetting = this.add(new BooleanSetting("Vanity", true).markAsGroup());
   private final Setting<Boolean> shulkerFramesSetting = this.add(new BooleanSetting("ShulkerFrames", true).dependsOn(this.vanitySetting));
   private final Setting<Boolean> glowFramesSetting = this.add(new BooleanSetting("GlowFrames", true).dependsOn(this.vanitySetting));
   private final Setting<Boolean> endermenSetting = this.add(new BooleanSetting("Endermen", true).dependsOn(this.vanitySetting));
   private final Setting<Boolean> orbsSetting = this.add(new BooleanSetting("XPOrbs", true).dependsOn(this.vanitySetting));
   private final Setting<Boolean> dropsSetting = this.add(new BooleanSetting("DroppedItems", false).dependsOn(this.vanitySetting));
   private final Setting<Set<Item>> dropFilterSetting = this.add(new RegistrySetSetting<>("DropFilter", BuiltInRegistries.ITEM).dependsOn(this.vanitySetting));
   private final Setting<Integer> minDropSetting = this.add(new NumberSetting<>("MinDropCount", 1, 1, 64).dependsOn(this.vanitySetting));
   private final Setting<Boolean> stackedSetting = this.add(new BooleanSetting("Stacked", true).dependsOn(this.vanitySetting));
   private final Setting<Integer> stackSizeSetting = this.add(new NumberSetting<>("StackSize", 6, 3, 50).dependsOn(this.vanitySetting));
   private final Setting<Color> vanityColorSetting = this.add(new ColorSetting("VanityColor", new Color(120, 160, 255, 220)).dependsOn(this.vanitySetting));
   private final HuntMarkers markers = new HuntMarkers(this, new Color(255, 255, 255, 40), new Color(255, 255, 255, 220), false).withoutColours();
   private final List<Entity> mobGearEntities = new ArrayList<>();
   private final List<Entity> vanityEntities = new ArrayList<>();
   private int ticks;

   public HuntersESPModule() {
      super("HuntersESP", "Highlights every visible sign of players: collectibles, pots, mob gear and vanity, each in its own colour.", ModuleCategory.BASE_HUNTING);
      this.dropFilterSetting.addVisibilityPredicate(value -> this.dropsSetting.getValue());
      this.minDropSetting.addVisibilityPredicate(value -> this.dropsSetting.getValue());
      this.stackSizeSetting.addVisibilityPredicate(value -> this.stackedSetting.getValue());
   }

   @Override
   public void onDisable() {
      this.markers.clear();
      this.mobGearEntities.clear();
      this.vanityEntities.clear();
   }

   @Override
   public String getInfo() {
      int total = this.markers.size() + this.mobGearEntities.size() + this.vanityEntities.size();
      return total == 0 ? null : String.valueOf(total);
   }

   @EventHandler
   private void onDisconnect(MinecraftDisconnectEvent event) {
      this.onDisable();
   }

   private boolean isGear(ItemStack stack) {
      return HuntItems.isPlayerItem(stack, this.gearSetting.getValue(), this.needEnchantsSetting.getValue())
         && HuntItems.hasAnyEnchant(stack, this.enchantListSetting.getValue());
   }

   @EventHandler
   private void onTick(LocalPlayerTickEvent event) {
      if (this.isWorldUnavailable() || ++this.ticks < 10) {
         return;
      }

      this.ticks = 0;
      this.mobGearEntities.clear();
      this.vanityEntities.clear();
      this.markers.pruneFarFrom(ClientAccess.argusclientMinecraft.player.position());
      Map<String, List<Entity>> crowds = new HashMap<>();
      for (Entity entity : ClientAccess.argusclientMinecraft.level.entitiesForRendering()) {
         this.checkEntity(entity, crowds);
      }

      this.checkCrowds(crowds);
      this.checkBlockEntities();
   }

   private void checkEntity(Entity entity, Map<String, List<Entity>> crowds) {
      Color collectible = this.collectibleColorSetting.getValue();
      Color vanity = this.vanityColorSetting.getValue();
      boolean collectibles = this.collectiblesSetting.getValue();
      boolean vanityOn = this.vanitySetting.getValue();
      if (entity instanceof ItemFrame frame) {
         ItemStack stack = frame.getItem();
         String what = null;
         Color color = null;
         if (collectibles && this.mapsSetting.getValue() && stack.is(Items.FILLED_MAP)) {
            what = "a framed map";
            color = collectible;
         } else if (collectibles && this.framedGearSetting.getValue() && this.isGear(stack)) {
            what = "a framed " + stack.getHoverName().getString();
            color = collectible;
         } else if (vanityOn && this.shulkerFramesSetting.getValue() && stack.getItem() instanceof BlockItem block && block.getBlock() instanceof ShulkerBoxBlock) {
            what = "a framed shulker box";
            color = vanity;
         } else if (vanityOn && this.glowFramesSetting.getValue() && frame instanceof GlowItemFrame && !stack.isEmpty()) {
            // Glow item frames never generate, so a filled one is decoration someone put up.
            what = "a glow frame holding " + stack.getHoverName().getString();
            color = vanity;
         }

         if (what != null) {
            this.markers.report(frame.blockPosition(), frame.getBoundingBox(), what, color);
         } else {
            this.markers.remove(frame.blockPosition());
         }

         return;
      }

      if (entity instanceof ItemEntity item) {
         ItemStack stack = item.getItem();
         if (collectibles && this.groundGearSetting.getValue() && this.isGear(stack)) {
            this.markers.report(item.blockPosition(), new AABB(item.blockPosition()), stack.getCount() + " dropped " + stack.getHoverName().getString(), collectible);
         } else if (vanityOn && this.dropsSetting.getValue() && this.wantedDrop(stack)) {
            this.vanityEntities.add(entity);
         }

         return;
      }

      if (entity instanceof ExperienceOrb) {
         if (vanityOn && this.orbsSetting.getValue()) {
            this.vanityEntities.add(entity);
         }

         return;
      }

      if (entity instanceof ArmorStand stand) {
         if (collectibles && this.standsSetting.getValue()) {
            List<ItemStack> gear = HuntItems.allPlayerGear(stand, this.gearSetting.getValue(), this.needEnchantsSetting.getValue(), this.enchantListSetting.getValue());
            if (!gear.isEmpty()) {
               this.markers.report(stand.blockPosition(), stand.getBoundingBox(), "an armour stand with " + gear.get(0).getHoverName().getString(), collectible);
            }
         }

         return;
      }

      if (entity instanceof Player) {
         return;
      }

      if (vanityOn && this.endermenSetting.getValue()) {
         BlockState carried = HuntCompat.carriedBlock(entity);
         if (carried != null) {
            this.vanityEntities.add(entity);
            this.markers.alertOnce(entity.getUUID(), entity.blockPosition(), "an enderman carrying " + carried.getBlock().getName().getString());
         }
      }

      if (this.mobGearSetting.getValue() && entity instanceof LivingEntity living) {
         this.checkMobGear(living);
      }

      if (vanityOn && this.stackedSetting.getValue()) {
         crowds.computeIfAbsent(EntityType.getKey(entity.getType()) + "@" + entity.blockPosition().asLong(), unused -> new ArrayList<>()).add(entity);
      }
   }

   private void checkMobGear(LivingEntity living) {
      List<ItemStack> gear = HuntItems.allPlayerGear(living, this.gearSetting.getValue(), this.needEnchantsSetting.getValue(), this.enchantListSetting.getValue());
      if (gear.isEmpty()) {
         return;
      }

      int hide = this.hideWithinSetting.getValue();
      if (hide <= 0 || living.distanceTo(ClientAccess.argusclientMinecraft.player) > hide) {
         this.mobGearEntities.add(living);
      }

      List<String> names = new ArrayList<>();
      for (ItemStack stack : this.listAllSetting.getValue() ? gear : gear.subList(0, 1)) {
         names.add(stack.getHoverName().getString() + (stack.isEnchanted() ? " (enchanted)" : ""));
      }

      this.markers.alertOnce(living.getUUID(), living.blockPosition(), "a " + living.getType().getDescription().getString() + " with " + String.join(", ", names));
   }

   private void checkCrowds(Map<String, List<Entity>> crowds) {
      int stackSize = this.stackSizeSetting.getValue();
      for (List<Entity> crowd : crowds.values()) {
         if (crowd.size() >= stackSize) {
            Entity first = crowd.get(0);
            this.vanityEntities.add(first);
            this.markers.alertOnce(
               "stack" + first.blockPosition().asLong(), first.blockPosition(), crowd.size() + " " + first.getType().getDescription().getString() + " in one block"
            );
         }
      }
   }

   private void checkBlockEntities() {
      boolean banners = this.collectiblesSetting.getValue() && this.bannersSetting.getValue();
      boolean pots = this.potsSetting.getValue();
      if (!banners && !pots) {
         return;
      }

      for (BlockEntity blockEntity : WorldUtils.getLoadedBlockEntities()) {
         BlockPos position = blockEntity.getBlockPos();
         if (banners && blockEntity instanceof BannerBlockEntity) {
            this.markers.report(position, new AABB(position), "a banner", this.collectibleColorSetting.getValue());
         } else if (pots && blockEntity instanceof DecoratedPotBlockEntity pot) {
            ItemStack stack = pot.getTheItem();
            if (!stack.isEmpty() && !NATURAL_POT_ITEMS.contains(stack.getItem()) && !this.potIgnoreSetting.getValue().contains(stack.getItem())) {
               this.markers.report(position, new AABB(position), "a pot holding " + stack.getCount() + " " + stack.getHoverName().getString(), this.potColorSetting.getValue());
            }
         }
      }
   }

   private boolean wantedDrop(ItemStack stack) {
      Set<Item> filter = this.dropFilterSetting.getValue();
      return stack.getCount() >= this.minDropSetting.getValue() && (filter.isEmpty() || filter.contains(stack.getItem()));
   }

   @EventHandler
   private void onWorldRender(WorldRenderEvent.Main event) {
      this.markers.render(event);
      this.markers.renderBoxes(event, boxes(this.mobGearEntities, event.getPartialTick()), this.mobGearColorSetting.getValue());
      this.markers.renderBoxes(event, boxes(this.vanityEntities, event.getPartialTick()), this.vanityColorSetting.getValue());
   }

   /** Where each entity is this frame, so boxes glide with them instead of jumping every half second. */
   private static List<AABB> boxes(List<Entity> entities, float partialTick) {
      List<AABB> boxes = new ArrayList<>(entities.size());
      for (Entity entity : entities) {
         if (!entity.isRemoved()) {
            boxes.add(HuntEntities.interpolatedBox(entity, partialTick));
         }
      }

      return boxes;
   }
}
