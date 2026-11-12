package dev.argus.client.modules.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.managers.WaypointRegistry;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.render.LineMeshRenderer;
import dev.argus.client.render.RenderUtils;
import dev.argus.client.render.WorldBoxRenderer;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.SettingLabelCondition;
import dev.argus.client.utils.math.MathUtils;
import dev.argus.client.utils.world.Waypoint;
import dev.argus.client.utils.world.WorldUtils;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class WaypointsModule extends Module {
   private final Setting<Float> distanceSetting = this.add(new NumberSetting<>("Distance", 101.0F, 0.1F, 101.0F).withUnit("k"));
   private final Setting<Float> lineWidthSetting = this.add(new NumberSetting<>("LineWidth", 1.0F, 0.1F, 3.0F));
   private final Setting<Boolean> boxSetting = this.add(new BooleanSetting("Box", true));
   private final Setting<Boolean> beamSetting = this.add(new BooleanSetting("Beam", false));
   private final Setting<Boolean> tracersSetting = this.add(new BooleanSetting("Tracers", false));
   final Setting<Boolean> nameSetting = this.add(new BooleanSetting("Name", true));
   private final Setting<WaypointsModule.InfoChoice> infoSetting;
   private final Setting<Float> textScaleSetting;
   private final Setting<Boolean> textBackgroundSetting;
   private final Setting<Boolean> eyesAlignSetting;
   private final Setting<Color> colorSetting;
   private final Setting<Color> textSetting;
   private final Setting<Integer> beamAlphaSetting;
   private final CopyOnWriteArrayList<Waypoint> visibleWaypoints;
   private static final int DISTANCE_UNITS_PER_KILOMETER = 1000;
   private static final double FAR_WAYPOINT_DISTANCE = 120.0;
   private static final double FAR_WAYPOINT_RADIUS = 100.0;
   private static final double WAYPOINT_HALF_SIZE = 0.25;
   private static final int TEXT_BACKGROUND_ALPHA = 100;

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      if (player.tickCount % 2 != 0) {
         WaypointRegistry registry = ClientContext.waypointRegistry;
         List<Waypoint> registeredWaypoints = (List<Waypoint>)registry.getRegistry();
         List<Waypoint> invalidWaypoints = new ArrayList<>();

         for (Waypoint waypoint : registeredWaypoints) {
            if (waypoint != null && (waypoint.getServer() == null || waypoint.getDimension() == null || waypoint.getName() == null)) {
               invalidWaypoints.add(waypoint);
            }
         }

         for (Waypoint invalidWaypoint : invalidWaypoints) {
            registry.unregisterWaypoint(invalidWaypoint);
         }

         this.visibleWaypoints.clear();
         String currentServer = WaypointRegistry.currentServerKey();

         for (Waypoint waypoint : registeredWaypoints) {
            if (waypoint != null && waypoint.isToggled()) {
               String waypointServer = waypoint.getServer();
               if (waypointServer.equalsIgnoreCase(currentServer)) {
                  this.visibleWaypoints.add(waypoint);
               }
            }
         }

         this.visibleWaypoints.sort(Comparator.comparingDouble(waypointx -> player.distanceToSqr(waypointx.getPosition())));
      }
   }

   public static Vec3 getPositionInCurrentDimension(Waypoint waypoint) {
      Vec3 position = waypoint.getPosition();
      String currentDimension = WorldUtils.getDimension().getDisplayName().toLowerCase(Locale.ROOT);
      String waypointDimension = waypoint.getDimension();
      if (waypointDimension.equals("overworld") && currentDimension.equals("nether")) {
         return position.multiply(0.125, 1.0, 0.125);
      } else if (waypointDimension.equals("nether") && currentDimension.equals("overworld")) {
         return position.multiply(8.0, 1.0, 8.0);
      } else {
         return waypointDimension.equals(currentDimension) ? position : null;
      }
   }

   private Vec3 getRenderPosition(Vec3 cameraPosition, Vec3 waypointPosition) {
      double dx = waypointPosition.x - cameraPosition.x;
      double dz = waypointPosition.z - cameraPosition.z;
      double horizontalDistance = Math.hypot(dx, dz);
      if (horizontalDistance < 120.0) {
         return waypointPosition;
      }

      double distanceScale = 100.0 / horizontalDistance;
      double x = cameraPosition.x + dx * distanceScale;
      double y = Mth.lerp(distanceScale, cameraPosition.y, waypointPosition.y);
      double z = cameraPosition.z + dz * distanceScale;
      return new Vec3(x, y, z);
   }

   private void renderWaypoint(PoseStack poseStack, Vec3 cameraPosition, Waypoint waypoint) {
      Vec3 waypointPosition = getPositionInCurrentDimension(waypoint);
      if (waypointPosition != null) {
         float maximumDistance = this.distanceSetting.getValue();
         if (this.distanceSetting.isAtMaximum() || !(waypointPosition.distanceTo(cameraPosition) > maximumDistance * 1000.0F)) {
            Vec3 renderPosition = this.getRenderPosition(cameraPosition, waypointPosition);
            if (this.beamSetting.getValue()) {
               AABB beamBounds = around(renderPosition, 0.25);
               double worldTop = WorldUtils.getTopY();
               LevelReader world = ClientAccess.argusclientMinecraft.level;
               beamBounds = beamBounds.setMaxY(worldTop).setMinY(world.getMinY());
               Color beamColor = this.colorSetting.getValue();
               int beamAlpha = this.beamAlphaSetting.getValue();
               WorldBoxRenderer.fillBox(poseStack, beamBounds, ColorUtils.withAlpha(beamColor, beamAlpha));
            }

            if (this.boxSetting.getValue()) {
               AABB box = around(waypointPosition, 0.25);
               Color color = this.colorSetting.getValue();
               float lineWidth = this.lineWidthSetting.getValue();
               WorldBoxRenderer.outlineBox(poseStack, box, color, lineWidth);
            }

            if (this.tracersSetting.getValue()) {
               LineMeshRenderer.addTracer(renderPosition, this.colorSetting.getValue());
            }

            if (this.nameSetting.getValue()) {
               this.renderWaypointName(poseStack, cameraPosition, renderPosition, waypointPosition, waypoint);
            }
         }
      }
   }

   private void renderWaypointName(PoseStack poseStack, Vec3 cameraPosition, Vec3 renderPosition, Vec3 waypointPosition, Waypoint waypoint) {
      if (this.eyesAlignSetting.getValue()) {
         renderPosition = renderPosition.with(Axis.Y, cameraPosition.y);
      }

      String label = waypoint.getName() + " " + this.infoSetting.getValue().formatInfo(waypointPosition);
      double textScale = MathUtils.getDistanceScale(cameraPosition, renderPosition, this.textScaleSetting.getValue().floatValue());
      float textWidth = FontRenderer.INSTANCE.getWidth(label);
      if (this.textBackgroundSetting.getValue()) {
         float textHeight = FontRenderer.INSTANCE.getHeight() + 1.0F;
         RenderUtils.INSTANCE.drawWorldRect(poseStack, renderPosition, -0.03F, 0.25F, -textWidth, textHeight, textScale, new Color(0, 0, 0, 100));
      }

      RenderUtils.INSTANCE.drawWorldText(poseStack, label, renderPosition, 0.0F, 0.23F, -textWidth / 2.0F, 0.0F, textScale, this.textSetting.getValue(), true);
   }

   private static AABB around(Vec3 position, double halfSize) {
      return new AABB(position.add(-halfSize, -halfSize, -halfSize), position.add(halfSize, halfSize, halfSize));
   }

   @EventHandler
   private void onWorldRender(WorldRenderEvent.Main event) {
      if (!ClientAccess.argusclientMinecraft.gui.hud.isHidden) {
         PoseStack poseStack = event.getPoseStack();
         Vec3 cameraPosition = ClientAccess.argusclientMinecraft.gameRenderer.mainCamera().position();
         LineMeshRenderer.begin(event, this.lineWidthSetting.getValue());

         for (Waypoint waypoint : this.visibleWaypoints) {
            if (waypoint != null && waypoint.getDimension() != null && waypoint.getName() != null && waypoint.getServer() != null) {
               this.renderWaypoint(poseStack, cameraPosition, waypoint);
            }
         }

         LineMeshRenderer.draw();
      }
   }

   public WaypointsModule() {
      super("Waypoints", "Renders certain places you mark using ;waypoints.", ModuleCategory.RENDER);
      Predicate<Object> infoVisibility = ignored -> this.nameSetting.getValue();
      this.infoSetting = this.add(new EnumSetting<>("Info", WaypointsModule.InfoChoice.NONE, infoVisibility));
      this.textScaleSetting = this.add(new NumberSetting<>("TextScale", 1.0F, 0.5F, 3.0F, ignored -> this.nameSetting.getValue()));
      this.textBackgroundSetting = this.add(new BooleanSetting("TextBackground", true, ignored -> this.nameSetting.getValue()));
      this.eyesAlignSetting = this.add(new BooleanSetting("EyesAlign", false).dependsOn(this.nameSetting));
      this.colorSetting = this.add(new ColorSetting("Color", new Color(-457336321, true)));
      this.textSetting = this.add(new ColorSetting("Text", new Color(-1, true)));
      this.beamAlphaSetting = this.add(new NumberSetting<>("BeamAlpha", 40, 0, 255));
      this.visibleWaypoints = new CopyOnWriteArrayList<>();
      Setting<Float> distanceSettingForLabel = this.distanceSetting;
      SettingLabelCondition unlimitedBoundary = SettingLabelCondition.MAX;
      distanceSettingForLabel.setBoundaryLabel("Unlimited", unlimitedBoundary);
      this.setDrawn(false);
   }

   private enum InfoChoice implements NamedEntry {
      NONE("None"),
      COORDS("Coords") {
         @Override
         public String formatInfo(Vec3 position) {
            Locale locale = Locale.US;
            return String.format(locale, "X: %.1f, Y: %.1f, Z: %.1f", position.x(), position.y(), position.z());
         }
      },
      DISTANCE("Distance") {
         @Override
         public String formatInfo(Vec3 position) {
            return String.format("%.1fm", position.distanceTo(ClientAccess.argusclientMinecraft.player.position()));
         }
      };

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      InfoChoice(String displayName) {
         this.displayName = displayName;
      }

      public String formatInfo(Vec3 position) {
         return "";
      }
   }
}
