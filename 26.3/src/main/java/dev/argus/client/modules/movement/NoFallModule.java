package dev.argus.client.modules.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.EventStage;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.events.LocalPlayerSendPositionEvent;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.combat.BlockRaycastContext;
import dev.argus.client.utils.combat.RaycastUtils;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.network.PacketSender;
import dev.argus.client.utils.world.WorldUtils;
import dev.argus.mixin.ServerboundMovePlayerPacketAccessor;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public class NoFallModule extends Module {
   final Setting<NoFallModule.ModeChoice> modeSetting = this.add(new EnumSetting<>("Mode", NoFallModule.ModeChoice.PACKET));
   private final Setting<Float> groundDistanceSetting;
   private double previousFallDistance;
   private boolean predictedTopFaceCollision;

   @Override
   public String getInfo() {
      return this.modeSetting.getValue().getName();
   }

   @EventHandler
   private void onConnectionSendPacketEvent(PacketEvent.Send event) {
      NoFallModule.ModeChoice mode = this.modeSetting.getValue();
      mode.handleOutgoingPacket(event);
   }

   @EventHandler
   private void onLocalPlayerPreTickEvent(LocalPlayerPreTickEvent event) {
      NoFallModule.ModeChoice mode = this.modeSetting.getValue();
      mode.beforePlayerTick();
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      NoFallModule.ModeChoice mode = this.modeSetting.getValue();
      mode.afterPlayerTick();
   }

   private HashSet<BlockPos> collectBottomCorners(AABB box) {
      return new HashSet<>(
         List.of(
            BlockPos.containing(box.maxX, box.minY, box.maxZ),
            BlockPos.containing(box.maxX, box.minY, box.minZ),
            BlockPos.containing(box.minX, box.minY, box.maxZ),
            BlockPos.containing(box.minX, box.minY, box.minZ)
         )
      );
   }

   @EventHandler
   private void onLocalPlayerSendPositionEvent(LocalPlayerSendPositionEvent event) {
      if (event.getStage() == EventStage.Pre) {
         this.updatePredictedTopFaceCollision();
      }

      NoFallModule.ModeChoice mode = this.modeSetting.getValue();
      mode.handleSendPosition(event);
      this.previousFallDistance = ClientContext.playerStateTracker.getFallDistance();
   }

   private void updatePredictedTopFaceCollision() {
      Vec3 currentPosition = ClientAccess.argusclientMinecraft.player.position();
      double previousX = ClientAccess.argusclientMinecraft.player.xo;
      double previousY = ClientAccess.argusclientMinecraft.player.yo;
      double previousZ = ClientAccess.argusclientMinecraft.player.zo;
      Vec3 prediction = currentPosition.subtract(previousX, previousY, previousZ).scale(1.1F);
      BlockRaycastContext.Builder builder = new BlockRaycastContext.Builder(
         ClientAccess.argusclientMinecraft.player.position(), ClientAccess.argusclientMinecraft.player.position().add(prediction)
      );
      BlockRaycastContext context = builder.build();
      BlockHitResult hit = RaycastUtils.raycast(context);
      if (hit.getType() == Type.MISS) {
         this.predictedTopFaceCollision = false;
      } else {
         this.predictedTopFaceCollision = hit.getDirection() == Direction.UP;
      }
   }

   public NoFallModule() {
      super("NoFall", "Prevents falling damage.", ModuleCategory.MOVEMENT);
      Predicate<Object> groundDistanceVisible = ignored -> this.modeSetting.getValue() == NoFallModule.ModeChoice.MLG;
      this.groundDistanceSetting = this.add(new NumberSetting<>("GroundDistance", 2.0F, 1.0F, 4.0F, groundDistanceVisible).withUnit("m"));

      for (NoFallModule.ModeChoice choice : NoFallModule.ModeChoice.values()) {
         choice.module = this;
      }
   }

   public enum ModeChoice implements NamedEntry {
      PACKET("Packet") {
         @Override
         public void handleOutgoingPacket(PacketEvent.Send event) {
            if (event.getPacket() instanceof ServerboundMovePlayerPacket movementPacket
               && PlayerStateUtils.getTrackedFallDistance() > 2.1
               && !PlayerStateUtils.isFallFlying()) {
               ((ServerboundMovePlayerPacketAccessor)movementPacket).setOnGround(true);
            }
         }

         @Override
         public void handleSendPosition(LocalPlayerSendPositionEvent event) {
            if (ClientAccess.argusclientMinecraft.player.isFallFlying()) {
               NoFallModule.ModeChoice.GRIM.handleSendPosition(event);
            }
         }
      },
      ANTI("Anti") {
         @Override
         public void handleOutgoingPacket(PacketEvent.Send event) {
            if (event.getPacket() instanceof ServerboundMovePlayerPacket && PlayerStateUtils.getTrackedFallDistance() > 3.0F && this.canApplyFallProtection()) {
               ClientLevel world = ClientAccess.argusclientMinecraft.level;
               AABB bounds = ClientAccess.argusclientMinecraft.player.getBoundingBox();
               double verticalExpansion = ClientAccess.argusclientMinecraft.player.getDeltaMovement().y * 1.1;
               AABB expandedBounds = bounds.expandTowards(0.0, verticalExpansion, 0.0);
               if (!world.noCollision(expandedBounds)) {
                  LocalPlayer target = ClientAccess.argusclientMinecraft.player;
                  double x = ClientAccess.argusclientMinecraft.player.getX();
                  double y = ClientAccess.argusclientMinecraft.player.getY() + 2.0;
                  double z = ClientAccess.argusclientMinecraft.player.getZ();
                  target.setPosRaw(x, y, z);
               }
            }
         }
      },
      TELEPORT("Teleport") {
         private boolean teleportPending;

         @Override
         public void afterPlayerTick() {
            if (PlayerStateUtils.getTrackedFallDistance() > 3.0F) {
               this.teleportPending = true;
            }
         }

         @Override
         public void handleSendPosition(LocalPlayerSendPositionEvent event) {
            if (this.teleportPending) {
               if (event.isOnGround()) {
                  if (event.getStage() == EventStage.Pre) {
                     if (this.canApplyFallProtection()) {
                        event.setX(0.0);
                        event.setY(0.0);
                        event.setZ(0.0);
                        event.setOnGround(false);
                        if (WorldUtils.getDimension().isEnd()) {
                           event.setY(64.0);
                        }

                        this.teleportPending = false;
                     }
                  }
               }
            }
         }
      },
      MLG("MLG") {
         private boolean skipNextMlgTick;

         @Override
         public void afterPlayerTick() {
            if (this.skipNextMlgTick) {
               this.skipNextMlgTick = false;
            } else {
               InventoryUtils.SwapSlot bucket = InventoryUtils.findSwapSlot(Items.WATER_BUCKET, true);
               int selectedSlot = ClientAccess.argusclientMinecraft.player.getInventory().getSelectedSlot();
               if (!(PlayerStateUtils.getTrackedFallDistance() <= 3.0F)) {
                  if (bucket.isFound()) {
                     if (!ClientAccess.argusclientMinecraft.player.isFallFlying()) {
                        NoFallModule owner = this.module;
                        AABB bounds = ClientAccess.argusclientMinecraft.player.getBoundingBox();
                        HashSet<BlockPos> positions = owner.collectBottomCorners(bounds);
                        Vec3 playerPosition = ClientAccess.argusclientMinecraft.player.position();
                        positions.add(BlockPos.containing(playerPosition));

                        for (int depth = 0; depth <= this.module.groundDistanceSetting.getValue(); depth++) {
                           for (BlockPos position : positions) {
                              ClientLevel blockWorld = ClientAccess.argusclientMinecraft.level;
                              BlockPos below = position.relative(Direction.DOWN, depth);
                              BlockState state = blockWorld.getBlockState(below);
                              if (!(state.getBlock() instanceof AirBlock)) {
                                 ClientLevel fluidWorld = ClientAccess.argusclientMinecraft.level;
                                 if (fluidWorld.getFluidState(position).is(FluidTags.WATER)) {
                                    return;
                                 }

                                 LocalPlayer pitchPlayer = ClientAccess.argusclientMinecraft.player;
                                 float pitch = 90.0F;
                                 pitchPlayer.setXRot(pitch);
                                 InventoryUtils.swapTo(bucket);
                                 MultiPlayerGameMode gameMode = ClientAccess.argusclientMinecraft.gameMode;
                                 LocalPlayer usePlayer = ClientAccess.argusclientMinecraft.player;
                                 InteractionHand hand = bucket.hand();
                                 gameMode.useItem(usePlayer, hand);
                                 LocalPlayer swingPlayer = ClientAccess.argusclientMinecraft.player;
                                 InteractionHand swingHand = bucket.hand();
                                 swingPlayer.swing(swingHand);
                                 InventoryUtils.swapBack(selectedSlot, bucket);
                                 this.skipNextMlgTick = true;
                                 return;
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      },
      GRIM("Grim") {
         @Override
         public void handleSendPosition(LocalPlayerSendPositionEvent event) {
            if (event.getStage() != EventStage.Post) {
               if (ClientAccess.argusclientMinecraft.player.onGround() && !(this.module.previousFallDistance < 3.0)) {
                  Vec3 position = ClientContext.playerStateTracker.getPacketPosition();
                  double x = position.x();
                  double y = position.y() + 1.0E-9;
                  double z = position.z();
                  float yaw = ClientAccess.argusclientMinecraft.player.getYRot();
                  float pitch = ClientAccess.argusclientMinecraft.player.getXRot();
                  PacketSender.sendPositionRotation(x, y, z, yaw, pitch, false);
               }
            }
         }
      },
      GRIM_JUMP("GrimJump") {
         private boolean jumpPending;

         @Override
         public void handleOutgoingPacket(PacketEvent.Send event) {
            if (event.getPacket() instanceof ServerboundMovePlayerPacketAccessor movementPacket
               && (PlayerStateUtils.getTrackedFallDistance() > 3.0F || this.jumpPending)
               && !ClientAccess.argusclientMinecraft.player.isFallFlying()) {
               movementPacket.setOnGround(false);
               this.jumpPending = true;
            }
         }

         @Override
         public void beforePlayerTick() {
            if (this.jumpPending) {
               if (ClientAccess.argusclientMinecraft.player.onGround()) {
                  ClientContext.inputOverrides.requestJump(true);
                  this.jumpPending = false;
               }
            }
         }
      };

      private final String displayName;
      protected NoFallModule module;

      public void afterPlayerTick() {
      }

      @Override
      public String getName() {
         return this.displayName;
      }

      ModeChoice(String displayName) {
         this.displayName = displayName;
      }

      public void handleOutgoingPacket(PacketEvent.Send event) {
      }

      public void handleSendPosition(LocalPlayerSendPositionEvent event) {
      }

      public void beforePlayerTick() {
      }

      protected boolean canApplyFallProtection() {
         if (!ClientAccess.argusclientMinecraft.player.isFallFlying()) {
            return true;
         }

         float pitch = ClientContext.playerStateTracker.getPacketRotations()[1];
         return Math.abs(pitch) <= 45.0F || this.module.predictedTopFaceCollision;
      }
   }
}
