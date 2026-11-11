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
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.RegistrySetSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.ItemSwapMode;
import dev.argus.client.settings.options.ListFilterMode;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.world.BlockInteractionUtils;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class AutoNameTagModule extends Module {
   public final Setting<Set<EntityType<?>>> entitiesSetting;
   private final Setting<ListFilterMode> modeSetting;
   private final Setting<Float> rangeSetting;
   private final Setting<Integer> frequencySetting;
   private final Setting<Float> delaySetting;
   private final Setting<Boolean> rotateSetting;
   private final Setting<ItemSwapMode> swapSetting;
   private final ElapsedTimer nameTagTimer;

   private boolean isNamedNameTag(ItemStack stack) {
      Item nameTagItem = Items.NAME_TAG;
      if (!stack.is(nameTagItem)) {
         return false;
      }

      DataComponentType<?> customNameType = DataComponents.CUSTOM_NAME;
      return stack.has(customNameType);
   }

   @EventHandler
   private void onLocalPlayerSendPositionEvent(LocalPlayerSendPositionEvent event) {
      if (event.getStage() != EventStage.Post) {
         ElapsedTimer timerSnapshot = this.nameTagTimer;
         double delaySecondsSnapshot = this.delaySetting.getValue().floatValue();
         TimeUnit delayUnit = TimeUnit.SECONDS;
         if (timerSnapshot.hasElapsed(delaySecondsSnapshot, delayUnit)) {
            Predicate<ItemStack> nameTagPredicate = this::isNamedNameTag;
            int nameTagSlot = InventoryUtils.findHotbarSlot(nameTagPredicate);
            int previousSlot = ClientAccess.argusclientMinecraft.player.getInventory().getSelectedSlot();
            int taggedCount = 0;
            if (this.swapSetting.getValue() != ItemSwapMode.NONE) {
               InventoryUtils.selectHotbarSlotForPlacement(nameTagSlot);
            }

            ClientLevel level = ClientAccess.argusclientMinecraft.level;

            for (Entity entity : level.entitiesForRendering()) {
               ItemStack heldStack = ClientAccess.argusclientMinecraft.player.getMainHandItem();
               if (!this.isNamedNameTag(heldStack)) {
                  break;
               }

               ListFilterMode selectedMode = this.modeSetting.getValue();
               EntityType selectedEntityType = entity.getType();
               Setting<Set<EntityType<?>>> entitiesSettingSnapshot = this.entitiesSetting;
               if (ListFilterMode.matches(selectedMode, selectedEntityType, entitiesSettingSnapshot) && !(entity instanceof Player)) {
                  String heldNameText = ClientAccess.argusclientMinecraft.player.getMainHandItem().getHoverName().getString();
                  String selectedEntityText;
                  if (entity.hasCustomName()) {
                     selectedEntityText = entity.getCustomName().getString();
                  } else {
                     selectedEntityText = "";
                  }

                  if (!heldNameText.equalsIgnoreCase(selectedEntityText)) {
                     Vec3 playerEyePosition = ClientAccess.argusclientMinecraft.player.getEyePosition();
                     Vec3 entityPosition = entity.position();
                     double distance = playerEyePosition.distanceTo(entityPosition);
                     Float range = this.rangeSetting.getValue();
                     if (!(distance > range.floatValue())) {
                        InteractionHand interactionHand = InteractionHand.MAIN_HAND;
                        BlockInteractionUtils.interactWithEntity(entity, interactionHand);
                        taggedCount++;
                        if (this.rotateSetting.getValue()) {
                           Vec3 targetPosition = entity.position();
                           float[] rotations = RotationManager.getRotationsToPosition(targetPosition);
                           event.setRotation(rotations);
                        }

                        Integer frequency = this.frequencySetting.getValue();
                        if (taggedCount >= frequency) {
                           break;
                        }
                     }
                  }
               }
            }

            if (this.swapSetting.getValue() == ItemSwapMode.SILENT) {
               InventoryUtils.selectHotbarSlotForPlacement(previousSlot);
            }

            timerSnapshot = this.nameTagTimer;
            timerSnapshot.reset();
         }
      }
   }

   public AutoNameTagModule() {
      super("AutoNameTag", "Will use name tags on nearby entities.", ModuleCategory.PLAYER);
      DefaultedRegistry entityRegistry = BuiltInRegistries.ENTITY_TYPE;
      EntityType[] defaultEntities = new EntityType[1];
      EntityType witherType = EntityTypes.WITHER;
      defaultEntities[0] = witherType;
      this.entitiesSetting = this.add(new RegistrySetSetting<>("Entities", entityRegistry, defaultEntities));
      ListFilterMode defaultMode = ListFilterMode.WHITELIST;
      this.modeSetting = this.add(new EnumSetting<>("Mode", defaultMode));
      this.rangeSetting = this.add(new NumberSetting<>("Range", 4.0F, 1.0F, 6.0F).withUnit("m"));
      this.frequencySetting = this.add(new NumberSetting<>("Frequency", 1, 1, 8));
      this.delaySetting = this.add(new NumberSetting<>("Delay", 0.0F, 0.1F, 5.0F).withUnit("s"));
      Boolean defaultRotate = true;
      this.rotateSetting = this.add(new BooleanSetting("Rotate", defaultRotate));
      ItemSwapMode defaultSwap = ItemSwapMode.NONE;
      this.swapSetting = this.add(new EnumSetting<>("Swap", defaultSwap));
      this.nameTagTimer = new ElapsedTimer();
   }
}
