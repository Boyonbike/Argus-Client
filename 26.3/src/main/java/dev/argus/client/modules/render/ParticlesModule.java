package dev.argus.client.modules.render;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.ParticleEngineAddEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.client.ColorsModule;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.render.ParticleInitialAlpha;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import java.awt.Color;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.particle.AshParticle;
import net.minecraft.client.particle.BaseAshSmokeParticle;
import net.minecraft.client.particle.CritParticle;
import net.minecraft.client.particle.PortalParticle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.TotemParticle;
import net.minecraft.client.particle.WhiteAshParticle;
import net.minecraft.client.particle.FireworkParticles.SparkParticle;
import net.minecraft.client.particle.FireworkParticles.Starter;
import net.minecraft.client.player.LocalPlayer;

public class ParticlesModule extends Module {
   private final Random random = new Random();
   private static ColorsModule colors = ClientContext.getModule(ColorsModule.class);
   public final Setting<Boolean> totemsSetting = this.add(new BooleanSetting("Totems", false).markVisibilityDependency());
   public final Setting<Float> totemsScaleSetting = this.add(new NumberSetting<>("TotemsScale", 1.0F, 0.0F, 2.0F).dependsOn(this.totemsSetting));
   public final Setting<Float> totemsVelocitySetting = this.add(new NumberSetting<>("TotemsVelocity", 1.0F, 0.0F, 5.0F).dependsOn(this.totemsSetting));
   public final Setting<Color> totemsColor1Setting = this.add(new ColorSetting("TotemsColor1", new Color(-4351489, true)).dependsOn(this.totemsSetting));
   public final Setting<Color> totemsColor2Setting = this.add(new ColorSetting("TotemsColor2", new Color(-9286761, true)).dependsOn(this.totemsSetting));
   public final Setting<Boolean> ignoreSelfSetting = this.add(new BooleanSetting("IgnoreSelf", false).dependsOn(this.totemsSetting));
   public final Setting<Boolean> rocketsSetting = this.add(new BooleanSetting("Rockets", false).markVisibilityDependency());
   public final Setting<Float> rocketScaleSetting = this.add(new NumberSetting<>("RocketScale", 1.0F, 0.0F, 2.0F).dependsOn(this.rocketsSetting));
   public final Setting<Color> rocketColorSetting = this.add(new ColorSetting("RocketColor", new Color(-4351489, true)).dependsOn(this.rocketsSetting));
   public final Setting<Boolean> damageSetting = this.add(new BooleanSetting("Damage", false).markVisibilityDependency());
   public final Setting<Float> damageScaleSetting = this.add(new NumberSetting<>("DamageScale", 1.0F, 0.0F, 2.0F).dependsOn(this.damageSetting));
   public final Setting<Float> damageVelocitySetting = this.add(new NumberSetting<>("DamageVelocity", 1.0F, 0.0F, 5.0F).dependsOn(this.damageSetting));
   public final Setting<Color> damageColorSetting = this.add(new ColorSetting("DamageColor", new Color(-4351489, true)).dependsOn(this.damageSetting));
   public final Setting<Boolean> portalSetting = this.add(new BooleanSetting("Portal", false).markVisibilityDependency());
   public final Setting<Float> portalScaleSetting = this.add(new NumberSetting<>("PortalScale", 1.0F, 0.0F, 2.0F).dependsOn(this.portalSetting));
   public final Setting<Color> portalColorSetting = this.add(new ColorSetting("PortalColor", new Color(-4351489, true)).dependsOn(this.portalSetting));
   public final Setting<Boolean> dustSetting = this.add(new BooleanSetting("Dust", false).markVisibilityDependency());
   public final Setting<Color> fromSetting = this.add(new ColorSetting("From", new Color(-4351489, true)).dependsOn(this.dustSetting));
   public final Setting<Color> toSetting = this.add(new ColorSetting("To", new Color(-4351489, true)).dependsOn(this.dustSetting));

   private void setParticleColor(SingleQuadParticle particle, int argb) {
      float alpha = (argb >>> 24 & 0xFF) / 255.0F;
      float red = (argb >>> 16 & 0xFF) / 255.0F;
      float green = (argb >>> 8 & 0xFF) / 255.0F;
      float blue = (argb & 0xFF) / 255.0F;
      particle.setColor(red, green, blue);
      ((ParticleInitialAlpha)particle).argusclient$setInitialAlpha(alpha);
   }

   @EventHandler
   private void onParticleEngineAddEvent(ParticleEngineAddEvent event) {
      if (event.getParticle() instanceof TotemParticle totem) {
         if (this.ignoreSelfSetting.getValue()) {
            LocalPlayer player = ClientAccess.argusclientMinecraft.player;
            if (player.getBoundingBox().intersects(totem.getBoundingBox())) {
               event.cancel();
            }
         }

         if (!this.totemsSetting.getValue()) {
            return;
         }

         Setting<Color> colorSetting = this.random.nextInt(4) == 0 ? this.totemsColor1Setting : this.totemsColor2Setting;
         this.setParticleColor(totem, this.getParticleColor(colorSetting));
         totem.scale(this.totemsScaleSetting.getValue());
         totem.setPower(this.totemsVelocitySetting.getValue());
      }

      if (this.rocketsSetting.getValue()) {
         if (event.getParticle() instanceof Starter starter) {
            starter.scale(this.rocketScaleSetting.getValue());
         }

         if (event.getParticle() instanceof SparkParticle spark) {
            this.setParticleColor(spark, this.getParticleColor(this.rocketColorSetting));
            spark.scale(this.rocketScaleSetting.getValue());
         }
      }

      if (this.damageSetting.getValue() && event.getParticle() instanceof CritParticle damageParticle) {
         this.setParticleColor(damageParticle, this.damageColorSetting.getValue().getRGB());
         damageParticle.scale(this.damageScaleSetting.getValue());
         damageParticle.setPower(this.damageVelocitySetting.getValue());
      }

      if (this.portalSetting.getValue() && event.getParticle() instanceof PortalParticle portalParticle) {
         this.setParticleColor(portalParticle, this.portalColorSetting.getValue().getRGB());
         portalParticle.scale(this.portalScaleSetting.getValue());
      }

      if (this.dustSetting.getValue() && event.getParticle() instanceof BaseAshSmokeParticle dustParticle) {
         if (dustParticle instanceof WhiteAshParticle) {
            Color whiteAshColor = this.toSetting.getValue().brighter();
            this.setParticleColor(dustParticle, whiteAshColor.getRGB());
         }

         if (dustParticle instanceof AshParticle) {
            Color from = this.fromSetting.getValue();
            Color to = this.toSetting.getValue();
            Color blended = ColorUtils.blend(from, to, ThreadLocalRandom.current().nextFloat());
            this.setParticleColor(dustParticle, blended.getRGB());
         }
      }
   }

   private int getParticleColor(Setting<Color> setting) {
      Color color = setting.getValue();
      ColorSetting colorSetting = (ColorSetting)setting;
      if (!colorSetting.isRainbow()) {
         return color.getRGB();
      }

      float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
      int period = (int)(360.0F * colors.getRainbowPeriod());
      period = Math.max(period, 1);
      int offset = this.random.nextInt(period);
      Color rainbow = ColorUtils.rainbow(period, offset, hsb[1], hsb[2], color.getAlpha());
      return rainbow.getRGB();
   }

   public static void bindColorsModule(ModuleRegistry registry) {
      colors = (ColorsModule)registry.getModule(ColorsModule.class);
   }

   public ParticlesModule() {
      super("Particles", "Allows you to adjust certain particles.", ModuleCategory.RENDER);
      this.setDrawn(false);
   }
}
