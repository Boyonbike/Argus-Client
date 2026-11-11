package dev.argus.client.utils.world;

import com.google.common.collect.AbstractIterator;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.modules.combat.AutoCrystalModule;
import dev.argus.client.settings.options.BlockSampleMode;
import dev.argus.client.settings.options.DimensionType;
import dev.argus.client.utils.combat.BlockRaycastContext;
import dev.argus.client.utils.combat.BoundingBoxUtils;
import dev.argus.client.utils.combat.RaycastUtils;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.inventory.EnchantmentUtils;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class WorldUtils implements ClientAccess {
   private static final AutoCrystalModule autoCrystal = ClientContext.getModule(AutoCrystalModule.class);
   public static final List BASTION_FLOOR_BLOCKS = List.of(Blocks.BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.GILDED_BLACKSTONE);
   public static final List ANCIENT_CITY_FLOOR_BLOCKS = List.of(
      Blocks.DEEPSLATE_BRICKS,
      Blocks.CRACKED_DEEPSLATE_BRICKS,
      Blocks.DEEPSLATE_TILES,
      Blocks.CRACKED_DEEPSLATE_TILES,
      Blocks.DEEPSLATE,
      Blocks.CHISELED_DEEPSLATE,
      Blocks.SCULK
   );
   public static final List TRIAL_CHAMBER_FLOOR_BLOCKS = List.of(
      Blocks.WAXED_OXIDIZED_COPPER,
      Blocks.WAXED_OXIDIZED_CUT_COPPER,
      Blocks.WAXED_COPPER_BLOCK,
      Blocks.WAXED_OXIDIZED_COPPER_GRATE,
      Blocks.CHISELED_TUFF_BRICKS
   );

   public static float getTopY() {
      int minimumY = ClientAccess.argusclientMinecraft.level.getMinY();
      ClientLevel heightLevel = ClientAccess.argusclientMinecraft.level;
      return minimumY + heightLevel.getHeight();
   }

   public static List<LevelChunk> getLoadedChunks() {
      int distance = ClientAccess.argusclientMinecraft.options.renderDistance().get();
      List<LevelChunk> chunks = new ArrayList<>((distance * 2 + 1) * (distance * 2 + 1));
      // Floor to the chunk grid once; integer division rounded the wrong way at negative coordinates.
      int centerX = Mth.floor(ClientAccess.argusclientMinecraft.player.getX()) >> 4;
      int centerZ = Mth.floor(ClientAccess.argusclientMinecraft.player.getZ()) >> 4;
      net.minecraft.client.multiplayer.ClientChunkCache chunkSource = ClientAccess.argusclientMinecraft.level.getChunkSource();

      for (int x = -distance; x <= distance; x++) {
         for (int z = -distance; z <= distance; z++) {
            LevelChunk chunk = chunkSource.getChunkNow(centerX + x, centerZ + z);
            if (chunk != null) {
               chunks.add(chunk);
            }
         }
      }

      return chunks;
   }

   public static DimensionType getDimension() {
      if (ClientAccess.argusclientMinecraft.level == null) {
         return DimensionType.OVERWORLD;
      }

      Identifier dimensionId = ClientAccess.argusclientMinecraft.level.dimension().identifier();

      return switch (dimensionId.getPath()) {
         case "the_nether" -> DimensionType.THE_NETHER;
         case "the_end" -> DimensionType.THE_END;
         default -> DimensionType.OVERWORLD;
      };
   }

   public static List<BlockEntity> getLoadedBlockEntities() {
      ArrayList<BlockEntity> blockEntities = new ArrayList<>();

      for (LevelChunk chunk : getLoadedChunks()) {
         if (!chunk.getBlockEntities().isEmpty()) {
            try {
               blockEntities.addAll(chunk.getBlockEntities().values());
            } catch (Throwable failure) {
               autoCrystal.completeCalculation();
               break;
            }
         }
      }

      return blockEntities;
   }

   public static boolean hasFluid(BlockPos position) {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      FluidState fluidState = level.getFluidState(position);
      return !fluidState.isEmpty();
   }

   public static boolean hasCollision(BlockPos position) {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      BlockState collisionState = level.getBlockState(position);
      ClientLevel collisionLevel = ClientAccess.argusclientMinecraft.level;
      VoxelShape collisionShape = collisionState.getCollisionShape(collisionLevel, position);
      return !collisionShape.isEmpty();
   }

   public static boolean isReplaceable(BlockPos position) {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      return level.getBlockState(position).canBeReplaced();
   }

   private WorldUtils() {
   }

   public static float getHardness(BlockPos position) {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      BlockState hardnessState = level.getBlockState(position);
      ClientLevel hardnessLevel = ClientAccess.argusclientMinecraft.level;
      return hardnessState.getDestroySpeed(hardnessLevel, position);
   }

   public static boolean canSeeFromEyes(Vec3 target) {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      Vec3 eyePosition = player.getEyePosition();
      return canSee(eyePosition, target);
   }

   public static void reloadRenderer() {
      if (ClientAccess.argusclientMinecraft.level != null) {
         ClientAccess.argusclientMinecraft.levelRenderer.allChanged();
      }
   }

   public static boolean isPlacementOccupied(BlockPos position) {
      return ClientContext.worldStateManager.isPlacementOccupied(position);
   }

   public static Block getBlock(BlockPos position) {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      return level.getBlockState(position).getBlock();
   }

   public static double getBreakProgress(ItemStack stack, BlockState state, boolean onGround) {
      float hardness = state.getDestroySpeed(null, null);
      if (hardness == -1.0F) {
         return 0.0;
      }

      double damagePerHardness = getMiningSpeed(stack, state, onGround) / hardness;
      return state.requiresCorrectToolForDrops() && !stack.isCorrectToolForDrops(state) ? damagePerHardness / 100.0 : damagePerHardness / 30.0;
   }

   public static boolean isInteractive(HitResult hit) {
      if (hit == null) {
         return false;
      }

      if (hit instanceof BlockHitResult blockHit) {
         BlockPos position = blockHit.getBlockPos();
         ClientLevel level = ClientAccess.argusclientMinecraft.level;
         BlockState state = level.getBlockState(position);
         if (state.hasBlockEntity()) {
            return true;
         }

         Block block = state.getBlock();
         if (BlockInteractionUtils.isInteractiveBlock(block)) {
            return true;
         }
      }

      if (!(hit instanceof EntityHitResult matchedEntityHit)) {
         return false;
      } else {
         Entity entity = matchedEntityHit.getEntity();
         return entity instanceof VehicleEntity || entity instanceof Villager;
      }
   }

   public static boolean canSeeAny(Vec3 origin, Iterable<Vec3> points) {
      for (Vec3 point : points) {
         if (canSee(origin, point)) {
            return true;
         }
      }

      return false;
   }

   public static double distanceToBounds(Vec3 point, AABB bounds) {
      Vec3 nearestPoint = BoundingBoxUtils.clampToBounds(point, bounds);
      return point.distanceTo(nearestPoint);
   }

   public static boolean isStructureChest(
      ChunkAccess chunk, BlockPos position, boolean fortress, boolean bastion, boolean ancientCity, boolean dungeon, boolean trialChambers
   ) {
      BlockState chestState = chunk.getBlockState(position);
      Block chestBlock = Blocks.CHEST;
      if (!chestState.is(chestBlock)) {
         return false;
      }

      BlockPos belowPosition = position.below();
      BlockState floorState = chunk.getBlockState(belowPosition);
      EnumProperty chestTypeProperty = ChestBlock.TYPE;
      boolean singleChest = chestState.getValue(chestTypeProperty) == ChestType.SINGLE;
      if (getDimension() == DimensionType.THE_NETHER) {
         if (fortress && singleChest) {
            Block fortressFloorBlock = Blocks.NETHER_BRICKS;
            if (floorState.is(fortressFloorBlock)) {
               return true;
            }
         }

         return bastion && BASTION_FLOOR_BLOCKS.contains(floorState.getBlock());
      } else if (getDimension() == DimensionType.OVERWORLD) {
         if (!singleChest) {
            return false;
         }

         if (trialChambers && isTrialChamberChest(chunk, position, floorState)) {
            return true;
         }

         if (ancientCity && isAncientCityChest(position, floorState)) {
            return true;
         }

         if (dungeon) {
            Block mossyFloorBlock = Blocks.MOSSY_COBBLESTONE;
            if (floorState.is(mossyFloorBlock)) {
               return true;
            }

            Block cobbleFloorBlock = Blocks.COBBLESTONE;
            if (floorState.is(cobbleFloorBlock)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static boolean isTrialChamberChest(ChunkAccess chunk, BlockPos position, BlockState floorState) {
      if (position.getY() > 34) {
         return false;
      }

      if (!TRIAL_CHAMBER_FLOOR_BLOCKS.contains(floorState.getBlock())) {
         return false;
      }

      BlockPos polishedTuffAbovePosition = position.above(3);
      BlockState polishedTuffState = chunk.getBlockState(polishedTuffAbovePosition);
      Block polishedTuffBlock = Blocks.POLISHED_TUFF;
      boolean polishedTuffAbove = polishedTuffState.is(polishedTuffBlock);
      boolean clearAbove = true;

      for (int heightOffset = 1; heightOffset <= 2; heightOffset++) {
         BlockPos abovePosition = position.above(heightOffset);
         BlockState clearanceState = chunk.getBlockState(abovePosition);
         if (!(clearanceState.getBlock() instanceof AirBlock)) {
            clearAbove = false;
         }
      }

      return clearAbove && polishedTuffAbove;
   }

   private static boolean isAncientCityChest(BlockPos position, BlockState floorState) {
      if (position.getY() > -32) {
         return false;
      } else {
         return position.getY() < -51 ? false : ANCIENT_CITY_FLOOR_BLOCKS.contains(floorState.getBlock());
      }
   }

   public static boolean canSee(Vec3 origin, Vec3 target, BlockPos targetBlock, Direction targetFace) {
      if (target.distanceTo(origin) > 128.0) {
         return false;
      } else {
         BlockRaycastContext context = new BlockRaycastContext.Builder(origin, target).build();
         BlockHitResult hit = RaycastUtils.raycast(context);
         if (hit.getType() == Type.MISS) {
            return true;
         } else if (hit.getLocation().equals(target)) {
            return true;
         } else {
            return !hit.getBlockPos().equals(targetBlock) ? false : hit.getDirection() == targetFace || targetFace == null;
         }
      }
   }

   public static boolean canSee(Vec3 origin, Vec3 target) {
      BlockPos capturedTargetBlock = BlockPos.containing(target);
      return canSee(origin, target, capturedTargetBlock, null);
   }

   public static boolean canSeeBlock(Vec3i position) {
      Vec3 capturedCenter = Vec3.atCenterOf(position);
      return canSeeFromEyes(capturedCenter);
   }

   public static boolean canSeeAny(Iterable<Vec3> points) {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      Vec3 eyePosition = player.getEyePosition();
      return canSeeAny(eyePosition, points);
   }

   public static Vec3 findVisiblePoint(BlockPos position, BlockSampleMode samplingMode) {
      for (Vec3 point : samplingMode.sampleBlock(position)) {
         if (canSeeFromEyes(point)) {
            return point;
         }
      }

      return null;
   }

   public static Stream<BlockPos> streamSphereBlocks(Vec3 center, float radius) {
      Iterator<BlockPos> positions = new WorldUtils.SphereBlockIterator(center, radius);
      Spliterator<BlockPos> spliterator = Spliterators.spliteratorUnknownSize(positions, 0);
      return StreamSupport.stream(spliterator, false);
   }

   public static List<BlockPos> collectSphereBlocks(Vec3 center, float radius, boolean excludeAir) {
      return streamSphereBlocks(center, radius).filter(position -> !excludeAir || !isAir(position)).toList();
   }

   public static boolean isTargetable(Entity entity) {
      if (entity == ClientAccess.argusclientMinecraft.player) {
         return false;
      } else {
         return entity instanceof Player player && ClientContext.socialRegistry.isFriendPlayer(player) ? false : entity.isAlive();
      }
   }

   private static double getMiningSpeed(ItemStack stack, BlockState state, boolean onGround) {
      double speed = stack.getDestroySpeed(state);
      if (speed > 1.0) {
         ResourceKey efficiencyEnchantment = Enchantments.EFFICIENCY;
         int efficiency = EnchantmentUtils.getLevel(efficiencyEnchantment, stack);
         if (efficiency > 0 && !stack.isEmpty()) {
            speed += efficiency * efficiency + 1;
         }
      }

      LocalPlayer hastePlayer = ClientAccess.argusclientMinecraft.player;
      if (MobEffectUtil.hasDigSpeed(hastePlayer)) {
         float hasteBase = 1.0F;
         LocalPlayer hasteAmplifierPlayer = ClientAccess.argusclientMinecraft.player;
         float hasteAmplifier = MobEffectUtil.getDigSpeedAmplification(hasteAmplifierPlayer) + 1;
         speed *= hasteBase + hasteAmplifier * 0.2F;
      }

      LocalPlayer fatiguePlayer = ClientAccess.argusclientMinecraft.player;
      Holder miningFatigue = MobEffects.MINING_FATIGUE;
      if (fatiguePlayer.hasEffect(miningFatigue)) {
         LocalPlayer fatigueInstancePlayer = ClientAccess.argusclientMinecraft.player;
         Holder fatigueEffect = MobEffects.MINING_FATIGUE;
         MobEffectInstance fatigue = fatigueInstancePlayer.getEffect(fatigueEffect);

         speed *= switch (fatigue.getAmplifier()) {
            case 0 -> 0.3F;
            case 1 -> 0.09F;
            case 2 -> 0.0027F;
            default -> 8.1E-4F;
         };
      }

      LocalPlayer fluidPlayer = ClientAccess.argusclientMinecraft.player;
      TagKey waterTag = FluidTags.WATER;
      if (fluidPlayer.isEyeInFluid(waterTag)) {
         ResourceKey aquaAffinity = Enchantments.AQUA_AFFINITY;
         EquipmentSlot headSlot = EquipmentSlot.HEAD;
         if (!EnchantmentUtils.hasEnchantment(aquaAffinity, headSlot)) {
            speed /= 5.0;
         }
      }

      if (!onGround) {
         speed /= 5.0;
      }

      return speed;
   }

   public static double getEntityDistance(Entity entity, boolean sampleBody) {
      WorldUtils.EntityRange range = getEntityRange(entity, sampleBody);
      return range.distance();
   }

   public static boolean isBedrockOrObsidian(BlockPos position) {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      return level.getBlockState(position).getBlock() == Blocks.BEDROCK
         || ClientAccess.argusclientMinecraft.level.getBlockState(position).getBlock() == Blocks.OBSIDIAN;
   }

   public static boolean isAir(BlockPos position) {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      return level.getBlockState(position).isAir();
   }

   public static WorldUtils.EntityRange getEntityRange(Entity entity, boolean sampleBody) {
      float[] selectedHeights;
      if (sampleBody) {
         selectedHeights = PlayerStateUtils.getBodySampleHeights();
      } else {
         selectedHeights = new float[1];
         LocalPlayer eyeHeightPlayer = ClientAccess.argusclientMinecraft.player;
         selectedHeights[0] = eyeHeightPlayer.getEyeHeight();
      }

      float[] sampleHeights = selectedHeights;
      float selectedScale;
      if (sampleBody) {
         LocalPlayer scalePlayer = ClientAccess.argusclientMinecraft.player;
         Holder scaleAttribute = Attributes.SCALE;
         selectedScale = (float)scalePlayer.getAttributeValue(scaleAttribute);
      } else {
         selectedScale = 1.0F;
      }

      LocalPlayer capturedBoundsPlayer = ClientAccess.argusclientMinecraft.player;
      Vec3 basePosition = BoundingBoxUtils.getInterpolationBounds(capturedBoundsPlayer).getBottomCenter();
      AABB entityBounds = entity.getBoundingBox();
      double minimumDistance = Double.MAX_VALUE;
      Vec3 closestOrigin = null;

      for (float sampleHeight : sampleHeights) {
         double scaledSampleHeight = sampleHeight * selectedScale;
         Vec3 origin = basePosition.add(0.0, scaledSampleHeight, 0.0);
         if (entity instanceof EnderDragon dragon) {
            for (EnderDragonPart part : dragon.getSubEntities()) {
               AABB partBounds = part.getBoundingBox();
               double partDistance = distanceToBounds(origin, partBounds);
               if (partDistance < minimumDistance) {
                  minimumDistance = partDistance;
                  closestOrigin = origin;
               }
            }
         } else {
            double boundsDistance = distanceToBounds(origin, entityBounds);
            if (boundsDistance < minimumDistance) {
               minimumDistance = boundsDistance;
               closestOrigin = origin;
            }
         }
      }

      return new WorldUtils.EntityRange(minimumDistance, closestOrigin);
   }

   public static boolean canMineBlock(BlockPos position) {
      if (position == null) {
         return false;
      }

      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      BlockState state = level.getBlockState(position);
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      if (!player.isCreative()) {
         ClientLevel hardnessLevel = ClientAccess.argusclientMinecraft.level;
         if (state.getDestroySpeed(hardnessLevel, position) < 0.0F) {
            return false;
         }
      }

      if (state.isAir()) {
         return false;
      }

      Block waterBlock = Blocks.WATER;
      if (!state.is(waterBlock)) {
         Block lavaBlock = Blocks.LAVA;
         if (!state.is(lavaBlock)) {
            ClientLevel shapeLevel = ClientAccess.argusclientMinecraft.level;
            return state.getShape(shapeLevel, position) != Shapes.empty();
         }
      }

      return false;
   }

   public record EntityRange(double distance, Vec3 origin) {
   }

   private static class SphereBlockIterator extends AbstractIterator<BlockPos> {
      private final BlockPos center;
      private final float radius;
      private final int extent;
      private int offsetX;
      private int offsetY;
      private int offsetZ;

      public SphereBlockIterator(Vec3 center, float radius) {
         this.center = BlockPos.containing(center);
         this.radius = radius;
         double radiusAsDouble = radius;
         this.extent = (int)Math.ceil(radiusAsDouble);
         this.offsetX = -this.extent;
         this.offsetY = -this.extent;
         this.offsetZ = -this.extent;
      }

      protected BlockPos computeNext() {
         while (this.offsetX <= this.extent) {
            while (this.offsetY <= this.extent) {
               while (this.offsetZ <= this.extent) {
                  int z = this.offsetZ;
                  int y = this.offsetY;
                  int x = this.offsetX;
                  BlockPos position = this.center.offset(x, y, z);
                  this.offsetZ++;
                  double radiusAsDouble = this.radius;
                  if (this.center.closerThan(position, radiusAsDouble)) {
                     return position;
                  }
               }

               this.offsetZ = -this.extent;
               this.offsetY++;
            }

            this.offsetY = -this.extent;
            this.offsetX++;
         }

         return (BlockPos)this.endOfData();
      }
   }
}
