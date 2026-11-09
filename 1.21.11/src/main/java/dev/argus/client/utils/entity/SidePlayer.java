package dev.argus.client.utils.entity;

import com.mojang.authlib.GameProfile;
import dev.argus.client.ClientAccess;
import dev.argus.client.utils.inventory.ArmorSlot;
import dev.argus.mixin.LivingEntityAccessor;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec3;

public class SidePlayer extends RemotePlayer {
   public static final String SIDE_PLAYER_TAG = "argusclient_side_player";
   private final Set<PlayerModelPart> visibleModelParts = new HashSet<>();
   private PlayerSkin capturedSkin;
   private boolean animationBoosted;
   private Vec3 capturedPosition;
   private int capturedTickCount;

   public void setAnimationBoosted(boolean boosted) {
      this.animationBoosted = boosted;
   }

   @Override
   public void setPos(double x, double y, double z) {
      super.setPos(x, y, z);
      this.xOld = x;
      this.yOld = y;
      this.zOld = z;
   }

   @Override
   public void restoreFrom(Entity entity) {
      ProblemReporter outputProblems = ProblemReporter.DISCARDING;
      TagValueOutput output = TagValueOutput.createWithoutContext(outputProblems);
      boolean markerValue = true;
      String markerTag = "argusclient_side_player";
      output.putBoolean(markerTag, markerValue);
      entity.saveWithoutId(output);
      String dimensionTag = "Dimension";
      output.discard(dimensionTag);
      ProblemReporter problems = ProblemReporter.DISCARDING;
      RegistryAccess registries = entity.registryAccess();
      CompoundTag snapshotTag = output.buildResult();
      ValueInput input = TagValueInput.create(problems, registries, snapshotTag);
      this.load(input);
      this.portalProcess = entity.portalProcess;
   }

   public SidePlayer(ClientLevel level) {
      super(level, new GameProfile(UUID.randomUUID(), ""));
   }

   public SidePlayer(Player player) {
      super(Minecraft.getInstance().level, player.getGameProfile());
      this.copyPlayerState(player);
   }

   @Override
   public void aiStep() {
   }

   @Override
   public boolean isModelPartShown(PlayerModelPart modelPart) {
      return this.visibleModelParts.contains(modelPart);
   }

   public void readSnapshot(DataInputStream input) throws IOException {
      int snapshotTickCount = input.readInt();
      if (ClientAccess.argusclientMinecraft.player != null && ClientAccess.argusclientMinecraft.level != null && snapshotTickCount >= 0) {
         this.capturedTickCount = snapshotTickCount;
         double capturedX = input.readDouble();
         double capturedY = input.readDouble();
         double capturedZ = input.readDouble();
         this.capturedPosition = new Vec3(capturedX, capturedY, capturedZ);
         Vec3 eyePosition = ClientAccess.argusclientMinecraft.player.getEyePosition();
         Vec3 capturedPositionForDistance = this.capturedPosition;
         double capturedDistance = eyePosition.distanceTo(capturedPositionForDistance);
         if (capturedDistance >= 9999.0) {
            throw new IllegalStateException();
         }

         double positionX = input.readDouble();
         double positionY = input.readDouble();
         double positionZ = input.readDouble();
         LocalPlayer localPlayerForPositionDistance = ClientAccess.argusclientMinecraft.player;
         eyePosition = localPlayerForPositionDistance.getEyePosition();
         Vec3 snapshotPositionForDistance = new Vec3(positionX, positionY, positionZ);
         double positionDistance = eyePosition.distanceTo(snapshotPositionForDistance);
         if (positionDistance >= 9999.0) {
            throw new IllegalStateException();
         }

         float previousYawValue = input.readFloat();
         float previousYaw = Mth.wrapDegrees(previousYawValue);
         float previousPitchValue = input.readFloat();
         float unclampedPreviousPitch = previousPitchValue % 360.0F;
         float decodedPreviousPitchMinimum = -90.0F;
         float previousPitchMaximum = 90.0F;
         float previousPitchMinimum = decodedPreviousPitchMinimum;
         float previousPitchRemainder = unclampedPreviousPitch;
         float previousPitch = Math.clamp(previousPitchRemainder, previousPitchMinimum, previousPitchMaximum);
         float yawValue = input.readFloat();
         float yaw = Mth.wrapDegrees(yawValue);
         float pitchValue = input.readFloat();
         float unclampedPitch = pitchValue % 360.0F;
         float decodedPitchMinimum = -90.0F;
         float pitchMaximum = 90.0F;
         float pitchMinimum = decodedPitchMinimum;
         float pitchRemainder = unclampedPitch;
         float pitch = Math.clamp(pitchRemainder, pitchMinimum, pitchMaximum);
         float snapshotPitch = pitch;
         float snapshotYaw = yaw;
         double snapshotZ = positionZ;
         double snapshotY = positionY;
         double snapshotX = positionX;
         this.snapTo(snapshotX, snapshotY, snapshotZ, snapshotYaw, snapshotPitch);
         this.yRotO = previousYaw;
         float newYaw = yaw;
         this.setYRot(newYaw);
         this.xRotO = previousPitch;
         float newPitch = pitch;
         this.setXRot(newPitch);
         float bodyYaw = input.readFloat();
         this.yBodyRot = Mth.wrapDegrees(bodyYaw);
         float previousBodyYaw = input.readFloat();
         this.yBodyRotO = Mth.wrapDegrees(previousBodyYaw);
         float headYaw = input.readFloat();
         this.yHeadRot = Mth.wrapDegrees(headYaw);
         float previousHeadYaw = input.readFloat();
         this.yHeadRotO = Mth.wrapDegrees(previousHeadYaw);
         this.walkAnimation.speed = input.readFloat();
         this.walkAnimation.speedOld = this.walkAnimation.speed;
         this.walkAnimation.position = input.readFloat();
         if (!(this.walkAnimation.speed < 0.0F) && !(this.walkAnimation.position < 0.0F)) {
            float walkSpeed = this.walkAnimation.speed;
            if (!Float.isFinite(walkSpeed)) {
               throw new IllegalStateException();
            }

            float walkPosition = this.walkAnimation.position;
            if (!Float.isFinite(walkPosition)) {
               throw new IllegalStateException();
            }

            this.deathTime = 0;
            Vec3 zeroMotion = new Vec3(0.0, 0.0, 0.0);
            this.setDeltaMovement(zeroMotion);
            Pose pose = null;
            int poseId = input.readInt();

            for (Pose candidatePose : Pose.values()) {
               Pose poseCandidate = candidatePose;
               if (poseCandidate.id() == poseId) {
                  pose = candidatePose;
                  break;
               }
            }

            if (pose == null) {
               throw new IllegalStateException();
            }

            Pose selectedPose = pose;
            this.setPose(selectedPose);
            boolean sneaking = input.readBoolean();
            this.setShiftKeyDown(sneaking);
            DefaultedRegistry mainHandRegistry = BuiltInRegistries.ITEM;
            String mainHandIdentifier = input.readUTF();
            Identifier mainHandItemId = Identifier.parse(mainHandIdentifier);
            Item readMainHandItem = (Item)mainHandRegistry.getValue(mainHandItemId);
            DefaultedRegistry offHandRegistry = BuiltInRegistries.ITEM;
            String offHandIdentifier = input.readUTF();
            Identifier offHandItemId = Identifier.parse(offHandIdentifier);
            Item readOffHandItem = (Item)offHandRegistry.getValue(offHandItemId);
            InteractionHand mainHand = InteractionHand.MAIN_HAND;
            ItemStack mainHandStack = readMainHandItem.getDefaultInstance();
            this.setItemInHand(mainHand, mainHandStack);
            InteractionHand offHand = InteractionHand.OFF_HAND;
            ItemStack offHandStack = readOffHandItem.getDefaultInstance();
            this.setItemInHand(offHand, offHandStack);
            int armorCount = input.readInt();
            if (armorCount < 0) {
               throw new IllegalStateException();
            }

            if (armorCount > 4) {
               throw new IllegalStateException();
            }

            for (int armorIndex = 0; armorIndex < armorCount; armorIndex++) {
               DefaultedRegistry armorRegistry = BuiltInRegistries.ITEM;
               String armorIdentifier = input.readUTF();
               Identifier armorItemId = Identifier.parse(armorIdentifier);
               Item armorItem = (Item)armorRegistry.getValue(armorItemId);
               ItemStack armorStack = armorItem.getDefaultInstance();
               int armorSlot = -1;
               TagKey headArmorTag = ItemTags.HEAD_ARMOR;
               ItemStack headArmorStack = armorStack;
               if (headArmorStack.is(headArmorTag)) {
                  ArmorSlot helmetSlot = ArmorSlot.HELMET;
                  armorSlot = helmetSlot.getInventoryIndex();
               } else {
                  TagKey chestArmorTag = ItemTags.CHEST_ARMOR;
                  ItemStack chestArmorStack = armorStack;
                  if (!chestArmorStack.is(chestArmorTag) && armorItem != Items.ELYTRA) {
                     TagKey legArmorTag = ItemTags.LEG_ARMOR;
                     ItemStack legArmorStack = armorStack;
                     if (legArmorStack.is(legArmorTag)) {
                        ArmorSlot leggingsSlot = ArmorSlot.LEGGINGS;
                        armorSlot = leggingsSlot.getInventoryIndex();
                     } else {
                        TagKey footArmorTag = ItemTags.FOOT_ARMOR;
                        ItemStack footArmorStack = armorStack;
                        if (footArmorStack.is(footArmorTag)) {
                           ArmorSlot bootsSlot = ArmorSlot.BOOTS;
                           armorSlot = bootsSlot.getInventoryIndex();
                        }
                     }
                  } else {
                     ArmorSlot chestplateSlot = ArmorSlot.CHESTPLATE;
                     armorSlot = chestplateSlot.getInventoryIndex();
                  }
               }

               if (armorSlot != -1) {
                  Inventory snapshotInventory = this.getInventory();
                  ItemStack equippedArmorStack = armorStack;
                  int targetArmorSlot = armorSlot;
                  snapshotInventory.setItem(targetArmorSlot, equippedArmorStack);
               }
            }

            LivingEntityAccessor leanAccessor = (LivingEntityAccessor)this;
            float leanValue = input.readFloat();
            float unclampedLean = leanValue % 360.0F;
            float decodedLeanMinimum = -90.0F;
            float leanMaximum = 90.0F;
            float leanMinimum = decodedLeanMinimum;
            float leanRemainder = unclampedLean;
            leanAccessor.argusclient$setLeaningPitch(Math.clamp(leanRemainder, leanMinimum, leanMaximum));
            LivingEntityAccessor previousLeanAccessor = (LivingEntityAccessor)this;
            float previousLeanValue = input.readFloat();
            float unclampedPreviousLean = previousLeanValue % 360.0F;
            float decodedPreviousLeanMinimum = -90.0F;
            float previousLeanMaximum = 90.0F;
            float previousLeanMinimum = decodedPreviousLeanMinimum;
            float previousLeanRemainder = unclampedPreviousLean;
            previousLeanAccessor.argusclient$setLastLeaningPitch(Math.clamp(previousLeanRemainder, previousLeanMinimum, previousLeanMaximum));
            boolean fallFlying = input.readBoolean();
            LivingEntityAccessor glidingTicksAccessor = (LivingEntityAccessor)this;
            glidingTicksAccessor.argusclient$setGlidingTicks(input.readInt());
            if (this.getFallFlyingTicks() < 0) {
               throw new IllegalStateException();
            }

            if (fallFlying) {
               this.startFallFlying();
            }

            int animationTick = 0;

            while (true) {
               int snapshotTicksForAnimation = snapshotTickCount;
               if (animationTick >= Math.min(snapshotTicksForAnimation, 10)) {
                  double zeroZ = 0.0;
                  double zeroY = 0.0;
                  double zeroX = 0.0;
                  this.setDeltaMovement(zeroX, zeroY, zeroZ);
                  return;
               }

               this.elytraAnimationState.tick();
               animationTick++;
            }
         } else {
            throw new IllegalStateException();
         }
      } else {
         throw new IllegalStateException();
      }
   }

   public void copyPlayerState(Player player) {
      this.capturedTickCount = player.tickCount;
      this.capturedPosition = new Vec3(player.getX(), player.getY(), player.getZ());
      this.restoreFrom(player);
      this.copyPosition(player);
      this.yRotO = player.getYRot();
      float yaw = player.getYRot();
      this.setYRot(yaw);
      this.xRotO = player.getXRot();
      float pitch = player.getXRot();
      this.setXRot(pitch);
      this.yBodyRot = player.yBodyRot;
      this.yBodyRotO = player.yBodyRot;
      this.yHeadRot = player.yHeadRot;
      this.yHeadRotO = player.yHeadRot;
      this.walkAnimation.speed = player.walkAnimation.speed;
      this.walkAnimation.speedOld = player.walkAnimation.speed;
      this.walkAnimation.position = player.walkAnimation.position;
      this.deathTime = 0;
      Pose pose = player.getPose();
      this.setPose(pose);
      boolean sneaking = player.isShiftKeyDown();
      this.setShiftKeyDown(sneaking);
      if (player instanceof AbstractClientPlayer clientPlayer) {
         AbstractClientPlayer sourceForSkin = clientPlayer;
         this.capturedSkin = sourceForSkin.getSkin();

         for (PlayerModelPart modelPart : PlayerModelPart.values()) {
            if (modelPart != PlayerModelPart.CAPE) {
               PlayerModelPart candidateModelPart = modelPart;
               AbstractClientPlayer sourceForModelPart = clientPlayer;
               if (sourceForModelPart.isModelPartShown(candidateModelPart)) {
                  this.visibleModelParts.add(modelPart);
               }
            }
         }
      }

      int inventorySlot = 0;

      while (true) {
         Inventory inventoryForSize = this.getInventory();
         if (inventorySlot >= inventoryForSize.getContainerSize()) {
            InteractionHand hand = InteractionHand.MAIN_HAND;
            ItemStack sourceMainHandStack = player.getMainHandItem();
            ItemStack copiedMainHandStack = sourceMainHandStack.copy();
            InteractionHand mainHand = hand;
            this.setItemInHand(mainHand, copiedMainHandStack);
            InteractionHand var31 = InteractionHand.OFF_HAND;
            ItemStack sourceOffHandStack = player.getOffhandItem();
            ItemStack copiedOffHandStack = sourceOffHandStack.copy();
            InteractionHand offHand = var31;
            this.setItemInHand(offHand, copiedOffHandStack);
            Inventory inventoryForSelectedSlot = this.getInventory();
            Inventory sourceInventoryForSelectedSlot = player.getInventory();
            int selectedSlot = sourceInventoryForSelectedSlot.getSelectedSlot();
            inventoryForSelectedSlot.setSelectedSlot(selectedSlot);
            ((LivingEntityAccessor)this).argusclient$setLeaningPitch(((LivingEntityAccessor)player).argusclient$getLeaningPitch());
            ((LivingEntityAccessor)this).argusclient$setLastLeaningPitch(((LivingEntityAccessor)player).argusclient$getLastLeaningPitch());
            LivingEntityAccessor glidingTicksAccessor = (LivingEntityAccessor)this;
            glidingTicksAccessor.argusclient$setGlidingTicks(player.getFallFlyingTicks());
            Vec3 motion = player.getDeltaMovement();
            this.setDeltaMovement(motion);
            if (player.isFallFlying()) {
               this.startFallFlying();
            }

            int animationTick = 0;

            while (true) {
               int sourceTickCount = player.tickCount;
               int sourceTicksForAnimation = sourceTickCount;
               if (animationTick >= Math.min(sourceTicksForAnimation, 10)) {
                  double zeroZ = 0.0;
                  double zeroY = 0.0;
                  double zeroX = 0.0;
                  this.setDeltaMovement(zeroX, zeroY, zeroZ);
                  return;
               }

               this.elytraAnimationState.tick();
               animationTick++;
            }
         }

         Inventory targetInventory = this.getInventory();
         Inventory inventoryFromPlayer = player.getInventory();
         int sourceSlot = inventorySlot;
         ItemStack sourceStack = inventoryFromPlayer.getItem(sourceSlot);
         ItemStack copiedStack = sourceStack.copy();
         int targetSlot = inventorySlot;
         targetInventory.setItem(targetSlot, copiedStack);
         inventorySlot++;
      }
   }

   @Override
   public PlayerSkin getSkin() {
      return this.capturedSkin != null ? this.capturedSkin : super.getSkin();
   }

   public int getCapturedTickCount() {
      return this.capturedTickCount;
   }

   public Vec3 getCapturedPosition() {
      return this.capturedPosition;
   }

   public boolean isAnimationBoosted() {
      return this.animationBoosted;
   }
}
