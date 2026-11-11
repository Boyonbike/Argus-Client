package dev.argus.client.modules.render;

import dev.argus.client.ClientAccess;
import dev.argus.client.gui.screens.ViewModelEditorScreen;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import org.joml.Vector3f;

public class ViewModelModule extends Module {
   private final Setting<Boolean> adjustSetting = this.add(new BooleanSetting("Adjust", false));
   public final Setting<Boolean> instantSwapSetting = this.add(new BooleanSetting("InstantSwap", false));
   public final Setting<Boolean> eatingSetting = this.add(new BooleanSetting("Eating", true));
   public final Setting<Boolean> lighterSetting = this.add(new BooleanSetting("Lighter", false));
   private final Setting<Boolean> mainHandSetting = this.add(new BooleanSetting("MainHand", true).markAsGroup().markVisibilityDependency());
   public final Setting<Boolean> armSetting = this.add(new BooleanSetting("Arm", false).dependsOn(this.mainHandSetting));
   public final Setting<Float> mainXSetting = this.add(new NumberSetting<>("MainX", 0.0F, -1.0F, 1.0F).dependsOn(this.mainHandSetting));
   public final Setting<Float> mainYSetting = this.add(new NumberSetting<>("MainY", 0.0F, -1.0F, 1.0F).dependsOn(this.mainHandSetting));
   public final Setting<Float> mainZSetting = this.add(new NumberSetting<>("MainZ", 0.0F, -1.0F, 1.0F).dependsOn(this.mainHandSetting));
   public final Setting<Float> mainScaleXSetting = this.add(new NumberSetting<>("MainScaleX", 1.0F, 0.0F, 2.0F).dependsOn(this.mainHandSetting));
   public final Setting<Float> mainScaleYSetting = this.add(new NumberSetting<>("MainScaleY", 1.0F, 0.0F, 2.0F).dependsOn(this.mainHandSetting));
   public final Setting<Float> mainScaleZSetting = this.add(new NumberSetting<>("MainScaleZ", 1.0F, 0.0F, 2.0F).dependsOn(this.mainHandSetting));
   public final Setting<Float> mainRotateXSetting = this.add(
      new NumberSetting<>("MainRotateX", 0.0F, 0.0F, 360.0F).withUnit("°").dependsOn(this.mainHandSetting)
   );
   public final Setting<Float> mainRotateYSetting = this.add(
      new NumberSetting<>("MainRotateY", 0.0F, 0.0F, 360.0F).withUnit("°").dependsOn(this.mainHandSetting)
   );
   public final Setting<Float> mainRotateZSetting = this.add(
      new NumberSetting<>("MainRotateZ", 0.0F, 0.0F, 360.0F).withUnit("°").dependsOn(this.mainHandSetting)
   );
   private final Setting<Boolean> offHandSetting = this.add(new BooleanSetting("OffHand", true).markAsGroup().markVisibilityDependency());
   public final Setting<Float> offXSetting = this.add(new NumberSetting<>("OffX", 0.0F, -1.0F, 1.0F).dependsOn(this.offHandSetting));
   public final Setting<Float> offYSetting = this.add(new NumberSetting<>("OffY", 0.0F, -1.0F, 1.0F).dependsOn(this.offHandSetting));
   public final Setting<Float> offZSetting = this.add(new NumberSetting<>("OffZ", 0.0F, -1.0F, 1.0F).dependsOn(this.offHandSetting));
   public final Setting<Float> offScaleXSetting = this.add(new NumberSetting<>("OffScaleX", 1.0F, 0.0F, 2.0F).dependsOn(this.offHandSetting));
   public final Setting<Float> offScaleYSetting = this.add(new NumberSetting<>("OffScaleY", 1.0F, 0.0F, 2.0F).dependsOn(this.offHandSetting));
   public final Setting<Float> offScaleZSetting = this.add(new NumberSetting<>("OffScaleZ", 1.0F, 0.0F, 2.0F).dependsOn(this.offHandSetting));
   public final Setting<Float> offRotateXSetting = this.add(new NumberSetting<>("OffRotateX", 0.0F, 0.0F, 360.0F).withUnit("°").dependsOn(this.offHandSetting));
   public final Setting<Float> offRotateYSetting = this.add(new NumberSetting<>("OffRotateY", 0.0F, 0.0F, 360.0F).withUnit("°").dependsOn(this.offHandSetting));
   public final Setting<Float> offRotateZSetting = this.add(new NumberSetting<>("OffRotateZ", 0.0F, 0.0F, 360.0F).withUnit("°").dependsOn(this.offHandSetting));
   public final Setting<Boolean> miscSetting = this.add(new BooleanSetting("Misc", false).markAsGroup().markVisibilityDependency());
   public final Setting<Float> eatMultiplierSetting = this.add(new NumberSetting<>("EatMultiplier", 1.0F, 0.0F, 1.0F).dependsOn(this.miscSetting));
   public final Setting<Boolean> noSwaySetting = this.add(new BooleanSetting("NoSway", false).dependsOn(this.miscSetting));
   public final Setting<Boolean> noTridentAnimSetting = this.add(new BooleanSetting("NoTridentAnim", false).dependsOn(this.miscSetting));
   public final Setting<Boolean> swingProgressSetting = this.add(
      new BooleanSetting("SwingProgress", false).markVisibilityDependency().dependsOn(this.miscSetting)
   );
   public final Setting<Boolean> staticSetting = this.add(new BooleanSetting("Static", true).dependsOn(this.miscSetting, this.swingProgressSetting));
   private final Setting<Boolean> mainHandSwingSetting = this.add(new BooleanSetting("MainHand", false).dependsOn(this.miscSetting, this.swingProgressSetting));
   public final Setting<Float> mainHandSwingProgressAmountSetting = this.add(
      new NumberSetting<>("SwingProgressAmount", 1.0F, 0.0F, 1.0F).dependsOn(this.miscSetting, this.swingProgressSetting, this.mainHandSwingSetting)
   );
   private final Setting<Boolean> offHandSwingSetting = this.add(new BooleanSetting("OffHand", false).dependsOn(this.miscSetting, this.swingProgressSetting));
   public final Setting<Float> offHandSwingProgressAmountSetting = this.add(
      new NumberSetting<>("SwingProgressAmount", 1.0F, 0.0F, 1.0F).dependsOn(this.miscSetting, this.swingProgressSetting, this.offHandSwingSetting)
   );
   public final Setting<Boolean> viewModelFovSetting = this.add(
      new BooleanSetting("ViewModelFov", false).markVisibilityDependency().dependsOn(this.miscSetting)
   );
   public final Setting<Integer> fovAmountSetting = this.add(
      new NumberSetting<>("FovAmount", 90, 70, 180).dependsOn(this.miscSetting, this.viewModelFovSetting)
   );
   private final Vector3f scaleVector = new Vector3f(1.0F);

   public Vector3f getScale(InteractionHand hand) {
      if (hand == InteractionHand.MAIN_HAND) {
         Vector3f mainScale = this.scaleVector;
         float mainX = this.mainScaleXSetting.getValue();
         float mainY = this.mainScaleYSetting.getValue();
         float mainZ = this.mainScaleZSetting.getValue();
         return mainScale.set(mainX, mainY, mainZ);
      } else {
         Vector3f offScale = this.scaleVector;
         float offX = this.offScaleXSetting.getValue();
         float offY = this.offScaleYSetting.getValue();
         float offZ = this.offScaleZSetting.getValue();
         return offScale.set(offX, offY, offZ);
      }
   }

   public float applySwingProgressOverride(InteractionHand hand, float originalProgress) {
      if (!this.isToggled() || !this.swingProgressSetting.getValue()) {
         return originalProgress;
      } else if (hand == InteractionHand.MAIN_HAND && this.mainHandSwingSetting.getValue()) {
         boolean mainStatic = this.staticSetting.getValue();
         float mainProgress = mainStatic ? 0.0F : originalProgress;
         float mainMinimum = this.mainHandSwingProgressAmountSetting.getValue();
         return Math.max(mainProgress, mainMinimum);
      } else if (hand == InteractionHand.OFF_HAND && this.offHandSwingSetting.getValue()) {
         boolean offStatic = this.staticSetting.getValue();
         float offProgress = offStatic ? 0.0F : originalProgress;
         float offMinimum = this.offHandSwingProgressAmountSetting.getValue();
         return Math.max(offProgress, offMinimum);
      } else {
         return originalProgress;
      }
   }

   public ViewModelModule() {
      super("ViewModel", "Transforms your 1st person view model.", ModuleCategory.RENDER);
      this.mainHandSwingSetting.setConfigName("SwingMainHand");
      this.offHandSwingSetting.setConfigName("SwingOffHand");
      this.offHandSwingProgressAmountSetting.setConfigName("SwingProgressAmountOffHand");
      Setting<Boolean> callbackSetting = this.adjustSetting;
      Runnable callback = () -> {
         Boolean openAdjust = this.adjustSetting.getValue();
         if (openAdjust) {
            Minecraft minecraft = ClientAccess.argusclientMinecraft;
            ViewModelEditorScreen screen = new ViewModelEditorScreen(this);
            minecraft.setScreen(screen);
            this.adjustSetting.setValue(false);
         }
      };
      callbackSetting.setChangeCallback(callback);
   }
}
