package dev.argus.client.modules.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.audio.BuiltinSound;
import dev.argus.client.commands.FakeplayerCommand;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.HudRenderEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.managers.SoundManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.render.GuiDrawUtils;
import dev.argus.client.render.VertexGeometry;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.IdentifierSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.collections.NamedIdentifier;
import dev.argus.client.utils.network.InteractPacketUtils;
import dev.argus.mixin.ServerboundInteractPacketAccessor;
import java.awt.Color;
import java.util.function.Predicate;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class HitmarkerModule extends Module {
   private static CrosshairModule crosshair = ClientContext.getModule(CrosshairModule.class);
   final Setting<Boolean> drawSetting = this.add(new BooleanSetting("Draw", true).markVisibilityDependency());
   private final Setting<Float> lengthSetting;
   private final Setting<Float> timeSetting;
   private final Setting<Float> fadeTimeSetting;
   private final Setting<Color> colorSetting;
   final Setting<Boolean> soundSetting;
   private final Setting<NamedIdentifier> soundTypeSetting;
   private final Setting<Float> volumeSetting;
   final Setting<Boolean> targetsSetting;
   private final Setting<Boolean> animalsSetting;
   private final Setting<Boolean> hostilesSetting;
   private final Setting<Boolean> playersSetting;
   private final Setting<Boolean> crystalsSetting;
   public long lastHitTime;
   public boolean soundPending;

   public boolean isEligibleTarget(Entity entity) {
      Boolean animalsEnabled = this.animalsSetting.getValue();
      if (animalsEnabled && entity instanceof AgeableMob) {
         return true;
      }

      Boolean hostilesEnabled = this.hostilesSetting.getValue();
      if (hostilesEnabled && entity instanceof Enemy) {
         return true;
      }

      Boolean playersEnabled = this.playersSetting.getValue();
      return playersEnabled && entity instanceof Player ? true : this.crystalsSetting.getValue() && entity instanceof EndCrystal;
   }

   private float getMarkerOpacity() {
      float secondsCapture = this.timeSetting.getValue();
      long durationMillis = (long)(secondsCapture * 1000.0F);
      secondsCapture = this.fadeTimeSetting.getValue();
      long fadeMillis = (long)(secondsCapture * 1000.0F);
      long elapsedMillis = System.currentTimeMillis() - this.lastHitTime;
      return elapsedMillis < durationMillis - fadeMillis ? 1.0F : 1.0F - (float)(elapsedMillis - (durationMillis - fadeMillis)) / (float)fadeMillis;
   }

   @Override
   public void onToggle() {
      this.lastHitTime = -1L;
      this.soundPending = false;
   }

   @EventHandler
   private void onHudRender(HudRenderEvent event) {
      if (!this.isWorldUnavailable()) {
         if (this.soundPending) {
            this.soundPending = false;
            Boolean soundEnabled = this.soundSetting.getValue();
            if (soundEnabled) {
               SoundManager soundManagerCapture = ClientContext.soundManager;
               NamedIdentifier soundIdentifierCapture = this.soundTypeSetting.getValue();
               float volume = this.volumeSetting.getValue();
               soundManagerCapture.playSound(soundIdentifierCapture, volume);
            }
         }

         if (!ClientAccess.argusclientMinecraft.gui.hud.isHidden) {
            GameRenderer gameRenderer = ClientAccess.argusclientMinecraft.gameRenderer;
            Camera camera = gameRenderer.mainCamera();
            if (!camera.isDetached()) {
               long hitTimeCapture = this.lastHitTime;
               if (hitTimeCapture != -1L) {
                  long elapsedMillis = System.currentTimeMillis() - this.lastHitTime;
                  float durationCapture = this.timeSetting.getValue();
                  long durationMillis = (long)(durationCapture * 1000.0F);
                  if (elapsedMillis > durationMillis) {
                     this.lastHitTime = -1L;
                  } else {
                     float opacity = this.getMarkerOpacity();
                     if (opacity > 0.0F) {
                        Boolean drawEnabled = this.drawSetting.getValue();
                        if (drawEnabled) {
                           float scaledWidth = ClientAccess.argusclientMinecraft.getWindow().getGuiScaledWidth();
                           float centerX = scaledWidth / 2.0F;
                           float scaledHeight = ClientAccess.argusclientMinecraft.getWindow().getGuiScaledHeight();
                           float halfHeight = scaledHeight / 2.0F;
                           float centerY = halfHeight - 0.5F;
                           CrosshairModule crosshairForOffset = crosshair;
                           if (!crosshairForOffset.isToggled()) {
                              centerX -= 0.5F;
                           } else {
                              centerY += 0.5F;
                           }

                           Float lengthValue = this.lengthSetting.getValue();
                           float outerOffset = 2.0F + lengthValue;
                           Setting<Color> colorOption = this.colorSetting;
                           Color markerColor = ColorUtils.multiplyAlpha(colorOption.getValue(), opacity);
                           VertexConsumer buffer = GuiDrawUtils.getLineVertices();
                           PoseStack poseStackForPush = event.getPoseStack();
                           poseStackForPush.pushPose();
                           CrosshairModule crosshairForTranslation = crosshair;
                           if (crosshairForTranslation.isToggled()) {
                              PoseStack translationStackCapture = event.getPoseStack();
                              translationStackCapture.translate(-0.6, 0.0, 0.0);
                           }

                           PoseStack topLeftStackCapture = event.getPoseStack();
                           float topLeftOuterX = centerX - outerOffset;
                           float lineYCapture = centerY - outerOffset;
                           float lineXCapture = centerX - 2.0F;
                           float topLeftEndY = centerY - 2.0F;
                           drawMarkerLine(topLeftStackCapture, buffer, markerColor, topLeftOuterX, lineYCapture, lineXCapture, topLeftEndY);
                           PoseStack bottomLeftStackCapture = event.getPoseStack();
                           float bottomLeftOuterX = centerX - outerOffset;
                           lineYCapture = centerY + outerOffset;
                           lineXCapture = centerX - 2.0F;
                           float bottomLeftEndY = centerY + 2.0F;
                           drawMarkerLine(bottomLeftStackCapture, buffer, markerColor, bottomLeftOuterX, lineYCapture, lineXCapture, bottomLeftEndY);
                           PoseStack topRightStackCapture = event.getPoseStack();
                           float topRightInnerX = centerX + 2.0F;
                           lineYCapture = centerY - 2.0F;
                           lineXCapture = centerX + outerOffset;
                           float topRightEndY = centerY - outerOffset;
                           drawMarkerLine(topRightStackCapture, buffer, markerColor, topRightInnerX, lineYCapture, lineXCapture, topRightEndY);
                           PoseStack bottomRightStackCapture = event.getPoseStack();
                           float bottomRightInnerX = centerX + 2.0F;
                           lineYCapture = centerY + 2.0F;
                           lineXCapture = centerX + outerOffset;
                           float bottomRightEndY = centerY + outerOffset;
                           drawMarkerLine(bottomRightStackCapture, buffer, markerColor, bottomRightInnerX, lineYCapture, lineXCapture, bottomRightEndY);
                           PoseStack poseStackForPop = event.getPoseStack();
                           poseStackForPop.popPose();
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void drawMarkerLine(PoseStack poseStack, VertexConsumer buffer, Color color, float startX, float startY, float endX, float endY) {
      VertexGeometry.appendLine(buffer, poseStack.last(), startX, startY, 0.0F, endX, endY, 0.0F, 1.0F, color.hashCode());
   }

   @EventHandler(priority = -100)
   private void onConnectionSendPacketEvent(PacketEvent.Send event) {
      if (!this.isWorldUnavailable()) {
         Entity attackedEntity = event.getPacket() instanceof ServerboundAttackPacket attack ? InteractPacketUtils.getEntity(attack)
            : event.getPacket() instanceof ServerboundInteractPacket interact && InteractPacketUtils.getAction(interact) == InteractPacketUtils.Action.ATTACK ? InteractPacketUtils.getEntity(interact)
            : null;
         if (attackedEntity != null) {
            {
               Entity target = attackedEntity;
               long elapsedMillis = System.currentTimeMillis() - this.lastHitTime;
               if (elapsedMillis > 100L) {
                  boolean simulatedPlayer = target instanceof FakeplayerCommand.SimulatedPlayer;
                  if (!event.isCancelled() || simulatedPlayer) {
                     if (target != null) {
                        if (target.level() != null && target != ClientAccess.argusclientMinecraft.player) {
                           Level targetLevel = target.level();
                           if (targetLevel.isClientSide()) {
                              if (this.isEligibleTarget(target)) {
                                 this.lastHitTime = System.currentTimeMillis();
                                 this.soundPending = true;
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public static void bindCrosshair(ModuleRegistry registry) {
      crosshair = (CrosshairModule)registry.getModule(CrosshairModule.class);
   }

   public HitmarkerModule() {
      super("Hitmarker", "Marks your screen when you hit entities.", ModuleCategory.RENDER);
      Predicate<Object> lengthVisibility = ignored -> this.drawSetting.isExpanded();
      this.lengthSetting = this.add(new NumberSetting<>("Length", 3.0F, 0.1F, 10.0F, lengthVisibility));
      Predicate<Object> timeVisibility = ignored -> this.drawSetting.isExpanded();
      this.timeSetting = this.add(new NumberSetting<>("Time", 1.0F, 0.5F, 3.0F, timeVisibility).withUnit("s"));
      Predicate<Object> fadeTimeVisibility = ignored -> this.drawSetting.isExpanded();
      this.fadeTimeSetting = this.add(new NumberSetting<>("FadeTime", 0.5F, 0.5F, 3.0F, fadeTimeVisibility).withUnit("s"));
      Predicate<Object> colorVisibility = ignored -> this.drawSetting.isExpanded();
      this.colorSetting = this.add(new ColorSetting("Color", new Color(-1581409793, true), colorVisibility));
      this.soundSetting = this.add(new BooleanSetting("Sound", false).markVisibilityDependency());
      this.soundTypeSetting = this.add(new IdentifierSetting("Sound", BuiltinSound.NEVERLOSE.getIdentifier()).dependsOn(this.soundSetting));
      Predicate<Object> volumeVisibility = ignored -> this.soundSetting.isExpanded();
      this.volumeSetting = this.add(new NumberSetting<>("Volume", 0.5F, 0.0F, 1.0F, volumeVisibility));
      this.targetsSetting = this.add(new BooleanSetting("Targets", false).markAsGroup().markVisibilityDependency());
      Predicate<Object> animalsVisibility = ignored -> this.targetsSetting.isExpanded();
      this.animalsSetting = this.add(new BooleanSetting("Animals", false, animalsVisibility));
      Predicate<Object> hostilesVisibility = ignored -> this.targetsSetting.isExpanded();
      this.hostilesSetting = this.add(new BooleanSetting("Hostiles", false, hostilesVisibility));
      Predicate<Object> playersVisibility = ignored -> this.targetsSetting.isExpanded();
      this.playersSetting = this.add(new BooleanSetting("Players", true, playersVisibility));
      Predicate<Object> crystalsVisibility = ignored -> this.targetsSetting.isExpanded();
      this.crystalsSetting = this.add(new BooleanSetting("Crystals", false, crystalsVisibility));
      this.lastHitTime = -1L;
      this.soundPending = false;
      this.setDrawn(false);
      this.soundTypeSetting.setConfigName("Type");
   }
}
