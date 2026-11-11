package dev.argus.client.modules.render;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.ClientLevelAddEntityEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.HudRenderEvent;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.client.ColorsModule;
import dev.argus.client.modules.render.targets.BlockTargetIndex;
import dev.argus.client.modules.render.targets.CellIndex;
import dev.argus.client.modules.render.targets.RuleSettings;
import dev.argus.client.modules.render.targets.SpecificRules;
import dev.argus.client.modules.render.targets.SpecificTargets;
import dev.argus.client.modules.render.targets.TargetMatcher;
import dev.argus.client.modules.render.targets.TargetSettings;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.render.RenderUtils;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.ColorsSchemeChoice;
import dev.argus.client.settings.options.TracersHitboxChoice;
import dev.argus.client.settings.options.TracersModeChoice;
import dev.argus.client.utils.combat.BoundingBoxUtils;
import dev.argus.client.utils.math.Vec3Utils;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws lines (or screen-edge markers) to players, mobs, dropped items and chosen blocks. The four target cards match
 * ESP's (see {@link TargetSettings}) and can be toggled or mirrored there; the look settings here stay Tracers' own.
 */
public class TracersModule extends Module {
   private static ColorsModule colorsModule = ClientContext.getModule(ColorsModule.class);
   final Setting<TracersModeChoice> modeSetting = this.add(new EnumSetting<>("Mode", TracersModeChoice.LINES));
   public final Setting<TracersHitboxChoice> hitboxSetting = this.add(
      new EnumSetting<>("Hitbox", TracersHitboxChoice.HEAD, ignored -> this.modeSetting.getValue() == TracersModeChoice.LINES)
   );
   private final Setting<Float> minDistanceSetting = this.add(new NumberSetting<>("MinDistance", 0.0F, 0.0F, 128.0F).withUnit("m"));
   public final Setting<Integer> shadowSetting = this.add(
      new NumberSetting<>("Shadow", 1, 0, 3, ignored -> this.modeSetting.getValue() == TracersModeChoice.MARKER)
   );
   public final Setting<Float> distanceSetting = this.add(
      new NumberSetting<>("Distance", 16.0F, 8.0F, 50.0F, ignored -> this.modeSetting.getValue() == TracersModeChoice.MARKER).withUnit("px")
   );
   public final Setting<Float> lineWidthSetting = this.add(
      new NumberSetting<>("LineWidth", 1.0F, 0.1F, 5.0F, ignored -> this.modeSetting.getValue() == TracersModeChoice.LINES)
   );
   public final Setting<Boolean> stemSetting = this.add(
      new BooleanSetting("Stem", true, ignored -> this.modeSetting.getValue() == TracersModeChoice.LINES)
   );
   private final Setting<Boolean> distanceColorSetting = this.add(new BooleanSetting("DistanceColor", false));
   public final Setting<Boolean> ignoreVisibleSetting = this.add(
      new BooleanSetting("IgnoreVisible", true, ignored -> this.modeSetting.getValue() == TracersModeChoice.MARKER)
   );
   public final TargetSettings targets = new TargetSettings(this, TargetSettings.Kind.PLAYERS);
   private final LineTracerRenderer lineRenderer;
   private final MarkerTracerRenderer markerRenderer;
   private final List<Target> collected = new ArrayList<>();
   /** Most block tracers drawn at once; past this the screen is unreadable anyway. */
   private static final int MAX_BLOCK_TRACERS = 256;
   private final List<Target> blockTargets = new ArrayList<>();
   private final LongArrayList candidatePositions = new LongArrayList();
   private final DoubleArrayList candidateDistances = new DoubleArrayList();
   private final IntArrayList candidateColors = new IntArrayList();
   private long tickCounter;
   private long blockTargetsTick = -1L;
   private long alertMark;

   /**
    * One line's worth of target: where the line aims, the stem drawn along it (null for none), the bounds the marker
    * mode fades against, and its colour.
    */
   public record Target(Vec3 aim, Vec3 stemStart, Vec3 stemEnd, AABB bounds, Color color, Entity entity) {
   }

   public TracersModule() {
      super("Tracers", "Draws lines from your crosshair to players, mobs, items and chosen blocks.", ModuleCategory.RENDER);
      this.lineRenderer = new LineTracerRenderer(this);
      this.markerRenderer = new MarkerTracerRenderer(this);
      BlockTargetIndex.get().register(this, this.targets);
   }

   public static void bindReferencedModules(ModuleRegistry moduleRegistry) {
      colorsModule = (ColorsModule)moduleRegistry.getModule(ColorsModule.class);
   }

   /** Every target to draw this frame. */
   public List<Target> collect(float tickDelta) {
      this.collected.clear();
      if (argusclientMinecraft.level == null || argusclientMinecraft.player == null) {
         return this.collected;
      }

      Vec3 camera = argusclientMinecraft.gameRenderer.getMainCamera().position();
      double minimum = this.minDistanceSetting.getValue();
      double minimumSquared = minimum * minimum;
      for (Entity entity : argusclientMinecraft.level.entitiesForRendering()) {
         TargetSettings.Kind kind = TargetMatcher.kindOf(this.targets, entity, camera);
         if (kind == null || entity.position().distanceToSqr(camera) < minimumSquared) {
            continue;
         }

         if (kind == TargetSettings.Kind.ITEMS && entity instanceof ItemEntity item) {
            TargetMatcher.alertItem(this.targets, item);
         }

         Vec3 start;
         Vec3 end;
         if (entity instanceof Player player) {
            TracerEndpoints endpoints = this.getEndpoints(player);
            start = endpoints.start();
            end = endpoints.end();
         } else {
            AABB bounds = BoundingBoxUtils.getRenderBounds(entity, tickDelta);
            start = new Vec3(bounds.getCenter().x, bounds.minY, bounds.getCenter().z);
            end = new Vec3(bounds.getCenter().x, bounds.maxY, bounds.getCenter().z);
         }

         Vec3 aim = Vec3Utils.lerp(this.hitboxSetting.getValue().getHeightFraction(), start, end);
         Color color = this.colorFor(TargetMatcher.entityColor(this.targets, kind, entity, camera), aim);
         this.collected.add(new Target(aim, start, end, BoundingBoxUtils.getRenderBounds(entity, tickDelta), color, entity));
      }

      this.collectBlocks(camera, minimumSquared);
      return this.collected;
   }

   private void collectBlocks(Vec3 camera, double minimumSquared) {
      TargetSettings.Blocks card = this.targets.blocks;
      if (!card.active() && !this.targets.specific.hasBlocks()) {
         this.blockTargets.clear();
         return;
      }

      // Block targets don't move, so they're rebuilt once per tick (or when the index changes) instead of
      // every frame, and only the nearest MAX_BLOCK_TRACERS are kept: walking up to 300k indexed blocks
      // per frame with several allocations each could freeze slower PCs.
      if (this.blockTargetsTick != this.tickCounter) {
         this.blockTargetsTick = this.tickCounter;
         this.rebuildBlockTargets(card, camera, minimumSquared);
      }

      this.collected.addAll(this.blockTargets);
   }

   @Override
   public void onDisable() {
      super.onDisable();
      this.blockTargets.clear();
      this.blockTargetsTick = -1L;
   }

   private void rebuildBlockTargets(TargetSettings.Blocks card, Vec3 camera, double minimumSquared) {
      this.blockTargets.clear();
      int ignored = card.ignoredStructures();
      boolean cardOn = card.active();
      SpecificTargets specific = this.targets.specific;
      CellIndex<BlockTargetIndex.Entry> index = BlockTargetIndex.get().index();
      // Cells are skipped beyond the furthest range of the card and of every specific block target.
      double range = cardOn ? (card.range.isAtMaximum() ? -1.0 : card.range.getValue()) : 0.0;
      for (SpecificRules.Rule rule : specific.rules().all()) {
         if (range >= 0 && rule.enabled && rule.type == SpecificRules.Type.BLOCK) {
            RuleSettings settings = specific.settings(rule);
            range = settings.unlimited() ? -1.0 : Math.max(range, settings.range.getValue());
         }
      }
      LongArrayList positions = this.candidatePositions;
      DoubleArrayList distances = this.candidateDistances;
      IntArrayList colors = this.candidateColors;
      positions.clear();
      distances.clear();
      colors.clear();
      ColorsSchemeChoice scheme = colorsModule == null ? null : colorsModule.schemeSetting.getValue();
      for (CellIndex.Cell<BlockTargetIndex.Entry> cell : index.cells()) {
         if (cell.size() == 0) {
            continue;
         }

         if (range > 0) {
            CellIndex.CellKey key = cell.key();
            double dx = key.minX() + 4.0 - camera.x;
            double dy = key.minY() + 4.0 - camera.y;
            double dz = key.minZ() + 4.0 - camera.z;
            double reach = range + 7.0;
            if (dx * dx + dy * dy + dz * dz > reach * reach) {
               continue;
            }
         }

         cell.forEach((packed, entry) -> {
            // A specific target overrides the card for its block.
            SpecificRules.Rule rule = specific.forBlock(entry.state().getBlock());
            RuleSettings settings = rule == null ? null : specific.settings(rule);
            if (settings == null ? !cardOn || !TargetMatcher.acceptsBlock(card, ignored, packed, entry) : !settings.acceptsBlock(packed, entry)) {
               return;
            }

            double dx = CellIndex.unpackX(packed) + 0.5 - camera.x;
            double dy = CellIndex.unpackY(packed) + 0.5 - camera.y;
            double dz = CellIndex.unpackZ(packed) + 0.5 - camera.z;
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared < minimumSquared || !(settings == null ? card.inRange(distanceSquared) : settings.inRange(distanceSquared))) {
               return;
            }

            positions.add(packed);
            distances.add(distanceSquared);
            colors.add(settings == null ? TargetMatcher.blockColor(card, scheme, entry) : settings.blockColor(entry, scheme));
         });
      }

      int count = positions.size();
      int[] order = new int[count];
      for (int i = 0; i < count; i++) {
         order[i] = i;
      }

      if (count > MAX_BLOCK_TRACERS) {
         IntArrays.quickSort(order, (left, right) -> Double.compare(distances.getDouble(left), distances.getDouble(right)));
         count = MAX_BLOCK_TRACERS;
      }

      for (int i = 0; i < count; i++) {
         long packed = positions.getLong(order[i]);
         Vec3 center = new Vec3(CellIndex.unpackX(packed) + 0.5, CellIndex.unpackY(packed) + 0.5, CellIndex.unpackZ(packed) + 0.5);
         Color color = this.colorFor(new Color(colors.getInt(order[i]), true), center);
         AABB bounds = new AABB(center.x - 0.5, center.y - 0.5, center.z - 0.5, center.x + 0.5, center.y + 0.5, center.z + 0.5);
         this.blockTargets.add(new Target(center, null, null, bounds, color, null));
      }
   }

   /** Applies Distance colour (red near, green far) when it's on. */
   private Color colorFor(Color base, Vec3 position) {
      if (!this.distanceColorSetting.getValue()) {
         return base;
      }

      double distance = argusclientMinecraft.player.getEyePosition().distanceTo(position);
      float fraction = 1.0F - (float)((Math.min(Math.max(distance, 8.0), 64.0) - 8.0) / 56.0);
      Color gradient = ColorUtils.withAlpha(ColorUtils.blend(Color.red, Color.green, fraction), base.getAlpha());
      return colorsModule == null ? gradient : colorsModule.schemeSetting.getValue().transform(gradient);
   }

   public TracerEndpoints getEndpoints(Player player) {
      float tickDelta = RenderUtils.getPartialTick();
      Vec3 position = player.getPosition(tickDelta);
      AABB bounds = player.getBoundingBox();
      if (player.isPassenger()) {
         return TracerEndpoints.riding(player, position);
      }

      if (player.isSleeping()) {
         return TracerEndpoints.sleeping(player);
      }

      return bounds.getYsize() <= 1.0 ? TracerEndpoints.lowPose(player, position, bounds) : TracerEndpoints.standing(player, position);
   }

   @EventHandler
   private void onTick(LocalPlayerTickEvent event) {
      this.tickCounter++;
      TargetSettings.Blocks card = this.targets.blocks;
      if (!TargetMatcher.blockAlertsOn(this.targets) || argusclientMinecraft.player == null) {
         return;
      }

      int ignored = card.ignoredStructures();
      Vec3 eye = argusclientMinecraft.player.getEyePosition();
      boolean[] alert = new boolean[1];
      this.alertMark = BlockTargetIndex.get().forEachAddedSince(this.alertMark, packed -> {
         BlockTargetIndex.Entry entry = BlockTargetIndex.get().index().get(packed);
         if (!alert[0] && entry != null && TargetMatcher.alertsForBlock(this.targets, ignored, packed, entry, eye)) {
            alert[0] = true;
         }
      });
      if (alert[0]) {
         TargetMatcher.playAlert();
      }
   }

   @EventHandler
   private void onEntityAdded(ClientLevelAddEntityEvent event) {
      if (argusclientMinecraft.player != null
         && TargetMatcher.alertsForSpawn(this.targets, event.getEntity(), argusclientMinecraft.player.getEyePosition())) {
         TargetMatcher.playAlert();
      }
   }

   @EventHandler
   private void onRenderMarkers(HudRenderEvent event) {
      if (this.modeSetting.getValue() == TracersModeChoice.MARKER) {
         this.markerRenderer.render(event.getPoseStack(), event.getPartialTick());
      }
   }

   @EventHandler(priority = -9999)
   private void onRenderLines(WorldRenderEvent.Main event) {
      if (this.modeSetting.getValue() == TracersModeChoice.LINES) {
         this.lineRenderer.render(event.getPoseStack(), event.getPartialTick());
      }
   }

   // ---- config migration ------------------------------------------------------------------------------------------

   @Override
   public void fromJson(JsonElement json) {
      if (json != null && json.isJsonObject() && json.getAsJsonObject().get("settings") instanceof JsonObject settings && !settings.has("PlayersEnabled")) {
         JsonObject migrated = json.getAsJsonObject().deepCopy();
         JsonObject out = migrated.getAsJsonObject("settings");
         out.addProperty("PlayersEnabled", true);
         if (settings.has("Color")) {
            out.add("PlayersColor", settings.get("Color").deepCopy());
         }

         if (settings.has("MaxDistance")) {
            out.add("PlayersRange", settings.get("MaxDistance").deepCopy());
         }

         if (settings.has("IgnoreFriends") && settings.get("IgnoreFriends").getAsBoolean()) {
            out.addProperty("PlayersFriends", "Hide");
         }

         if (settings.has("IgnoreNakeds")) {
            out.add("PlayersIgnoreNaked", settings.get("IgnoreNakeds").deepCopy());
         }

         super.fromJson(migrated);
      } else {
         super.fromJson(json);
      }
   }
}
