package dev.argus.client.modules.basehunting;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.MinecraftDisconnectEvent;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.exploit.ChunkKind;
import dev.argus.client.modules.exploit.NewChunksModule;
import dev.argus.client.render.LineMeshRenderer;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.chat.ChatUtils;
import dev.argus.client.utils.world.WorldUtils;
import java.awt.Color;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * Follows chunk trails: lines of old chunks that players left crossing new terrain, which lead to bases. It reads the
 * chunk ages NewChunks works out (switching NewChunks on if needed), looks down a fan of headings ahead and turns
 * smoothly toward the one with the most trail. It can steer only, walk, or fly itself with Volytra Fly or an elytra and
 * rockets. When the trail runs out it keeps going, searches around, or stops. Adapted from bephax's TrailFollower.
 */
public class TrailFollowerModule extends Module {
   private final Setting<Boolean> only112Setting = this.add(new BooleanSetting("Only1.12", false));
   private final Setting<Integer> startChunksSetting = this.add(new NumberSetting<>("StartChunks", 4, 1, 32));
   private final Setting<Float> coneSetting = this.add(new NumberSetting<>("Cone", 60.0F, 10.0F, 120.0F).withUnit("°"));
   private final Setting<Integer> lookaheadSetting = this.add(new NumberSetting<>("Lookahead", 12, 3, 32).withUnit(" chunks"));
   private final Setting<Float> turnCostSetting = this.add(new NumberSetting<>("TurnCost", 0.6F, 0.0F, 5.0F));
   private final Setting<Integer> lostAfterSetting = this.add(new NumberSetting<>("LostAfter", 20, 3, 300).withUnit("s"));
   private final Setting<LostAction> lostSetting = this.add(new EnumSetting<>("WhenLost", LostAction.SEARCH));
   private final Setting<Boolean> renderSetting = this.add(new BooleanSetting("ShowHeading", true).markVisibilityDependency());
   private final Setting<Color> colorSetting = this.add(new ColorSetting("HeadingColor", new Color(255, 210, 80, 220)).dependsOn(this.renderSetting));
   private final HuntPilot pilot = new HuntPilot(this, HuntPilot.Travel.STEER, 2.0F);
   private NewChunksModule newChunks;
   private boolean enabledNewChunks;
   private boolean following;
   private float heading;
   private float desired;
   private long lastTrailMillis;
   private int planTicks;
   private float searchOffset;

   public TrailFollowerModule() {
      super("TrailFollower", "Steers you along trails of old chunks, which lead to bases.", ModuleCategory.BASE_HUNTING);
   }

   @Override
   public void onEnable() {
      if (this.isWorldUnavailable()) {
         this.disable();
         return;
      }

      this.newChunks = ClientContext.getModule(NewChunksModule.class);
      this.enabledNewChunks = false;
      if (this.newChunks != null) {
         this.newChunks.classifyAgeForOthers = true;
         if (!this.newChunks.isToggled()) {
            this.newChunks.enable();
            this.enabledNewChunks = true;
         }
      }

      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      this.heading = TrailPlanner.wrap(player.getYRot());
      this.desired = this.heading;
      this.following = false;
      this.lastTrailMillis = System.currentTimeMillis();
      this.planTicks = 0;
      this.searchOffset = 0.0F;
      this.pilot.start();
      this.say("Looking for a trail ahead.");
   }

   @Override
   public void onDisable() {
      this.pilot.stop();
      if (this.newChunks != null) {
         this.newChunks.classifyAgeForOthers = false;
         if (this.enabledNewChunks && this.newChunks.isToggled()) {
            this.newChunks.disable();
         }
      }

      this.enabledNewChunks = false;
   }

   @EventHandler
   private void onDisconnect(MinecraftDisconnectEvent event) {
      this.disable();
   }

   @Override
   public String getInfo() {
      return this.following ? "following" : "searching";
   }

   private boolean isTrail(int chunkX, int chunkZ) {
      if (this.newChunks == null) {
         return false;
      }

      ChunkKind kind = this.newChunks.getChunkKind(new ChunkPos(chunkX, chunkZ), WorldUtils.getDimension());
      if (kind == null) {
         return false;
      }

      return this.only112Setting.getValue() ? kind == ChunkKind.BLOCKS : kind == ChunkKind.OLD || kind == ChunkKind.BLOCKS || kind == ChunkKind.OVERFLOW;
   }

   @EventHandler
   private void onTick(LocalPlayerTickEvent event) {
      if (this.isWorldUnavailable()) {
         return;
      }

      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      if (++this.planTicks >= 10) {
         this.planTicks = 0;
         this.plan(player);
      }

      double radians = Math.toRadians(this.desired);
      double ahead = this.lookaheadSetting.getValue() * 16.0;
      Vec3 goal = player.position().add(-Math.sin(radians) * ahead, 0.0, Math.cos(radians) * ahead);
      this.pilot.tick(player, this.desired, goal);
      this.heading = TrailPlanner.wrap(player.getYRot());
   }

   private void plan(LocalPlayer player) {
      int lookahead = this.lookaheadSetting.getValue();
      TrailPlanner.Choice choice = TrailPlanner.choose(
         this::isTrail, player.getX(), player.getZ(), this.heading, this.coneSetting.getValue(), lookahead, this.turnCostSetting.getValue()
      );
      double trailAhead = TrailPlanner.score(this::isTrail, player.getX(), player.getZ(), choice.yaw(), lookahead);
      long now = System.currentTimeMillis();

      if (!this.following) {
         if (trailAhead >= this.startChunksSetting.getValue() * 0.5) {
            this.following = true;
            this.lastTrailMillis = now;
            this.desired = choice.yaw();
            this.say("Trail found, following it.");
         }

         return;
      }

      if (trailAhead > 0.5) {
         this.lastTrailMillis = now;
         this.desired = choice.yaw();
         this.searchOffset = 0.0F;
         return;
      }

      if (now - this.lastTrailMillis < this.lostSetting.getValue().graceMillis(this.lostAfterSetting.getValue())) {
         return;
      }

      switch (this.lostSetting.getValue()) {
         case STOP -> {
            this.say("Lost the trail; stopping.");
            this.disable();
         }
         case KEEP_GOING -> {
         }
         case SEARCH -> {
            // Sweep left and right a little further each time until the trail shows up again.
            this.searchOffset = this.searchOffset <= 0.0F ? -this.searchOffset + 15.0F : -this.searchOffset;
            this.desired = TrailPlanner.wrap(this.heading + this.searchOffset);
            if (Math.abs(this.searchOffset) > 180.0F) {
               this.searchOffset = 0.0F;
            }
         }
      }
   }

   @EventHandler
   private void onWorldRender(WorldRenderEvent.Main event) {
      if (!this.renderSetting.getValue() || this.isWorldUnavailable()) {
         return;
      }

      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      double radians = Math.toRadians(this.desired);
      double distance = this.lookaheadSetting.getValue() * 16.0;
      Vec3 from = player.position();
      Vec3 to = from.add(-Math.sin(radians) * distance, 0.0, Math.cos(radians) * distance);
      LineMeshRenderer.begin(event, 2.0F);
      LineMeshRenderer.addLine(from, to, this.colorSetting.getValue().hashCode());
      LineMeshRenderer.draw();
   }

   private void say(String message) {
      ChatUtils.printChat(Component.literal("TrailFollower: " + message), ChatUtils.moduleSignature(this));
   }

   public enum LostAction implements NamedEntry {
      SEARCH("Search"),
      KEEP_GOING("Keep going"),
      STOP("Stop");

      private final String displayName;

      LostAction(String displayName) {
         this.displayName = displayName;
      }

      long graceMillis(int seconds) {
         return seconds * 1000L;
      }

      @Override
      public String getName() {
         return this.displayName;
      }
   }
}
