package dev.argus.client.modules.render;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.render.RenderUtils;
import dev.argus.client.modules.render.targets.RuleSettings;
import dev.argus.client.modules.render.targets.SpecificRules;
import dev.argus.client.modules.render.targets.SpecificTargets;
import dev.argus.client.modules.render.targets.TargetMatcher;
import dev.argus.client.modules.render.targets.TargetSettings;
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
import java.util.IdentityHashMap;
import java.util.Map;
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
   private static final Resolved UNMATCHED = new Resolved(null, null, Color.white);
   /** Each entity's target this frame, so the box, label and 2D passes don't each match it again. */
   private final Map<Entity, Resolved> resolved = new IdentityHashMap<>();

   /** What an entity matched this frame: its card (null if not drawn), its group's settings (or null) and colour. */
   record Resolved(TargetSettings.Kind kind, RuleSettings settings, Color color) {
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

   /** Forgets last frame's matches; call once at the start of each frame. */
   void beginFrame() {
      this.resolved.clear();
   }

   /** The entity's target for this frame, worked out on first use. */
   Resolved resolve(Entity entity) {
      Resolved result = this.resolved.get(entity);
      if (result == null) {
         Vec3 camera = ClientAccess.argusclientMinecraft.gameRenderer.getMainCamera().position();
         SpecificTargets specific = this.module.targets.specific;
         SpecificRules.Rule rule = specific.forEntity(entity);
         RuleSettings settings = rule == null ? null : specific.settings(rule);
         TargetSettings.Kind kind = TargetMatcher.kindOf(this.module.targets, entity, camera, rule);
         result = kind == null && settings == null
            ? UNMATCHED
            : new Resolved(kind, settings, kind == null ? Color.white : TargetMatcher.entityColor(this.module.targets, kind, entity, camera, rule));
         this.resolved.put(entity, result);
      }

      return result;
   }

   /** Whether an entity is drawn: in one of the target groups (dropped items only if their group draws a box), and in view. */
   protected boolean shouldRenderEntity(Entity entity) {
      if (entity == null || entity.getBoundingBox() == null || !RotationManager.isVisible(entity.getBoundingBox().inflate(0.1))) {
         return false;
      }

      Resolved target = this.resolve(entity);
      if (target.kind() == TargetSettings.Kind.ITEMS) {
         return target.settings() == null || target.settings().itemLook() != RuleSettings.ItemLook.LABEL;
      }

      return target.kind() != null;
   }

   protected Color getBaseEntityColor(Entity entity) {
      return this.resolve(entity).color();
   }

   protected Color getEntityColor(Entity entity, boolean fill) {
      return this.getEntityColor(entity, this.getBaseEntityColor(entity).getRGB(), fill);
   }

   /** The fill or outline colour from a base colour, faded in over the entity's first ticks. */
   protected Color getEntityColor(Entity entity, int baseColor, boolean fill) {
      int color = ARGB.color((fill ? this.module.entityFillSetting : this.module.entityOutlineSetting).getValue(), baseColor);
      float age = Mth.clamp((entity.tickCount + RenderUtils.getPartialTick()) / 12.0F, 0.0F, 1.0F);
      float opacity = age * age * (3.0F - 2.0F * age);
      int alpha = entity instanceof ThrownExperienceBottle ? ARGB.alpha(color) : (int)(opacity * ARGB.alpha(color));
      return new Color(ARGB.color(alpha, color), true);
   }
}
