package dev.argus.client.modules.player;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.combat.BlockRaycastClassifier;
import dev.argus.client.utils.combat.BlockRaycastContext;
import dev.argus.client.utils.combat.BlockRaycastOverride;
import dev.argus.client.utils.combat.RaycastUtils;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public class AutoFishModule extends Module {
   private final Setting<Boolean> waterSetting;
   private final Setting<Float> recastDelaySetting;
   private final ElapsedTimer castTimer;
   private boolean nearbySplashDetected;

   @Override
   public void onEnable() {
      this.castTimer.reset();
   }

   private void castIfNoHook(InteractionHand hand) {
      if (ClientAccess.argusclientMinecraft.player.fishing == null) {
         this.useRod(hand);
      }

      this.nearbySplashDetected = false;
   }

   private void useRod(InteractionHand hand) {
      MultiPlayerGameMode gameMode = ClientAccess.argusclientMinecraft.gameMode;
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      gameMode.useItem(player, hand);
      ClientAccess.argusclientMinecraft.player.swing(hand);
      this.castTimer.reset();
   }

   @EventHandler
   private void updateFishing(LocalPlayerPreTickEvent event) {
      ItemStack capturedOffhand = ClientAccess.argusclientMinecraft.player.getOffhandItem();
      Item offhandRod = Items.FISHING_ROD;
      InteractionHand rodHand = capturedOffhand.is(offhandRod) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
      Item rod = Items.FISHING_ROD;
      LocalPlayer holdingPlayer = ClientAccess.argusclientMinecraft.player;
      if (holdingPlayer.isHolding(rod)) {
         if (this.nearbySplashDetected) {
            this.castIfNoHook(rodHand);
         }

         if (!this.waterSetting.getValue() || !this.isWaterTargetMissing()) {
            ElapsedTimer capturedFallbackTimer = this.castTimer;
            TimeUnit fallbackUnit = TimeUnit.MINUTES;
            if (!capturedFallbackTimer.hasElapsed(1.0, fallbackUnit)) {
               if (ClientAccess.argusclientMinecraft.player.fishing != null) {
                  return;
               }

               ElapsedTimer capturedRecastTimer = this.castTimer;
               double recastSeconds = this.recastDelaySetting.getValue().floatValue();
               TimeUnit recastUnit = TimeUnit.SECONDS;
               if (!capturedRecastTimer.hasElapsed(recastSeconds, recastUnit)) {
                  return;
               }
            }

            this.useRod(rodHand);
         }
      }
   }

   @EventHandler
   private void detectNearbySplash(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundSoundPacket soundPacket) {
         Holder<SoundEvent> sound = soundPacket.getSound();
         Predicate<ResourceKey<SoundEvent>> splashSound = key -> key.identifier().toString().equalsIgnoreCase("minecraft:entity.fishing_bobber.splash");
         if (sound.is(splashSound) && ClientAccess.argusclientMinecraft.player.fishing != null) {
            Vec3 capturedSoundPosition = new Vec3(soundPacket.getX(), soundPacket.getY(), soundPacket.getZ());
            Vec3 hookPosition = ClientAccess.argusclientMinecraft.player.fishing.position();
            if (capturedSoundPosition.distanceTo(hookPosition) <= 1.0) {
               this.nearbySplashDetected = true;
            }
         }
      }
   }

   private boolean isWaterTargetMissing() {
      Vec3 lookDirection = ClientAccess.argusclientMinecraft.player.getLookAngle();
      Vec3 eyePosition = ClientAccess.argusclientMinecraft.player.getEyePosition();
      BlockRaycastContext.Builder builder = new BlockRaycastContext.Builder(eyePosition, eyePosition.add(lookDirection.scale(8.0)));
      BlockRaycastClassifier waterClassifier = (position, blockState) -> {
         Block waterBlock = Blocks.WATER;
         return blockState.is(waterBlock) ? BlockRaycastOverride.SOLID : BlockRaycastOverride.NONE;
      };
      builder.withClassifier(waterClassifier);
      BlockRaycastContext context = builder.build();
      if (RaycastUtils.raycast(context) instanceof BlockHitResult hit && hit.getType() != Type.MISS) {
         ClientLevel capturedLevel = ClientAccess.argusclientMinecraft.level;
         BlockPos hitPosition = hit.getBlockPos();
         BlockState state = capturedLevel.getBlockState(hitPosition);
         Block water = Blocks.WATER;
         return !state.is(water);
      } else {
         return true;
      }
   }

   public AutoFishModule() {
      super("AutoFish", "Fishes.", ModuleCategory.PLAYER);
      Boolean defaultWater = false;
      this.waterSetting = this.add(new BooleanSetting("Water", defaultWater));
      this.recastDelaySetting = this.add(new NumberSetting<>("RecastDelay", 2.0F, 1.0F, 5.0F).withUnit("s"));
      this.castTimer = new ElapsedTimer();
   }
}
