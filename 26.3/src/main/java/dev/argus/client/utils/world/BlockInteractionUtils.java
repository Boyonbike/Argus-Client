package dev.argus.client.utils.world;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.CrystalInteractionManager;
import dev.argus.client.managers.SwingInteractionManager;
import dev.argus.client.modules.client.AntiCheatModule;
import dev.argus.client.modules.misc.KillEffectsModule;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.AntiCheatStrictDirectionChoice;
import dev.argus.client.settings.options.BlockSampleMode;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.combat.BoundingBoxUtils;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.entity.SwingAction;
import dev.argus.client.utils.network.PacketSender;
import dev.argus.mixin.BlockBehaviourAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResult.Success;
import net.minecraft.world.InteractionResult.SwingSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ExperienceBottleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class BlockInteractionUtils implements ClientAccess {
   private static final Direction[] placementDirections = new Direction[]{
      Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP
   };
   private static final ElapsedTimer interactionTimer = new ElapsedTimer();
   public static boolean isPlacingBlock = false;
   private static AntiCheatModule antiCheat = ClientContext.getModule(AntiCheatModule.class);
   private static KillEffectsModule killEffects = ClientContext.getModule(KillEffectsModule.class);

   public static boolean isInteractionCooldownActive() {
      ElapsedTimer timerSnapshot = interactionTimer;
      return !timerSnapshot.hasElapsed(50L);
   }

   public static void resetInteractionCooldown() {
      interactionTimer.reset();
   }

   public static boolean requiresSneakForInteraction(BlockPos position) {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      BlockState state = level.getBlockState(position);
      Block block = state.getBlock();
      return block == Blocks.RESPAWN_ANCHOR
         ? true
         : state.hasBlockEntity()
            || block instanceof BedBlock
            || block instanceof AbstractChestBlock
            || block instanceof DoorBlock
            || block instanceof TrapDoorBlock
            || block instanceof ButtonBlock
            || block instanceof ComposterBlock
            || block instanceof NoteBlock
            || block instanceof StandingSignBlock
            || block instanceof AnvilBlock
            || block instanceof AbstractFurnaceBlock
            || block instanceof CraftingTableBlock
            || block instanceof EnchantingTableBlock
            || block instanceof CakeBlock;
   }

   private BlockInteractionUtils() {
   }

   public static boolean canPlaceAt(BlockPos position, boolean playersOnly) {
      return canPlaceAt(position, playersOnly, false);
   }

   public static boolean isThrowableItem(Item item) {
      return item instanceof EggItem
         || item instanceof SnowballItem
         || item instanceof EnderpearlItem
         || item instanceof ExperienceBottleItem
         || item instanceof ThrowablePotionItem
         || item instanceof TridentItem;
   }

   public static boolean isGlass(BlockState state) {
      Block glassPane = Blocks.GLASS_PANE;
      if (state.is(glassPane)) {
         return true;
      }

      Block glass = Blocks.GLASS;
      return state.is(glass) || state.getBlock() instanceof StainedGlassBlock || state.getBlock() instanceof StainedGlassPaneBlock;
   }

   public static Direction findSupportDirection(BlockPos position, boolean strictDirection, boolean checkRaytrace) {
      Direction[] directions = placementDirections;

      for (Direction direction : directions) {
         BlockPos neighbor = position.relative(direction);
         if (!(neighbor.getY() >= WorldUtils.getTopY())) {
            ClientLevel level = ClientAccess.argusclientMinecraft.level;
            WorldBorder borderSnapshot = level.getWorldBorder();
            double x = neighbor.getX();
            double z = neighbor.getZ();
            if (borderSnapshot.isWithinBounds(x, z)) {
               if (checkRaytrace) {
                  BlockSampleMode raytraceMode = BlockSampleMode.X8;
                  Direction clickedFace = direction.getOpposite();
                  if (!WorldUtils.canSeeAny(raytraceMode.sampleFace(neighbor, clickedFace))) {
                     continue;
                  }
               }

               if (ClientContext.worldStateManager.isPlacementOccupied(neighbor)) {
                  if (!strictDirection) {
                     return direction;
                  }

                  if (getStrictDirections(neighbor).contains(direction.getOpposite())) {
                     return direction;
                  }
               }
            }
         }
      }

      return null;
   }

   public static boolean isInteractiveBlock(Block block) {
      return block instanceof CraftingTableBlock
         || block instanceof AnvilBlock
         || block instanceof ButtonBlock
         || block instanceof BasePressurePlateBlock
         || block instanceof BaseEntityBlock
         || block instanceof BedBlock
         || block instanceof FenceGateBlock
         || block instanceof DoorBlock
         || block instanceof NoteBlock
         || block instanceof TrapDoorBlock;
   }

   public static boolean canPlaceCrystal(
      BlockPos basePosition,
      boolean requireTwoAirBlocks,
      boolean checkEntities,
      boolean checkCrystals,
      boolean allowFire,
      boolean shortHeight,
      boolean expandPlayers
   ) {
      if (!WorldUtils.isBedrockOrObsidian(basePosition)) {
         return false;
      }

      BlockPos crystalPosition = basePosition.offset(0, 1, 0);
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      Block crystalBlock = level.getBlockState(crystalPosition).getBlock();
      if (allowFire) {
         if (crystalBlock != Blocks.AIR && crystalBlock != Blocks.FIRE) {
            return false;
         }
      } else if (crystalBlock != Blocks.AIR) {
         return false;
      }

      if (requireTwoAirBlocks) {
         ClientLevel upperLevel = ClientAccess.argusclientMinecraft.level;
         BlockPos upperPosition = basePosition.offset(0, 2, 0);
         if (upperLevel.getBlockState(upperPosition).getBlock() != Blocks.AIR) {
            return false;
         }
      }

      AABB boundsSnapshot = new AABB(
         crystalPosition.getX(),
         crystalPosition.getY(),
         crystalPosition.getZ(),
         crystalPosition.getX() + 1,
         crystalPosition.getY() + (shortHeight ? 0.9 : 2.0),
         crystalPosition.getZ() + 1
      );
      boolean blocked = false;
      ClientLevel entityLevel = ClientAccess.argusclientMinecraft.level;

      for (Entity entity : entityLevel.entitiesForRendering()) {
         if (!entity.isRemoved() && entity.isAlive()) {
            AABB entityBounds = entity.getBoundingBox();
            if (entity instanceof Player && entity != ClientAccess.argusclientMinecraft.player && expandPlayers) {
               entityBounds = entityBounds.inflate(0.01);
            } else if (entity instanceof EndCrystal && !checkCrystals) {
               continue;
            }

            if (entityBounds.intersects(boundsSnapshot)) {
               blocked = true;
               break;
            }
         }
      }

      return !checkEntities || !blocked;
   }

   public static boolean placeBlockIgnoringCrystals(BlockPos position, Direction direction, boolean airPlace, InteractionHand hand) {
      return placeBlockIgnoringCrystals(position, null, direction, airPlace, hand);
   }

   public static boolean placeBlock(BlockPos position, Vec3 hitPosition, Direction direction, boolean airPlace, InteractionHand hand) {
      if (ClientAccess.argusclientMinecraft.level != null
         && ClientAccess.argusclientMinecraft.player != null
         && ClientAccess.argusclientMinecraft.gameMode != null) {
         Block heldBlockSnapshot = getHeldBlock(hand);
         if (!canPlaceBlock(position, heldBlockSnapshot, false)) {
            return false;
         }

         boolean placingInAir = airPlace && direction == null;
         if (direction == null) {
            if (!airPlace) {
               return false;
            }

            Direction fallbackDirection;
            if (ClientAccess.argusclientMinecraft.hitResult instanceof BlockHitResult targetHitResult) {
               fallbackDirection = targetHitResult.getDirection();
            } else {
               fallbackDirection = Direction.DOWN;
            }

            direction = fallbackDirection;
         }

         BlockPos neighborSnapshot;
         if (placingInAir) {
            neighborSnapshot = position;
         } else {
            neighborSnapshot = position.relative(direction);
         }

         boolean requiresSneak = requiresSneakForInteraction(neighborSnapshot);
         if (hitPosition == null) {
            hitPosition = Vec3.atCenterOf(position).add(Vec3.atLowerCornerOf(direction.getUnitVec3i()).scale(0.5));
         }

         if (requiresSneak) {
            ClientContext.playerStateTracker.setSneaking(true);
         }

         isPlacingBlock = true;
         MultiPlayerGameMode gameModeSnapshot = ClientAccess.argusclientMinecraft.gameMode;
         LocalPlayer playerSnapshot = ClientAccess.argusclientMinecraft.player;
         BlockHitResult hitResultSnapshot = new BlockHitResult(hitPosition, placingInAir ? direction : direction.getOpposite(), neighborSnapshot, false);
         InteractionResult result = gameModeSnapshot.useItemOn(playerSnapshot, hand, hitResultSnapshot);
         isPlacingBlock = false;
         if (result instanceof Success success && success.swingSource() == SwingSource.PREDICTED) {
            ClientAccess.argusclientMinecraft.player.swing(hand, ClientAccess.argusclientMinecraft.player.getItemInHand(hand).getAttackAnimation(), false);
         }

         if (requiresSneak) {
            ClientContext.playerStateTracker.setSneaking(false);
         }

         return true;
      } else {
         return false;
      }
   }

   private static Block getHeldBlock(InteractionHand hand) {
      return ClientAccess.argusclientMinecraft.player.getItemInHand(hand).getItem() instanceof BlockItem blockItem ? blockItem.getBlock() : null;
   }

   public static Entity attackCrystalAt(BlockPos position, int minimumAge) {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;

      for (Entity entity : level.entitiesForRendering()) {
         if (entity instanceof EndCrystal) {
            AABB entityBounds = entity.getBoundingBox();
            AABB blockBounds = new AABB(position);
            if (entityBounds.intersects(blockBounds) && entity.tickCount >= minimumAge) {
               attackEntity(entity);
               return entity;
            }
         }
      }

      return null;
   }

   public static boolean canPlaceBlock(BlockPos position, Block block, boolean playersOnly) {
      return canPlaceBlock(position, block, playersOnly, false);
   }

   public static boolean canPlaceBlock(BlockPos position, Block block, boolean playersOnly, boolean ignoreCrystals) {
      if (isOutsidePlacementBounds(position)) {
         return false;
      }

      if (WorldUtils.isPlacementOccupied(position)) {
         return false;
      }

      VoxelShape shapeSnapshot;
      if (block == null) {
         shapeSnapshot = Shapes.empty();
      } else {
         BlockState collisionState = block.defaultBlockState();
         ClientLevel collisionLevel = ClientAccess.argusclientMinecraft.level;
         BlockPos collisionPosition = BlockPos.ZERO;
         shapeSnapshot = collisionState.getCollisionShape(collisionLevel, collisionPosition);
      }

      AABB boundsSnapshot;
      if (shapeSnapshot.isEmpty()) {
         boundsSnapshot = new AABB(position);
      } else {
         AABB shapeBounds = shapeSnapshot.bounds();
         boundsSnapshot = BoundingBoxUtils.placeAtBlock(shapeBounds, position);
      }

      return block != null && !((BlockBehaviourAccessor)block).isCollidable() ? true : !hasBlockingEntity(boundsSnapshot, playersOnly, ignoreCrystals);
   }

   public static boolean hasBlockingEntity(AABB bounds, boolean playersOnly, boolean ignoreCrystals) {
      ClientLevel entityLevel = ClientAccess.argusclientMinecraft.level;
      Class<Entity> entityType = Entity.class;
      Predicate<Entity> entityFilter = candidateEntity -> !(candidateEntity instanceof ThrownExperienceBottle)
         && !(candidateEntity instanceof ItemEntity)
         && !(candidateEntity instanceof ItemFrame)
         && !(candidateEntity instanceof FireworkRocketEntity)
         && !(candidateEntity instanceof ExperienceOrb);

      for (Entity entity : entityLevel.getEntitiesOfClass(entityType, bounds, entityFilter)) {
         AABB entityBounds = entity.getBoundingBox();
         if ((BoundingBoxUtils.hasVolumeOverlap(bounds, entityBounds) || entity == ClientAccess.argusclientMinecraft.player)
            && (!(entity instanceof EndCrystal) || !ignoreCrystals)
            && !(entity instanceof Arrow)) {
            if (entity instanceof Player) {
               if (entity.getBoundingBox().intersects(bounds)) {
                  return true;
               }
            } else if (!playersOnly) {
               return true;
            }
         }
      }

      return false;
   }

   public static void attackEntity(Entity entity) {
      CrystalInteractionManager attackManagerSnapshot = ClientContext.crystalInteractionManager;
      int entityId = entity.getId();
      if (attackManagerSnapshot.attackEntity(entityId)) {
         if (killEffects.isToggled() && entity instanceof Player) {
            KillEffectsModule effectsSnapshot = killEffects;
            Player targetPlayer = (Player)entity;
            effectsSnapshot.trackTarget(targetPlayer);
         }

         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         player.resetOnlyAttackStrengthTicker();
      }
   }

   public static void interactWithEntity(Entity entity, InteractionHand hand) {
      MultiPlayerGameMode gameModeSnapshot = ClientAccess.argusclientMinecraft.gameMode;
      LocalPlayer playerSnapshot = ClientAccess.argusclientMinecraft.player;
      EntityHitResult hitResultSnapshot = new EntityHitResult(entity);
      gameModeSnapshot.interact(playerSnapshot, entity, hitResultSnapshot, hand);
      ClientAccess.argusclientMinecraft.gameMode.interact(ClientAccess.argusclientMinecraft.player, entity, hitResultSnapshot, hand);
      ClientAccess.argusclientMinecraft.player.swing(hand, ClientAccess.argusclientMinecraft.player.getItemInHand(hand).getAttackAnimation(), false);
   }

   public static BlockPos findSupportPosition(
      BlockPos position, int maxDistance, boolean checkPlayers, boolean strictDirection, Predicate<? super BlockPos> predicate
   ) {
      for (int distance = 1; distance <= maxDistance; distance++) {
         for (Direction direction : placementDirections) {
            BlockPos candidate = position.relative(direction, distance);
            if (predicate.test(candidate) && findSupportDirection(candidate, strictDirection) != null) {
               ClientLevel playerLevel = ClientAccess.argusclientMinecraft.level;
               Class<Player> playerType = Player.class;
               AABB playerBounds = new AABB(candidate);
               Predicate<Player> playerFilter = candidatePlayer -> checkPlayers;
               if (playerLevel.getEntitiesOfClass(playerType, playerBounds, playerFilter).isEmpty()) {
                  return candidate;
               }
            }
         }
      }

      return null;
   }

   public static boolean placeBlock(BlockPos position, boolean airPlace, InteractionHand hand) {
      Direction directionSnapshot = findSupportDirection(position);
      return placeBlock(position, null, directionSnapshot, airPlace, hand);
   }

   public static BlockPos findSupportPosition(BlockPos position, int maxDistance, boolean checkPlayers, boolean strictDirection) {
      Predicate<BlockPos> acceptPosition = candidate -> true;
      return findSupportPosition(position, maxDistance, checkPlayers, strictDirection, acceptPosition);
   }

   public static boolean isCollidable(BlockPos position) {
      return ((BlockBehaviourAccessor)getBlock(position)).isCollidable();
   }

   public static boolean isOutsidePlacementBounds(BlockPos position) {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      WorldBorder borderSnapshot = level.getWorldBorder();
      if (!borderSnapshot.isWithinBounds(position)) {
         return true;
      } else if (!Level.isInSpawnableBounds(position)) {
         return true;
      } else {
         return position.getY() >= WorldUtils.getTopY() ? true : position.getY() < ClientAccess.argusclientMinecraft.level.getMinY();
      }
   }

   public static Block getBlock(BlockPos position) {
      if (position == null) {
         return null;
      }

      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      return level.getBlockState(position).getBlock();
   }

   public static boolean placeBlockIgnoringCrystals(BlockPos position, Vec3 hitPosition, Direction direction, boolean airPlace, InteractionHand hand) {
      return placeBlockIgnoringCrystals(position, hitPosition, direction, airPlace, hand, ClientAccess.argusclientMinecraft.player.getItemInHand(hand));
   }

   public static boolean placeBlockIgnoringCrystals(
      BlockPos position, Vec3 hitPosition, Direction direction, boolean airPlace, InteractionHand hand, ItemStack heldStack
   ) {
      if (ClientAccess.argusclientMinecraft.level != null
         && ClientAccess.argusclientMinecraft.player != null
         && ClientAccess.argusclientMinecraft.gameMode != null) {
         Block heldBlock;
         if (heldStack.getItem() instanceof BlockItem blockItem) {
            heldBlock = blockItem.getBlock();
         } else {
            heldBlock = null;
         }

         return !canPlaceBlock(position, heldBlock, false, true) ? false : sendPlacement(position, hitPosition, direction, airPlace, hand);
      } else {
         return false;
      }
   }

   public static List<BlockPos> findSupportPath(
      BlockPos position, int maxDistance, boolean checkPlayers, boolean strictDirection, Predicate<? super BlockPos> predicate
   ) {
      ArrayList<BlockPos> path = new ArrayList<>();

      for (Direction direction : placementDirections) {
         path.clear();

         for (int distance = 1; distance <= maxDistance; distance++) {
            BlockPos candidate = position.relative(direction, distance);
            path.addFirst(candidate);
            if (!predicate.test(candidate)) {
               break;
            }

            ClientLevel playerLevel = ClientAccess.argusclientMinecraft.level;
            Class<Player> playerType = Player.class;
            AABB playerBounds = new AABB(candidate);
            Predicate<Player> playerFilter = candidatePlayer -> checkPlayers;
            if (!playerLevel.getEntitiesOfClass(playerType, playerBounds, playerFilter).isEmpty()) {
               break;
            }

            if (findSupportDirection(candidate, strictDirection) != null) {
               return List.copyOf(path);
            }
         }
      }

      return Collections.emptyList();
   }

   public static List<BlockPos> findSupportPath(BlockPos position, int maxDistance, boolean checkPlayers, boolean strictDirection) {
      Predicate<BlockPos> acceptPosition = candidate -> true;
      return findSupportPath(position, maxDistance, checkPlayers, strictDirection, acceptPosition);
   }

   public static Direction findSupportDirection(BlockPos position, boolean strictDirection) {
      return findSupportDirection(position, strictDirection, false);
   }

   public static List<Direction> getStrictDirectionsOrFallback(BlockPos position, Direction fallback) {
      List<Direction> directions = getStrictDirections(position);
      return directions.isEmpty() ? Collections.singletonList(fallback) : directions;
   }

   public static boolean canPlaceAt(BlockPos position, boolean playersOnly, boolean ignoreCrystals) {
      if (isOutsidePlacementBounds(position)) {
         return false;
      }

      if (WorldUtils.isPlacementOccupied(position)) {
         return false;
      }

      AABB boundsSnapshot = new AABB(position);
      return !hasBlockingEntity(boundsSnapshot, playersOnly, ignoreCrystals);
   }

   public static boolean canPlayerInteract() {
      if (ClientAccess.argusclientMinecraft.player.isSpectator()) {
         return false;
      }

      LocalPlayer alivePlayer = ClientAccess.argusclientMinecraft.player;
      if (!alivePlayer.isAlive()) {
         return false;
      }

      if (ClientAccess.argusclientMinecraft.player.isHandsBusy()) {
         return false;
      }

      LocalPlayer sleepingPlayer = ClientAccess.argusclientMinecraft.player;
      return !sleepingPlayer.isSleeping();
   }

   public static boolean isStrictDirectionEnabled() {
      Setting<AntiCheatStrictDirectionChoice> strictDirectionSetting = antiCheat.strictDirectionSetting;
      return strictDirectionSetting.getValue() != AntiCheatStrictDirectionChoice.OFF;
   }

   public static Direction findSupportDirection(BlockPos position) {
      return findSupportDirection(position, false);
   }

   public static boolean sendPlacement(BlockPos position, Vec3 hitPosition, Direction direction, boolean airPlace, InteractionHand hand) {
      boolean placingInAir = airPlace && direction == null;
      if (direction == null) {
         List<Direction> directions = getStrictDirections(position);
         if (!airPlace) {
            return false;
         }

         direction = directions.isEmpty() ? Direction.DOWN : directions.getFirst();
      }

      BlockPos neighborSnapshot;
      if (placingInAir) {
         neighborSnapshot = position;
      } else {
         neighborSnapshot = position.relative(direction);
      }

      boolean requiresSneak = requiresSneakForInteraction(neighborSnapshot) && !placingInAir;
      if (requiresSneak) {
         ClientContext.playerStateTracker.setSneaking(true);
      }

      if (hitPosition == null) {
         hitPosition = Vec3.atCenterOf(position).add(Vec3.atLowerCornerOf(direction.getUnitVec3i()).scale(0.5));
      }

      BlockHitResult hitResultSnapshot = new BlockHitResult(hitPosition, placingInAir ? direction : direction.getOpposite(), neighborSnapshot, false);
      PacketSender.sendUseItemOn(hand, hitResultSnapshot);
      SwingInteractionManager swingManager = ClientContext.swingManager;
      SwingAction interactionKind = SwingAction.INTERACT;
      swingManager.swing(hand, interactionKind);
      if (requiresSneak) {
         ClientContext.playerStateTracker.setSneaking(false);
      }

      return true;
   }

   public static List getStrictDirections(BlockPos position) {
      Direction[] directionOrder = placementDirections;
      ArrayList<Direction> directions = new ArrayList<>(List.of(directionOrder));
      if (antiCheat.strictDirectionSetting.getValue() == AntiCheatStrictDirectionChoice.NCP) {
         for (Direction direction : placementDirections) {
            BlockPos neighbor = position.relative(direction);
            ClientLevel level = ClientAccess.argusclientMinecraft.level;
            BlockState neighborState = level.getBlockState(neighbor);
            if (WorldUtils.isPlacementOccupied(neighbor) && neighborState.isSolidRender()) {
               directions.remove(direction);
            }
         }
      }

      if (position.getY() + 1 > WorldUtils.getTopY()) {
         directions.remove(Direction.UP);
      }

      double deltaX = ClientAccess.argusclientMinecraft.player.getEyePosition().x - Vec3.atCenterOf(position).x;
      double deltaZ = ClientAccess.argusclientMinecraft.player.getEyePosition().z - Vec3.atCenterOf(position).z;
      if (deltaX > 0.5) {
         directions.remove(Direction.WEST);
      } else if (deltaX < -0.5) {
         directions.remove(Direction.EAST);
      } else {
         directions.remove(Direction.WEST);
         directions.remove(Direction.EAST);
      }

      if (deltaZ > 0.5) {
         directions.remove(Direction.NORTH);
      } else if (deltaZ < -0.5) {
         directions.remove(Direction.SOUTH);
      } else {
         directions.remove(Direction.NORTH);
         directions.remove(Direction.SOUTH);
      }

      if (antiCheat.strictDirectionSetting.getValue() == AntiCheatStrictDirectionChoice.GRIM) {
         boolean sampleAbove = PlayerStateUtils.streamBodySamplePositions().noneMatch(bodySample -> {
            double sampleY = bodySample.y;
            return sampleY > position.getY() + 1;
         });
         boolean sampleBelow = PlayerStateUtils.streamBodySamplePositions().noneMatch(bodySample -> {
            double sampleY = bodySample.y;
            return sampleY < position.getY();
         });
         if (sampleAbove) {
            directions.remove(Direction.UP);
         }

         if (sampleBelow) {
            directions.remove(Direction.DOWN);
         }
      } else {
         if (ClientAccess.argusclientMinecraft.player.getEyePosition().y < position.getY()) {
            directions.remove(Direction.UP);
         }

         if (ClientAccess.argusclientMinecraft.player.getEyePosition().y > position.getY()) {
            directions.remove(Direction.DOWN);
         }
      }

      return directions;
   }
}
