package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.BlockUpdateAppliedEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.PlacementModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.RegistrySetSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.SettingLabelCondition;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.world.BlockStateUtils;
import dev.argus.client.utils.world.HoleInfo;
import dev.argus.mixin.BlockBehaviourAccessor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class HoleFillModule extends PlacementModule {
   protected final Setting<Set<Block>> blocksSetting = this.add(
      new RegistrySetSetting<>("Blocks", BuiltInRegistries.BLOCK, Blocks.OBSIDIAN, Blocks.ENDER_CHEST)
   );
   private final Setting<Boolean> sequentialSetting = this.add(new BooleanSetting("Sequential", false));
   final Setting<HoleFillModule.ModeChoice> modeSetting = this.add(new EnumSetting<>("Mode", HoleFillModule.ModeChoice.PLAIN));
   protected final Setting<Float> rangeSetting = this.add(new NumberSetting<>("Range", 4.0F, 1.0F, 6.5F).withUnit("m"));
   protected final Setting<Float> verticalRangeSetting = this.add(new NumberSetting<>("VerticalRange", 3.0F, 1.0F, 6.0F).withUnit("m"));
   protected final Setting<Boolean> doublesSetting = this.add(new BooleanSetting("Doubles", false));
   protected final Setting<Boolean> onlySafeSetting = this.add(new BooleanSetting("OnlySafe", false));
   protected final Setting<Boolean> selfSetting = this.add(new BooleanSetting("Self", true));
   protected final Setting<Boolean> extrapolationSetting = this.add(new BooleanSetting("Extrapolation", true).markVisibilityDependency());
   public final Setting<Integer> ticksSetting;
   public final Setting<Boolean> smartSetting;
   protected final Setting<Float> smartRangeSetting;
   protected final Setting<Boolean> selfSafetySetting;
   protected final Setting<Boolean> ignoreNakedsSetting;
   protected final PlainHoleFillPolicy plainPolicy;
   protected final PlainHoleFillPolicy smartPolicy;
   protected int candidateCount;

   private Block getStackBlock(ItemStack stack) {
      return stack.getItem() instanceof BlockItem blockItem ? blockItem.getBlock() : Blocks.VOID_AIR;
   }

   @Override
   protected int getPlacementRotationPriority() {
      return 150;
   }

   @Override
   protected InventoryUtils.SwapSlot findPlacementSwapSlot() {
      if (this.blocksSetting.getValue().isEmpty()) {
         return super.findPlacementSwapSlot();
      }

      Predicate<ItemStack> blockPredicate = stack -> this.blocksSetting.getValue().contains(this.getStackBlock(stack));
      return InventoryUtils.findSwapSlot(blockPredicate, false);
   }

   @Override
   public String getInfo() {
      return String.valueOf(this.candidateCount);
   }

   @Override
   protected void runPlacementCycle() {
      if (ClientContext.holeManager.isInHole() || !this.onlySafeSetting.getValue()) {
         super.runPlacementCycle();
      }
   }

   @EventHandler
   private void onSequentialPlacement(BlockUpdateAppliedEvent event) {
      if (this.sequentialSetting.getValue()) {
         this.runPlacementCycle();
      }
   }

   @Override
   public List<BlockPos> getPlacementPositions() {
      ArrayList<BlockPos> positions = new ArrayList<>();
      Iterator<HoleInfo> holes = ClientContext.holeManager.getHoles().iterator();

      while (true) {
         HoleInfo hole;
         while (true) {
            if (!holes.hasNext()) {
               this.candidateCount = positions.size();
               positions.sort(Comparator.comparing(sortPos -> {
                  Vec3 eyeSnapshot = ClientAccess.argusclientMinecraft.player.getEyePosition();
                  Vec3 blockCenter = sortPos.getCenter();
                  double distance = eyeSnapshot.distanceTo(blockCenter);
                  return distance;
               }));
               return positions;
            }

            hole = holes.next();
            if (!hole.trapped() && this.modeSetting.getValue().getPolicy(this).shouldFill(hole)) {
               if (this.selfSetting.getValue() && this.selfSetting.isVisible()) {
                  break;
               }

               BlockPos localCheckPosition = hole.pos();
               if (!localCheckPosition.equals(PlayerStateUtils.getLocalRoundedBlockPosition())) {
                  break;
               }
            }
         }

         boolean useAbovePosition = true;
         BlockPos holeOrigin = hole.pos();
         BlockPos aboveHole = holeOrigin.above();

         for (Direction direction : Direction.values()) {
            Axis axis = direction.getAxis();
            if (!axis.isVertical()) {
               ClientLevel sideLevelSnapshot = ClientAccess.argusclientMinecraft.level;
               BlockPos sidePos = aboveHole.relative(direction);
               BlockState sideState = sideLevelSnapshot.getBlockState(sidePos);
               boolean resistantSide = BlockStateUtils.isBlastResistantBreakable(sideState);
               if (!resistantSide) {
                  ClientLevel secondSideLevelSnapshot = ClientAccess.argusclientMinecraft.level;
                  BlockPos secondSidePos = aboveHole.relative(direction);
                  BlockState sideStateSnapshot = secondSideLevelSnapshot.getBlockState(secondSidePos);
                  resistantSide = BlockStateUtils.isBlastResistantUnbreakable(sideStateSnapshot);
               }

               BlockState aboveStateSnapshot = ClientAccess.argusclientMinecraft.level.getBlockState(aboveHole);
               if (BlockStateUtils.isBlocking(aboveStateSnapshot) || !resistantSide) {
                  useAbovePosition = false;
                  break;
               }
            }
         }

         if (useAbovePosition) {
            positions.add(aboveHole);
         } else {
            positions.add(hole.pos());
         }
      }
   }

   public HoleFillModule() {
      super("HoleFill", "Blocks safe holes nearby with obsidian.", ModuleCategory.COMBAT);
      Predicate<Object> ticksVisibility = unused -> this.extrapolationSetting.isExpanded();
      this.ticksSetting = this.add(new NumberSetting<>("Ticks", 4, 0, 8, ticksVisibility).setBoundaryLabel("Auto", SettingLabelCondition.MIN));
      Predicate<Object> smartVisibility = unused -> this.modeSetting.getValue() == HoleFillModule.ModeChoice.SMART;
      this.smartSetting = this.add(new BooleanSetting("Smart", false, smartVisibility).markAsGroup());
      this.smartRangeSetting = this.add(new NumberSetting<>("SmartRange", 1.5F, 0.5F, 4.0F).dependsOn(this.smartSetting));
      this.selfSafetySetting = this.add(new BooleanSetting("SelfSafety", false).dependsOn(this.smartSetting));
      this.ignoreNakedsSetting = this.add(new BooleanSetting("IgnoreNakeds", false).dependsOn(this.smartSetting));
      this.plainPolicy = new PlainHoleFillPolicy(this);
      this.smartPolicy = new SmartHoleFillPolicy(this);
      this.unregister(this.placementAirPlaceSetting);
      this.unregister(this.placementChorusSetting);
      this.unregister(this.placementAttackSetting);
      this.placementAttackSetting.setValue(false);
      this.placementJumpSetting.setDefaultValue(false);
      this.selfSetting.addVisibilityPredicate(unused -> {
         for (Block selectedBlock : this.blocksSetting.getValue()) {
            if (!((BlockBehaviourAccessor)selectedBlock).isCollidable()) {
               return true;
            }
         }

         return false;
      });
   }

   enum ModeChoice implements NamedEntry {
      PLAIN("Plain"),
      SMART("Smart");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      ModeChoice(String displayName) {
         this.displayName = displayName;
      }

      public PlainHoleFillPolicy getPolicy(HoleFillModule holeFillModule) {
         return this == PLAIN ? holeFillModule.plainPolicy : holeFillModule.smartPolicy;
      }
   }
}
