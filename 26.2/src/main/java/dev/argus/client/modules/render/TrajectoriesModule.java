package dev.argus.client.modules.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.render.ArgusTesselator;
import com.mojang.blaze3d.PrimitiveTopology;
import dev.argus.client.ClientAccess;
import dev.argus.client.events.ConnectScreenConnectEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.render.ClientRenderTypes;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.render.MeshBuffer;
import dev.argus.client.render.MeshDrawContext;
import dev.argus.client.render.RenderUtils;
import dev.argus.client.render.WorldBoxRenderer;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.combat.BoundingBoxUtils;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.CameraType;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public class TrajectoriesModule extends Module {
   private static final int MAX_SIMULATION_TICKS = 400;
   private static final int MAX_TRACKED_PROJECTILES = 256;
   private final ProjectileTrajectorySimulator simulator = new ProjectileTrajectorySimulator();
   private final Setting<Float> lineWidthSetting;
   private final Setting<Boolean> onlyOwnSetting;
   private final Setting<Boolean> predictSetting;
   private final Setting<Boolean> airborneSetting;
   private final Setting<Boolean> trailSetting;
   private final Setting<Integer> trailLengthSetting;
   private final Setting<Color> colorSetting;
   protected final Setting<Boolean> bowSetting;
   protected final Setting<Boolean> crossbowSetting;
   protected final Setting<Boolean> tridentSetting;
   protected final Setting<Boolean> pearlsSetting;
   protected final Setting<Boolean> expSetting;
   protected final Setting<Boolean> othersSetting;
   private final Map<Integer, List<Vec3>> trailsByEntityId = new LinkedHashMap<>();
   private final ElapsedTimer trailSampleTimer = new ElapsedTimer();
   private ClientLevel trailLevel;
   private Vec3 previousPlayerVelocity = Vec3.ZERO;
   private float previousBowPower;

   public TrajectoriesModule() {
      super("Trajectories", "Draws trajectories for projectiles and bows.", ModuleCategory.RENDER);
      this.lineWidthSetting = this.add(new NumberSetting<>("LineWidth", 1.0F, 0.1F, 5.0F));
      this.onlyOwnSetting = this.add(new BooleanSetting("OnlyOwn", true));
      this.predictSetting = this.add(new BooleanSetting("Predict", true));
      this.airborneSetting = this.add(new BooleanSetting("Airborne", false).markVisibilityDependency());
      this.trailSetting = this.add(new BooleanSetting("Trail", true).dependsOn(this.airborneSetting));
      this.trailLengthSetting = this.add(new NumberSetting<>("TrailLength", 100, 5, 400).dependsOn(this.airborneSetting, this.trailSetting));
      this.colorSetting = this.add(new ColorSetting("Color", new Color(-1312974337, true)));
      Setting<Boolean> targets = this.add(new BooleanSetting("Targets", false).markAsGroup().markVisibilityDependency());
      this.bowSetting = this.add(new BooleanSetting("Bow", true).dependsOn(targets));
      this.crossbowSetting = this.add(new BooleanSetting("Crossbow", true).dependsOn(targets));
      this.tridentSetting = this.add(new BooleanSetting("Trident", true).dependsOn(targets));
      this.pearlsSetting = this.add(new BooleanSetting("Pearls", true).dependsOn(targets));
      this.expSetting = this.add(new BooleanSetting("Exp", true).dependsOn(targets));
      this.othersSetting = this.add(new BooleanSetting("Others", true).dependsOn(targets));
      this.trailSetting.setChangeCallback(this.trailsByEntityId::clear);
      this.trailLengthSetting.setChangeCallback(this.trailsByEntityId::clear);
      this.airborneSetting.setChangeCallback(this.trailsByEntityId::clear);
      this.setDrawn(false);
   }

   private boolean shouldTraceEntity(Entity entity) {
      Player localPlayer = ClientAccess.argusclientMinecraft.player;
      if (entity instanceof Player) {
         return this.predictSetting.getValue() && (entity == localPlayer || !this.onlyOwnSetting.getValue());
      } else if (!(entity instanceof Projectile projectile && !(entity instanceof WitherSkull) && this.airborneSetting.getValue())) {
         return false;
      } else if (this.onlyOwnSetting.getValue() && projectile.getOwner() != localPlayer) {
         return false;
      } else if (entity instanceof ThrownEnderpearl) {
         return this.pearlsSetting.getValue();
      } else if (entity instanceof ThrownExperienceBottle) {
         return this.expSetting.getValue();
      } else if (entity instanceof ThrownTrident) {
         return this.tridentSetting.getValue();
      } else {
         return !(entity instanceof AbstractArrow) ? this.othersSetting.getValue() : this.bowSetting.getValue() || this.crossbowSetting.getValue();
      }
   }

   private InteractionHand getLaunchHand(Player player) {
      if (player.isUsingItem() && this.simulator.isEnabledProjectileItem(player.getUseItem().getItem())) {
         return player.getUsedItemHand();
      } else {
         return this.simulator.isEnabledProjectileItem(player.getMainHandItem().getItem()) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
      }
   }

   @EventHandler
   private void onWorldRender(WorldRenderEvent.Main event) {
      if (!this.isWorldUnavailable()) {
         ClientLevel level = ClientAccess.argusclientMinecraft.level;
         this.refreshTrailLevel(level);
         boolean sampleTrail = this.trailSampleTimer.hasElapsedAndReset(25L);
         List<TrajectoriesModule.TrajectoryPath> paths = new ArrayList<>();

         for (Entity entity : level.entitiesForRendering()) {
            if (this.shouldTraceEntity(entity)) {
               boolean predicted = entity instanceof Player;
               if (entity instanceof Projectile projectile) {
                  if (!this.simulator.initializeFromProjectile(projectile, event.getPartialTick())) {
                     continue;
                  }
               } else if (entity instanceof Player player) {
                  ItemStack stack = player.getItemInHand(this.getLaunchHand(player));
                  if (!this.simulator.initializeFromItem(player, stack, 0.0, true, event.getPartialTick())) {
                     continue;
                  }
               }

               TrajectoriesModule.TrajectoryPath path = new TrajectoriesModule.TrajectoryPath(entity, predicted);
               path.simulate();
               if (!predicted && this.trailSetting.getValue()) {
                  path.trail = this.sampleTrail(entity, sampleTrail, event.getPartialTick());
               }

               paths.add(path);
            }
         }

         if (!paths.isEmpty()) {
            Color color = this.colorSetting.getValue();
            float lineWidth = this.lineWidthSetting.getValue();
            BufferBuilder buffer = ArgusTesselator.getInstance().begin(PrimitiveTopology.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH);
            RenderType previousRenderType = MeshDrawContext.getRenderType();

            try {
               for (TrajectoriesModule.TrajectoryPath path : paths) {
                  path.render(event, buffer, color, lineWidth);
               }
            } finally {
               try {
                  MeshDrawContext.setRenderType(ClientRenderTypes.worldLines());
                  MeshBuffer.draw(buffer);
               } finally {
                  MeshDrawContext.setRenderType(previousRenderType);
               }
            }
         }
      }
   }

   private List<Vec3> sampleTrail(Entity entity, boolean sample, float partialTick) {
      int id = entity.getId();
      List<Vec3> trail = this.trailsByEntityId.get(id);
      if (trail == null) {
         if (this.trailsByEntityId.size() >= 256) {
            this.trailsByEntityId.remove(this.trailsByEntityId.keySet().iterator().next());
         }

         trail = new ArrayList<>();
         this.trailsByEntityId.put(id, trail);
      }

      if (sample) {
         trail.add(entity.getPosition(partialTick));
      }

      int limit = Math.max(5, Math.min(400, this.trailLengthSetting.getValue()));

      while (trail.size() > limit) {
         trail.removeFirst();
      }

      return trail;
   }

   private void refreshTrailLevel(ClientLevel level) {
      if (this.trailLevel != level) {
         this.trailsByEntityId.clear();
         this.trailLevel = level;
      }

      this.trailsByEntityId.keySet().removeIf(id -> level.getEntity(id) == null);
   }

   @Override
   public void onDisable() {
      this.trailsByEntityId.clear();
      this.trailLevel = null;
      this.previousPlayerVelocity = Vec3.ZERO;
      this.previousBowPower = 0.0F;
   }

   @EventHandler
   private void onConnectScreenConnectEvent(ConnectScreenConnectEvent event) {
      this.onDisable();
   }

   @EventHandler
   private void onLocalPlayerPreTick(LocalPlayerPreTickEvent event) {
      if (!this.isWorldUnavailable()) {
         Player player = ClientAccess.argusclientMinecraft.player;
         this.previousPlayerVelocity = player.getDeltaMovement();
         this.previousBowPower = player.isUsingItem() && player.getUseItem().getItem() instanceof BowItem
            ? BowItem.getPowerForTime(player.getTicksUsingItem())
            : 0.0F;
         this.refreshTrailLevel(ClientAccess.argusclientMinecraft.level);
      }
   }

   public Vec3 getPreviousPlayerVelocity() {
      return this.previousPlayerVelocity;
   }

   public float getPreviousBowPower() {
      return this.previousBowPower;
   }

   private final class TrajectoryPath {
      private final List<Vec3> points = new ArrayList<>();
      private final Entity source;
      private final boolean predicted;
      private List<Vec3> trail = Collections.emptyList();
      private AABB blockHitBounds;
      private Entity hitEntity;

      private TrajectoryPath(Entity source, boolean predicted) {
         this.source = source;
         this.predicted = predicted;
      }

      private void simulate() {
         this.appendPosition();

         for (int tick = 0; tick < 400; tick++) {
            HitResult hit = TrajectoriesModule.this.simulator.step();
            if (hit != null) {
               if (hit instanceof BlockHitResult blockHit && hit.getType() == Type.BLOCK) {
                  this.blockHitBounds = new AABB(hit.getLocation(), hit.getLocation());
                  Axis axis = blockHit.getDirection().getAxis();
                  this.blockHitBounds = this.blockHitBounds.inflate(axis == Axis.X ? 0.0 : 0.25, axis == Axis.Y ? 0.0 : 0.25, axis == Axis.Z ? 0.0 : 0.25);
                  this.points.add(hit.getLocation());
                  break;
               }

               if (hit instanceof EntityHitResult entityHit) {
                  this.hitEntity = entityHit.getEntity();
                  this.points.add(hit.getLocation());
               }
               break;
            }

            this.appendPosition();
         }
      }

      private void appendPosition() {
         this.points
            .add(
               new Vec3(
                  TrajectoriesModule.this.simulator.position.x, TrajectoriesModule.this.simulator.position.y, TrajectoriesModule.this.simulator.position.z
               )
            );
      }

      private void render(WorldRenderEvent.Main event, BufferBuilder buffer, Color color, float lineWidth) {
         PoseStack poses = event.getPoseStack();
         if (this.predicted
            && this.source == ClientAccess.argusclientMinecraft.player
            && ClientAccess.argusclientMinecraft.options.getCameraType() == CameraType.FIRST_PERSON) {
            poses = new PoseStack();
            RenderUtils.applyInverseViewBobbing(poses, event.getPartialTick());
            RenderUtils.applyCameraRotation(poses);
         }

         Vec3 last = this.drawPolyline(poses, buffer, this.trail, null, color.getRGB(), lineWidth);
         this.drawPolyline(poses, buffer, this.points, last, color.getRGB(), lineWidth);
         if (this.blockHitBounds != null) {
            WorldBoxRenderer.fillBox(poses, this.blockHitBounds, ColorUtils.withAlpha(color, 60));
            WorldBoxRenderer.outlineBox(poses, this.blockHitBounds, color, lineWidth);
         }

         if (this.hitEntity != null && this.source == ClientAccess.argusclientMinecraft.player) {
            AABB bounds = BoundingBoxUtils.getRenderBounds(this.hitEntity, event.getPartialTick());
            WorldBoxRenderer.fillBox(poses, bounds, ColorUtils.withAlpha(color, 60));
         }
      }

      private Vec3 drawPolyline(PoseStack poses, BufferBuilder buffer, List<Vec3> path, Vec3 previous, int color, float lineWidth) {
         for (Vec3 point : path) {
            if (previous != null) {
               RenderUtils.INSTANCE.addWorldLine(poses, buffer, previous, point, lineWidth, color, color);
            }

            previous = point;
         }

         return previous;
      }
   }
}
