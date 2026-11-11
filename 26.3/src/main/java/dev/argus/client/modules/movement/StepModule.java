package dev.argus.client.modules.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.EventStage;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.StepHeightEvent;
import dev.argus.client.managers.HoleManager;
import dev.argus.client.managers.ModuleRegistry;
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
import dev.argus.client.utils.network.PacketSender;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;

public class StepModule extends Module {
   private static HoleSnapModule holeSnap;
   final Setting<StepModule.ModeChoice> modeSetting = this.add(new EnumSetting<>("Mode", StepModule.ModeChoice.VANILLA));
   private final Setting<Float> heightSetting = this.add(new NumberSetting<>("Height", 2.0F, 1.0F, 4.0F).withUnit("m"));
   private final Setting<Boolean> safeDisableSetting = this.add(new BooleanSetting("SafeDisable", false));
   final Setting<Boolean> useTimerSetting;
   private final Setting<Float> timerSpeedSetting;
   private boolean usingStepTimer;
   private int stepCount;

   @Override
   public String getInfo() {
      return FontRenderer.capitalize(this.modeSetting.getValue());
   }

   @Override
   public void onDisable() {
      this.usingStepTimer = false;
      this.stepCount = 0;
   }

   @EventHandler(priority = -100)
   private void onLocalPlayerPreTickEvent(LocalPlayerPreTickEvent event) {
      if (this.stepCount > 0) {
         HoleManager holes = ClientContext.holeManager;
         BlockPos position = PlayerStateUtils.getLocalRoundedBlockPosition();
         if (holes.intersectsHole(position)) {
            if (this.safeDisableSetting.getValue()) {
               this.disable();
            }
         }
      }
   }

   @EventHandler
   private void onStepHeight(StepHeightEvent event) {
      if (!holeSnap.shouldPauseStep()) {
         if (ClientAccess.argusclientMinecraft.player.onGround()
            && !(PlayerStateUtils.getTrackedFallDistance() > 0.0F)
            && ClientAccess.argusclientMinecraft.player.verticalCollision) {
            if (EventStage.Pre == event.getStage()) {
               Setting<Float> height = this.heightSetting;
               float requestedHeight = height.getValue();
               event.setHeight(requestedHeight);
            }

            if (event.getStage() == EventStage.Post) {
               double actualHeight = event.getHeight();
               if (actualHeight <= 0.6) {
                  TickRateController timer = ClientContext.tickRateController;
                  timer.releaseMultiplier(this);
               } else if (this.modeSetting.getValue() == StepModule.ModeChoice.VANILLA) {
                  this.stepCount++;
               } else {
                  ArrayList<Double> offsets = new ArrayList<>();
                  if (this.useTimerSetting.getValue() && !PlayerStateUtils.isInCobweb(ClientAccess.argusclientMinecraft.player)) {
                     TickRateController timer = ClientContext.tickRateController;
                     float requestedSpeed = this.timerSpeedSetting.getValue();
                     timer.requestMultiplier(this, requestedSpeed);
                     this.usingStepTimer = true;
                  }

                  this.stepCount++;
                  float height = event.getHeight();
                  if (height <= 1.0F) {
                     offsets.addAll(List.of(0.42 * height, 0.75 * height));
                  } else if (height <= 1.5) {
                     offsets.addAll(List.of(0.42, 0.75, 1.0, 1.16, 1.23, 1.2));
                  } else if (height <= this.heightSetting.getValue()) {
                     offsets.addAll(List.of(0.42, 0.78, 0.63, 0.51, 0.9, 1.21, 1.45, 1.43));
                  }

                  if (!offsets.isEmpty()) {
                     for (Double offset : offsets) {
                        double x = ClientAccess.argusclientMinecraft.player.getX();
                        double baseY = ClientAccess.argusclientMinecraft.player.getY();
                        double y = baseY + offset;
                        double z = ClientAccess.argusclientMinecraft.player.getZ();
                        boolean onGround = ClientAccess.argusclientMinecraft.player.onGround();
                        PacketSender.sendPosition(x, y, z, onGround);
                     }
                  }
               }
            }
         } else {
            TickRateController timer = ClientContext.tickRateController;
            timer.releaseMultiplier(this);
         }
      }
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      this.usingStepTimer = false;
   }

   public boolean isUsingTimer() {
      return this.usingStepTimer;
   }

   public static void bindReferencedModules(ModuleRegistry registry) {
      holeSnap = (HoleSnapModule)registry.getModule(HoleSnapModule.class);
   }

   public StepModule() {
      super("Step", "Allows you to step up blocks without jumping.", ModuleCategory.MOVEMENT);
      Predicate<Object> timerVisibility = ignored -> this.modeSetting.getValue() == StepModule.ModeChoice.NORMAL;
      this.useTimerSetting = this.add(new BooleanSetting("UseTimer", true, timerVisibility).markVisibilityDependency());
      Predicate<Object> timerSpeedVisibility = ignored -> this.modeSetting.getValue() == StepModule.ModeChoice.NORMAL && this.useTimerSetting.isExpanded();
      this.timerSpeedSetting = this.add(new NumberSetting<>("TimerSpeed", 0.3F, 0.01F, 1.0F, timerSpeedVisibility));
      this.stepCount = 0;
   }

   public enum ModeChoice implements NamedEntry {
      VANILLA("Vanilla"),
      NORMAL("Normal");

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
