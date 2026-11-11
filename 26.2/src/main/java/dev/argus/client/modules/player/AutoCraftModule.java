package dev.argus.client.modules.player;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.events.ScreenChangeEvent;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.managers.TaskScheduler;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.RegistrySetSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.BlockSampleMode;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.collections.ObjectPair;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.network.PacketSender;
import dev.argus.client.utils.world.BlockInteractionUtils;
import dev.argus.client.utils.world.WorldUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection.CraftableStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundRecipeBookChangeSettingsPacket;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class AutoCraftModule extends Module {
   private static final List<Ingredient> rocketIngredients;
   private final Setting<Set<Item>> itemsSetting = this.add(new RegistrySetSetting<>("Items", BuiltInRegistries.ITEM, Items.GOLD_INGOT));
   private final Setting<Set<Item>> recipeBlackListSetting = this.add(new RegistrySetSetting<>("RecipeBlackList", BuiltInRegistries.ITEM, Items.GOLD_BLOCK));
   private final Setting<Integer> delaySetting = this.add(new NumberSetting<>("Delay", 150, 0, 1500).withUnit("ms"));
   private final Setting<Integer> frequencySetting;
   private final Setting<Boolean> inInventorySetting = this.add(new BooleanSetting("InInventory", false));
   final Setting<AutoCraftModule.DropChoice> dropSetting;
   final Setting<Boolean> craftAllSetting;
   private final Setting<Boolean> fastCloseSetting;
   private final Setting<Boolean> autoOpenSetting;
   private final Setting<Float> rangeSetting;
   private final Setting<Boolean> raytraceSetting;
   private final Setting<Boolean> strictDirectionSetting;
   private final Setting<Boolean> rotateSetting;
   private final Setting<Boolean> limitSetting;
   private final Setting<Integer> maxSetting;
   private final Setting<Boolean> stackLimitSetting;
   private final ElapsedTimer craftTimer;
   private final ElapsedTimer openTimer;
   private final List<AutoCraftModule.CachedRecipe> cachedRecipes;
   private boolean recipesCached;
   private Boolean recipeBookWasOpen;

   private boolean hasPotentialRecipe() {
      if (this.itemsSetting.getValue().isEmpty()) {
         return false;
      }

      if (!this.recipesCached) {
         return true;
      }

      for (AutoCraftModule.CachedRecipe recipe : this.cachedRecipes) {
         ItemStack output = recipe.output();
         if (this.shouldCraftRecipe(output, recipe.ingredients())) {
            return true;
         }
      }

      return false;
   }

   private RecipeDisplayEntry findCraftableRecipe() {
      if (this.itemsSetting.getValue().isEmpty()) {
         return null;
      }

      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      ContextMap displayContext = SlotDisplayContext.fromLevel(level);
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      ClientRecipeBook recipeBook = player.getRecipeBook();

      for (RecipeCollection collection : recipeBook.getCollections()) {
         CraftableStatus status = CraftableStatus.ANY;

         for (RecipeDisplayEntry recipe : collection.getSelectedRecipes(status)) {
            RecipeDisplay display = recipe.display();
            ItemStack output = display.result().resolveForFirstStack(displayContext);
            Optional<List<Ingredient>> capturedRequirements = recipe.craftingRequirements();
            List<Ingredient> emptyRequirements = Collections.emptyList();
            List<Ingredient> ingredients = capturedRequirements.orElse(emptyRequirements);
            if (this.shouldCraftRecipe(output, ingredients)) {
               return recipe;
            }
         }
      }

      return null;
   }

   private void cacheRecipeDisplays() {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      ContextMap displayContext = SlotDisplayContext.fromLevel(level);
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      ClientRecipeBook recipeBook = player.getRecipeBook();

      for (RecipeCollection collection : recipeBook.getCollections()) {
         CraftableStatus status = CraftableStatus.ANY;

         for (RecipeDisplayEntry recipe : collection.getSelectedRecipes(status)) {
            RecipeDisplay display = recipe.display();
            ItemStack output = display.result().resolveForFirstStack(displayContext);
            Optional<List<Ingredient>> capturedRequirements = recipe.craftingRequirements();
            List<Ingredient> emptyRequirements = Collections.emptyList();
            List<Ingredient> ingredients = capturedRequirements.orElse(emptyRequirements);
            this.cachedRecipes.add(new AutoCraftModule.CachedRecipe(output, ingredients));
         }
      }
   }

   private ObjectPair findCraftingTable() {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      Vec3 capturedEyes = player.getEyePosition();
      float capturedRange = this.rangeSetting.getValue();
      boolean searchFlag = true;

      for (BlockPos position : WorldUtils.collectSphereBlocks(capturedEyes, capturedRange, searchFlag)) {
         ClientLevel level = ClientAccess.argusclientMinecraft.level;
         BlockState capturedState = level.getBlockState(position);
         Block craftingTable = Blocks.CRAFTING_TABLE;
         if (capturedState.is(craftingTable)) {
            Iterable<Vec3> visibleSamples = BlockSampleMode.X8.sampleBlock(position);
            if (WorldUtils.canSeeAny(visibleSamples) || !this.raytraceSetting.getValue()) {
               List<Direction> validDirections = BlockInteractionUtils.getStrictDirections(position);
               if (!validDirections.isEmpty() || !this.strictDirectionSetting.getValue()) {
                  return new ObjectPair(position, validDirections.isEmpty() ? Direction.UP : validDirections.get(0));
               }
            }
         }
      }

      return null;
   }

   @Override
   public void onEnable() {
      this.cachedRecipes.clear();
      this.recipesCached = false;
   }

   @Override
   public void onDisable() {
      if (!this.isWorldUnavailable()) {
         this.restoreRecipeBook();
      }
   }

   private void restoreRecipeBook() {
      if (this.recipeBookWasOpen != null) {
         boolean wasOpen = this.recipeBookWasOpen;
         this.recipeBookWasOpen = null;
         if (!wasOpen) {
            ClientRecipeBook stateBook = ClientAccess.argusclientMinecraft.player.getRecipeBook();
            RecipeBookType stateType = RecipeBookType.CRAFTING;
            if (stateBook.isOpen(stateType)) {
               ClientRecipeBook closeBook = ClientAccess.argusclientMinecraft.player.getRecipeBook();
               RecipeBookType closeType = RecipeBookType.CRAFTING;
               closeBook.setOpen(closeType, false);
               ClientRecipeBook filterBook = ClientAccess.argusclientMinecraft.player.getRecipeBook();
               RecipeBookType filterType = RecipeBookType.CRAFTING;
               boolean filtering = filterBook.isFiltering(filterType);
               PacketSender.send(new ServerboundRecipeBookChangeSettingsPacket(RecipeBookType.CRAFTING, false, filtering));
            }
         }
      }
   }

   private boolean hasIngredients(Collection<Ingredient> ingredients) {
      if (ingredients.isEmpty()) {
         return false;
      }

      HashMap<Item, Integer> itemCounts = new HashMap<>();

      for (Slot slot : ClientAccess.argusclientMinecraft.player.containerMenu.slots) {
         ItemStack stack = slot.getItem();
         itemCounts.compute(
            stack.getItem(),
            (item, previousCount) -> item != null && previousCount != null ? slot.getItem().getCount() + previousCount : slot.getItem().getCount()
         );
      }

      for (Ingredient ingredient : ingredients) {
         if (!ingredient.isEmpty()) {
            boolean matchedIngredient = false;

            for (Holder<Item> itemHolder : ingredient.items().toList()) {
               if (this.recipeBlackListSetting.getValue().contains(itemHolder.value())) {
                  return false;
               }

               if (itemCounts.containsKey(itemHolder.value())) {
                  Integer availableCount = itemCounts.get(itemHolder.value());
                  if (availableCount - 1 >= 0) {
                     Item consumedItem = itemHolder.value();
                     int remainingCount = availableCount - 1;
                     itemCounts.replace(consumedItem, remainingCount);
                     matchedIngredient = true;
                     break;
                  }
               }
            }

            if (!matchedIngredient) {
               return false;
            }
         }
      }

      return true;
   }

   private boolean shouldCraftRecipe(ItemStack output, List<Ingredient> ingredients) {
      if (output == null) {
         return false;
      }

      if (output.getItem() != null && !(output.getItem() instanceof SmithingTemplateItem)) {
         Item rocket = Items.FIREWORK_ROCKET;
         if (output.is(rocket)) {
            ingredients = rocketIngredients;
         }

         Set<?> selectedItems = this.itemsSetting.getValue();
         if (selectedItems.contains(output.getItem()) && this.hasIngredients(ingredients)) {
            Item outputItem = output.getItem();
            return this.isBelowItemLimit(outputItem);
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   @EventHandler
   private void prepareCraftingScreen(ScreenChangeEvent.Before event) {
      if (event.getNewScreen() instanceof CraftingScreen) {
         if (this.recipeBookWasOpen == null) {
            ClientRecipeBook stateBook = ClientAccess.argusclientMinecraft.player.getRecipeBook();
            RecipeBookType stateType = RecipeBookType.CRAFTING;
            this.recipeBookWasOpen = stateBook.isOpen(stateType);
         }

         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         ClientRecipeBook capturedRecipeBook = player.getRecipeBook();
         boolean open = true;
         RecipeBookType recipeBookType = RecipeBookType.CRAFTING;
         capturedRecipeBook.setOpen(recipeBookType, open);
         TaskScheduler scheduler = ClientContext.taskScheduler;
         Runnable cacheCallback = () -> {
            if (!this.recipesCached) {
               this.cacheRecipeDisplays();
               this.recipesCached = true;
            }
         };
         scheduler.scheduleRotationTask(cacheCallback, 2);
         if (this.fastCloseSetting.getValue() && event.getNewScreen() instanceof CraftingScreen) {
            Screen capturedScreen = event.getNewScreen();
            byte height = 0;
            byte width = 0;
            capturedScreen.init(width, height);
            event.cancel();
            TaskScheduler closeScheduler = ClientContext.taskScheduler;
            Runnable closeCallback = () -> {
               int containerId = ClientAccess.argusclientMinecraft.player.containerMenu.containerId;
               ServerboundContainerClosePacket closePacket = new ServerboundContainerClosePacket(containerId);
               PacketSender.send(closePacket);
               ClientAccess.argusclientMinecraft.player.containerMenu = ClientAccess.argusclientMinecraft.player.inventoryMenu;
            };
            closeScheduler.scheduleRotationTask(closeCallback, 0);
         }
      }
   }

   @EventHandler
   private void craftSelectedItems(LocalPlayerPreTickEvent event) {
      if (this.recipeBookWasOpen != null
         && !(ClientAccess.argusclientMinecraft.screen instanceof CraftingScreen)
         && !(ClientAccess.argusclientMinecraft.player.containerMenu instanceof CraftingMenu)) {
         this.restoreRecipeBook();
      }

      RecipeDisplayEntry recipe = this.findCraftableRecipe();
      if (this.autoOpenSetting.getValue() && this.openTimer.hasElapsed(500L) && !(ClientAccess.argusclientMinecraft.screen instanceof AbstractContainerScreen)) {
         ObjectPair craftingTable = this.findCraftingTable();
         if (craftingTable != null && (recipe != null || this.hasPotentialRecipe())) {
            if (this.rotateSetting.getValue()) {
               Vec3 tableCenter = ((BlockPos)craftingTable.first()).getCenter();
               float[] capturedAngles = RotationManager.getRotationsToPosition(tableCenter);
               boolean onGround = ClientAccess.argusclientMinecraft.player.onGround();
               PacketSender.sendRotation(capturedAngles, onGround);
            }

            InteractionHand capturedHand = InteractionHand.MAIN_HAND;
            Vec3 hitCenter = ((BlockPos)craftingTable.first()).getCenter();
            Direction hitDirection = (Direction)craftingTable.second();
            BlockHitResult hitResult = new BlockHitResult(hitCenter, hitDirection, (BlockPos)craftingTable.first(), false);
            PacketSender.sendUseItemOn(capturedHand, hitResult);
            InteractionHand swingHand = InteractionHand.MAIN_HAND;
            PacketSender.sendSwing(swingHand);
            this.openTimer.reset();
         }
      }

      if (recipe != null) {
         if (ClientAccess.argusclientMinecraft.player.containerMenu instanceof CraftingMenu
            || this.inInventorySetting.getValue()
               && ClientAccess.argusclientMinecraft.player.containerMenu == ClientAccess.argusclientMinecraft.player.inventoryMenu
               && ClientAccess.argusclientMinecraft.screen instanceof InventoryScreen) {
            if (!this.itemsSetting.getValue().isEmpty()) {
               ElapsedTimer capturedCraftTimer = this.craftTimer;
               long delayMillis = this.delaySetting.getValue().intValue();
               if (capturedCraftTimer.hasElapsed(delayMillis)) {
                  AbstractContainerMenu craftingMenu = ClientAccess.argusclientMinecraft.player.containerMenu;
                  ClientLevel level = ClientAccess.argusclientMinecraft.level;
                  ContextMap displayContext = SlotDisplayContext.fromLevel(level);
                  RecipeDisplay display = recipe.display();
                  ItemStack output = display.result().resolveForFirstStack(displayContext);
                  Item rocket = Items.FIREWORK_ROCKET;
                  if (!output.is(rocket)) {
                     int requestIndex = 0;

                     while (true) {
                        Item requestedOutputItem = output.getItem();
                        if (requestIndex >= this.getCraftRequestCount(requestedOutputItem)) {
                           break;
                        }

                        MultiPlayerGameMode capturedRecipeGameMode = ClientAccess.argusclientMinecraft.gameMode;
                        int capturedRecipeContainerId = craftingMenu.containerId;
                        RecipeDisplayId capturedRecipeId = recipe.id();
                        boolean placeAll = this.craftAllSetting.getValue();
                        capturedRecipeGameMode.handlePlaceRecipe(capturedRecipeContainerId, capturedRecipeId, placeAll);
                        requestIndex++;
                     }
                  } else {
                     boolean inventoryCrafting = craftingMenu == ClientAccess.argusclientMinecraft.player.inventoryMenu;
                     int gunpowderSlot = -1;
                     int paperSlot = -1;
                     int gunpowderUnits = 0;

                     for (int sourceSlotIndex = inventoryCrafting ? 9 : 10; sourceSlotIndex < (inventoryCrafting ? 45 : 46); sourceSlotIndex++) {
                        ItemStack sourceStack = craftingMenu.getSlot(sourceSlotIndex).getItem();
                        Item gunpowder = Items.GUNPOWDER;
                        if (sourceStack.is(gunpowder)) {
                           if (sourceStack.getCount() == sourceStack.getMaxStackSize()) {
                              gunpowderUnits++;
                           }

                           if (gunpowderSlot == -1) {
                              gunpowderSlot = sourceSlotIndex;
                           } else if (sourceStack.getCount() == sourceStack.getMaxStackSize()) {
                              gunpowderSlot = sourceSlotIndex;
                           }
                        }

                        Item paper = Items.PAPER;
                        if (sourceStack.is(paper) && paperSlot == -1) {
                           paperSlot = sourceSlotIndex;
                        }
                     }

                     if (paperSlot != -1) {
                        byte paperGridSlot = 1;
                        if (craftingMenu.getSlot(paperGridSlot).getItem().isEmpty()) {
                           capturedCraftTimer = this.craftTimer;
                           capturedCraftTimer.reset();
                           byte paperTargetSlot = 1;
                           InventoryUtils.swapSlotsWithPickup(paperSlot, paperTargetSlot);
                           return;
                        }
                     }

                     int[] gunpowderGridSlots = inventoryCrafting ? new int[]{2, 3, 4} : new int[]{2, 4, 5};
                     boolean gridHasGunpowder = true;

                     for (int gridSlotIndex : gunpowderGridSlots) {
                        ItemStack gridStack = craftingMenu.getSlot(gridSlotIndex).getItem();
                        Item gridGunpowder = Items.GUNPOWDER;
                        if (!gridStack.is(gridGunpowder)) {
                           gridHasGunpowder = false;
                        } else if (gridStack.getCount() == gridStack.getMaxStackSize()) {
                           gunpowderUnits++;
                        }
                     }

                     if (gunpowderSlot != -1 && !gridHasGunpowder) {
                        InventoryUtils.clickSlot(craftingMenu.containerId, gunpowderSlot, 0, ContainerInput.PICKUP);

                        for (int targetGridSlot : gunpowderGridSlots) {
                           ItemStack capturedPlacementStack = craftingMenu.getSlot(targetGridSlot).getItem();
                           Item placementGunpowder = Items.GUNPOWDER;
                           if (!capturedPlacementStack.is(placementGunpowder)) {
                              ItemStack carriedStack = craftingMenu.getCarried();
                              if (!carriedStack.isEmpty()) {
                                 int targetGridButton = gunpowderUnits >= 3 ? 0 : 1;
                                 InventoryUtils.clickSlot(craftingMenu.containerId, targetGridSlot, targetGridButton, ContainerInput.PICKUP);
                              }
                              break;
                           }
                        }

                        InventoryUtils.clickSlot(craftingMenu.containerId, gunpowderSlot, 0, ContainerInput.PICKUP);
                        capturedCraftTimer = this.craftTimer;
                        capturedCraftTimer.reset();
                        return;
                     }

                     byte resultIndex = 0;
                     ItemStack rocketResult = craftingMenu.getSlot(resultIndex).getItem();
                     DataComponentType fireworksComponent = DataComponents.FIREWORKS;
                     Fireworks defaultFireworks = new Fireworks(0, Collections.emptyList());
                     Fireworks fireworks = rocketResult.getOrDefault(fireworksComponent, defaultFireworks);
                     Item resultRocket = Items.FIREWORK_ROCKET;
                     if (!rocketResult.is(resultRocket)) {
                        return;
                     }

                     int flightDuration = fireworks.flightDuration();
                     if (flightDuration != 3 || !gridHasGunpowder) {
                        return;
                     }

                     capturedCraftTimer = this.craftTimer;
                     int rocketDelay = this.delaySetting.getValue();
                     long rocketWaitMillis = Math.max(200, rocketDelay);
                     if (!capturedCraftTimer.hasElapsed(rocketWaitMillis)) {
                        return;
                     }
                  }

                  int capturedResultContainerId = craftingMenu.containerId;
                  ContainerInput capturedClickType = this.dropSetting.getValue().getClickType();
                  InventoryUtils.clickSlot(capturedResultContainerId, 0, 1, capturedClickType);
                  ItemStack carriedAfterResult = craftingMenu.getCarried();
                  if (this.dropSetting.getValue() == AutoCraftModule.DropChoice.Pickup
                     && output.getCount() + carriedAfterResult.getCount() > carriedAfterResult.getMaxStackSize()) {
                     int capturedOverflowContainerId = craftingMenu.containerId;
                     InventoryUtils.clickSlot(capturedOverflowContainerId, -999, 0, ContainerInput.PICKUP);
                  }

                  capturedCraftTimer = this.craftTimer;
                  capturedCraftTimer.reset();
               }
            }
         }
      }
   }

   private int countInventoryItems(Item item) {
      int inventoryCount = 0;
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;

      for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
         if (stack.is(item)) {
            inventoryCount += stack.getCount();
         }
      }

      return inventoryCount;
   }

   private boolean isBelowItemLimit(Item item) {
      Boolean limitEnabled = this.limitSetting.getValue();
      if (!limitEnabled) {
         return true;
      }

      if (this.craftAllSetting.getValue()) {
         return true;
      }

      int maximumCount = this.maxSetting.getValue();
      if (this.stackLimitSetting.getValue()) {
         maximumCount *= item.getDefaultMaxStackSize();
      }

      return this.countInventoryItems(item) < maximumCount;
   }

   private int getCraftRequestCount(Item item) {
      Boolean limitEnabled = this.limitSetting.getValue();
      if (!limitEnabled) {
         return this.frequencySetting.getValue();
      }

      int maximumCount = this.maxSetting.getValue();
      if (this.stackLimitSetting.getValue()) {
         maximumCount *= item.getDefaultMaxStackSize();
      }

      int inventoryCount = this.countInventoryItems(item);
      int missingCount = maximumCount - inventoryCount;
      int maximumRequests = this.frequencySetting.getValue();
      byte minimumRequests = 0;
      return Mth.clamp(missingCount, minimumRequests, maximumRequests);
   }

   public AutoCraftModule() {
      super("AutoCraft", "Automatically crafts listed items.", ModuleCategory.PLAYER, "craftbot");
      this.frequencySetting = this.add(new NumberSetting<>("Frequency", 1, 1, 6));
      this.dropSetting = this.add(new EnumSetting<>("Drop", AutoCraftModule.DropChoice.None));
      this.craftAllSetting = this.add(new BooleanSetting("CraftAll", false, ignored -> this.dropSetting.getValue() == AutoCraftModule.DropChoice.None));
      this.fastCloseSetting = this.add(new BooleanSetting("FastClose", false));
      this.autoOpenSetting = this.add(new BooleanSetting("AutoOpen", false).markVisibilityDependency());
      this.rangeSetting = this.add(new NumberSetting<>("Range", 4.0F, 1.0F, 6.0F).withUnit("m").dependsOn(this.autoOpenSetting));
      this.raytraceSetting = this.add(new BooleanSetting("Raytrace", false).dependsOn(this.autoOpenSetting));
      this.strictDirectionSetting = this.add(new BooleanSetting("StrictDirection", false).dependsOn(this.autoOpenSetting));
      this.rotateSetting = this.add(new BooleanSetting("Rotate", false).dependsOn(this.autoOpenSetting));
      this.limitSetting = this.add(new BooleanSetting("Limit", false, ignored -> !this.craftAllSetting.getValue()).markVisibilityDependency());
      this.maxSetting = this.add(new NumberSetting<>("Max", 64, 1, 64, ignored -> !this.craftAllSetting.getValue()).dependsOn(this.limitSetting));
      this.stackLimitSetting = this.add(new BooleanSetting("StackLimit", false).dependsOn(this.limitSetting));
      this.craftTimer = new ElapsedTimer();
      this.openTimer = new ElapsedTimer();
      this.cachedRecipes = new ArrayList<>();
   }

   static {
      Ingredient paperIngredient = Ingredient.of(new ItemLike[]{Items.PAPER});
      Ingredient gunpowderIngredient = Ingredient.of(new ItemLike[]{Items.GUNPOWDER});
      rocketIngredients = List.of(paperIngredient, gunpowderIngredient);
   }

   private record CachedRecipe(ItemStack output, List<Ingredient> ingredients) {
   }

   enum DropChoice implements NamedEntry {
      None("None", ContainerInput.QUICK_MOVE),
      Throw("Throw", ContainerInput.THROW),
      Pickup("Pickup", ContainerInput.PICKUP);

      private final String displayName;
      private final ContainerInput clickType;

      public ContainerInput getClickType() {
         return this.clickType;
      }

      @Override
      public String getName() {
         return this.displayName;
      }

      DropChoice(String displayName, ContainerInput clickType) {
         this.displayName = displayName;
         this.clickType = clickType;
      }
   }
}
