package dev.argus.client.modules.player;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.EventStage;
import dev.argus.client.events.LocalPlayerSendPositionEvent;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.RegistrySetSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.ItemSwapMode;
import dev.argus.client.settings.options.ListFilterMode;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.chat.ChatUtils;
import dev.argus.client.utils.inventory.InventoryUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Uses named name tags on nearby entities (Meteor's AutoNametag, reworked for Argus).
 *
 * <p>Finds a named tag in the offhand or hotbar, picks targets by priority, rotates first when
 * asked (the interaction is sent after the rotated movement packet), and sends exactly one
 * interaction per entity. Entities just tagged are skipped briefly so a tag is not wasted
 * while the server's name update is still on its way.
 */
public class AutoNameTagModule extends Module {
   private static final long RETAG_COOLDOWN_MS = 1500L;

   public final Setting<Set<EntityType<?>>> entitiesSetting;
   private final Setting<ListFilterMode> modeSetting;
   private final Setting<Float> rangeSetting;
   private final Setting<PriorityChoice> prioritySetting;
   private final Setting<Boolean> renameSetting;
   private final Setting<Integer> frequencySetting;
   private final Setting<Float> delaySetting;
   private final Setting<Boolean> rotateSetting;
   private final Setting<ItemSwapMode> swapSetting;
   private final Setting<Boolean> autoDisableSetting;
   private final ElapsedTimer nameTagTimer = new ElapsedTimer();
   private final Map<Integer, Long> recentlyTagged = new HashMap<>();
   private Entity pendingTarget;

   public AutoNameTagModule() {
      super("AutoNameTag", "Uses named name tags on nearby entities.", ModuleCategory.PLAYER);
      this.entitiesSetting = this.add(new RegistrySetSetting<>("Entities", BuiltInRegistries.ENTITY_TYPE, new EntityType[]{EntityType.WITHER}));
      this.modeSetting = this.add(new EnumSetting<>("Mode", ListFilterMode.WHITELIST));
      this.rangeSetting = this.add(new NumberSetting<>("Range", 4.5F, 1.0F, 6.0F).withUnit("m"));
      this.prioritySetting = this.add(new EnumSetting<>("Priority", PriorityChoice.CLOSEST));
      this.renameSetting = this.add(new BooleanSetting("Renametag", false));
      this.frequencySetting = this.add(new NumberSetting<>("Frequency", 1, 1, 8));
      this.delaySetting = this.add(new NumberSetting<>("Delay", 0.1F, 0.1F, 5.0F).withUnit("s"));
      this.rotateSetting = this.add(new BooleanSetting("Rotate", true));
      this.swapSetting = this.add(new EnumSetting<>("Swap", ItemSwapMode.SILENT));
      this.autoDisableSetting = this.add(new BooleanSetting("Auto disable", false));
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.pendingTarget = null;
      this.recentlyTagged.clear();
   }

   @Override
   public String getInfo() {
      if (ClientAccess.argusclientMinecraft.player == null) {
         return null;
      }
      return String.valueOf(InventoryUtils.countItems(AutoNameTagModule::isNamedNameTag));
   }

   private static boolean isNamedNameTag(ItemStack stack) {
      return stack.is(Items.NAME_TAG) && stack.has(DataComponents.CUSTOM_NAME);
   }

   @EventHandler
   private void onLocalPlayerSendPositionEvent(LocalPlayerSendPositionEvent event) {
      if (this.isWorldUnavailable()) {
         this.pendingTarget = null;
         return;
      }

      if (event.getStage() == EventStage.Post) {
         // Rotated last tick: the server now has our facing, so use the tag.
         Entity target = this.pendingTarget;
         this.pendingTarget = null;
         if (target != null && target.isAlive()) {
            this.useTagOn(List.of(target));
         }
         return;
      }

      if (!this.nameTagTimer.hasElapsed(this.delaySetting.getValue().floatValue(), TimeUnit.SECONDS)) {
         return;
      }
      this.nameTagTimer.reset();

      InventoryUtils.SwapSlot tag = this.findTag();
      if (!tag.isFound()) {
         if (this.autoDisableSetting.getValue()) {
            ChatUtils.printChat(Component.literal("No named name tags " + (this.swapSetting.getValue() == ItemSwapMode.NONE ? "in hand" : "in your hotbar") + ", disabling."), ChatUtils.moduleSignature(this));
            this.disable();
         }
         return;
      }

      String tagName = tag.getStack().getHoverName().getString();
      List<Entity> targets = this.findTargets(tagName);
      if (targets.isEmpty()) {
         return;
      }

      if (this.rotateSetting.getValue()) {
         // One target per rotation; the interaction goes out in the Post stage.
         Entity target = targets.get(0);
         event.setRotation(RotationManager.getRotationsToEntity(target));
         this.pendingTarget = target;
         return;
      }

      this.useTagOn(targets.subList(0, Math.min(targets.size(), this.frequencySetting.getValue())));
   }

   private InventoryUtils.SwapSlot findTag() {
      InventoryUtils.SwapSlot slot = InventoryUtils.findSwapSlot(AutoNameTagModule::isNamedNameTag, false);
      if (this.swapSetting.getValue() == ItemSwapMode.NONE && slot.isFound() && !slot.isInHand()) {
         return new InventoryUtils.SwapSlot(-1, true, InteractionHand.MAIN_HAND);
      }
      return slot;
   }

   private List<Entity> findTargets(String tagName) {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      Vec3 eyes = player.getEyePosition();
      double rangeSq = this.rangeSetting.getValue() * this.rangeSetting.getValue();
      long now = System.currentTimeMillis();
      this.recentlyTagged.values().removeIf(time -> now - time > RETAG_COOLDOWN_MS);

      List<Entity> targets = new ArrayList<>();
      for (Entity entity : ClientAccess.argusclientMinecraft.level.entitiesForRendering()) {
         if (entity instanceof Player || !entity.isAlive() || this.recentlyTagged.containsKey(entity.getId())) {
            continue;
         }
         if (!ListFilterMode.matches(this.modeSetting.getValue(), entity.getType(), this.entitiesSetting)) {
            continue;
         }
         if (entity.hasCustomName()) {
            if (!this.renameSetting.getValue() || tagName.equals(entity.getCustomName().getString())) {
               continue;
            }
         }
         if (entity.getBoundingBox().distanceToSqr(eyes) > rangeSq) {
            continue;
         }
         targets.add(entity);
      }

      targets.sort(this.prioritySetting.getValue().comparator(eyes));
      return targets;
   }

   private void useTagOn(List<Entity> targets) {
      InventoryUtils.SwapSlot tag = this.findTag();
      if (!tag.isFound()) {
         return;
      }

      int previousSlot = InventoryUtils.getSelectedSlot();
      InventoryUtils.swapTo(tag);
      try {
         for (Entity target : targets) {
            if (!isNamedNameTag(ClientAccess.argusclientMinecraft.player.getItemInHand(tag.hand()))) {
               break;
            }
            interact(target, tag.hand());
            this.recentlyTagged.put(target.getId(), System.currentTimeMillis());
         }
      } finally {
         if (this.swapSetting.getValue() == ItemSwapMode.SILENT) {
            InventoryUtils.swapBack(previousSlot, tag);
         }
      }
   }

   /** Sends a single interaction: a second one would use up another name tag. */
   private static void interact(Entity entity, InteractionHand hand) {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      ClientAccess.argusclientMinecraft.gameMode.interact(player, entity, hand);
      player.swing(hand);
   }

   private enum PriorityChoice implements NamedEntry {
      CLOSEST("Closest"),
      FARTHEST("Farthest"),
      LOWEST_HEALTH("Lowest health"),
      HIGHEST_HEALTH("Highest health");

      private final String displayName;

      PriorityChoice(String displayName) {
         this.displayName = displayName;
      }

      Comparator<Entity> comparator(Vec3 eyes) {
         Comparator<Entity> byDistance = Comparator.comparingDouble(entity -> entity.getBoundingBox().distanceToSqr(eyes));
         Comparator<Entity> byHealth = Comparator.comparingDouble(entity -> entity instanceof LivingEntity living ? living.getHealth() : Float.MAX_VALUE);
         return switch (this) {
            case CLOSEST -> byDistance;
            case FARTHEST -> byDistance.reversed();
            case LOWEST_HEALTH -> byHealth.thenComparing(byDistance);
            case HIGHEST_HEALTH -> byHealth.reversed().thenComparing(byDistance);
         };
      }

      @Override
      public String getName() {
         return this.displayName;
      }
   }
}
