package dev.argus.client.modules.render;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.managers.BlockColorRegistry;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.render.RenderUtils;
import dev.argus.client.settings.options.ESPModeChoice;
import dev.argus.client.utils.entity.EntityUtils;
import dev.argus.client.utils.math.MathUtils;
import java.awt.Color;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BedBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.MapColor.Brightness;
import net.minecraft.world.phys.Vec3;

public class ESPRenderHelper implements ClientAccess {
   private final ESPModule module;
   protected final HashMap<UUID, String> ownerNames = new HashMap<>();

   public boolean hasStorageTargets() {
      return this.module.chestsSetting.getValue()
         || this.module.enderChestsSetting.getValue()
         || this.module.shulkersSetting.getValue()
         || this.module.bedsSetting.getValue()
         || this.module.signsSetting.getValue()
         || this.module.dispensersSetting.getValue()
         || this.module.hoppersSetting.getValue()
         || this.module.furnacesSetting.getValue();
   }

   public ESPRenderHelper(ESPModule module) {
      this.module = module;
   }

   protected String getOwnerName(Entity entity) {
      UUID ownerUuid = null;
      if (entity instanceof ThrownEnderpearl pearl && pearl.getOwner() != null) {
         return pearl.getOwner().getName().getString();
      } else {
         if (entity instanceof TamableAnimal tamedAnimal && tamedAnimal.getOwnerReference() != null) {
            ownerUuid = tamedAnimal.getOwnerReference().getUUID();
         }

         if (ownerUuid != null) {
            Player ownerPlayer = ClientAccess.argusclientMinecraft.level.getPlayerByUUID(ownerUuid);
            if (ownerPlayer != null) {
               String ownerName = ownerPlayer.getGameProfile().name();
               this.ownerNames.putIfAbsent(ownerUuid, ownerName);
               return ownerName;
            }

            if (this.ownerNames.containsKey(ownerUuid)) {
               return this.ownerNames.get(ownerUuid);
            }

            this.ownerNames.put(ownerUuid, null);
            UUID requestedUuid = ownerUuid;
            ClientAccess.executorService.submit(() -> this.fetchOwnerName(requestedUuid));
         }

         return null;
      }
   }

   protected void drawLabel(WorldRenderEvent event, String text, Vec3 position, float scale, Color textColor, Color backgroundColor) {
      Vec3 cameraPosition = ClientAccess.argusclientMinecraft.gameRenderer.getMainCamera().position();
      double distanceScale = MathUtils.getDistanceScale(cameraPosition, position, scale);
      if (backgroundColor != null) {
         RenderUtils.INSTANCE
            .drawWorldRect(
               event.getPoseStack(),
               position,
               0.0F,
               0.0F,
               FontRenderer.INSTANCE.getWidth(text),
               FontRenderer.INSTANCE.getHeight(),
               distanceScale,
               backgroundColor
            );
      }

      RenderUtils.INSTANCE.drawWorldText(event.getPoseStack(), text, position, 0.0F, 0.0F, distanceScale, textColor, true);
   }

   private void fetchOwnerName(UUID ownerUuid) {
      try (HttpClient httpClient = HttpClient.newHttpClient()) {
         Builder requestBuilder = HttpRequest.newBuilder();
         requestBuilder = requestBuilder.uri(new URI("https://sessionserver.mojang.com/session/minecraft/profile/" + ownerUuid.toString().replace("-", "")));
         HttpRequest request = requestBuilder.timeout(Duration.of(3L, ChronoUnit.SECONDS)).GET().build();
         JsonObject profileJson = JsonParser.parseString(httpClient.sendAsync(request, BodyHandlers.ofString()).orTimeout(15L, TimeUnit.SECONDS).join().body())
            .getAsJsonObject();
         if (profileJson.has("name")) {
            this.ownerNames.put(ownerUuid, profileJson.get("name").getAsString());
         }
      } catch (Exception var8) {
      }
   }

   protected boolean shouldRenderBlockEntity(BlockEntity blockEntity) {
      return blockEntity == null
         ? false
         : blockEntity instanceof ChestBlockEntity && this.module.chestsSetting.getValue()
            || blockEntity instanceof BarrelBlockEntity && this.module.barrelsSetting.getValue()
            || blockEntity instanceof BedBlockEntity && this.module.bedsSetting.getValue()
            || blockEntity instanceof EnderChestBlockEntity && this.module.enderChestsSetting.getValue()
            || blockEntity instanceof ShulkerBoxBlockEntity && this.module.shulkersSetting.getValue()
            || blockEntity instanceof AbstractFurnaceBlockEntity && this.module.furnacesSetting.getValue()
            || blockEntity instanceof DispenserBlockEntity && this.module.dispensersSetting.getValue()
            || blockEntity instanceof HopperBlockEntity && this.module.hoppersSetting.getValue()
            || blockEntity instanceof SignBlockEntity && this.module.signsSetting.getValue()
            || blockEntity instanceof DecoratedPotBlockEntity && this.module.potsSetting.getValue();
   }

   protected Color getBaseEntityColor(Entity entity) {
      if (entity instanceof Player) {
         if (this.module.socialSetting.getValue()) {
            if (ClientContext.socialRegistry.isFriendEntity(entity)) {
               return ClientContext.socialRegistry.getFriendColor();
            }

            if (ClientContext.socialRegistry.isEnemyEntity(entity)) {
               return ClientContext.socialRegistry.getEnemyColor();
            }
         }

         return this.module.playerColorSetting.getValue();
      } else if (entity instanceof ThrownExperienceBottle) {
         return this.module.experienceColorSetting.getValue();
      } else if (entity instanceof ItemEntity) {
         return this.module.itemColorSetting.getValue();
      } else if (EntityUtils.isHostile(entity)) {
         return this.module.hostileColorSetting.getValue();
      } else {
         return EntityUtils.isPassive(entity) ? this.module.animalColorSetting.getValue() : Color.white;
      }
   }

   protected Color getEntityColor(Entity entity, boolean fill) {
      if (entity instanceof ThrownEnderpearl) {
         return (fill ? this.module.pearlFillSetting : this.module.pearlOutlineSetting).getValue();
      }

      int color = ARGB.color((fill ? this.module.entityFillSetting : this.module.entityOutlineSetting).getValue(), this.getBaseEntityColor(entity).getRGB());
      float age = Mth.clamp((entity.tickCount + RenderUtils.getPartialTick()) / 12.0F, 0.0F, 1.0F);
      float opacity = age * age * (3.0F - 2.0F * age);
      int alpha = entity instanceof ThrownExperienceBottle ? ARGB.alpha(color) : (int)(opacity * ARGB.alpha(color));
      return new Color(ARGB.color(alpha, color), true);
   }

   protected boolean shouldRenderEntity(Entity entity) {
      if (entity != null && entity.getBoundingBox() != null) {
         if (!RotationManager.isVisible(entity.getBoundingBox().inflate(0.1))) {
            return false;
         }

         Vec3 cameraPosition = ClientAccess.argusclientMinecraft.gameRenderer.getMainCamera().position();
         if (entity instanceof ItemEntity) {
            double itemDistance = entity.position().distanceTo(cameraPosition);
            Integer configuredRange = this.module.rangeSetting.getValue();
            if (itemDistance > configuredRange.intValue()) {
               return false;
            }
         }

         if (entity instanceof Player && this.module.playersSetting.getValue() && entity != ClientAccess.argusclientMinecraft.player) {
            return true;
         } else if (entity instanceof ItemEntity && this.module.itemDrawSetting.getValue() && this.module.modeSetting.getValue() != ESPModeChoice.TEXT) {
            return true;
         } else if (entity instanceof ThrownExperienceBottle && this.module.experienceSetting.getValue()) {
            return true;
         } else if (EntityUtils.isPassive(entity) && this.module.animalsSetting.getValue()) {
            return true;
         } else {
            return EntityUtils.isHostile(entity) && this.module.hostilesSetting.getValue()
               ? true
               : entity instanceof ThrownEnderpearl
                  && this.module.entityModeSetting.getValue() == ESPModule.EntityMode.BOX
                  && this.module.pearlsSetting.getValue();
         }
      } else {
         return false;
      }
   }

   protected Color getBlockEntityColor(BlockEntity blockEntity) {
      ClientLevel levelSnapshot = ClientAccess.argusclientMinecraft.level;
      BlockPos blockPosition = blockEntity.getBlockPos();
      BlockState blockState = levelSnapshot.getBlockState(blockPosition);
      BlockColorRegistry blockColorManager = ClientContext.blockColorRegistry;
      Color customBlockColor = blockColorManager.getColor(blockState.getBlock());
      if (customBlockColor != null) {
         return customBlockColor;
      }

      ClientLevel mapLevel = ClientAccess.argusclientMinecraft.level;
      BlockPos mapPosition = blockEntity.getBlockPos();
      MapColor stateMapColor = blockState.getMapColor(mapLevel, mapPosition);
      int mapArgb = stateMapColor.calculateARGBColor(Brightness.HIGH);
      if (blockEntity instanceof ShulkerBoxBlockEntity) {
         return new Color(163, 64, 163);
      }

      if (blockEntity instanceof EnderChestBlockEntity) {
         return new Color(64, 0, 64);
      }

      if (blockEntity instanceof BedBlockEntity bed) {
         MapColor dyeMapColor = bed.getColor().getMapColor();
         mapArgb = dyeMapColor.calculateARGBColor(Brightness.HIGH);
      }

      return new Color(mapArgb, false).brighter();
   }
}
