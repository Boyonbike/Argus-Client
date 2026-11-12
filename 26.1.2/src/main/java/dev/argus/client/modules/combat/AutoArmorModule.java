package dev.argus.client.modules.combat;

import com.mojang.authlib.GameProfile;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.events.MinecraftTickEvent;
import dev.argus.client.events.ScreenChangeEvent;
import dev.argus.client.gui.screens.ClientScreen;
import dev.argus.client.managers.HoleManager;
import dev.argus.client.managers.InventoryStateManager;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.managers.SocialRegistry;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.movement.ElytraFlyModule;
import dev.argus.client.modules.movement.NoSlowModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.SettingLabelCondition;
import dev.argus.client.settings.options.AutoArmorBlastChoice;
import dev.argus.client.settings.options.AutoArmorHelmetChoice;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.combat.DamageUtils;
import dev.argus.client.utils.entity.EntityUtils;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.inventory.ArmorSlot;
import dev.argus.client.utils.inventory.EnchantmentUtils;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.movement.NewGrimElytraFlyHandler;
import dev.argus.client.utils.movement.VolytraElytraFlyHandler;
import dev.argus.client.utils.movement.WallsVelocityHandler;
import dev.argus.client.utils.network.PacketSender;
import dev.argus.client.utils.world.WorldUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemAttributeModifiers.Entry;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class AutoArmorModule extends Module {
   private static ElytraFlyModule elytraFlyModule = ClientContext.getModule(ElytraFlyModule.class);
   private static NoSlowModule noSlowModule = ClientContext.getModule(NoSlowModule.class);
   private final Setting<Integer> frequencySetting = this.add(new NumberSetting<>("Frequency", 1, 1, 4));
   private final Setting<Integer> delaySetting = this.add(new NumberSetting<>("Delay", 50, 0, 500).withUnit("ms"));
   public final Setting<Boolean> alternativeSetting = this.add(new BooleanSetting("Alternative", false));
   final Setting<Boolean> safeSetting = this.add(new BooleanSetting("Safe", false).markVisibilityDependency());
   private final Setting<Integer> safeAmountSetting;
   final Setting<Boolean> inRangeSetting;
   private final Setting<Float> rangeSetting;
   private final Setting<Boolean> allowFriendsSetting;
   private final Setting<Boolean> preferencesSetting;
   final Setting<AutoArmorHelmetChoice> helmetSetting;
   private final Setting<Boolean> goldenBootsSetting;
   private final Setting<Float> piglinRangeSetting;
   private final Setting<Boolean> onlySafeSetting;
   private final Setting<AutoArmorBlastChoice> blastSetting;
   private final Setting<Boolean> bindingCurseSetting;
   private final Setting<Boolean> thornsSetting;
   public final Setting<Boolean> elytraSetting;
   private final Setting<Boolean> onlyAirSetting;
   private final Setting<Boolean> blastBootsSetting;
   private final ElapsedTimer swapDelayTimer;
   private final ElapsedTimer lastGlideTimer;
   private final ElapsedTimer safetyPredicateChangeTimer;
   private final ElapsedTimer heightPredicateChangeTimer;
   private final ElapsedTimer jumpToggleTimer;
   private boolean jumpToggleActive;
   private boolean previousJumpDown;
   private boolean previousSafetyPredicate;
   private boolean previousHeightPredicate;
   private boolean skipNextArmorUpdate;
   private double nearestAdultPiglinDistance;

   /**
    * Whether the worn chest item is wings that elytra flight is using: while gliding or in the air, or any time
    * ElytraFly is on. Swapping them for a chestplate would end the flight (NewGrim's FallChestplate excepted).
    */
   private boolean isFlyingOnWings(ItemStack chest) {
      if (chest.isEmpty() || !chest.has(DataComponents.GLIDER)) {
         return false;
      }

      if (elytraFlyModule.getActiveMode() instanceof NewGrimElytraFlyHandler flightMode && flightMode.shouldFallWithChestplate()) {
         return false;
      }

      return elytraFlyModule.isToggled()
         || ClientAccess.argusclientMinecraft.player.isFallFlying()
         || !EntityUtils.hasCollisionBelow(ClientAccess.argusclientMinecraft.player);
   }

   /** Whether Volytra's Smart Elytra puts wings on for takeoff and off on landing, so AutoArmor never equips them. */
   private static boolean isVolytraSwappingWings() {
      return elytraFlyModule.isToggled()
         && elytraFlyModule.getActiveMode() instanceof VolytraElytraFlyHandler volytra
         && volytra.isSmartElytraOn();
   }

   /** AutoArmor's score for a chestplate (higher is better, negative never wear), for Volytra's landing swap. */
   public double scoreChestplate(ItemStack stack) {
      return stack.has(DataComponents.GLIDER) ? -1.0 : this.scoreArmor(stack, ArmorSlot.CHESTPLATE);
   }

   private boolean shouldPreferGoldenHelmet() {
      return this.helmetSetting.getValue().isGold() && this.isPiglinWithinRange();
   }

   private boolean shouldPreferElytraForCurrentFlight() {
      if (elytraFlyModule.isToggled()) {
         Setting<ElytraFlyModule.ModeChoice> modeOption = elytraFlyModule.modeSetting;
         if (modeOption.getValue() == ElytraFlyModule.ModeChoice.BOUNCE) {
            Setting<ElytraFlyModule.InfDurabilityChoice> durabilityOption = elytraFlyModule.infDurabilitySetting;
            return durabilityOption.getValue().isDisabled();
         }
      }

      if (EntityUtils.hasCollisionBelow(ClientAccess.argusclientMinecraft.player)) {
         return false;
      } else {
         return !PlayerStateUtils.isInLiquid(ClientAccess.argusclientMinecraft.player) || PlayerStateUtils.isFallFlying()
            ? this.jumpToggleActive
            : ClientAccess.argusclientMinecraft.player.isInLava() && !this.jumpToggleTimer.hasElapsed(200L);
      }
   }

   private void updateNearestAdultPiglinDistance() {
      Vec3 eyePosition = ClientAccess.argusclientMinecraft.player.getEyePosition();
      double nearestDistance = Double.MAX_VALUE;

      for (Entity entity : ClientAccess.argusclientMinecraft.level.entitiesForRendering()) {
         if (entity instanceof Piglin piglin && !piglin.isBaby()) {
            AABB bounds = entity.getBoundingBox();
            double piglinDistance = WorldUtils.distanceToBounds(eyePosition, bounds);
            nearestDistance = Math.min(nearestDistance, piglinDistance);
         }
      }

      this.nearestAdultPiglinDistance = nearestDistance;
   }

   private static boolean isGoldenBootsEnabled(AutoArmorModule owner) {
      return owner.goldenBootsSetting.getValue() && owner.goldenBootsSetting.isVisible();
   }

   private boolean shouldRemoveHelmet() {
      return !this.helmetSetting.getValue().isNone()
         ? false
         : !this.onlySafeSetting.getValue() || this.previousSafetyPredicate && this.safetyPredicateChangeTimer.hasElapsed(500L);
   }

   private void updateArmorSafetyState() {
      if (ClientAccess.argusclientMinecraft.player.isFallFlying()) {
         this.lastGlideTimer.reset();
      }

      double height = ClientAccess.argusclientMinecraft.player.getY();
      boolean heightPredicate = WallsVelocityHandler.hasPlayerCollisionAtHeight(height);
      boolean previousSafety = this.previousSafetyPredicate;
      HoleManager holeManager = ClientContext.holeManager;
      if (previousSafety != holeManager.isInHole()) {
         this.safetyPredicateChangeTimer.reset();
      }

      if (this.previousHeightPredicate != heightPredicate) {
         this.heightPredicateChangeTimer.reset();
      }

      this.previousHeightPredicate = heightPredicate;
      HoleManager updatedHoleManager = ClientContext.holeManager;
      this.previousSafetyPredicate = updatedHoleManager.isInHole();
   }

   private boolean isPiglinWithinRange() {
      if (this.piglinRangeSetting.isAtMaximum()) {
         return true;
      }

      double piglinDistance = this.nearestAdultPiglinDistance;
      Float range = this.piglinRangeSetting.getValue();
      return piglinDistance <= range.floatValue();
   }

   private boolean hasRelevantPlayerInRange() {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;

      for (AbstractClientPlayer player : level.players()) {
         if (player != ClientAccess.argusclientMinecraft.player) {
            float distance = player.distanceTo(ClientAccess.argusclientMinecraft.player);
            Float range = this.rangeSetting.getValue();
            if (distance <= range) {
               SocialRegistry socialRegistry = ClientContext.socialRegistry;
               GameProfile profile = player.getGameProfile();
               String playerName = profile.name();
               if (!socialRegistry.isFriendName(playerName)) {
                  return true;
               }

               if (!this.allowFriendsSetting.getValue()) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private static void updateJumpToggle(AutoArmorModule owner) {
      KeyMapping jumpKey = ClientAccess.argusclientMinecraft.options.keyJump;
      boolean jumpDown = jumpKey.isDown() && ClientAccess.argusclientMinecraft.screen == null;
      if (ClientContext.playerStateTracker.getGroundedTicks() > 1) {
         owner.jumpToggleActive = false;
      } else if (jumpDown && !owner.previousJumpDown) {
         if (owner.jumpToggleActive) {
            owner.jumpToggleTimer.reset();
         }

         owner.jumpToggleActive = true;
         owner.lastGlideTimer.reset();
      }

      owner.previousJumpDown = jumpDown;
   }

   public void skipNextArmorUpdate() {
      this.skipNextArmorUpdate = true;
   }

   private void performArmorUpdates() {
      if (this.skipNextArmorUpdate) {
         this.skipNextArmorUpdate = false;
      } else {
         this.updateNearestAdultPiglinDistance();
         this.updateArmorSafetyState();
         Screen currentScreen = ClientAccess.argusclientMinecraft.screen;
         if (this.isArmorScreenAllowed(currentScreen)) {
            ElapsedTimer delayTimer = this.swapDelayTimer;
            long delayMillis = this.delaySetting.getValue().intValue();
            if (delayTimer.hasElapsed(delayMillis)) {
               if (!this.inRangeSetting.getValue() || this.hasRelevantPlayerInRange()) {
                  List<ArmorSwapAction> armorChanges = this.collectArmorChanges();
                  if (!armorChanges.isEmpty()) {
                     int appliedChanges = 0;
                     noSlowModule.prepareForInventoryAction();

                     for (ArmorSwapAction change : armorChanges) {
                        change.execute();
                        appliedChanges++;
                        Integer frequency = this.frequencySetting.getValue();
                        if (appliedChanges >= frequency) {
                           break;
                        }
                     }

                     this.swapDelayTimer.reset();
                  }
               }
            }
         }
      }
   }

   private List<ArmorSwapAction> collectArmorChanges() {
      List<ArmorSwapAction> armorChanges = new ArrayList<>();

      for (ArmorSlot armorKind : ArmorSlot.values()) {
         Inventory equippedInventory = ClientAccess.argusclientMinecraft.player.getInventory();
         int equippedSlot = armorKind.getInventoryIndex();
         ItemStack equippedStack = equippedInventory.getItem(equippedSlot);
         if (armorKind == ArmorSlot.CHESTPLATE && this.isFlyingOnWings(equippedStack)) {
            continue;
         }

         ResourceKey bindingCurse = Enchantments.BINDING_CURSE;
         if (!EnchantmentUtils.hasEnchantment(bindingCurse, equippedStack)) {
            double bestScore = this.scoreArmor(equippedStack, armorKind);
            int bestSlot = armorKind.getInventoryIndex();

            for (int candidateSlot = 0; candidateSlot < 36; candidateSlot++) {
               ItemStack candidateStack = ClientAccess.argusclientMinecraft.player.getInventory().getItem(candidateSlot);
               double candidateScore = this.scoreArmor(candidateStack, armorKind);
               if (candidateScore > bestScore) {
                  bestScore = candidateScore;
                  bestSlot = candidateSlot;
               }
            }

            if (bestSlot != armorKind.getInventoryIndex() && bestScore != -1.0) {
               ArmorSwapAction change = new ArmorSwapAction(armorKind, bestSlot);
               boolean alternative = this.alternativeSetting.getValue();
               change.setAlternative(alternative);
               armorChanges.add(change);
            }
         }
      }

      if (this.shouldRemoveHelmet() && armorChanges.isEmpty()) {
         Inventory helmetInventory = ClientAccess.argusclientMinecraft.player.getInventory();
         ArmorSlot helmetKind = ArmorSlot.HELMET;
         int helmetSlot = helmetKind.getInventoryIndex();
         ItemStack helmetStack = helmetInventory.getItem(helmetSlot);
         if (!helmetStack.isEmpty()) {
            ResourceKey bindingCurse = Enchantments.BINDING_CURSE;
            if (!EnchantmentUtils.hasEnchantment(bindingCurse, helmetStack) && !InventoryUtils.isInventoryFull()) {
               ArmorSwapAction removal = new ArmorSwapAction(ArmorSlot.HELMET, -1);
               removal.setQuickMove(true);
               armorChanges.add(removal);
            }
         }
      }

      return armorChanges;
   }

   private boolean shouldUseBlastProtection(ArmorSlot armorKind) {
      if (this.elytraSetting.getValue() && this.blastBootsSetting.getValue() && armorKind == ArmorSlot.BOOTS) {
         return true;
      }

      boolean heightPredicateSettled = false;
      if (!this.previousHeightPredicate && this.heightPredicateChangeTimer.hasElapsed(150L)) {
         heightPredicateSettled = true;
      }

      AutoArmorBlastChoice blastChoice = this.blastSetting.getValue();

      return switch (blastChoice) {
         case NONE -> false;
         case ALL -> true;
         case LEGS -> armorKind == ArmorSlot.LEGGINGS;
         case SMART -> armorKind == ArmorSlot.LEGGINGS || armorKind == ArmorSlot.BOOTS && heightPredicateSettled;
      };
   }

   private double scoreArmor(ItemStack stack, ArmorSlot armorKind) {
      float durability = InventoryUtils.getDurabilityPercent(stack);
      if (this.safeSetting.getValue()) {
         Integer minimumDurability = this.safeAmountSetting.getValue();
         if (durability < minimumDurability.intValue()) {
            return -1.0;
         }
      }

      Item shieldItem = Items.SHIELD;
      if (stack.is(shieldItem)) {
         return -1.0;
      }

      if (!this.bindingCurseSetting.getValue()) {
         ResourceKey bindingCurse = Enchantments.BINDING_CURSE;
         if (EnchantmentUtils.hasEnchantment(bindingCurse, stack)) {
            return -1.0;
         }
      }

      Item elytraItem = Items.ELYTRA;
      if (stack.is(elytraItem) && armorKind == ArmorSlot.CHESTPLATE) {
         if (DamageUtils.getDurabilityPercent(stack) <= 1 || isVolytraSwappingWings()) {
            return -1.0;
         }

         double elytraScore = 1.0;
         Boolean preferElytra = this.elytraSetting.getValue();
         if (preferElytra && (!this.onlyAirSetting.getValue() || this.shouldPreferElytraForCurrentFlight())) {
            elytraScore *= 9999.0;
         }

         if (stack.isEnchanted()) {
            ResourceKey unbreaking = Enchantments.UNBREAKING;
            elytraScore += EnchantmentUtils.getLevel(unbreaking, stack);
            ResourceKey mending = Enchantments.MENDING;
            elytraScore += EnchantmentUtils.getLevel(mending, stack);
         }

         return elytraFlyModule.getActiveMode() instanceof NewGrimElytraFlyHandler flightMode && flightMode.shouldFallWithChestplate() ? -1.0 : elytraScore;
      } else {
         DataComponentType<Equippable> equippableType = DataComponents.EQUIPPABLE;
         if (stack.get(equippableType) instanceof Equippable equippable) {
            EntityType playerType = EntityType.PLAYER;
            if (equippable.canBeEquippedBy(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(playerType)) && !(stack.getItem() instanceof BlockItem)) {
               EquipmentSlot equipmentSlot = equippable.slot();
               int equipmentIndex = equipmentSlot.getIndex();
               if (equipmentIndex == armorKind.getArmorIndex()) {
                  double armorScore = 0.0;
                  if (this.shouldRemoveHelmet() && equippable.slot() == EquipmentSlot.HEAD) {
                     return -1.0;
                  }

                  if (this.helmetSetting.getValue().isTurtle()) {
                     Item turtleHelmet = Items.TURTLE_HELMET;
                     if (stack.is(turtleHelmet)) {
                        armorScore = 9999.0;
                     }
                  }

                  if (this.shouldPreferGoldenHelmet()) {
                     Item goldenHelmet = Items.GOLDEN_HELMET;
                     if (stack.is(goldenHelmet)) {
                        armorScore = 9999.0;
                     }
                  }

                  if (isGoldenBootsEnabled(this) && this.isPiglinWithinRange()) {
                     Item goldenBoots = Items.GOLDEN_BOOTS;
                     if (stack.is(goldenBoots)) {
                        armorScore = 9999.0;
                     }
                  }

                  DataComponentType<ItemAttributeModifiers> modifiersType = DataComponents.ATTRIBUTE_MODIFIERS;
                  if (stack.get(modifiersType) instanceof ItemAttributeModifiers modifiers) {
                     for (Entry entry : modifiers.modifiers()) {
                        if (entry.attribute() == Attributes.ARMOR_TOUGHNESS || entry.attribute() == Attributes.ARMOR) {
                           AttributeModifier modifier = entry.modifier();
                           double amount = modifier.amount();
                           armorScore += amount * 10.0;
                        }
                     }
                  }

                  if (stack.isEnchanted()) {
                     ResourceKey preferredProtection = this.shouldUseBlastProtection(armorKind) ? Enchantments.BLAST_PROTECTION : Enchantments.PROTECTION;
                     ItemEnchantments enchantments = stack.getEnchantments();

                     for (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry entry : enchantments.entrySet()) {
                        int level = entry.getIntValue();
                        Holder curseHolder = (Holder)entry.getKey();
                        TagKey curseTag = EnchantmentTags.CURSE;
                        if (!curseHolder.is(curseTag)) {
                           Holder thornsHolder = (Holder)entry.getKey();
                           ResourceKey thornsEnchantment = Enchantments.THORNS;
                           boolean thorns = thornsHolder.is(thornsEnchantment);
                           if (((Holder)entry.getKey()).is(preferredProtection)) {
                              armorScore += level * 3;
                           } else if (thorns) {
                              Boolean allowThorns = this.thornsSetting.getValue();
                              double penalty;
                              if (allowThorns) {
                                 penalty = 0.0;
                              } else {
                                 penalty = 1.0;
                              }

                              armorScore -= penalty;
                           } else {
                              double enchantmentLevel = level;
                              armorScore += enchantmentLevel * 0.1;
                           }
                        }
                     }
                  }

                  return armorScore;
               }
            }
         }

         return -1.0;
      }
   }

   @EventHandler(priority = 100)
   private void onLocalPlayerPreTick(LocalPlayerPreTickEvent event) {
      this.performArmorUpdates();
      if (PlayerStateUtils.isWearingElytra(ClientAccess.argusclientMinecraft.player)
         && !ClientAccess.argusclientMinecraft.player.isFallFlying()
         && !EntityUtils.hasCollisionBelow(ClientAccess.argusclientMinecraft.player)
         && !this.swapDelayTimer.hasElapsed(300L)
         && !this.lastGlideTimer.hasElapsed(500L)) {
         PacketSender.sendStartFallFlying();
         ClientAccess.argusclientMinecraft.player.startFallFlying();
      }
   }

   @EventHandler
   private void onMinecraftSetScreenEvent(ScreenChangeEvent.Before event) {
      if (!this.isWorldUnavailable()
         && event.getNewScreen() == null
         && RotationManager.is2b2tMode()
         && !InventoryStateManager.isClickRateLimited(ClientContext.inventoryStateManager)) {
         if (event.getPreviousScreen() instanceof AbstractContainerScreen previousContainer) {
            AbstractContainerMenu previousMenu = previousContainer.getMenu();
            if (previousMenu == ClientAccess.argusclientMinecraft.player.inventoryMenu) {
               return;
            }
         }

         ClientContext.taskScheduler.scheduleRotationTask(() -> {
            InventoryUtils.clickSlot(0, 0, ContainerInput.PICKUP);
            InventoryUtils.clickSlot(0, 0, ContainerInput.PICKUP);
         }, 0);
      }
   }

   @EventHandler
   private void onMinecraftTick(MinecraftTickEvent event) {
      if (!this.isWorldUnavailable()) {
         updateJumpToggle(this);
      }
   }

   private boolean isArmorScreenAllowed(Screen screen) {
      if (screen instanceof ClientScreen) {
         return true;
      } else {
         return screen instanceof AbstractContainerScreen ? screen instanceof InventoryScreen : true;
      }
   }

   public static void bindModuleRegistry(ModuleRegistry registry) {
      elytraFlyModule = (ElytraFlyModule)registry.getModule(ElytraFlyModule.class);
      noSlowModule = (NoSlowModule)registry.getModule(NoSlowModule.class);
   }

   public AutoArmorModule() {
      super("AutoArmor", "Equips the best armor in your inventory for you.", ModuleCategory.COMBAT);
      Predicate<Object> safeAmountVisibility = unused -> {
         Setting<Boolean> setting = this.safeSetting;
         return setting.isExpanded();
      };
      this.safeAmountSetting = this.add(new NumberSetting<>("SafeAmount", 5, 1, 100, safeAmountVisibility).withUnit("%"));
      this.inRangeSetting = this.add(new BooleanSetting("InRange", false).markVisibilityDependency());
      Predicate<Object> rangeVisibility = unused -> {
         Setting<Boolean> setting = this.inRangeSetting;
         return setting.isExpanded();
      };
      this.rangeSetting = this.add(new NumberSetting<>("Range", 8.0F, 1.0F, 16.0F, rangeVisibility).withUnit("m"));
      Predicate<Object> friendsVisibility = unused -> {
         Setting<Boolean> setting = this.inRangeSetting;
         return setting.isExpanded();
      };
      this.allowFriendsSetting = this.add(new BooleanSetting("AllowFriends", true, friendsVisibility));
      this.preferencesSetting = this.add(new BooleanSetting("Preferences", false).markAsGroup().markVisibilityDependency());
      this.helmetSetting = this.add(new EnumSetting<>("Helmet", AutoArmorHelmetChoice.BEST).dependsOn(this.preferencesSetting));
      Predicate<Object> goldenBootsVisibility = unused -> {
         Setting<AutoArmorHelmetChoice> helmetOption = this.helmetSetting;
         return helmetOption.getValue() != AutoArmorHelmetChoice.GOLD;
      };
      this.goldenBootsSetting = this.add(new BooleanSetting("GoldenBoots", false, goldenBootsVisibility).dependsOn(this.preferencesSetting));
      this.piglinRangeSetting = this.add(new NumberSetting<>("PiglinRange", 8.0F, 1.0F, 16.0F).withUnit("m").dependsOn(this.preferencesSetting));
      this.onlySafeSetting = this.add(new BooleanSetting("OnlySafe", false).dependsOn(this.preferencesSetting));
      this.blastSetting = this.add(new EnumSetting<>("Blast", AutoArmorBlastChoice.LEGS).dependsOn(this.preferencesSetting));
      this.bindingCurseSetting = this.add(new BooleanSetting("BindingCurse", false).dependsOn(this.preferencesSetting));
      this.thornsSetting = this.add(new BooleanSetting("Thorns", false).dependsOn(this.preferencesSetting));
      this.elytraSetting = this.add(new BooleanSetting("Elytra", false).markVisibilityDependency().dependsOn(this.preferencesSetting));
      this.onlyAirSetting = this.add(new BooleanSetting("OnlyAir", false).dependsOn(this.elytraSetting, this.preferencesSetting));
      this.blastBootsSetting = this.add(new BooleanSetting("BlastBoots", false).dependsOn(this.elytraSetting, this.preferencesSetting));
      this.swapDelayTimer = new ElapsedTimer();
      this.lastGlideTimer = new ElapsedTimer();
      this.safetyPredicateChangeTimer = new ElapsedTimer();
      this.heightPredicateChangeTimer = new ElapsedTimer();
      this.jumpToggleTimer = new ElapsedTimer();
      Setting<Float> piglinRange = this.piglinRangeSetting;
      SettingLabelCondition maximumBoundary = SettingLabelCondition.MAX;
      piglinRange.setBoundaryLabel("None", maximumBoundary);
      this.piglinRangeSetting.addVisibilityPredicate(unused -> {
         Setting<AutoArmorHelmetChoice> helmetOption = this.helmetSetting;
         return helmetOption.getValue().isGold() || isGoldenBootsEnabled(this);
      });
      this.onlySafeSetting.addVisibilityPredicate(unused -> {
         Setting<AutoArmorHelmetChoice> helmetOption = this.helmetSetting;
         return helmetOption.getValue().isNone();
      });
      this.blastSetting.setChangeCallback(this.swapDelayTimer::clear);
      this.elytraSetting.setChangeCallback(() -> {
         this.swapDelayTimer.clear();
         this.lastGlideTimer.reset();
      });
      this.helmetSetting.setChangeCallback(this.swapDelayTimer::clear);
   }
}
