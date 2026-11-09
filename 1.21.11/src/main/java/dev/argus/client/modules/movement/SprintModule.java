package dev.argus.client.modules.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.access.LocalPlayerTickAccess;
import dev.argus.client.events.CancellableEvent;
import dev.argus.client.events.ClientPacketListenerHandleExplosionEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.EventStage;
import dev.argus.client.events.LocalPlayerAiStepEvent;
import dev.argus.client.events.LocalPlayerMoveEvent;
import dev.argus.client.events.LocalPlayerSendPositionEvent;
import dev.argus.client.events.MultiPlayerGameModeUseItemOnEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.events.PlayerAttackEvent;
import dev.argus.client.events.RotationRequestEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.client.AntiCheatModule;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.AntiCheatRotationsChoice;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.combat.RotationRequest;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.movement.MovementPhysics;
import dev.argus.client.utils.movement.WallsVelocityHandler;
import dev.argus.client.utils.network.PacketSender;
import dev.argus.mixin.EntityAccessor;
import dev.argus.mixin.LocalPlayerAccessor;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class SprintModule extends Module {
   public static boolean forceSprintKeyDown;
   private static ElytraFlyModule elytraFlyModule;
   private static LongJumpModule longJumpModule;
   private static FastFallModule fastFallModule;
   private static SpeedModule speedModule;
   private static FastWebModule fastWebModule;
   private static AntiCheatModule antiCheatModule;
   private static NoSlowModule noSlowModule;
   private static FlightModule flightModule;
   final Setting<SprintModule.ModeChoice> modeSetting = this.add(new EnumSetting<>("Mode", SprintModule.ModeChoice.RAGE));
   private final Setting<Boolean> stopInLiquidsSetting = this.add(new BooleanSetting("StopInLiquids", true));
   private final Setting<Boolean> inBlocksSetting;
   public final Setting<Boolean> directionSpoofSetting;
   public final Setting<Boolean> keepSetting;
   private final Setting<Boolean> vanillaSlowdownSetting;
   private final Setting<Boolean> adjustSetting;
   private final Setting<Float> factorSetting;
   final Setting<Boolean> boostSetting;
   private final Setting<Float> boostSpeedSetting;
   private final ElapsedTimer explosionBoostTimer;
   private Vec2 previousMoveVector;
   private float explosionBoostSpeed;
   private boolean wasSprintingBeforeAttack;
   private boolean fallFlyingSinceGround;

   @Override
   public String getInfo() {
      return FontRenderer.capitalize(this.modeSetting.getValue());
   }

   private void updateDirectionSpoof(CancellableEvent event) {
      if (this.directionSpoofSetting.getValue() && !ClientContext.baritoneBridge.isPathingOrCalculating()) {
         if (!this.isWorldUnavailable()) {
            if (!this.isSprintSuppressed()) {
               if (!this.shouldDeferToVanillaSprinting()) {
                  if (MovementPhysics.isLocalPlayerMoving()) {
                     if (!PlayerStateUtils.isFallFlying()) {
                        if (!ClientAccess.argusclientMinecraft.player.input.hasForwardImpulse()) {
                           if (!flightModule.isToggled()) {
                              if (event instanceof LocalPlayerSendPositionEvent positionEvent) {
                                 float yaw = MovementPhysics.getMovementYaw();
                                 positionEvent.setYaw(yaw);
                              } else {
                                 RotationManager rotationManager = ClientContext.rotationManager;
                                 float[] rotations = new float[]{MovementPhysics.getMovementYaw(), ClientAccess.argusclientMinecraft.player.getXRot()};
                                 rotationManager.requestRotation(rotations, 0, true);
                                 ClientContext.rotationManager.useRequestedYawForPriority(0);
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

   @EventHandler(priority = 100)
   private void onLocalPlayerSendPositionEvent(LocalPlayerSendPositionEvent event) {
      if (this.modeSetting.getValue() == SprintModule.ModeChoice.INSTANT) {
         this.updateDirectionSpoof(event);
      }
   }

   @EventHandler
   private void onMultiPlayerGameModeUseItemOnEvent(MultiPlayerGameModeUseItemOnEvent event) {
      if (!ClientContext.baritoneBridge.isPathingOrCalculating()) {
         if (antiCheatModule.movementSyncSetting.getValue()) {
            if (antiCheatModule.rotationsSetting.getValue() == AntiCheatRotationsChoice.SILENT) {
               float rotationDifference = this.getRotationDifference();
               boolean hasLargeRotation = false;
               if (rotationDifference >= 5.0F && this.modeSetting.getValue() != SprintModule.ModeChoice.LEGIT) {
                  hasLargeRotation = true;
               }

               if (!this.shouldDeferToVanillaSprinting() || hasLargeRotation) {
                  RotationRequest request = ClientContext.rotationManager.getRequest();
                  boolean hasForwardMovement = ClientAccess.argusclientMinecraft.player.input.hasForwardImpulse() || hasLargeRotation;
                  boolean hasHigherPriority = request != null && request.priority() > 0;
                  if (this.directionSpoofSetting.getValue() && !hasHigherPriority && hasForwardMovement) {
                     ClientLevel level = ClientAccess.argusclientMinecraft.level;
                     BlockPos hitPosition = event.getHitResult().getBlockPos();
                     if (level.getBlockEntity(hitPosition) == null) {
                        InteractionHand hand = event.getHand();
                        if (this.hasFoodComponentInHand(hand)) {
                           MultiPlayerGameMode gameMode = ClientAccess.argusclientMinecraft.gameMode;
                           LocalPlayer player = ClientAccess.argusclientMinecraft.player;
                           InteractionHand useHand = event.getHand();
                           gameMode.useItem(player, useHand);
                           event.cancel();
                           return;
                        }
                     }

                     LocalPlayerTickAccess serverRotations = (LocalPlayerTickAccess)ClientAccess.argusclientMinecraft.player;
                     double x = ClientAccess.argusclientMinecraft.player.getX();
                     double y = ClientAccess.argusclientMinecraft.player.getY();
                     double z = ClientAccess.argusclientMinecraft.player.getZ();
                     float yaw = ClientAccess.argusclientMinecraft.player.getYRot();
                     float pitch = ClientAccess.argusclientMinecraft.player.getXRot();
                     boolean onGround = ClientAccess.argusclientMinecraft.player.onGround();
                     PacketSender.sendPositionRotation(x, y, z, yaw, pitch, onGround);
                     float serverYaw = ClientAccess.argusclientMinecraft.player.getYRot();
                     serverRotations.argusclient$updateServer(serverYaw, ClientAccess.argusclientMinecraft.player.getXRot());
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private void onClientPacketListenerHandleExplosionEvent(ClientPacketListenerHandleExplosionEvent event) {
      if (!ClientContext.baritoneBridge.isPathingOrCalculating()) {
         Vec3 explosionPosition = new Vec3(event.getCenterX(), event.getCenterY(), event.getCenterZ());
         if (MovementPhysics.isLocalPlayerMoving()) {
            if (ClientContext.serverStateManager.getSetbackTimer().hasElapsed(750L)) {
               if (this.boostSetting.getValue()) {
                  double squaredDistance = ClientAccess.argusclientMinecraft.player.distanceToSqr(explosionPosition);
                  double distance = Math.sqrt(squaredDistance);
                  if (!(distance > 6.0)) {
                     this.explosionBoostTimer.reset();
                     double impulseX = event.getVelocityX();
                     double impulseZ = event.getVelocityZ();
                     float horizontalImpulse = (float)Math.hypot(impulseX, impulseZ);
                     Float boostMultiplier = this.boostSpeedSetting.getValue();
                     this.explosionBoostSpeed = horizontalImpulse * boostMultiplier;
                  }
               }
            }
         }
      }
   }

   @EventHandler(priority = 1000)
   private void onLocalPlayerMoveEvent(LocalPlayerMoveEvent event) {
      if (PlayerStateUtils.isFallFlying()) {
         this.fallFlyingSinceGround = true;
      } else if (ClientAccess.argusclientMinecraft.player.onGround()) {
         this.fallFlyingSinceGround = false;
      }

      if (!ClientContext.baritoneBridge.isPathingOrCalculating() && !this.isWorldUnavailable()) {
         if (!this.isSprintSuppressed()) {
            if (this.modeSetting.getValue() == SprintModule.ModeChoice.INSTANT) {
               if (!PlayerStateUtils.isHungerLimited()) {
                  if (!PlayerStateUtils.isFallFlying()) {
                     if (!ClientAccess.argusclientMinecraft.player.getAbilities().flying) {
                        if (!ClientAccess.argusclientMinecraft.player.isInWater()) {
                           if (!ClientAccess.argusclientMinecraft.player.isInLava()) {
                              if (!ClientAccess.argusclientMinecraft.player.isShiftKeyDown()) {
                                 if (!ClientAccess.argusclientMinecraft.player.isVisuallySwimming() && !fastFallModule.suppressSprint) {
                                    if (!longJumpModule.isToggled()) {
                                       if (!speedModule.isToggled()) {
                                          if (ClientAccess.argusclientMinecraft.player.onGround() || !this.fallFlyingSinceGround) {
                                             Boolean slowdownEnabled = this.vanillaSlowdownSetting.getValue();
                                             double movementSpeed = MovementPhysics.getBaseMoveSpeed(slowdownEnabled);
                                             if (this.adjustSetting.getValue()) {
                                                Float multiplier = this.factorSetting.getValue();
                                                movementSpeed *= multiplier.floatValue();
                                             }

                                             if (ClientContext.serverStateManager.getSetbackTimer().hasElapsed(500L)) {
                                                double currentX = event.getX();
                                                double currentZ = event.getZ();
                                                double currentSpeed = Math.hypot(currentX, currentZ);
                                                movementSpeed = Math.max(movementSpeed, currentSpeed);
                                             }

                                             if (this.vanillaSlowdownSetting.getValue()) {
                                                ClientLevel level = ClientAccess.argusclientMinecraft.level;
                                                BlockPos velocityPosition = ((EntityAccessor)ClientAccess.argusclientMinecraft.player)
                                                   .argusclient$getVelocityAffectingPos();
                                                Block velocityBlock = level.getBlockState(velocityPosition).getBlock();
                                                if (velocityBlock == Blocks.SLIME_BLOCK && !noSlowModule.isSlimeSlowdownDisabled()) {
                                                   movementSpeed *= 0.7;
                                                }

                                                movementSpeed *= velocityBlock.getSpeedFactor();
                                             }

                                             if (this.boostSetting.getValue() && !this.explosionBoostTimer.hasElapsed(500L)) {
                                                movementSpeed += this.explosionBoostSpeed;
                                             }

                                             MovementPhysics.applyHorizontalMotion(event, movementSpeed);
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
            }
         }
      }
   }

   @EventHandler
   private void onLocalPlayerAiStepEvent(LocalPlayerAiStepEvent event) {
      if (!ClientContext.baritoneBridge.isPathingOrCalculating() && !this.isWorldUnavailable()) {
         if (!this.isSprintSuppressed()) {
            if (!this.shouldDeferToVanillaSprinting()) {
               if (!RotationManager.isMovementSyncEnabled()
                  || !this.directionSpoofSetting.getValue()
                  || !this.consumeMoveVectorChange() && !(ClientAccess.argusclientMinecraft.player.zza <= 0.0F)) {
                  boolean sprinting = this.canStartSprinting();
                  this.setPlayerSprinting(sprinting);
               } else {
                  this.setPlayerSprinting(false);
               }
            }
         }
      }
   }

   @EventHandler
   private void onPlayerAttackEvent(PlayerAttackEvent event) {
      if (!ClientContext.baritoneBridge.isPathingOrCalculating() && this.keepSetting.getValue()) {
         if (event.getStage() == EventStage.Pre) {
            LocalPlayer sprintPlayer = ClientAccess.argusclientMinecraft.player;
            this.wasSprintingBeforeAttack = sprintPlayer.isSprinting();
            if (this.wasSprintingBeforeAttack) {
               LocalPlayer packetPlayer = ClientAccess.argusclientMinecraft.player;
               PacketSender.sendPlayerCommand(packetPlayer, Action.STOP_SPRINTING);
            }
         } else if (this.wasSprintingBeforeAttack) {
            LocalPlayer sprintPlayer = ClientAccess.argusclientMinecraft.player;
            sprintPlayer.setSprinting(true);
            LocalPlayer packetPlayer = ClientAccess.argusclientMinecraft.player;
            PacketSender.sendPlayerCommand(packetPlayer, Action.START_SPRINTING);
         }
      }
   }

   private boolean hasWaterBelowWhileAirborne() {
      if (ClientAccess.argusclientMinecraft.player.onGround()) {
         return false;
      }

      ClientLevel eyeLevel = ClientAccess.argusclientMinecraft.level;
      Vec3 eyePosition = ClientAccess.argusclientMinecraft.player.getEyePosition();
      BlockPos eyeBlockPosition = BlockPos.containing(eyePosition);
      if (eyeLevel.isWaterAt(eyeBlockPosition)) {
         return false;
      }

      AABB expandedBounds = ClientAccess.argusclientMinecraft.player.getBoundingBox().expandTowards(0.0, -0.1, 0.0);
      Stream<BlockPos> positions = BlockPos.betweenClosedStream(expandedBounds);
      Predicate<BlockPos> isWater = position -> ClientAccess.argusclientMinecraft.level.isWaterAt(position);
      return positions.anyMatch(isWater);
   }

   private boolean isSprintSuppressed() {
      if (ClientContext.inputOverrides.isSprintBlocked()) {
         return true;
      } else if (noSlowModule.isUsingItemInConstantiamMode()) {
         return true;
      } else if (ClientAccess.argusclientMinecraft.player.isShiftKeyDown()) {
         return true;
      } else if (ClientContext.baritoneBridge.isAvailable() && ClientContext.baritoneBridge.isPathingOrCalculating()) {
         return true;
      } else {
         return PlayerStateUtils.isInLiquid(ClientAccess.argusclientMinecraft.player) && this.stopInLiquidsSetting.getValue()
            ? true
            : elytraFlyModule.isToggled() && elytraFlyModule.modeSetting.getValue() == ElytraFlyModule.ModeChoice.BOUNCE && elytraFlyModule.isElytraEquipped();
      }
   }

   public boolean shouldDeferToVanillaSprinting() {
      if (ClientContext.inputOverrides.isSprintBlocked()) {
         return false;
      }

      if (antiCheatModule.is2b2tEnabled()
         && ClientAccess.argusclientMinecraft.player != null
         && ClientAccess.argusclientMinecraft.player.containerMenu != ClientAccess.argusclientMinecraft.player.inventoryMenu) {
         return true;
      }

      if (this.isSprintInBlocksRestricted() && WallsVelocityHandler.hasUsableGroundCollision()) {
         return true;
      }

      if (this.fallFlyingSinceGround) {
         return true;
      }

      RotationManager rotationManager = ClientContext.rotationManager;
      boolean hasUnreadyRotation = false;
      if (rotationManager.getRequest() != null && !ClientContext.rotationManager.isRequestedYawUsedForMovement()) {
         hasUnreadyRotation = true;
      }

      if (this.directionSpoofSetting.getValue()) {
         Setting<SprintModule.ModeChoice> mode = this.modeSetting;
         if (mode.getValue() == SprintModule.ModeChoice.RAGE && hasUnreadyRotation) {
            return true;
         }
      }

      Setting<SprintModule.ModeChoice> mode = this.modeSetting;
      return mode.getValue() == SprintModule.ModeChoice.LEGIT;
   }

   public void setPlayerSprinting(boolean sprinting) {
      try {
         ClientAccess.argusclientMinecraft.player.setSprinting(sprinting);
      } catch (Exception var3) {
      }
   }

   private boolean consumeMoveVectorChange() {
      Vec2 currentVector = ClientAccess.argusclientMinecraft.player.input.moveVector;
      boolean changed;
      if (this.previousMoveVector != null) {
         changed = !currentVector.equals(this.previousMoveVector);
      } else {
         changed = true;
      }

      this.previousMoveVector = currentVector;
      return changed;
   }

   private float getRotationDifference() {
      float[] rotations = ClientContext.playerStateTracker.getPacketRotations();
      float sourceYaw = rotations[0];
      float playerYaw = ClientAccess.argusclientMinecraft.player.getYRot();
      float yawDifference = Mth.degreesDifferenceAbs(sourceYaw, playerYaw);
      float sourcePitch = rotations[1];
      float pitchDifference = sourcePitch - ClientAccess.argusclientMinecraft.player.getXRot();
      return yawDifference + Math.abs(pitchDifference);
   }

   private boolean hasMovementSpeedFactorChange() {
      Vec2 inputVector = ClientAccess.argusclientMinecraft.player.input.moveVector;
      Vec2 adjustedVector = ((LocalPlayerAccessor)ClientAccess.argusclientMinecraft.player).argusclient$applyMovementSpeedFactors(inputVector);
      float inputLength = inputVector.length();
      float lengthDifference = inputLength - adjustedVector.lengthSquared();
      double absoluteDifference = Math.abs(lengthDifference);
      return absoluteDifference > 0.05;
   }

   public boolean isSprintInBlocksRestricted() {
      return this.inBlocksSetting.isVisible() ? !this.inBlocksSetting.getValue() : false;
   }

   public boolean canStartSprinting() {
      if (this.isSprintSuppressed()) {
         return false;
      }

      if (this.directionSpoofSetting.getValue() && PlayerStateUtils.isFallFlying()) {
         return false;
      }

      if (PlayerStateUtils.isHungerLimited()) {
         return false;
      }

      if (this.hasWaterBelowWhileAirborne()) {
         return false;
      }

      if (this.shouldDeferToVanillaSprinting()) {
         return false;
      }

      if (ClientAccess.argusclientMinecraft.player.horizontalCollision && !ClientAccess.argusclientMinecraft.player.minorHorizontalCollision) {
         return false;
      }

      if (this.hasMovementSpeedFactorChange()) {
         return true;
      }

      if (fastWebModule.isGrimEnabled() && PlayerStateUtils.isInCobweb(ClientAccess.argusclientMinecraft.player)) {
         int ticks = ClientContext.playerStateTracker.getGroundedTicks();
         if (ticks < 2) {
            return false;
         }

         if (ClientAccess.argusclientMinecraft.player.input.keyPresses.jump()) {
            return false;
         }
      }

      return ClientAccess.argusclientMinecraft.player.input.getMoveVector().lengthSquared() > 0.0F;
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
         this.explosionBoostSpeed = 0.0F;
      }
   }

   @EventHandler(priority = 100)
   private void onDirectionUpdateEvent(RotationRequestEvent event) {
      if (this.modeSetting.getValue() != SprintModule.ModeChoice.INSTANT) {
         this.updateDirectionSpoof(event);
      }
   }

   private boolean hasFoodComponentInHand(InteractionHand hand) {
      return ClientAccess.argusclientMinecraft.player.getItemInHand(hand).has(DataComponents.FOOD);
   }

   public static void bindReferencedModules(ModuleRegistry registry) {
      elytraFlyModule = (ElytraFlyModule)registry.getModule(ElytraFlyModule.class);
      longJumpModule = (LongJumpModule)registry.getModule(LongJumpModule.class);
      fastFallModule = (FastFallModule)registry.getModule(FastFallModule.class);
      speedModule = (SpeedModule)registry.getModule(SpeedModule.class);
      fastWebModule = (FastWebModule)registry.getModule(FastWebModule.class);
      antiCheatModule = (AntiCheatModule)registry.getModule(AntiCheatModule.class);
      noSlowModule = (NoSlowModule)registry.getModule(NoSlowModule.class);
      flightModule = (FlightModule)registry.getModule(FlightModule.class);
   }

   public SprintModule() {
      super("Sprint", "Sprints automatically.", ModuleCategory.MOVEMENT);
      Predicate<Object> inBlocksVisibility = ignored -> this.modeSetting.getValue() == SprintModule.ModeChoice.RAGE;
      this.inBlocksSetting = this.add(new BooleanSetting("InBlocks", true, inBlocksVisibility));
      this.directionSpoofSetting = this.add(new BooleanSetting("DirectionSpoof", false));
      Predicate<Object> keepVisibility = ignored -> this.modeSetting.getValue() != SprintModule.ModeChoice.INSTANT;
      this.keepSetting = this.add(new BooleanSetting("Keep", true, keepVisibility));
      Predicate<Object> slowdownVisibility = ignored -> this.modeSetting.getValue() == SprintModule.ModeChoice.INSTANT;
      this.vanillaSlowdownSetting = this.add(new BooleanSetting("VanillaSlowdown", false, slowdownVisibility));
      Predicate<Object> adjustVisibility = ignored -> this.modeSetting.getValue() == SprintModule.ModeChoice.INSTANT;
      this.adjustSetting = this.add(new BooleanSetting("Adjust", false, adjustVisibility).markVisibilityDependency());
      Predicate<Object> factorVisibility = ignored -> this.modeSetting.getValue() == SprintModule.ModeChoice.INSTANT;
      this.factorSetting = this.add(new NumberSetting<>("Factor", 1.0F, 0.5F, 2.0F, factorVisibility).dependsOn(this.adjustSetting));
      Predicate<Object> boostVisibility = ignored -> this.modeSetting.getValue() == SprintModule.ModeChoice.INSTANT;
      this.boostSetting = this.add(new BooleanSetting("Boost", false, boostVisibility).markVisibilityDependency());
      Predicate<Object> boostSpeedVisibility = ignored -> this.modeSetting.getValue() == SprintModule.ModeChoice.INSTANT && this.boostSetting.isExpanded();
      this.boostSpeedSetting = this.add(new NumberSetting<>("BoostSpeed", 0.5F, 0.1F, 1.0F, boostSpeedVisibility));
      this.explosionBoostTimer = new ElapsedTimer();
      this.previousMoveVector = null;
      this.explosionBoostSpeed = 0.0F;
      Setting<Boolean> stopVisibilitySetting = this.stopInLiquidsSetting;
      Predicate<Boolean> stopVisibility = ignored -> this.modeSetting.getValue() != SprintModule.ModeChoice.LEGIT;
      stopVisibilitySetting.addVisibilityPredicate(stopVisibility);
      Setting<Boolean> directionVisibilitySetting = this.directionSpoofSetting;
      Predicate<Boolean> directionVisibility = ignored -> this.modeSetting.getValue() != SprintModule.ModeChoice.LEGIT;
      directionVisibilitySetting.addVisibilityPredicate(directionVisibility);
      this.adjustSetting.disableNumericBounds();
   }

   public enum ModeChoice implements NamedEntry {
      LEGIT("Vanilla"),
      RAGE("Rage"),
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
