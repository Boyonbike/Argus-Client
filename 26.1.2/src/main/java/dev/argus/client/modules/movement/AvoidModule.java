package dev.argus.client.modules.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.AbstractClientPlayerTickEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.managers.TickRateController;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.entity.PlayerStateUtils;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipBlockStateContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public class AvoidModule extends Module {
   private final Setting<Boolean> unloadSetting = this.add(new BooleanSetting("Unload", false).markVisibilityDependency());
   private final Setting<Float> timerSetting = this.add(new NumberSetting<>("Timer", 0.25F, 0.1F, 0.5F).dependsOn(this.unloadSetting));
   private final Setting<Boolean> voidSetting = this.add(new BooleanSetting("Void", true).markVisibilityDependency());
   private final Setting<AvoidModule.ModeChoice> modeSetting = this.add(new EnumSetting<>("Mode", AvoidModule.ModeChoice.CANCEL).dependsOn(this.voidSetting));
   private final Setting<Integer> heightSetting = this.add(new NumberSetting<>("Height", 30, 1, 100).withUnit("m").dependsOn(this.voidSetting));

   @Override
   public String getInfo() {
      return !this.voidSetting.getValue() ? null : FontRenderer.capitalize(this.modeSetting.getValue());
   }

   @Override
   public void onDisable() {
      ClientContext.tickRateController.releaseMultiplier(this);
   }

   @EventHandler
   private void onClientPlayerTick(AbstractClientPlayerTickEvent event) {
      if (this.unloadSetting.getValue() && !this.areNearbyChunksLoaded()) {
         TickRateController timer = ClientContext.tickRateController;
         Float multiplier = this.timerSetting.getValue();
         timer.requestMultiplier(this, multiplier);
      } else {
         TickRateController timer = ClientContext.tickRateController;
         timer.releaseMultiplier(this);
      }
   }

   private boolean areNearbyChunksLoaded() {
      ClientChunkCache chunks = ClientAccess.argusclientMinecraft.level.getChunkSource();
      ChunkPos playerChunk = ClientAccess.argusclientMinecraft.player.chunkPosition();

      for (int x = -1; x <= 1; x++) {
         for (int z = -1; z <= 1; z++) {
            if (!chunks.hasChunk(playerChunk.x + x, playerChunk.z + z)) {
               return false;
            }
         }
      }

      return true;
   }

   @EventHandler
   private void onPlayerTick(LocalPlayerTickEvent event) {
      if (this.voidSetting.getValue()) {
         this.preventVoidFall();
      }
   }

   private void preventVoidFall() {
      if (!ClientAccess.argusclientMinecraft.player.onGround()) {
         if (!(PlayerStateUtils.getTrackedFallDistance() <= 0.0F)) {
            ClientLevel rayLevel = ClientAccess.argusclientMinecraft.level;
            ClipBlockStateContext rayContext = new ClipBlockStateContext(
               ClientAccess.argusclientMinecraft.player.position(),
               new Vec3(
                  ClientAccess.argusclientMinecraft.player.getX(),
                  ClientAccess.argusclientMinecraft.level.getMinY() - 1,
                  ClientAccess.argusclientMinecraft.player.getZ()
               ),
               state -> state.isSolid()
            );
            if (rayLevel.isBlockInLine(rayContext).getType() == Type.MISS) {
               int minimumY = ClientAccess.argusclientMinecraft.level.getMinY();
               Integer configuredHeight = this.heightSetting.getValue();
               double triggerY = minimumY + configuredHeight;
               if (!(triggerY < ClientAccess.argusclientMinecraft.player.getY())) {
                  if (this.modeSetting.getValue() == AvoidModule.ModeChoice.TELEPORT) {
                     ClientAccess.argusclientMinecraft.player.setDeltaMovement(0.0, 10.0, 0.0);
                  } else {
                     LocalPlayer stopPlayer = ClientAccess.argusclientMinecraft.player;
                     Vec3 stoppedVelocity = stopPlayer.getDeltaMovement().with(Axis.Y, 0.0);
                     stopPlayer.setDeltaMovement(stoppedVelocity);
                     ClientAccess.argusclientMinecraft.player.setOnGround(true);
                  }
               }
            }
         }
      }
   }

   public AvoidModule() {
      super("Avoid", "Prevents you from getting into dangerous situations.", ModuleCategory.MOVEMENT, "AntiVoid");
      this.setDrawn(false);
   }

   public enum ModeChoice implements NamedEntry {
      TELEPORT("Teleport"),
      CANCEL("Cancel");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      ModeChoice(String displayName) {
         this.displayName = displayName;
      }
   }
}
