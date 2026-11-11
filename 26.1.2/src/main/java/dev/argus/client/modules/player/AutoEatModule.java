package dev.argus.client.modules.player;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.EventStage;
import dev.argus.client.events.LocalPlayerSendPositionEvent;
import dev.argus.client.managers.SocialRegistry;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.RegistrySetSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.SettingLabelCondition;
import dev.argus.client.settings.options.ListFilterMode;
import dev.argus.client.utils.entity.EntityUtils;
import dev.argus.client.utils.inventory.CustomItemRegistries;
import dev.argus.client.utils.inventory.InventoryUtils;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.item.component.SuspiciousStewEffects.Entry;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;

public class AutoEatModule extends Module {
   private final Setting<Set<Item>> itemsSetting;
   private final Setting<ListFilterMode> modeSetting;
   private final Setting<Integer> healthSetting;
   private final Setting<Integer> hungerSetting;
   private final Setting<Boolean> untilFullSetting;
   private final Setting<Boolean> pauseBaritoneSetting;
   private final Setting<Boolean> autoSwapSetting;
   private final Setting<AutoEatModule.SortChoice> sortSetting;
   private final Setting<Boolean> swapBackSetting;
   private final Setting<Boolean> autoEscapeSetting;
   private final Setting<Boolean> autoDisableSetting;
   private int previousSelectedSlot;
   private boolean eating;
   private boolean continueUntilFull;
   private int swappedContainerSlot;

   @Override
   public void onToggle() {
      this.setEating(false);
      this.previousSelectedSlot = -1;
      this.swappedContainerSlot = -1;
   }

   @Override
   public void onDisable() {
      ClientAccess.argusclientMinecraft.options.keyUse.setDown(false);
      ClientContext.baritoneBridge.releasePause(this);
   }

   private void swapWithSelectedHotbarSlot(int containerSlot) {
      MultiPlayerGameMode gameMode = ClientAccess.argusclientMinecraft.gameMode;
      int containerId = ClientAccess.argusclientMinecraft.player.containerMenu.containerId;
      int selectedHotbarSlot = ClientAccess.argusclientMinecraft.player.getInventory().getSelectedSlot();
      ContainerInput clickType = ContainerInput.SWAP;
      LocalPlayer clickPlayer = ClientAccess.argusclientMinecraft.player;
      gameMode.handleContainerInput(containerId, containerSlot, selectedHotbarSlot, clickType, clickPlayer);
   }

   @EventHandler
   private void onLocalPlayerSendPositionEvent(LocalPlayerSendPositionEvent event) {
      EventStage postStage = EventStage.Post;
      if (event.getStage() != postStage) {
         if (this.continueUntilFull) {
            FoodData foodData = ClientAccess.argusclientMinecraft.player.getFoodData();
            if (!foodData.needsFood()) {
               this.continueUntilFull = false;
            }
         }

         ClientContext.baritoneBridge.releasePause(this);
         Boolean initialAutoSwapEnabled = this.autoSwapSetting.getValue();
         if (!initialAutoSwapEnabled && this.eating) {
            LocalPlayer player = ClientAccess.argusclientMinecraft.player;
            if (player.isUsingItem() && !this.canEat(player.getItemInHand(player.getUsedItemHand()))) {
               MultiPlayerGameMode invalidStackGameMode = ClientAccess.argusclientMinecraft.gameMode;
               LocalPlayer invalidStackPlayer = ClientAccess.argusclientMinecraft.player;
               invalidStackGameMode.releaseUsingItem(invalidStackPlayer);
               this.setEating(false);
               return;
            }
         }

         int foodSlot;
         label99: {
            foodSlot = this.findFoodSlot();
            if (foodSlot != -1) {
               ItemStack candidateStack = ClientAccess.argusclientMinecraft.player.getInventory().getItem(foodSlot);
               Item chorusFruitToCompare = Items.CHORUS_FRUIT;
               if (candidateStack.is(chorusFruitToCompare)) {
                  break label99;
               }
            }

            if (this.shouldEscape()) {
               Item chorusFruitToFind = Items.CHORUS_FRUIT;
               int chorusContainerSlot = InventoryUtils.findMenuSlot(chorusFruitToFind);
               if (chorusContainerSlot != -1) {
                  foodSlot = -1;
                  this.swappedContainerSlot = chorusContainerSlot;
                  int escapeContainerSlot = this.swappedContainerSlot;
                  this.swapWithSelectedHotbarSlot(escapeContainerSlot);
               }
            }
         }

         if (this.shouldStopEating() && !this.shouldEscape()) {
            boolean useTicksAtThreshold = ClientAccess.argusclientMinecraft.player.getTicksUsingItem() >= 2;
            if (this.eating && !useTicksAtThreshold) {
               MultiPlayerGameMode stopUseGameMode = ClientAccess.argusclientMinecraft.gameMode;
               LocalPlayer stopUsePlayer = ClientAccess.argusclientMinecraft.player;
               stopUseGameMode.releaseUsingItem(stopUsePlayer);
               this.setEating(false);
               if (this.autoSwapSetting.getValue() && this.swapBackSetting.getValue() && this.previousSelectedSlot != -1) {
                  int previousHotbarSlot = this.previousSelectedSlot;
                  InventoryUtils.selectHotbarSlotForPlacement(previousHotbarSlot);
                  this.previousSelectedSlot = -1;
               }

               if (this.swappedContainerSlot != -1) {
                  int restoredContainerSlot = this.swappedContainerSlot;
                  this.swapWithSelectedHotbarSlot(restoredContainerSlot);
                  this.swappedContainerSlot = -1;
               }

               if (this.autoDisableSetting.getValue() && this.autoEscapeSetting.getValue()) {
                  this.disable();
               }
            }
         } else {
            boolean useOffHand = foodSlot == 40;
            if (this.autoSwapSetting.getValue()
               && !useOffHand
               && foodSlot != -1
               && ClientAccess.argusclientMinecraft.player.getInventory().getSelectedSlot() != foodSlot) {
               this.previousSelectedSlot = ClientAccess.argusclientMinecraft.player.getInventory().getSelectedSlot();
               InventoryUtils.selectHotbarSlotForPlacement(foodSlot);
            }

            ItemStack mainHandStack = ClientAccess.argusclientMinecraft.player.getMainHandItem();
            LocalPlayer useStatePlayer = ClientAccess.argusclientMinecraft.player;
            if (!useStatePlayer.isUsingItem() && (useOffHand || this.canEat(mainHandStack))) {
               MultiPlayerGameMode useItemGameMode = ClientAccess.argusclientMinecraft.gameMode;
               LocalPlayer useItemPlayer = ClientAccess.argusclientMinecraft.player;
               InteractionHand useHand = useOffHand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
               useItemGameMode.useItem(useItemPlayer, useHand);
               this.setEating(true);
            }

            if (this.pauseBaritoneSetting.getValue() && ClientAccess.argusclientMinecraft.player.isUsingItem()) {
               ClientContext.baritoneBridge.requestPause(this);
            }
         }
      }
   }

   public boolean isEating() {
      return this.eating;
   }

   private void setEating(boolean requestedEating) {
      if (this.eating != requestedEating) {
         if (requestedEating && this.isUntilFullEnabled() && ClientAccess.argusclientMinecraft.player.getFoodData().needsFood()) {
            this.continueUntilFull = true;
         }

         this.eating = requestedEating;
      }
   }

   private int findFoodSlot() {
      double bestScore = -1.0;
      int selectedFoodSlot = -1;

      for (int scanSlot = 0; scanSlot < 10; scanSlot++) {
         if (scanSlot == 9) {
            scanSlot = 40;
         }

         ItemStack stack = ClientAccess.argusclientMinecraft.player.getInventory().getItem(scanSlot);
         FoodProperties foodProperties = stack.get(DataComponents.FOOD);
         if (this.canEat(stack)) {
            double candidateScore = this.sortSetting.getValue().score(stack, foodProperties);
            boolean betterScore = candidateScore > bestScore;
            if (!betterScore && candidateScore == bestScore) {
               Inventory tieBreakInventory = ClientAccess.argusclientMinecraft.player.getInventory();
               betterScore = scanSlot == tieBreakInventory.getSelectedSlot();
            }

            if (betterScore) {
               bestScore = candidateScore;
               selectedFoodSlot = scanSlot;
            }

            if (stack.getItem() == Items.CHORUS_FRUIT && this.shouldEscape()) {
               selectedFoodSlot = scanSlot;
               break;
            }
         }
      }

      return selectedFoodSlot;
   }

   private boolean canEat(ItemStack stack) {
      FoodProperties foodProperties = stack.get(DataComponents.FOOD);
      if (foodProperties == null) {
         return false;
      }

      if (!foodProperties.canAlwaysEat() && ClientAccess.argusclientMinecraft.player.getFoodData().getFoodLevel() >= 20) {
         return false;
      }

      Item chorusFruit = Items.CHORUS_FRUIT;
      if (stack.is(chorusFruit) && !this.shouldEscape()) {
         return false;
      }

      ListFilterMode selectionMode = this.modeSetting.getValue();
      Item item = stack.getItem();
      if (!ListFilterMode.matches(selectionMode, item, this.itemsSetting.getValue())) {
         return false;
      }

      SuspiciousStewEffects stewEffects = stack.get(DataComponents.SUSPICIOUS_STEW_EFFECTS);
      if (stewEffects != null) {
         for (Entry stewEffect : stewEffects.effects()) {
            MobEffect mobEffect = stewEffect.effect().value();
            if (!mobEffect.isBeneficial()) {
               return false;
            }
         }
      }

      return true;
   }

   private boolean shouldEscape() {
      Boolean autoEscapeEnabled = this.autoEscapeSetting.getValue();
      if (!autoEscapeEnabled) {
         return false;
      }

      ClientLevel level = ClientAccess.argusclientMinecraft.level;

      for (Player candidatePlayer : level.players()) {
         LocalPlayer localPlayer = ClientAccess.argusclientMinecraft.player;
         float distanceToLocalPlayer = candidatePlayer.distanceTo(localPlayer);
         float escapeRadius = 12.0F;
         if (distanceToLocalPlayer <= escapeRadius) {
            SocialRegistry playerExclusionFilter = ClientContext.socialRegistry;
            if (!playerExclusionFilter.isFriendPlayer(candidatePlayer) && candidatePlayer != ClientAccess.argusclientMinecraft.player) {
               return ClientAccess.argusclientMinecraft.player.tickCount <= 50;
            }
         }
      }

      return false;
   }

   private boolean isUntilFullEnabled() {
      return this.untilFullSetting.getValue() && this.untilFullSetting.isVisible();
   }

   private boolean shouldStopEating() {
      if (this.continueUntilFull && this.isUntilFullEnabled()) {
         return false;
      }

      if (this.hungerSetting.getValue() != -1) {
         int foodLevel = ClientAccess.argusclientMinecraft.player.getFoodData().getFoodLevel();
         Integer hungerThreshold = this.hungerSetting.getValue();
         if (foodLevel <= hungerThreshold) {
            return false;
         }
      }

      if (this.healthSetting.getValue() == 0) {
         return true;
      }

      float healthAndAbsorption = EntityUtils.getPlayerHealthWithAbsorption();
      Integer healthThreshold = this.healthSetting.getValue();
      return healthAndAbsorption > healthThreshold.intValue();
   }

   public AutoEatModule() {
      super("AutoEat", "Eats your food for you.", ModuleCategory.PLAYER);
      DefaultedRegistry itemRegistry = CustomItemRegistries.foodItems;
      Item[] defaultItems = new Item[]{Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE};
      this.itemsSetting = this.add(new RegistrySetSetting<>("Items", itemRegistry, defaultItems));
      this.modeSetting = this.add(new EnumSetting<>("Mode", ListFilterMode.ANY));
      NumberSetting<Integer> health = new NumberSetting<>("Health", 16, 0, 36);
      SettingLabelCondition healthOffBound = SettingLabelCondition.MIN;
      this.healthSetting = this.add(health.setBoundaryLabel("Off", healthOffBound));
      NumberSetting<Integer> hunger = new NumberSetting<>("Hunger", 6, -1, 20);
      SettingLabelCondition hungerOffBound = SettingLabelCondition.MIN;
      this.hungerSetting = this.add(hunger.setBoundaryLabel("Off", hungerOffBound));
      this.untilFullSetting = this.add(new BooleanSetting("UntilFull", false));
      this.pauseBaritoneSetting = this.add(new BooleanSetting("PauseBaritone", false));
      this.autoSwapSetting = this.add(new BooleanSetting("AutoSwap", true).markVisibilityDependency());
      AutoEatModule.SortChoice defaultSort = AutoEatModule.SortChoice.SATURATION;
      this.sortSetting = this.add(new EnumSetting<>("Sort", defaultSort).dependsOn(this.autoSwapSetting));
      this.swapBackSetting = this.add(new BooleanSetting("SwapBack", false).dependsOn(this.autoSwapSetting));
      this.autoEscapeSetting = this.add(new BooleanSetting("AutoEscape", false).markVisibilityDependency());
      this.autoDisableSetting = this.add(new BooleanSetting("AutoDisable", false).dependsOn(this.autoEscapeSetting));
      this.untilFullSetting.addVisibilityPredicate(ignored -> !this.hungerSetting.isAtMinimum());
      this.pauseBaritoneSetting.hideWithoutBaritone();
   }

   private enum SortChoice implements NamedEntry {
      HUNGER("Hunger") {
         @Override
         public double score(ItemStack unusedStack, FoodProperties foodProperties) {
            return foodProperties.nutrition();
         }
      },
      SATURATION("Saturation") {
         @Override
         public double score(ItemStack unusedStack, FoodProperties foodProperties) {
            return foodProperties.saturation();
         }
      },
      HEALTH("Health") {
         @Override
         public double score(ItemStack stack, FoodProperties unusedFoodProperties) {
            if (stack.get(DataComponents.CONSUMABLE) instanceof Consumable consumable) {
               for (ConsumeEffect consumeEffect : consumable.onConsumeEffects()) {
                  if (consumeEffect instanceof ApplyStatusEffectsConsumeEffect statusEffects) {
                     int regenerationAmplifier = getRegenerationAmplifier(statusEffects);
                     if (regenerationAmplifier > 0) {
                        return regenerationAmplifier;
                     }
                  }
               }
            }

            return 0.0;
         }

         static int getRegenerationAmplifier(ApplyStatusEffectsConsumeEffect statusEffects) {
            for (MobEffectInstance effect : statusEffects.effects()) {
               if (effect.getEffect() == MobEffects.REGENERATION) {
                  return effect.getAmplifier();
               }
            }

            return 0;
         }
      };

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      SortChoice(String displayName) {
         this.displayName = displayName;
      }

      public abstract double score(ItemStack var1, FoodProperties var2);
   }
}
