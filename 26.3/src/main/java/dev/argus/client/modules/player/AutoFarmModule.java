package dev.argus.client.modules.player;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.RotationRequestEvent;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.DimensionType;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.world.BlockInteractionUtils;
import dev.argus.client.utils.world.WorldUtils;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipBlockStateContext;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.AzaleaBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableFeaturePlacerBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class AutoFarmModule extends Module {
   final Setting<AutoFarmModule.ModeChoice> modeSetting;
   private final Setting<AutoFarmModule.SwapChoice> swapSetting;
   public final Setting<Float> rangeSetting;
   private final Setting<Integer> blocksPerTickSetting;
   private final Setting<Boolean> placeSetting;
   private final Setting<Boolean> rotateSetting;
   private final Setting<Boolean> fertilizeSetting;
   final Setting<Boolean> removeSetting;
   private final Setting<Float> removeDelaySetting;
   private final Setting<Boolean> onlyGrownSetting;
   final Setting<Boolean> cropsSetting;
   private final Setting<Boolean> wheatSetting;
   private final Setting<Boolean> carrotsSetting;
   private final Setting<Boolean> potatoesSetting;
   private final Setting<Boolean> beetRootsSetting;
   private final Setting<Boolean> melonsSetting;
   private final Setting<Boolean> pumpkinsSetting;
   private final Setting<Boolean> sugarCaneSetting;
   private final Setting<Boolean> netherWartSetting;
   private final Setting<Boolean> mushroomsSetting;
   private final Setting<Boolean> treesSetting;
   private final Setting<Boolean> mossSetting;
   private final ElapsedTimer harvestTimer;
   private InventoryUtils.SwapSlot plantingItem;
   private int actionsThisTick;

   private boolean isEnabledPlantingItem(Item item) {
      if (item == Items.MOSS_BLOCK) {
         return this.mossSetting.getValue();
      }

      if (item instanceof BlockItem blockItem) {
         Block block = blockItem.getBlock();
         if (this.isTreePlant(block)) {
            return this.treesSetting.getValue();
         }

         if (block == Blocks.MELON || this.isMelonStem(block)) {
            return this.melonsSetting.getValue();
         }

         if (block == Blocks.PUMPKIN || this.isPumpkinStem(block)) {
            return this.pumpkinsSetting.getValue();
         }
      }

      if (item == Items.WHEAT_SEEDS && this.wheatSetting.getValue()) {
         return true;
      } else if (item == Items.CARROT && this.carrotsSetting.getValue()) {
         return true;
      } else if (item == Items.POTATO && this.potatoesSetting.getValue()) {
         return true;
      } else if (item == Items.BEETROOT_SEEDS && this.beetRootsSetting.getValue()) {
         return true;
      } else if (item == Items.SUGAR_CANE && this.sugarCaneSetting.getValue()) {
         return true;
      } else if (item == Items.NETHER_WART && this.netherWartSetting.getValue()) {
         return true;
      } else if (item == Items.MELON_SEEDS && this.melonsSetting.getValue()) {
         return true;
      } else {
         return item == Items.PUMPKIN_SEEDS && this.pumpkinsSetting.getValue() ? true : this.isSupportedMushroom(item) && this.mushroomsSetting.getValue();
      }
   }

   private boolean isPumpkinStem(Block block) {
      return block == Blocks.PUMPKIN_STEM || block == Blocks.ATTACHED_MELON_STEM;
   }

   private void processFarmPosition(BlockPos position, Direction harvestFace) {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      BlockState state = level.getBlockState(position);
      Block sugarCaneBlock = Blocks.SUGAR_CANE;
      boolean sugarCane = state.is(sugarCaneBlock);
      Predicate<ItemStack> plantingPredicate = stack -> {
         Item candidateItem = stack.getItem();
         if (!this.isEnabledPlantingItem(candidateItem)) {
            return false;
         }

         Item plantingItemType = stack.getItem();
         return this.canPlantAt(position, state, plantingItemType);
      };
      this.plantingItem = InventoryUtils.findSwapSlot(plantingPredicate, false);
      boolean hasPlantingItem = this.plantingItem.isFound();
      if (this.swapSetting.getValue() == AutoFarmModule.SwapChoice.NONE) {
         InventoryUtils.SwapSlot heldItem = this.plantingItem;
         if (!heldItem.isInHand()) {
            hasPlantingItem = false;
            this.plantingItem = null;
         }
      }

      if (this.placeSetting.getValue() && hasPlantingItem) {
         InventoryUtils.SwapSlot itemToPlant = this.plantingItem;
         this.plantAt(position, itemToPlant);
      } else {
         Item itemType = state.getBlock().asItem();
         if (this.isEnabledPlantingItem(itemType)) {
            boolean readyToHarvest = this.isReadyToHarvest(state, position);
            itemType = Items.BONE_MEAL;
            InteractionHand boneMealHand = this.findItemHand(itemType);
            if (this.fertilizeSetting.getValue() && !readyToHarvest && boneMealHand != null && !sugarCane) {
               this.fertilizeAt(position, boneMealHand);
            } else {
               this.harvestAt(position, harvestFace, readyToHarvest);
            }
         }
      }
   }

   private Direction findHarvestFace(BlockPos position) {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      Vec3 eyePosition = player.getEyePosition();
      ClipBlockStateContext clipContext = new ClipBlockStateContext(eyePosition, Vec3.atCenterOf(position), state -> true);
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      Direction hitFace = level.isBlockInLine(clipContext).getDirection();
      List<Direction> validFaces = BlockInteractionUtils.getStrictDirections(position);
      return !validFaces.contains(hitFace) && !validFaces.isEmpty() ? validFaces.getFirst() : hitFace;
   }

   private InteractionHand findItemHand(Item item) {
      if (ClientAccess.argusclientMinecraft.player.getMainHandItem().is(item)) {
         return InteractionHand.MAIN_HAND;
      } else {
         return ClientAccess.argusclientMinecraft.player.getOffhandItem().is(item) ? InteractionHand.OFF_HAND : null;
      }
   }

   private boolean isMelonStem(Block block) {
      return block == Blocks.MELON_STEM || block == Blocks.ATTACHED_MELON_STEM;
   }

   private boolean isSupportedMushroom(Item item) {
      if (item != Items.RED_MUSHROOM && item != Items.BROWN_MUSHROOM) {
         return item == Items.CRIMSON_FUNGUS || item == Items.WARPED_FUNGUS;
      }

      DimensionType dimension = ClientContext.worldStateManager.getDimension();
      return !dimension.isNether();
   }

   private boolean isTreePlant(Block block) {
      return block instanceof SaplingBlock || block instanceof AzaleaBlock;
   }

   private boolean canPlantAt(BlockPos position, BlockState state, Item item) {
      boolean needsFarmland = item == Items.MELON_SEEDS || item == Items.PUMPKIN_SEEDS;
      if (item instanceof BlockItem blockItem) {
         Block block = blockItem.getBlock();
         if (this.isTreePlant(block)) {
            TagKey dirtTag = BlockTags.DIRT;
            if (!state.is(dirtTag)) {
               return false;
            }

            AABB plantBounds = new AABB(position.above());
            LocalPlayer player = ClientAccess.argusclientMinecraft.player;
            AABB playerBounds = player.getBoundingBox();
            if (plantBounds.intersects(playerBounds)) {
               return false;
            }

            TagKey supportDirtTag = BlockTags.DIRT;
            if (state.is(supportDirtTag)) {
               ClientLevel treeLevel = ClientAccess.argusclientMinecraft.level;
               BlockPos treePlantPosition = position.above();
               BlockState treePlantState = treeLevel.getBlockState(treePlantPosition);
               Block treeAirBlock = Blocks.AIR;
               if (treePlantState.is(treeAirBlock)) {
                  return true;
               }
            }

            return false;
         }

         if (block instanceof NetherWartBlock) {
            Block soulSand = Blocks.SOUL_SAND;
            if (state.is(soulSand)) {
               ClientLevel wartLevel = ClientAccess.argusclientMinecraft.level;
               BlockPos wartPlantPosition = position.above();
               BlockState wartPlantState = wartLevel.getBlockState(wartPlantPosition);
               Block wartAirBlock = Blocks.AIR;
               if (wartPlantState.is(wartAirBlock)) {
                  return true;
               }
            }

            return false;
         }

         if (block instanceof CropBlock) {
            needsFarmland = true;
         }

         if (block instanceof SugarCaneBlock) {
            ClientLevel sugarCaneLevelSnapshot = ClientAccess.argusclientMinecraft.level;
            BlockPos canePlantPosition = position.above();
            BlockState canePlantState = sugarCaneLevelSnapshot.getBlockState(canePlantPosition);
            Block caneAirBlock = Blocks.AIR;
            if (!canePlantState.is(caneAirBlock)) {
               return false;
            }

            TagKey caneDirtTag = BlockTags.DIRT;
            if (!state.is(caneDirtTag)) {
               TagKey caneSandTag = BlockTags.SAND;
               if (!state.is(caneSandTag)) {
                  return false;
               }
            }

            for (Direction direction : Plane.HORIZONTAL) {
               sugarCaneLevelSnapshot = ClientAccess.argusclientMinecraft.level;
               BlockPos neighborFluidPosition = position.relative(direction);
               FluidState neighborFluid = sugarCaneLevelSnapshot.getFluidState(neighborFluidPosition);
               sugarCaneLevelSnapshot = ClientAccess.argusclientMinecraft.level;
               BlockPos neighborBlockPosition = position.relative(direction);
               state = sugarCaneLevelSnapshot.getBlockState(neighborBlockPosition);
               TagKey waterTag = FluidTags.WATER;
               if (neighborFluid.is(waterTag)) {
                  return true;
               }

               Block frostedIce = Blocks.FROSTED_ICE;
               if (state.is(frostedIce)) {
                  return true;
               }
            }
         }
      }

      if (!needsFarmland) {
         return false;
      }

      Block farmland = Blocks.FARMLAND;
      if (state.is(farmland)) {
         ClientLevel cropLevel = ClientAccess.argusclientMinecraft.level;
         BlockPos cropPlantPosition = position.above();
         BlockState cropPlantState = cropLevel.getBlockState(cropPlantPosition);
         Block cropAirBlock = Blocks.AIR;
         if (cropPlantState.is(cropAirBlock)) {
            return true;
         }
      }

      return false;
   }

   private boolean isReadyToHarvest(BlockState state, BlockPos position) {
      Block block = state.getBlock();
      if (block == Blocks.MOSS_BLOCK) {
         BlockPos abovePosition = position.above();
         return !WorldUtils.isAir(abovePosition);
      } else if (this.isTreePlant(block)) {
         return false;
      } else if (block instanceof CropBlock crop) {
         return crop.getAge(state) == crop.getMaxAge();
      } else if (block instanceof NetherWartBlock) {
         IntegerProperty wartAgeProperty = NetherWartBlock.AGE;
         return state.getValue(wartAgeProperty) == 3;
      } else if (block instanceof SugarCaneBlock) {
         ClientLevel level = ClientAccess.argusclientMinecraft.level;
         BlockPos belowPosition = position.below();
         BlockState belowState = level.getBlockState(belowPosition);
         Block sugarCaneBlock = Blocks.SUGAR_CANE;
         return belowState.is(sugarCaneBlock);
      } else if (block == Blocks.PUMPKIN || block == Blocks.MELON) {
         return true;
      } else if (block instanceof StemBlock) {
         IntegerProperty stemAgeProperty = StemBlock.AGE;
         return state.getValue(stemAgeProperty) == 7;
      } else {
         return block instanceof AttachedStemBlock;
      }
   }

   private void plantAt(BlockPos position, InventoryUtils.SwapSlot plantingItem) {
      InventoryUtils.swapTo(plantingItem);
      BlockHitResult hitResult = new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false);
      MultiPlayerGameMode gameMode = ClientAccess.argusclientMinecraft.gameMode;
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      InteractionHand hand = plantingItem.hand();
      gameMode.useItemOn(player, hand, hitResult);
      LocalPlayer swingPlayer = ClientAccess.argusclientMinecraft.player;
      InteractionHand swingHand = plantingItem.hand();
      swingPlayer.swing(swingHand, swingPlayer.getItemInHand(swingHand).getAttackAnimation(), false);
      this.actionsThisTick++;
      if (this.rotateSetting.getValue()) {
         RotationManager rotationManager = ClientContext.rotationManager;
         Vec3 targetCenter = Vec3.atCenterOf(position);
         float[] targetRotation = RotationManager.getRotationsToPosition(targetCenter);
         byte rotationPriority = 0;
         rotationManager.requestRotation(targetRotation, rotationPriority);
      }
   }

   private void fertilizeAt(BlockPos position, InteractionHand hand) {
      if (this.rotateSetting.getValue()) {
         RotationManager rotationManager = ClientContext.rotationManager;
         Vec3 targetBottomCenter = Vec3.atBottomCenterOf(position);
         float[] targetRotation = RotationManager.getRotationsToPosition(targetBottomCenter);
         byte rotationPriority = 0;
         rotationManager.requestRotation(targetRotation, rotationPriority);
      }

      MultiPlayerGameMode gameMode = ClientAccess.argusclientMinecraft.gameMode;
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      BlockHitResult hitResult = new BlockHitResult(Vec3.atBottomCenterOf(position), Direction.UP, position, false);
      gameMode.useItemOn(player, hand, hitResult);
      ClientAccess.argusclientMinecraft.player.swing(hand, player.getItemInHand(hand).getAttackAnimation(), false);
      this.actionsThisTick++;
   }

   @EventHandler
   private void updateFarm(RotationRequestEvent event) {
      this.plantingItem = null;
      this.runFarmTick();
   }

   private void runFarmTick() {
      int originalSlot = InventoryUtils.getSelectedSlot();

      for (BlockPos position : this.modeSetting.getValue().getTargets(this)) {
         int actionCount = this.actionsThisTick;
         Integer blocksPerTickValue = this.blocksPerTickSetting.getValue();
         if (actionCount >= blocksPerTickValue) {
            break;
         }

         Direction harvestFace = this.findHarvestFace(position);
         this.processFarmPosition(position, harvestFace);
      }

      this.actionsThisTick = 0;
      if (this.plantingItem != null && this.swapSetting.getValue() == AutoFarmModule.SwapChoice.SILENT) {
         InventoryUtils.SwapSlot selectedPlantingItem = this.plantingItem;
         InventoryUtils.swapBack(originalSlot, selectedPlantingItem);
      }
   }

   private void harvestAt(BlockPos position, Direction face, boolean readyToHarvest) {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      Block block = level.getBlockState(position).getBlock();
      if (!(block instanceof SaplingBlock)) {
         if (!(block instanceof BonemealableFeaturePlacerBlock)) {
            if (block != Blocks.PUMPKIN_STEM && block != Blocks.MELON_STEM) {
               Boolean removeValue = this.removeSetting.getValue();
               if (removeValue) {
                  ElapsedTimer timer = this.harvestTimer;
                  double delaySeconds = this.removeDelaySetting.getValue().floatValue();
                  TimeUnit delayUnit = TimeUnit.SECONDS;
                  if (timer.hasElapsed(delaySeconds, delayUnit)) {
                     if (readyToHarvest || !this.onlyGrownSetting.getValue()) {
                        MultiPlayerGameMode gameMode = ClientAccess.argusclientMinecraft.gameMode;
                        gameMode.continueDestroyBlock(position, face);
                        LocalPlayer player = ClientAccess.argusclientMinecraft.player;
                        InteractionHand hand = InteractionHand.MAIN_HAND;
                        player.swing(hand, player.getItemInHand(hand).getAttackAnimation(), false);
                        this.actionsThisTick++;
                        timer = this.harvestTimer;
                        timer.reset();
                        if (this.rotateSetting.getValue()) {
                           RotationManager rotationManager = ClientContext.rotationManager;
                           Vec3 targetCenter = Vec3.atCenterOf(position);
                           float[] targetRotation = RotationManager.getRotationsToPosition(targetCenter);
                           byte rotationPriority = 0;
                           rotationManager.requestRotation(targetRotation, rotationPriority);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public AutoFarmModule() {
      super("AutoFarm", "Farms your crops for you.", ModuleCategory.PLAYER);
      AutoFarmModule.ModeChoice defaultMode = AutoFarmModule.ModeChoice.CROSSHAIR;
      this.modeSetting = this.add(new EnumSetting<>("Mode", defaultMode));
      AutoFarmModule.SwapChoice defaultSwap = AutoFarmModule.SwapChoice.NORMAL;
      this.swapSetting = this.add(new EnumSetting<>("Swap", defaultSwap));
      Predicate rangeVisibility = ignoredValue -> {
         Setting<AutoFarmModule.ModeChoice> setting = this.modeSetting;
         return setting.getValue() == AutoFarmModule.ModeChoice.SPHERE;
      };
      this.rangeSetting = this.add(new NumberSetting<>("Range", 4.0F, 1.0F, 6.0F, rangeVisibility).withUnit("m"));
      this.blocksPerTickSetting = this.add(new NumberSetting<>("Frequency", 1, 1, 8));
      Boolean defaultPlace = false;
      this.placeSetting = this.add(new BooleanSetting("Place", defaultPlace));
      Boolean defaultRotate = false;
      this.rotateSetting = this.add(new BooleanSetting("Rotate", defaultRotate));
      Boolean defaultFertilize = false;
      this.fertilizeSetting = this.add(new BooleanSetting("Fertilize", defaultFertilize));
      Boolean defaultRemove = false;
      this.removeSetting = this.add(new BooleanSetting("Remove", defaultRemove).markVisibilityDependency());
      Predicate removeDelayVisibility = ignoredValue -> {
         Setting<Boolean> setting = this.removeSetting;
         return setting.isExpanded();
      };
      this.removeDelaySetting = this.add(new NumberSetting<>("RemoveDelay", 0.5F, 0.0F, 5.0F, removeDelayVisibility).withUnit("s"));
      Predicate onlyGrownVisibility = ignoredValue -> {
         Setting<Boolean> setting = this.removeSetting;
         return setting.isExpanded();
      };
      Boolean defaultOnlyGrown = true;
      this.onlyGrownSetting = this.add(new BooleanSetting("OnlyGrown", defaultOnlyGrown, onlyGrownVisibility));
      Boolean defaultCrops = false;
      Setting<Boolean> registeredCrops = this.add(new BooleanSetting("Crops", defaultCrops).markAsGroup().markVisibilityDependency());
      this.cropsSetting = registeredCrops;
      Predicate wheatVisibility = ignoredValue -> {
         Setting<Boolean> setting = this.cropsSetting;
         return setting.isExpanded();
      };
      Boolean defaultWheat = true;
      this.wheatSetting = this.add(new BooleanSetting("Wheat", defaultWheat, wheatVisibility));
      Predicate carrotsVisibility = ignoredValue -> {
         Setting<Boolean> setting = this.cropsSetting;
         return setting.isExpanded();
      };
      Boolean defaultCarrots = true;
      this.carrotsSetting = this.add(new BooleanSetting("Carrots", defaultCarrots, carrotsVisibility));
      Predicate potatoesVisibility = ignoredValue -> {
         Setting<Boolean> setting = this.cropsSetting;
         return setting.isExpanded();
      };
      Boolean defaultPotatoes = true;
      this.potatoesSetting = this.add(new BooleanSetting("Potatoes", defaultPotatoes, potatoesVisibility));
      Predicate beetRootsVisibility = ignoredValue -> {
         Setting<Boolean> setting = this.cropsSetting;
         return setting.isExpanded();
      };
      Boolean defaultBeetRoots = false;
      this.beetRootsSetting = this.add(new BooleanSetting("BeetRoots", defaultBeetRoots, beetRootsVisibility));
      Boolean defaultMelons = false;
      this.melonsSetting = this.add(new BooleanSetting("Melons", defaultMelons).dependsOn(registeredCrops));
      Boolean defaultPumpkins = false;
      this.pumpkinsSetting = this.add(new BooleanSetting("Pumpkins", defaultPumpkins).dependsOn(registeredCrops));
      Boolean defaultSugarCane = false;
      this.sugarCaneSetting = this.add(new BooleanSetting("SugarCane", defaultSugarCane).dependsOn(registeredCrops));
      Boolean defaultNetherWart = false;
      this.netherWartSetting = this.add(new BooleanSetting("NetherWart", defaultNetherWart).dependsOn(registeredCrops));
      Boolean defaultMushrooms = false;
      this.mushroomsSetting = this.add(new BooleanSetting("Mushrooms", defaultMushrooms).dependsOn(registeredCrops));
      Boolean defaultTrees = false;
      this.treesSetting = this.add(new BooleanSetting("Trees", defaultTrees).dependsOn(registeredCrops));
      Boolean defaultMoss = false;
      this.mossSetting = this.add(new BooleanSetting("Moss", defaultMoss).dependsOn(registeredCrops));
      this.harvestTimer = new ElapsedTimer();
   }

   enum ModeChoice implements NamedEntry {
      CROSSHAIR("Crosshair") {
         @Override
         public List<BlockPos> getTargets(AutoFarmModule module) {
            HitResult hitResult = ClientAccess.argusclientMinecraft.hitResult;
            Vec3 hitLocation = hitResult.getLocation();
            BlockPos targetPosition = BlockPos.containing(hitLocation);
            return Collections.singletonList(targetPosition);
         }
      },
      SPHERE("Sphere") {
         @Override
         public List<BlockPos> getTargets(AutoFarmModule module) {
            LocalPlayer player = ClientAccess.argusclientMinecraft.player;
            Vec3 playerPosition = player.position();
            float rangeSnapshot = module.rangeSetting.getValue();
            return WorldUtils.collectSphereBlocks(playerPosition, rangeSnapshot, true);
         }
      };

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      ModeChoice(String displayName) {
         this.displayName = displayName;
      }

      public abstract List<BlockPos> getTargets(AutoFarmModule var1);
   }

   public enum SwapChoice implements NamedEntry {
      NONE("None"),
      NORMAL("Normal"),
      SILENT("Silent");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      SwapChoice(String displayName) {
         this.displayName = displayName;
      }
   }
}
