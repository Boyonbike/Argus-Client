package dev.argus.client.modules.render;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.HudRenderEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.render.GuiDrawUtils;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.SettingLabelCondition;
import dev.argus.client.utils.ClampedInterpolatedFloat;
import java.awt.Color;
import java.util.function.Predicate;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntryList;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;

public class CrosshairModule extends Module {
   private final Setting<CrosshairModule.CrosshairMode> modeSetting = this.add(new EnumSetting<>("Mode", CrosshairModule.CrosshairMode.CUSTOM));
   public final Setting<Boolean> thirdPersonSetting = this.add(new BooleanSetting("ThirdPerson", true));
   public final Setting<Integer> scaleSetting = this.add(
      new NumberSetting<>("Scale", 2, 1, 4, ignored -> this.modeSetting.getValue() == CrosshairModule.CrosshairMode.VANILLA)
   );
   private final Setting<Integer> shadowSetting = this.add(
      new NumberSetting<>("Shadow", 1, 0, 3, ignored -> this.modeSetting.getValue() == CrosshairModule.CrosshairMode.CUSTOM)
   );
   private final Setting<Float> amplitudeSetting = this.add(
      new NumberSetting<>("Amplitude", 2.0F, 0.0F, 8.0F, ignored -> this.modeSetting.getValue() == CrosshairModule.CrosshairMode.CUSTOM)
   );
   final Setting<Integer> lengthSetting = this.add(
      new NumberSetting<>("Length", 2, 0, 16, ignored -> this.modeSetting.getValue() == CrosshairModule.CrosshairMode.CUSTOM)
   );
   private final Setting<Integer> gapSetting;
   private final Setting<Integer> widthSetting;
   private final Setting<Boolean> elementsSetting;
   private final Setting<Boolean> topSetting;
   private final Setting<Boolean> centerSetting;
   private final Setting<Color> colorSetting;
   private final ClampedInterpolatedFloat movementAnimation;

   @EventHandler(priority = -200)
   private void onRenderHud(HudRenderEvent.Unconditional event) {
      if (this.modeSetting.getValue() == CrosshairModule.CrosshairMode.CUSTOM) {
         Identifier debugCrosshair = DebugScreenEntries.THREE_DIMENSIONAL_CROSSHAIR;
         DebugScreenEntryList debugEntries = ClientAccess.argusclientMinecraft.debugEntries;
         boolean debugCrosshairEnabled = debugEntries.isCurrentlyEnabled(debugCrosshair);
         if (!this.isWorldUnavailable() && !ClientAccess.argusclientMinecraft.gui.hud.isHidden && !debugCrosshairEnabled) {
            Camera camera = ClientAccess.argusclientMinecraft.gameRenderer.mainCamera();
            if (camera.isDetached()) {
               Setting<Boolean> thirdPerson = this.thirdPersonSetting;
               if (!thirdPerson.getValue()) {
                  return;
               }
            }

            Setting<Integer> gapSetting = this.gapSetting;
            float gap = gapSetting.getValue().intValue();
            Setting<Float> amplitudeCheck = this.amplitudeSetting;
            if (!amplitudeCheck.isAtMinimum()) {
               ClampedInterpolatedFloat targetAnimation = this.movementAnimation;
               Vec2 movement = ClientAccess.argusclientMinecraft.player.input.moveVector;
               boolean moving = movement.lengthSquared() > 0.0F;
               if (!moving) {
                  Input keys = ClientAccess.argusclientMinecraft.player.input.keyPresses;
                  moving = keys.jump();
               }

               targetAnimation.setTarget(moving);
               float movementAmplitude = this.amplitudeSetting.getValue();
               ClampedInterpolatedFloat currentAnimation = this.movementAnimation;
               gap += movementAmplitude * currentAnimation.updateAndGet();
            }

            float guiScale = ClientAccess.argusclientMinecraft.getWindow().getGuiScale();
            event.getPoseStack().pushPose();
            PoseStack scalePose = event.getPoseStack();
            float scaleX = 1.0F / guiScale;
            float scaleY = 1.0F / guiScale;
            float scaleZ = 1.0F / guiScale;
            scalePose.scale(scaleX, scaleY, scaleZ);
            Setting<Integer> shadowCheck = this.shadowSetting;
            if (!shadowCheck.isAtMinimum()) {
               PoseStack shadowPose = event.getPoseStack();
               shadowPose.pushPose();
               float shadowX = this.shadowSetting.getValue().intValue();
               float shadowY = this.shadowSetting.getValue().intValue();
               shadowPose.translate(shadowX, shadowY, 0.0F);
               PoseStack shadowDrawPose = event.getPoseStack();
               float shadowLength = this.lengthSetting.getValue().intValue();
               Setting<Integer> shadowWidthSetting = this.widthSetting;
               Integer shadowWidthValue = shadowWidthSetting.getValue();
               float shadowWidth = shadowWidthValue.intValue();
               float shadowHalfWidth = shadowWidth / 2.0F;
               Setting<Color> shadowColorSetting = this.colorSetting;
               Color shadowBaseColor = shadowColorSetting.getValue();
               Color shadowColor = ColorUtils.darken(shadowBaseColor, 0.25F);
               this.drawCrosshair(shadowDrawPose, shadowLength, shadowHalfWidth, gap, shadowColor);
               shadowPose.popPose();
            }

            PoseStack crosshairPose = event.getPoseStack();
            Setting<Integer> lengthSetting = this.lengthSetting;
            Integer lengthValue = lengthSetting.getValue();
            float length = lengthValue.intValue();
            Setting<Integer> widthSetting = this.widthSetting;
            Integer widthValue = widthSetting.getValue();
            float width = widthValue.intValue();
            float halfWidth = width / 2.0F;
            Setting<Color> colorSetting = this.colorSetting;
            Color color = colorSetting.getValue();
            this.drawCrosshair(crosshairPose, length, halfWidth, gap, color);
            PoseStack popPose = event.getPoseStack();
            popPose.popPose();
         }
      }
   }

   private void drawCrosshair(PoseStack pose, float length, float halfWidth, float gap, Color color) {
      int screenWidth = ClientAccess.argusclientMinecraft.getWindow().getScreenWidth();
      int centerX = screenWidth / 2;
      int screenHeight = ClientAccess.argusclientMinecraft.getWindow().getScreenHeight();
      int centerY = screenHeight / 2;
      double fractionalWidth = halfWidth;
      if (Math.ceil(fractionalWidth) - halfWidth != 0.0) {
         gap += 0.5F;
      } else {
         centerX--;
         centerY++;
      }

      Setting<Boolean> center = this.centerSetting;
      if (center.getValue()) {
         GuiDrawUtils.fillRect(pose, centerX - halfWidth, centerY - halfWidth, centerX + halfWidth, centerY + halfWidth, color);
      }

      if (length != 0.0F) {
         Setting<Boolean> top = this.topSetting;
         if (top.getValue()) {
            GuiDrawUtils.fillRect(pose, centerX - halfWidth, centerY - gap - length, centerX + halfWidth, centerY - gap, color);
         }

         GuiDrawUtils.fillRect(pose, centerX + gap, centerY - halfWidth, centerX + gap + length, centerY + halfWidth, color);
         GuiDrawUtils.fillRect(pose, centerX - gap - length, centerY - halfWidth, centerX - gap, centerY + halfWidth, color);
         GuiDrawUtils.fillRect(pose, centerX - halfWidth, centerY + gap, centerX + halfWidth, centerY + gap + length, color);
      }
   }

   public CrosshairModule() {
      super("Crosshair", "Allows you to customize your crosshair.", ModuleCategory.RENDER);
      Predicate<Object> gapVisibility = ignored -> !this.lengthSetting.isAtMinimum();
      this.gapSetting = this.add(new NumberSetting<>("Gap", 2, 0, 16, gapVisibility));
      this.widthSetting = this.add(new NumberSetting<>("Width", 1, 1, 5, ignored -> this.modeSetting.getValue() == CrosshairModule.CrosshairMode.CUSTOM));
      this.elementsSetting = this.add(
         new BooleanSetting("Elements", false, ignored -> this.modeSetting.getValue() == CrosshairModule.CrosshairMode.CUSTOM)
            .markAsGroup()
            .markVisibilityDependency()
      );
      this.topSetting = this.add(new BooleanSetting("Top", true).dependsOn(this.elementsSetting));
      this.centerSetting = this.add(new BooleanSetting("Center", true).dependsOn(this.elementsSetting));
      this.colorSetting = this.add(new ColorSetting("Color", new Color(-2828, true)).dependsOn(this.elementsSetting));
      this.movementAnimation = new ClampedInterpolatedFloat(2.0F, true);
      Setting<Float> amplitudeLabel = this.amplitudeSetting;
      SettingLabelCondition minimumLabel = SettingLabelCondition.MIN;
      amplitudeLabel.setBoundaryLabel("None", minimumLabel);
      this.shadowSetting.setBoundaryLabel("None", SettingLabelCondition.MIN);
      this.lengthSetting.setBoundaryLabel("None", SettingLabelCondition.MIN);
      this.setDrawn(false);
   }

   @Override
   public void fromJson(JsonElement json) {
      JsonObject object = json.getAsJsonObject();
      JsonObject settings = object.getAsJsonObject("settings");
      if (settings != null && !settings.has("Mode") && settings.has("Vanilla")) {
         JsonObject migrated = object.deepCopy();
         migrated.getAsJsonObject("settings").addProperty("Mode", settings.get("Vanilla").getAsBoolean() ? "Vanilla" : "Custom");
         super.fromJson(migrated);
      } else {
         super.fromJson(json);
      }
   }

   public boolean shouldHideVanillaCrosshair() {
      return this.isToggled() && this.modeSetting.getValue() == CrosshairModule.CrosshairMode.CUSTOM;
   }

   public enum CrosshairMode implements NamedEntry {
      VANILLA("Vanilla"),
      CUSTOM("Custom");

      private final String name;

      CrosshairMode(String name) {
         this.name = name;
      }

      @Override
      public String getName() {
         return this.name;
      }
   }
}
