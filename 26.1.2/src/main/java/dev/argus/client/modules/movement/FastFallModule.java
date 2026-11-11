package dev.argus.client.modules.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.EventStage;
import dev.argus.client.events.LocalPlayerMoveEvent;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.StepHeightEvent;
import dev.argus.client.managers.HoleManager;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.exploit.PhaseModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.combat.RaycastUtils;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.math.Vec3Utils;
import dev.argus.client.utils.movement.MovementPhysics;
import dev.argus.mixin.BlockBehaviourAccessor;
import dev.argus.mixin.LocalPlayerAccessor;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FastFallModule extends Module {
   private static SpeedModule speedModule;
   private static HoleSnapModule holeSnapModule;
   private static PhaseModule phaseModule;
   final Setting<FastFallModule.ModeChoice> modeSetting = this.add(new EnumSetting<>("Mode", FastFallModule.ModeChoice.PLAIN));
   private final Setting<Float> heightSetting = this.add(new NumberSetting<>("Height", 2.0F, 1.0F, 15.0F).withUnit("m"));
   private final Setting<Float> speedSetting;
   private final Setting<Boolean> stopInLiquidsSetting;
   private final Setting<Boolean> onlyHoleSetting;
   private final Setting<Boolean> safeDisableSetting;
   public boolean suppressSprint;
   private boolean strictTimerArmed;
   private boolean strictTimerPulse;
   private boolean holeBelow;
   private boolean acceleratingFall;
   private boolean hasFastFallen;
   private boolean steppedThisTick;
   private double collisionBlockYOffset;
   private double groundDistance;
   private final ElapsedTimer strictPulseTimer;

   private boolean canUseStrictTimer() {
      if (this.canFastFall() && this.isStrictMode() && this.collisionBlockYOffset != 0.0 && this.groundDistance != 0.0) {
         double currentGroundDistance = this.groundDistance;
         Float maximumHeight = this.heightSetting.getValue();
         return currentGroundDistance <= maximumHeight.floatValue();
      } else {
         return false;
      }
   }

   private void releaseStrictTimer() {
      if (this.isStrictMode()) {
         this.strictTimerArmed = false;
         ClientContext.playerTickController.releaseMultiplier(this);
      }
   }

   private boolean isUnsupportedAtEdge() {
      AABB bounds = ClientAccess.argusclientMinecraft.player.getBoundingBox();
      Vec3 center = bounds.getCenter();
      double minX = bounds.minX;
      double minZ = bounds.minZ;
      double maxX = bounds.maxX;
      double maxZ = bounds.maxZ;
      HashMap<Vec3, Vec3> rayEnds = new HashMap<>();
      rayEnds.put(center, new Vec3(center.x, center.y - 1.0, center.z));
      rayEnds.put(new Vec3(minX, center.y, minZ), new Vec3(minX, center.y - 1.0, minZ));
      rayEnds.put(new Vec3(maxX, center.y, minZ), new Vec3(maxX, center.y - 1.0, minZ));
      rayEnds.put(new Vec3(minX, center.y, maxZ), new Vec3(minX, center.y - 1.0, maxZ));
      rayEnds.put(new Vec3(maxX, center.y, maxZ), new Vec3(maxX, center.y - 1.0, maxZ));

      for (Vec3 rayStart : rayEnds.keySet()) {
         BlockHitResult hit = ClientAccess.argusclientMinecraft
            .level
            .clip(new ClipContext(rayStart, rayEnds.get(rayStart), Block.COLLIDER, Fluid.NONE, ClientAccess.argusclientMinecraft.player));
         if (hit != null && hit.getType() == Type.BLOCK) {
            return false;
         }
      }

      if (!this.hasCollidableBlockAhead()) {
         return false;
      }

      ClientLevel belowLevelSnapshot = ClientAccess.argusclientMinecraft.level;
      double playerX = ClientAccess.argusclientMinecraft.player.getX();
      double playerY = ClientAccess.argusclientMinecraft.player.getY();
      double belowPlayerY = playerY - 1.0;
      double belowZ = ClientAccess.argusclientMinecraft.player.getZ();
      BlockPos belowPos = BlockPos.containing(playerX, belowPlayerY, belowZ);
      BlockState stateAtFeet = belowLevelSnapshot.getBlockState(belowPos);
      return stateAtFeet == null || stateAtFeet.getBlock() == Blocks.AIR;
   }

   private boolean hasCollidableBlockAhead() {
      Vec3 velocity = ClientAccess.argusclientMinecraft.player.getDeltaMovement();
      double xDirection = velocity.x;
      double zDirection = velocity.z;
      double absoluteZ = Math.abs(zDirection);
      double absoluteX = Math.abs(xDirection);
      xDirection = absoluteZ > absoluteX ? 0.0 : Math.signum(xDirection);
      double projectedAbsoluteX = Math.abs(xDirection);
      double originalAbsoluteZ = Math.abs(zDirection);
      zDirection = projectedAbsoluteX > originalAbsoluteZ ? 0.0 : Math.signum(zDirection);
      double truncatedY = (int)ClientAccess.argusclientMinecraft.player.getY();
      double fractionY = ClientAccess.argusclientMinecraft.player.getY() - truncatedY;
      double scanY = truncatedY + Math.signum(fractionY);
      ClientLevel levelSnapshot = ClientAccess.argusclientMinecraft.level;
      double aheadXSnapshot = ClientAccess.argusclientMinecraft.player.getX() + xDirection;
      double aheadYSnapshot = scanY - 1.0;
      double aheadZ = ClientAccess.argusclientMinecraft.player.getZ() + zDirection;
      BlockPos aheadPos = BlockPos.containing(aheadXSnapshot, aheadYSnapshot, aheadZ);
      BlockState aheadState = levelSnapshot.getBlockState(aheadPos);
      return aheadState != null ? ((BlockBehaviourAccessor)aheadState.getBlock()).isCollidable() : true;
   }

   private boolean canFastFall() {
      if (speedModule.isToggled()
         && speedModule.modeSetting.getValue() != SpeedModule.ModeChoice.OnGround
         && speedModule.modeSetting.getValue() != SpeedModule.ModeChoice.Vanilla) {
         return false;
      }

      if (ClientAccess.argusclientMinecraft.player.isShiftKeyDown()) {
         return false;
      }

      if (ClientAccess.argusclientMinecraft.player.isFallFlying()) {
         return false;
      }

      if (ClientAccess.argusclientMinecraft.player.isInWall()) {
         return false;
      }

      if (PlayerStateUtils.isInLiquid(ClientAccess.argusclientMinecraft.player) && this.stopInLiquidsSetting.getValue()) {
         return false;
      }

      if (!ClientContext.serverStateManager.getSetbackTimer().hasElapsed(500L)) {
         return false;
      }

      ClientLevel levelSnapshot = ClientAccess.argusclientMinecraft.level;
      BlockPos feetPos = BlockPos.containing(ClientAccess.argusclientMinecraft.player.position());
      BlockState feetState = levelSnapshot.getBlockState(feetPos);
      return !(feetState.getBlock() instanceof BedBlock);
   }

   @Override
   public void onToggle() {
      this.suppressSprint = false;
      this.hasFastFallen = false;
   }

   private boolean isStrictMode() {
      return this.modeSetting.getValue() == FastFallModule.ModeChoice.STRICT;
   }

   @EventHandler
   private void onMove(LocalPlayerMoveEvent event) {
      this.collisionBlockYOffset = RaycastUtils.getCollisionBlockYOffset();
      this.groundDistance = RaycastUtils.getGroundDistance();
      HoleManager holeManagerSnapshot = ClientContext.holeManager;
      BlockPos position = ClientAccess.argusclientMinecraft.player.blockPosition();
      int blocksBelow = (int)this.groundDistance;
      BlockPos targetPos = position.below(blocksBelow);
      this.holeBelow = holeManagerSnapshot.intersectsHole(targetPos);
      if (this.holeBelow || !this.onlyHoleSetting.getValue()) {
         double horizontalFactor = 0.3;
         boolean hasHoleSnapTarget = false;
         if (holeSnapModule.isToggled() && holeSnapModule.targetTimeoutTimer.hasElapsed(500L)) {
            hasHoleSnapTarget = holeSnapModule.getSnapTarget() != null;
         }

         boolean strictGroundedFall = false;
         if (this.isStrictMode() && this.canFastFall() && this.isUnsupportedAtEdge()) {
            strictGroundedFall = ClientAccess.argusclientMinecraft.player.onGround();
         }

         if (!strictGroundedFall) {
            if (!this.acceleratingFall || hasHoleSnapTarget) {
               return;
            }

            if (!this.hasCollidableBlockAhead()) {
               return;
            }
         }

         Vec3 movementSnapshot = event.getVelocity();
         double scaledXSnapshot = event.getX() * horizontalFactor;
         double eventY = event.getY();
         double scaledZ = event.getZ() * horizontalFactor;
         Vec3Utils.setComponents(movementSnapshot, scaledXSnapshot, eventY, scaledZ);
      }
   }

   @EventHandler
   private void onStepHeight(StepHeightEvent event) {
      if (event.getStage() == EventStage.Post && ClientAccess.argusclientMinecraft.player.onGround()) {
         double height = event.getHeight();
         if (height > 0.6) {
            this.steppedThisTick = true;
         }
      }
   }

   @EventHandler
   private void onPlayerTick(LocalPlayerTickEvent event) {
      boolean movementAllowed = MovementPhysics.isLocalPlayerMoving();
      if (!movementAllowed && holeSnapModule.isToggled()) {
         movementAllowed = holeSnapModule.getSnapTarget() != null;
      }

      if (this.holeBelow || !this.onlyHoleSetting.getValue()) {
         if (movementAllowed) {
            if (this.steppedThisTick) {
               this.steppedThisTick = false;
            } else {
               this.acceleratingFall = false;
               if (((LocalPlayerAccessor)ClientAccess.argusclientMinecraft.player).lastOnGround()) {
                  Vec3 currentVelocity = ClientAccess.argusclientMinecraft.player.getDeltaMovement();
                  if (currentVelocity.y() <= 0.0) {
                     if (this.canFastFall()) {
                        if (!this.isStrictMode()) {
                           double playerY = ClientAccess.argusclientMinecraft.player.getY();
                           AABB boundsSnapshot = ClientAccess.argusclientMinecraft.player.getBoundingBox();
                           Float maximumHeight = this.heightSetting.getValue();
                           double negativeHeight = -maximumHeight;
                           AABB scanBounds = boundsSnapshot.expandTowards(0.0, negativeHeight, 0.0).setMaxY(playerY);
                           if (!ClientAccess.argusclientMinecraft.level.noCollision(ClientAccess.argusclientMinecraft.player, scanBounds)
                              || ClientAccess.argusclientMinecraft.level.containsAnyLiquid(scanBounds)) {
                              Iterable<VoxelShape> collisions = ClientAccess.argusclientMinecraft
                                 .level
                                 .getBlockCollisions(ClientAccess.argusclientMinecraft.player, scanBounds);
                              AtomicReference<Double> highestCollisionY = new AtomicReference<>((double)ClientAccess.argusclientMinecraft.level.getMinY());
                              collisions.forEach(shape -> {
                                 double previousHighestSnapshot = highestCollisionY.get();
                                 Axis verticalAxisx = Axis.Y;
                                 double shapeTopY = shape.max(verticalAxisx);
                                 double newHighestY = Math.max(previousHighestSnapshot, shapeTopY);
                                 highestCollisionY.set(newHighestY);
                              });
                              double highestY = highestCollisionY.get();
                              if (highestY < ClientAccess.argusclientMinecraft.player.getY()) {
                                 boolean limitDescent = true;
                                 double destinationY = highestCollisionY.get();
                                 Vec3 velocity = ClientAccess.argusclientMinecraft.player.getDeltaMovement();
                                 double destinationXSnapshot = ClientAccess.argusclientMinecraft.player.getX();
                                 double destinationZ = ClientAccess.argusclientMinecraft.player.getZ();
                                 BlockPos destinationPos = BlockPos.containing(destinationXSnapshot, destinationY, destinationZ);
                                 ClientLevel level = ClientAccess.argusclientMinecraft.level;
                                 Vec3i velocityOffset = new Vec3i((int)velocity.x, -1, (int)velocity.z);
                                 if (!level.isEmptyBlock(destinationPos.offset(velocityOffset))
                                    && ClientContext.serverStateManager.getTeleportAckTimer().hasElapsed(25L)) {
                                    limitDescent = this.modeSetting.getValue() == FastFallModule.ModeChoice.PLAIN;
                                 }

                                 double dropDistance = ClientAccess.argusclientMinecraft.player.getY() - destinationY;
                                 if (dropDistance > 2.0 || limitDescent) {
                                    double limitY = ClientAccess.argusclientMinecraft.player.getY();
                                    destinationY = limitY - 2.0;
                                    this.acceleratingFall = true;
                                    Setting<FastFallModule.ModeChoice> speedMode = this.modeSetting;
                                    float selectedSpeed;
                                    if (speedMode.getValue() == FastFallModule.ModeChoice.PLAIN) {
                                       Float configuredSpeed = this.speedSetting.getValue();
                                       selectedSpeed = configuredSpeed;
                                    } else {
                                       selectedSpeed = 1.0F;
                                    }

                                    if (selectedSpeed != 0.0F) {
                                       LocalPlayer velocityReceiver = ClientAccess.argusclientMinecraft.player;
                                       double downwardSpeed = -selectedSpeed;
                                       Axis verticalAxis = Axis.Y;
                                       Vec3 acceleratedVelocity = velocity.with(verticalAxis, downwardSpeed);
                                       velocityReceiver.setDeltaMovement(acceleratedVelocity);
                                    }
                                 }

                                 if (!limitDescent) {
                                    LocalPlayer positionTarget = ClientAccess.argusclientMinecraft.player;
                                    double positionXSnapshot = positionTarget.getX();
                                    double positionZ = ClientAccess.argusclientMinecraft.player.getZ();
                                    positionTarget.setPos(positionXSnapshot, destinationY, positionZ);
                                 }

                                 phaseModule.resetGroundedTicks();
                                 this.hasFastFallen = true;
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private void onStrictTimerTick(LocalPlayerPreTickEvent event) {
      if (this.safeDisableSetting.getValue() && ClientContext.holeManager.isInHole() && this.hasFastFallen) {
         this.disable();
      } else if (this.canUseStrictTimer() && (this.holeBelow || !this.onlyHoleSetting.getValue())) {
         if (!ClientAccess.argusclientMinecraft.player.onGround()) {
            ElapsedTimer pulseTimerSnapshot = this.strictPulseTimer;
            if (pulseTimerSnapshot.hasElapsed(500L)) {
               this.strictTimerPulse = !this.strictTimerPulse;
               pulseTimerSnapshot = this.strictPulseTimer;
               pulseTimerSnapshot.reset();
            }

            if (ClientAccess.argusclientMinecraft.player.getDeltaMovement().y < 0.0 && this.strictTimerArmed && this.strictTimerPulse) {
               ClientContext.playerTickController.requestMultiplier(this, 2.0F);
               this.hasFastFallen = true;
            } else {
               this.releaseStrictTimer();
            }
         } else {
            this.strictTimerArmed = true;
            ClientContext.playerTickController.releaseMultiplier(this);
            int groundCounter = ClientContext.playerStateTracker.getGroundedTicks();
            if (groundCounter > 3) {
               this.strictPulseTimer.reset();
               this.strictTimerPulse = true;
            }
         }
      } else {
         this.releaseStrictTimer();
      }
   }

   public static void bindReferencedModules(ModuleRegistry registry) {
      speedModule = (SpeedModule)registry.getModule(SpeedModule.class);
      holeSnapModule = (HoleSnapModule)registry.getModule(HoleSnapModule.class);
      phaseModule = (PhaseModule)registry.getModule(PhaseModule.class);
   }

   public FastFallModule() {
      super("FastFall", "Allows you to get in holes with ease.", ModuleCategory.MOVEMENT, "reversestep");
      Predicate<Object> showSpeed = ignored -> this.modeSetting.getValue() == FastFallModule.ModeChoice.PLAIN;
      this.speedSetting = this.add(new NumberSetting<>("Speed", 1.0F, 0.0F, 5.0F, showSpeed));
      this.stopInLiquidsSetting = this.add(new BooleanSetting("StopInLiquids", true));
      this.onlyHoleSetting = this.add(new BooleanSetting("OnlyHole", false));
      this.safeDisableSetting = this.add(new BooleanSetting("SafeDisable", false));
      this.strictPulseTimer = new ElapsedTimer();
   }

   public enum ModeChoice implements NamedEntry {
      PLAIN("Plain"),
      STRICT("Strict"),
      INSTANT("Instant");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      ModeChoice(String displayName) {
         this.displayName = displayName;
      }
   }
}
