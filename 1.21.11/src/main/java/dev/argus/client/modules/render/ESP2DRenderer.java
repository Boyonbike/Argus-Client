package dev.argus.client.modules.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.events.HudRenderEvent;
import dev.argus.client.modules.client.FontsModule;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.render.GuiDrawUtils;
import dev.argus.client.render.RenderUtils;
import dev.argus.client.utils.combat.BoundingBoxUtils;
import dev.argus.client.utils.entity.EntityUtils;
import dev.argus.client.utils.entity.PlayerState;
import dev.argus.client.utils.inventory.PotionUtils;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

final class ESP2DRenderer implements ClientAccess {
   private final ESPModule module;
   private final List<Entity> targets = new ArrayList<>();
   private final Matrix4f projection = new Matrix4f();
   private final Vector4f screenBounds = new Vector4f();
   private final Vector4f[] corners = new Vector4f[8];

   ESP2DRenderer(ESPModule module) {
      this.module = module;

      for (int index = 0; index < this.corners.length; index++) {
         this.corners[index] = new Vector4f();
      }
   }

   void render(HudRenderEvent event, Iterable<Entity> entities) {
      Vec3 camera = argusclientMinecraft.gameRenderer.getMainCamera().position();
      this.targets.clear();

      for (Entity entity : entities) {
         if (this.module.renderHelper.shouldRenderEntity(entity) && !(entity instanceof ThrownEnderpearl)) {
            this.targets.add(entity);
         }
      }

      this.targets.sort(Comparator.comparingDouble(entityx -> -entityx.distanceToSqr(camera)));
      Matrix4f view = new Matrix4f(event.getPoseStack().last().pose());
      RenderUtils.applyCameraRotation(view);
      RenderUtils.WORLD_PROJECTION_MATRIX.mul(view, this.projection);
      PoseStack poses = event.getPoseStack();
      poses.pushPose();

      try {
         float scale = 1.0F / argusclientMinecraft.getWindow().getGuiScale();
         poses.scale(scale, scale, scale);

         for (Entity entity : this.targets) {
            this.renderEntity(event, poses, entity, camera);
         }

         GuiDrawUtils.flush();
      } finally {
         poses.popPose();
      }
   }

   private void renderEntity(HudRenderEvent event, PoseStack poses, Entity entity, Vec3 camera) {
      AABB bounds = BoundingBoxUtils.getRenderBounds(entity, event.getPartialTick());
      if (entity instanceof Player) {
         bounds = bounds.inflate(0.125, 0.075, 0.125).move(0.0, 0.1, 0.0);
      } else if (entity instanceof ThrownExperienceBottle) {
         bounds = bounds.move(0.0, 0.2, 0.0);
      }

      if (projectBounds(
         this.projection,
         bounds,
         camera,
         argusclientMinecraft.getWindow().getScreenWidth(),
         argusclientMinecraft.getWindow().getScreenHeight(),
         this.corners,
         this.screenBounds
      )) {
         if (entity instanceof ItemEntity || entity instanceof ThrownExperienceBottle) {
            squareBounds(this.screenBounds);
         }

         int color = this.module.renderHelper.getBaseEntityColor(entity).getRGB();
         int deathTicks = entity instanceof LivingEntity living ? living.deathTime : 0;
         float lifeOpacity = lifecycleOpacity(entity.tickCount, deathTicks, event.getPartialTick());
         double distance = camera.distanceTo(
            new Vec3(
               Mth.clamp(camera.x, bounds.minX, bounds.maxX), Mth.clamp(camera.y, bounds.minY, bounds.maxY), Mth.clamp(camera.z, bounds.minZ, bounds.maxZ)
            )
         );
         float distanceOpacity = 0.25F + Mth.clamp(((float)distance - 0.5F) / 1.5F, 0.0F, 1.0F) * 0.75F;
         float opacity = lifeOpacity * distanceOpacity;
         int outline = ARGB.multiplyAlpha(ARGB.color(this.module.entityOutlineSetting.getValue(), color), opacity);

         int shadow = switch ((ESPModule.ShadowMode)this.module.entityShadowSetting.getValue()) {
            case BLACK -> outline & 0xFF000000;
            case DARK -> ColorUtils.darken(new Color(outline, true), 0.25F).getRGB();
            case NONE -> 0;
         };
         int fill = ARGB.multiplyAlpha(ARGB.color(this.module.entityFillSetting.getValue(), color), opacity);
         int tint = ARGB.multiplyAlpha(ARGB.color(this.module.entityTintSetting.getValue(), color), opacity);
         float left = this.screenBounds.x;
         float top = this.screenBounds.y;
         float right = this.screenBounds.z;
         float bottom = this.screenBounds.w;
         drawFill(poses, this.screenBounds, fill, tint);
         float length = this.module.entityLengthSetting.getValue();
         drawBox(poses, this.screenBounds, 3.0F, length, shadow);
         drawBox(poses, this.screenBounds, 1.0F, length, outline);
         float health = entity instanceof PlayerState state
            ? state.argusclient$getHealthFactor()
            : (entity instanceof LivingEntity living ? EntityUtils.getHealthFraction(living) : 0.0F);
         if (entity instanceof LivingEntity && this.module.healthBarSetting.getValue()) {
            int healthColor = this.module.healthColorSetting.getValue()
               ? ColorUtils.withAlpha(new Color(ColorUtils.getHealthColor(health), true), Math.round(255.0F * lifeOpacity)).getRGB()
               : outline;
            GuiDrawUtils.fillRect(poses, left - 5.0F, top - 1.0F, left - 1.0F, bottom + 1.0F, shadow);
            GuiDrawUtils.fillRect(
               poses, left - 4.0F, top, left - 2.0F, bottom, ColorUtils.withAlpha(new Color(healthColor, true), Math.round(50.0F * lifeOpacity))
            );
            GuiDrawUtils.fillRect(poses, left - 4.0F, bottom - (bottom - top) * health, left - 2.0F, bottom, healthColor);
         }

         this.drawInformation(poses, entity, health, Math.round(255.0F * lifeOpacity) << 24 | 16777215);
      }
   }

   private void drawInformation(PoseStack poses, Entity entity, float health, int textColor) {
      FontRenderer font = FontRenderer.INSTANCE;
      float scale = FontsModule.INSTANCE.isToggled() ? 1.0F : 1.6F;
      float center = (this.screenBounds.x + this.screenBounds.z) * 0.5F;
      if (this.module.nameSetting.getValue()) {
         Component name = entity instanceof Player player
            ? Component.literal(player.getGameProfile().name())
            : (entity instanceof ThrownExperienceBottle ? Component.literal("Exp") : entity.getName());
         drawText(poses, name, center, this.screenBounds.y - 2.0F - font.getHeight() * scale, scale, 0.5F, textColor);
      }

      List<Component> below = new ArrayList<>();
      if (this.module.distanceSetting.getValue()) {
         double distance = argusclientMinecraft.player.distanceTo(entity);
         below.add(Component.literal(formatDistance(distance)));
      }

      if (entity instanceof LivingEntity living) {
         if (this.module.heldItemSetting.getValue() && !living.getMainHandItem().isEmpty()) {
            below.add(living.getMainHandItem().getHoverName());
         }

         if (this.module.effectsSetting.getValue()) {
            float y = this.screenBounds.y;
            List<String> effects = new ArrayList<>();
            Set<?> activeEffects = PotionUtils.getEffectsByParticleColor(living);
            if (activeEffects.contains(MobEffects.RESISTANCE) && activeEffects.contains(MobEffects.SLOWNESS)) {
               effects.add("Resistance");
            }

            if (activeEffects.contains(MobEffects.STRENGTH)) {
               effects.add("Strength");
            }

            if (activeEffects.contains(MobEffects.WEAKNESS)) {
               effects.add("Weak");
            }

            for (String effect : effects) {
               drawText(poses, Component.literal(effect), this.screenBounds.z + 3.0F, y, scale, 0.0F, textColor);
               y += font.getHeight() * scale;
            }
         }

         if (this.module.healthBarSetting.getValue() && this.module.healthTextSetting.getValue()) {
            Component text = Component.literal(Integer.toString(Math.round(living.getHealth() + living.getAbsorptionAmount())));
            drawText(
               poses,
               text,
               this.screenBounds.x - 6.0F,
               this.screenBounds.y + (this.screenBounds.w - this.screenBounds.y) * (1.0F - health),
               scale,
               1.0F,
               textColor
            );
         }
      }

      float y = this.screenBounds.w + 2.0F;

      for (Component line : below) {
         drawText(poses, line, center, y, scale, 0.5F, textColor);
         y += font.getHeight() * scale;
      }
   }

   private static void drawText(PoseStack poses, Component text, float x, float y, float scale, float alignment, int color) {
      poses.pushPose();

      try {
         poses.translate(x, y, 0.0F);
         poses.scale(scale, scale, 1.0F);
         FontRenderer.INSTANCE.drawWithShadow(poses, text, -FontRenderer.INSTANCE.getWidth(text) * alignment, 0.0F, color, DisplayMode.NORMAL);
      } finally {
         poses.popPose();
      }
   }

   static float lifecycleOpacity(int ageTicks, int deathTicks, float partialTick) {
      float opacity = Mth.clamp((ageTicks - 1 + partialTick) / 6.0F, 0.0F, 1.0F);
      if (deathTicks > 0) {
         opacity *= 1.0F - Mth.clamp((deathTicks - 1 + partialTick) / 8.0F, 0.0F, 1.0F);
      }

      return opacity * opacity * (3.0F - 2.0F * opacity);
   }

   static void squareBounds(Vector4f bounds) {
      float centerX = (bounds.x + bounds.z) * 0.5F;
      float centerY = (bounds.y + bounds.w) * 0.5F;
      float radius = Math.max(bounds.z - bounds.x, bounds.w - bounds.y) * 0.5F;
      bounds.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
   }

   static void drawFill(PoseStack poses, Vector4f bounds, int fill, int tint) {
      GuiDrawUtils.fillRect(poses, bounds.x, bounds.y, bounds.z, bounds.w, fill);
      int x = (int)(bounds.x + 3.0F);
      int y = (int)(bounds.y + 3.0F);
      int width = (int)(bounds.z - bounds.x - 7.0F);
      int height = (int)(bounds.w - bounds.y - 7.0F);
      if (width > 0 && height > 0) {
         int upper = height / 2;
         int lower = height - upper;
         int transparent = tint & 16777215;
         GuiDrawUtils.fillGradientRect(poses.last().pose(), x, y, width, upper, tint, tint, transparent, transparent);
         GuiDrawUtils.fillGradientRect(poses.last().pose(), x, y + upper, width, lower, transparent, transparent, tint, tint);
      }
   }

   static void drawBox(PoseStack poses, Vector4f bounds, float width, float length, int color) {
      if (color >>> 24 != 0 && !(width <= 0.0F)) {
         float x = bounds.x;
         float y = bounds.y;
         float right = bounds.z;
         float bottom = bounds.w;
         float half = width * 0.5F;
         float horizontal = (right - x) * length * 0.5F;
         float vertical = (bottom - y) * length * 0.5F;

         for (int sideX = 0; sideX < 2; sideX++) {
            float edgeX = sideX == 0 ? x : right;
            float endX = edgeX == x ? x + horizontal : right - horizontal;

            for (int sideY = 0; sideY < 2; sideY++) {
               float edgeY = sideY == 0 ? y : bottom;
               float endY = edgeY == y ? y + vertical : bottom - vertical;
               GuiDrawUtils.fillRect(poses, Math.min(edgeX, endX) - half, edgeY - half, Math.max(edgeX, endX) + half, edgeY + half, color);
               GuiDrawUtils.fillRect(poses, edgeX - half, Math.min(edgeY, endY) - half, edgeX + half, Math.max(edgeY, endY) + half, color);
            }
         }
      }
   }

   static boolean projectBounds(Matrix4f projection, AABB box, Vec3 camera, int width, int height, Vector4f[] corners, Vector4f result) {
      if (width > 0 && height > 0) {
         for (int index = 0; index < 8; index++) {
            corners[index]
               .set(
                  (float)(((index & 1) == 0 ? box.minX : box.maxX) - camera.x),
                  (float)(((index & 2) == 0 ? box.minY : box.maxY) - camera.y),
                  (float)(((index & 4) == 0 ? box.minZ : box.maxZ) - camera.z),
                  1.0F
               );
            projection.transform(corners[index]);
            if (!corners[index].isFinite()) {
               return false;
            }
         }

         result.set(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);
         boolean visible = false;

         for (int index = 0; index < 8; index++) {
            Vector4f point = corners[index];
            if (point.w > 1.0E-4F && point.z + point.w >= 0.0F) {
               includePoint(point.x, point.y, point.w, width, height, result);
               visible = true;
            }

            for (int axis = 1; axis <= 4; axis <<= 1) {
               int otherIndex = index ^ axis;
               if (otherIndex > index) {
                  Vector4f other = corners[otherIndex];
                  float distance = point.z + point.w;
                  float otherDistance = other.z + other.w;
                  if (distance < 0.0F != otherDistance < 0.0F) {
                     float fraction = distance / (distance - otherDistance);
                     float w = Mth.lerp(fraction, point.w, other.w);
                     if (w > 1.0E-4F) {
                        includePoint(Mth.lerp(fraction, point.x, other.x), Mth.lerp(fraction, point.y, other.y), w, width, height, result);
                        visible = true;
                     }
                  }
               }
            }
         }

         if (visible
            && Float.isFinite(result.x)
            && Float.isFinite(result.y)
            && Float.isFinite(result.z)
            && Float.isFinite(result.w)
            && !(result.z < 0.0F)
            && !(result.x > width)
            && !(result.w < 0.0F)
            && !(result.y > height)) {
            result.set(Mth.clamp(result.x, 0.0F, width), Mth.clamp(result.y, 0.0F, height), Mth.clamp(result.z, 0.0F, width), Mth.clamp(result.w, 0.0F, height));
            return result.z > result.x && result.w > result.y;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static void includePoint(float x, float y, float w, int width, int height, Vector4f result) {
      float screenX = (x / w + 1.0F) * width * 0.5F;
      float screenY = (1.0F - y / w) * height * 0.5F;
      result.x = Math.min(result.x, screenX);
      result.y = Math.min(result.y, screenY);
      result.z = Math.max(result.z, screenX);
      result.w = Math.max(result.w, screenY);
   }

   /** Same output as {@code "%.1f%s"}, without String.format's parsing cost for every entity on every frame. */
   private static String formatDistance(double distance) {
      boolean kilometres = distance > 1000.0;
      long tenths = Math.round((kilometres ? distance / 1000.0 : distance) * 10.0);
      return (tenths / 10) + "." + (tenths % 10) + (kilometres ? "km" : "m");
   }
}
