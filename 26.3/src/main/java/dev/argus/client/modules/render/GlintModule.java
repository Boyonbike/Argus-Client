package dev.argus.client.modules.render;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.EventStage;
import dev.argus.client.events.GlintTextureTransformEvent;
import dev.argus.client.events.RenderTypeDrawEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import java.awt.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;

public class GlintModule extends Module {
   private static final Vector4f GLINT_COLOR_VECTOR = new Vector4f();
   private static final Identifier SHINE_TEXTURE = Identifier.fromNamespaceAndPath("argusclient-mount", "textures/shine.png");
   public final Setting<Color> colorSetting = this.add(new ColorSetting("Color", new Color(-1, true)));
   private final Setting<Integer> spreadSetting = this.add(new NumberSetting<>("Spread", 1, 1, 5));

   @EventHandler
   private void onGlintTextureTransform(GlintTextureTransformEvent event) {
      if (!ChamsModule.renderingGlintBatch) {
         int spreadChoice = this.spreadSetting.getValue();
         float scale = 6.25F * getSpreadMultiplier(spreadChoice);
         event.setScale(scale);
      }
   }

   @EventHandler(priority = 100)
   private void onRenderTypeDrawEvent(RenderTypeDrawEvent event) {
      if (!ChamsModule.renderingGlintBatch && EventStage.Pre == event.getStage()) {
         TextureManager textureManager = Minecraft.getInstance().getTextureManager();
         Identifier textureId = SHINE_TEXTURE;
         AbstractTexture texture = textureManager.getTexture(textureId);
         GpuTextureView textureView = texture.getTextureView();
         event.setOverride(textureView);
      }
   }

   public static float getSpreadMultiplier(int spread) {
      switch (spread) {
         case 2:
            return 0.24F;
         case 3:
            return 0.32F;
         case 4:
            return 0.42F;
         case 5:
            return 0.5F;
         default:
            return 0.16F;
      }
   }

   public Vector4f getGlintColor() {
      Color selectedColor = this.colorSetting.getValue();
      Vector4f colorVector = GLINT_COLOR_VECTOR;
      float red = selectedColor.getRed();
      red /= 255.0F;
      float green = selectedColor.getGreen();
      green /= 255.0F;
      float blue = selectedColor.getBlue();
      blue /= 255.0F;
      colorVector.set(red, green, blue, 1.0F);
      return GLINT_COLOR_VECTOR;
   }

   public GlintModule() {
      super("Glint", "Changes your enchantment glint's color.", ModuleCategory.RENDER);
   }
}
