package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.PlacementModule;
import dev.argus.client.modules.player.SpeedMineModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.BlockerModeChoice;
import dev.argus.client.settings.options.BlockerTargetOffsets;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.world.BlockBreakTracker;
import dev.argus.client.utils.world.BlockStateUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.protocol.Packet;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class BlockerModule extends PlacementModule {
   private static SpeedMineModule speedMineModule = ClientContext.getModule(SpeedMineModule.class);
   private final Setting<BlockerModeChoice> modeSetting = this.add(new EnumSetting<>("Mode", BlockerModeChoice.DAMAGE));
   private final Setting<Float> blockDamageSetting = this.add(new NumberSetting<>("BlockDamage", 0.8F, 0.1F, 1.0F));
   private final Setting<Boolean> targetsSetting = this.add(new BooleanSetting("Targets", false).markAsGroup());
   private final Setting<Boolean> fishSetting = this.add(new BooleanSetting("Fish", false).dependsOn(this.targetsSetting));
   private final Setting<Boolean> surroundSetting = this.add(new BooleanSetting("Surround", true).markVisibilityDependency().dependsOn(this.targetsSetting));
   private final Setting<Boolean> cornersSetting = this.add(new BooleanSetting("Corners", false).dependsOn(this.targetsSetting, this.surroundSetting));
   private final Setting<Boolean> faceSetting = this.add(new BooleanSetting("Face", true).dependsOn(this.targetsSetting, this.surroundSetting));
   private final Setting<Boolean> cevSetting = this.add(new BooleanSetting("Cev", true).markVisibilityDependency().dependsOn(this.targetsSetting));
   private final Setting<Boolean> extendedSetting = this.add(new BooleanSetting("Extended", false).dependsOn(this.targetsSetting, this.cevSetting));
   private final Map<BlockPos, Long> destroyedPositionExpiries = new HashMap<>();

   @Override
   public String getInfo() {
      return this.modeSetting.getValue() == BlockerModeChoice.DAMAGE
         ? "%.1f".formatted(this.blockDamageSetting.getValue())
         : this.modeSetting.getValue().getName();
   }

   private boolean shouldProtectBelow() {
      if (!this.fishSetting.getValue()) {
         return false;
      }

      AABB playerBounds = ClientAccess.argusclientMinecraft.player.getBoundingBox();
      double playerY = ClientAccess.argusclientMinecraft.player.getY();
      AABB feetBounds = playerBounds.setMaxY(playerY);
      double minX = feetBounds.minX;
      int minBlockX = Mth.floor(minX);
      double maxX = feetBounds.maxX;
      int maxBlockX = Mth.floor(maxX);
      double minZ = feetBounds.minZ;
      int minBlockZ = Mth.floor(minZ);
      double maxZ = feetBounds.maxZ;
      int maxBlockZ = Mth.floor(maxZ);
      int occupiedColumns = (maxBlockX - minBlockX + 1) * (maxBlockZ - minBlockZ + 1);
      return occupiedColumns == 1;
   }

   private List<BlockPos> getTargetOffsets() {
      ArrayList<BlockPos> offsets = new ArrayList<>();
      Collection<BlockPos> surroundPositions = PlayerStateUtils.getSurroundPositions(ClientAccess.argusclientMinecraft.player);
      if (this.surroundSetting.getValue()) {
         offsets.addAll(surroundPositions);
      }

      if (this.cevSetting.getValue()) {
         for (BlockPos position : PlayerStateUtils.getOccupiedBlockPositions(ClientAccess.argusclientMinecraft.player)) {
            offsets.add(position.above(2));
            offsets.add(position.above(3));
         }

         if (this.extendedSetting.getValue()) {
            for (BlockPos position : surroundPositions) {
               offsets.add(position.above());
            }
         }
      }

      return offsets;
   }

   @EventHandler
   private void onLocalPlayerPreTick(LocalPlayerPreTickEvent event) {
      synchronized (this.destroyedPositionExpiries) {
         Collection<Long> expiries = this.destroyedPositionExpiries.values();
         Predicate<Long> hasExpired = expiry -> System.currentTimeMillis() > expiry;
         expiries.removeIf(hasExpired);
      }
   }

   @Override
   public List<BlockPos> getPlacementPositions() {
      BlockPos playerBlockPosition = PlayerStateUtils.getLocalRoundedBlockPosition();
      ArrayList<BlockPos> triggeredTargets = new ArrayList<>();

      for (BlockPos targetPosition : this.getTargetOffsets()) {
         if (this.isTargetTriggered(targetPosition)) {
            triggeredTargets.add(targetPosition);
         }
      }

      ArrayList<BlockPos> placements = new ArrayList<>();
      if (this.shouldProtectBelow()) {
         BlockPos belowPlayerPosition = playerBlockPosition.below();
         if (this.isTargetTriggered(belowPlayerPosition)) {
            for (BlockPos belowSurroundPosition : PlayerStateUtils.getSurroundPositions(ClientAccess.argusclientMinecraft.player)) {
               BlockPos belowSidePosition = belowSurroundPosition.below();
               placements.add(belowSidePosition);
            }
         }
      }

      for (BlockPos triggeredTarget : triggeredTargets) {
         BlockerTargetOffsets surroundPattern = BlockerTargetOffsets.SURROUND;

         for (Vec3i surroundOffset : surroundPattern.getOffsets()) {
            int positionWord = triggeredTarget.getY();
            Vec3 playerPositionForY = ClientAccess.argusclientMinecraft.player.position();
            if (positionWord != (int)playerPositionForY.y()) {
               break;
            }

            BlockPos localBlockPosition = BlockPos.containing(ClientAccess.argusclientMinecraft.player.position());
            if (this.cornersSetting.getValue()) {
               if (!triggeredTarget.offset(surroundOffset).equals(localBlockPosition)) {
                  placements.add(triggeredTarget.offset(surroundOffset));
               }
            } else if (triggeredTarget.subtract(localBlockPosition).equals(surroundOffset)) {
               placements.add(triggeredTarget.offset(surroundOffset));
               break;
            }
         }

         double targetY = triggeredTarget.getY();
         double playerY = ClientAccess.argusclientMinecraft.player.getY();
         if (targetY == playerY + 3.0) {
            placements.add(triggeredTarget.below());
         } else if (this.faceSetting.getValue()) {
            placements.add(triggeredTarget.above());
         }
      }

      return placements;
   }

   private boolean isTargetTriggered(BlockPos position) {
      switch ((BlockerModeChoice)this.modeSetting.getValue()) {
         case DAMAGE:
            BlockBreakTracker breakManagerSnapshot = ClientContext.blockBreakTracker;
            float blockDamage = breakManagerSnapshot.getBreakProgress(position);
            Float minimumDamage = this.blockDamageSetting.getValue();
            return blockDamage >= minimumDamage;
         case INSTANT:
            return !BlockStateUtils.isBlastResistantUnbreakable(ClientAccess.argusclientMinecraft.level.getBlockState(position));
         case DESTROY:
            synchronized (this.destroyedPositionExpiries) {
               return this.destroyedPositionExpiries.containsKey(position);
            }
         default:
            throw new MatchException(null, null);
      }
   }

   @EventHandler
   private void onInboundBlockUpdates(PacketEvent.Receive event) {
      Packet packet = event.getPacket();
      if (BlockUpdateBatch.isSupportedPacket(packet)) {
         for (BlockUpdateBatch.Update update : new BlockUpdateBatch(event.getPacket())) {
            BlockPos miningPosition = speedMineModule.getMiningPos();
            BlockState updatedStateSnapshot = update.state();
            TagKey airTag = BlockTags.AIR;
            if (!updatedStateSnapshot.is(airTag)) {
               return;
            }

            if (miningPosition != null && miningPosition.equals(update.position())) {
               return;
            }

            synchronized (this.destroyedPositionExpiries) {
               Map<BlockPos, Long> expiryMap = this.destroyedPositionExpiries;
               BlockPos updatedPosition = update.position();
               long now = System.currentTimeMillis();
               long expiry = now + 150L;
               expiryMap.put(updatedPosition, expiry);
            }
         }
      }
   }

   public static void bindReferencedModules(ModuleRegistry registry) {
      speedMineModule = (SpeedMineModule)registry.getModule(SpeedMineModule.class);
   }

   public BlockerModule() {
      super("Blocker", "Blocks certain offsets to prevent crystal damage.", ModuleCategory.COMBAT);
      this.placementJumpSetting.setDefaultValue(false);
      this.blockDamageSetting.addVisibilityPredicate(unused -> this.modeSetting.getValue() == BlockerModeChoice.DAMAGE);
      this.ignoreMissingPlacementBlocks();
   }
}
