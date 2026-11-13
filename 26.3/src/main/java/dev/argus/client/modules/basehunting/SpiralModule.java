package dev.argus.client.modules.basehunting;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.MinecraftDisconnectEvent;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.render.LineMeshRenderer;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.DimensionType;
import dev.argus.client.utils.chat.ChatUtils;
import dev.argus.client.utils.world.WorldUtils;
import java.awt.Color;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * Searches an area for you so every chunk in it gets loaded and scanned by BaseFinder, NewChunks and the rest. Pick a
 * smooth curved spiral that starts the way you face and keeps your view at the angle you left it, a square or round
 * spiral out from where you start, a rectangle to a far corner, or an endless zigzag, then how to travel it: steer your heading only, walk it, fly it fully automatically with ElytraFly's Volytra Fly mode or with an
 * elytra and rockets, or hand each corner to Baritone. Progress is kept, so turning it off and on again carries on.
 * Merges the old Spiral with bephax's AreaLoader.
 */
public class SpiralModule extends Module {
   private final Setting<Mode> modeSetting = this.add(new EnumSetting<>("Mode", Mode.SMOOTH));
   private final Setting<Boolean> curveRightSetting = this.add(new BooleanSetting("CurveRight", true));
   private final Setting<Integer> lookaheadSetting = this.add(new NumberSetting<>("Lookahead", 40, 8, 256).withUnit("m"));
   private final Setting<Integer> gapSetting = this.add(new NumberSetting<>("Gap", 8, 1, 64).withUnit(" chunks"));
   private final Setting<Integer> endXSetting = this.add(new NumberSetting<>("EndX", 0, -30_000_000, 30_000_000).withSliderMaximum(100_000));
   private final Setting<Integer> endZSetting = this.add(new NumberSetting<>("EndZ", 0, -30_000_000, 30_000_000).withSliderMaximum(100_000));
   private final Setting<Integer> legLengthSetting = this.add(new NumberSetting<>("LegLength", 5000, 256, 100_000).withUnit("m").withSliderMaximum(20_000));
   private final Setting<Integer> maxRadiusSetting = this.add(new NumberSetting<>("MaxRadius", 20000, 512, 1_000_000).withUnit("m").withSliderMaximum(100_000));
   private final Setting<Integer> reachSetting = this.add(new NumberSetting<>("Reach", 24, 2, 200).withUnit("m"));
   private final Setting<Boolean> resumeSetting = this.add(new BooleanSetting("Resume", true));
   private final Setting<Finish> finishSetting = this.add(new EnumSetting<>("WhenDone", Finish.STOP));
   private final Setting<Boolean> renderSetting = this.add(new BooleanSetting("ShowPath", true).markVisibilityDependency());
   private final Setting<Integer> legsShownSetting = this.add(new NumberSetting<>("LegsShown", 6, 1, 32).dependsOn(this.renderSetting));
   private final Setting<Color> pathColorSetting = this.add(new ColorSetting("PathColor", new Color(120, 255, 160, 220)).dependsOn(this.renderSetting));
   private final HuntPilot pilot = new HuntPilot(this, HuntPilot.Travel.ROCKETS, 6.0F);
   private List<double[]> points = List.of();
   private int index;
   private SmoothSpiral smooth;
   private double theta;
   private Mode builtMode;
   private DimensionType builtDimension;

   public SpiralModule() {
      super("Spiral", "Searches an area chunk by chunk, steering, walking or flying itself along a spiral, rectangle or zigzag.", ModuleCategory.BASE_HUNTING);
      this.endXSetting.addVisibilityPredicate(value -> this.modeSetting.getValue() == Mode.RECTANGLE);
      this.endZSetting.addVisibilityPredicate(value -> this.modeSetting.getValue() == Mode.RECTANGLE);
      this.legLengthSetting.addVisibilityPredicate(value -> this.modeSetting.getValue() == Mode.ZIGZAG);
      this.maxRadiusSetting.addVisibilityPredicate(value -> this.modeSetting.getValue() != Mode.RECTANGLE);
      this.curveRightSetting.addVisibilityPredicate(value -> this.modeSetting.getValue() == Mode.SMOOTH);
      this.lookaheadSetting.addVisibilityPredicate(value -> this.modeSetting.getValue() == Mode.SMOOTH);
      this.reachSetting.addVisibilityPredicate(value -> this.modeSetting.getValue() != Mode.SMOOTH);
   }

   private boolean hasPath() {
      return this.modeSetting.getValue() == Mode.SMOOTH ? this.smooth != null : !this.points.isEmpty();
   }

   @Override
   public void onEnable() {
      if (this.isWorldUnavailable()) {
         this.disable();
         return;
      }

      DimensionType dimension = WorldUtils.getDimension();
      boolean canResume = this.resumeSetting.getValue() && this.hasPath() && this.builtMode == this.modeSetting.getValue() && this.builtDimension == dimension;
      if (!canResume) {
         this.build();
      }

      this.pilot.start();
      if (this.modeSetting.getValue() == Mode.RECTANGLE && this.endXSetting.getValue() == 0 && this.endZSetting.getValue() == 0) {
         this.say("Rectangle mode searches to EndX/EndZ, which are both 0; set the far corner first.");
      }

      if (this.modeSetting.getValue() == Mode.SMOOTH) {
         if (canResume) {
            // Pick up on whichever ring is nearest to where you are now.
            LocalPlayer player = ClientAccess.argusclientMinecraft.player;
            this.theta = this.smooth.thetaNear(player.getX(), player.getZ(), -1.0);
         }

         this.say(canResume ? "Carrying on the spiral " + Math.round(this.smooth.radius(this.theta)) + "m out." : "Spiralling out from here, starting the way you face.");
      } else {
         this.say(canResume ? "Carrying on at corner " + (this.index + 1) + " of " + this.points.size() + "." : "Path ready: " + this.points.size() + " corners.");
      }
   }

   @Override
   public void onDisable() {
      this.pilot.stop();
   }

   @EventHandler
   private void onDisconnect(MinecraftDisconnectEvent event) {
      this.disable();
   }

   /** Throws away saved progress; the next start builds a fresh path from where you are. */
   public void resetPath() {
      this.points = List.of();
      this.index = 0;
      this.smooth = null;
   }

   private void build() {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      double x = player.getX();
      double z = player.getZ();
      double gap = this.gapSetting.getValue() * 16.0;
      Mode mode = this.modeSetting.getValue();
      this.builtMode = mode;
      this.builtDimension = WorldUtils.getDimension();
      if (mode == Mode.SMOOTH) {
         // Starts heading exactly where you look, so nothing snaps. Only the heading changes from here; the pitch stays
         // yours (Elytra + rockets climbs and dives when it must, then settles back to it).
         this.smooth = SmoothSpiral.startingAt(x, z, player.getYRot(), gap, this.curveRightSetting.getValue());
         this.theta = SmoothSpiral.START_THETA;
         this.points = List.of();
         return;
      }

      this.smooth = null;
      this.points = switch (mode) {
         case RECTANGLE -> AreaPaths.rectangle(x, z, this.endXSetting.getValue(), this.endZSetting.getValue(), gap);
         case SPIRAL -> AreaPaths.squareSpiral(x, z, gap, (int)Math.ceil(this.maxRadiusSetting.getValue() / gap) * 2 + 2);
         case CIRCLE -> AreaPaths.circleSpiral(x, z, gap, Math.max(32.0, gap / 2.0), this.maxRadiusSetting.getValue());
         case ZIGZAG -> AreaPaths.zigzag(x, z, player.getYRot(), this.legLengthSetting.getValue(), gap, (int)Math.ceil(this.maxRadiusSetting.getValue() / gap));
         case SMOOTH -> List.of();
      };
      this.index = 0;
      this.builtMode = mode;
      this.builtDimension = WorldUtils.getDimension();
   }

   @Override
   public String getInfo() {
      if (this.modeSetting.getValue() == Mode.SMOOTH) {
         return this.smooth == null ? null : Math.round(this.smooth.radius(this.theta)) + "m";
      }

      return this.points.isEmpty() ? null : Math.min(this.index + 1, this.points.size()) + "/" + this.points.size();
   }

   @EventHandler
   private void onTick(LocalPlayerTickEvent event) {
      if (this.isWorldUnavailable() || !this.hasPath()) {
         return;
      }

      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      if (this.modeSetting.getValue() == Mode.SMOOTH) {
         this.tickSmooth(player);
         return;
      }

      if (this.index >= this.points.size()) {
         this.finish();
         return;
      }

      double[] target = this.points.get(this.index);
      double dx = target[0] - player.getX();
      double dz = target[1] - player.getZ();
      if (dx * dx + dz * dz < this.reachSetting.getValue() * this.reachSetting.getValue() || this.passed(player, target)) {
         this.index++;
         return;
      }

      float wanted = RotationManager.getRotationsToPosition(new Vec3(target[0], player.getY(), target[1]))[0];
      this.pilot.tick(player, wanted, new Vec3(target[0], player.getY(), target[1]));
   }

   /** Chases a point a little way along the spiral ahead of you, which turns you in one long, even curve. */
   private void tickSmooth(LocalPlayer player) {
      this.theta = Math.max(this.theta, this.smooth.thetaNear(player.getX(), player.getZ(), this.theta));
      if (this.smooth.radius(this.theta) > this.maxRadiusSetting.getValue()) {
         this.finish();
         return;
      }

      double target = this.smooth.ahead(this.theta, this.lookaheadSetting.getValue());
      double[] point = this.smooth.point(target);
      float wanted = this.smooth.yawTowards(player.getX(), player.getZ(), target);
      this.pilot.tick(player, wanted, new Vec3(point[0], player.getY(), point[1]));
   }

   private void finish() {
      if (this.finishSetting.getValue() == Finish.REPEAT) {
         this.say("Finished the path; starting a new one from here.");
         this.build();
         return;
      }

      this.say("Finished the path.");
      this.resetPath();
      this.disable();
   }

   /** Whether you have already gone past a corner along the leg leading to it, so it counts as reached. */
   private boolean passed(LocalPlayer player, double[] target) {
      if (this.index == 0) {
         return false;
      }

      double[] previous = this.points.get(this.index - 1);
      double legX = target[0] - previous[0];
      double legZ = target[1] - previous[1];
      double toPlayerX = player.getX() - target[0];
      double toPlayerZ = player.getZ() - target[1];
      return legX * toPlayerX + legZ * toPlayerZ > 0.0;
   }

   @EventHandler
   private void onWorldRender(WorldRenderEvent.Main event) {
      if (this.renderSetting.getValue() && !this.isWorldUnavailable() && this.smooth != null && this.modeSetting.getValue() == Mode.SMOOTH) {
         this.renderSmooth(event);
         return;
      }

      if (!this.renderSetting.getValue() || this.isWorldUnavailable() || this.points.isEmpty() || this.index >= this.points.size()) {
         return;
      }

      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      double y = player.getY();
      int color = this.pathColorSetting.getValue().hashCode();
      LineMeshRenderer.begin(event, 1.5F);
      double[] target = this.points.get(this.index);
      LineMeshRenderer.addLine(player.position(), new Vec3(target[0], y, target[1]), color);
      int last = Math.min(this.points.size(), this.index + 1 + this.legsShownSetting.getValue());
      for (int corner = this.index + 1; corner < last; corner++) {
         double[] from = this.points.get(corner - 1);
         double[] to = this.points.get(corner);
         LineMeshRenderer.addLine(new Vec3(from[0], y, from[1]), new Vec3(to[0], y, to[1]), color);
      }

      LineMeshRenderer.draw();
   }

   /** Draws the curve ahead, a quarter turn for each leg shown. */
   private void renderSmooth(WorldRenderEvent.Main event) {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      double y = player.getY();
      int color = this.pathColorSetting.getValue().hashCode();
      double end = this.theta + this.legsShownSetting.getValue() * Math.PI / 2.0;
      LineMeshRenderer.begin(event, 1.5F);
      double[] previous = this.smooth.point(this.theta);
      LineMeshRenderer.addLine(player.position(), new Vec3(previous[0], y, previous[1]), color);
      for (double step = this.theta; step < end; ) {
         step = Math.min(end, this.smooth.ahead(step, 12.0));
         double[] next = this.smooth.point(step);
         LineMeshRenderer.addLine(new Vec3(previous[0], y, previous[1]), new Vec3(next[0], y, next[1]), color);
         previous = next;
      }

      LineMeshRenderer.draw();
   }

   private void say(String message) {
      ChatUtils.printChat(Component.literal("Spiral: " + message), ChatUtils.moduleSignature(this));
   }

   public enum Mode implements NamedEntry {
      SMOOTH("Smooth spiral"),
      SPIRAL("Square spiral"),
      CIRCLE("Circle"),
      RECTANGLE("Rectangle"),
      ZIGZAG("ZigZag");

      private final String displayName;

      Mode(String displayName) {
         this.displayName = displayName;
      }

      @Override
      public String getName() {
         return this.displayName;
      }
   }

   public enum Finish implements NamedEntry {
      STOP("Stop"),
      REPEAT("New spiral from here");

      private final String displayName;

      Finish(String displayName) {
         this.displayName = displayName;
      }

      @Override
      public String getName() {
         return this.displayName;
      }
   }
}
