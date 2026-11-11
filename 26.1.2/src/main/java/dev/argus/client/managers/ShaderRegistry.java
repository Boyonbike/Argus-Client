package dev.argus.client.managers;

import dev.argus.client.ClientAccess;
import dev.argus.client.gui.components.ColorSubmitNodeStorage;
import dev.argus.client.render.BaseShaderPass;
import dev.argus.client.render.BloomMaskPipeline;
import dev.argus.client.render.BloomShaderPass;
import dev.argus.client.render.BlurMaskPipeline;
import dev.argus.client.render.ColorOutlineBufferSource;
import dev.argus.client.render.CustomShaderPass;
import dev.argus.client.render.DepthOfFieldPipeline;
import dev.argus.client.render.DiscardingBufferSource;
import dev.argus.client.render.ESPOutlineShaderPass;
import dev.argus.client.render.GlowyShaderPipeline;
import dev.argus.client.render.GradientShaderPass;
import dev.argus.client.render.NoOpOutlineBufferSource;
import dev.argus.client.render.RainbowShaderPass;
import dev.argus.client.render.ReloadableShaderPass;
import dev.argus.client.render.ShaderPass;
import dev.argus.client.render.SkyboxShaderPass;
import dev.argus.client.render.SolidShaderPass;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.resources.model.sprite.AtlasManager;

public final class ShaderRegistry implements ClientAccess {
   private static final ColorSubmitNodeStorage submitNodes = new ColorSubmitNodeStorage();
   private static FeatureRenderDispatcher featureDispatcher;
   private static ColorOutlineBufferSource bufferSource;
   private final Object2ObjectOpenHashMap<String, ShaderPass> passes = new Object2ObjectOpenHashMap();
   public boolean renderingIntoTarget;
   public boolean renderingHands;

   private static FeatureRenderDispatcher getFeatureDispatcher() {
      if (featureDispatcher == null) {
         featureDispatcher = new FeatureRenderDispatcher(
            submitNodes,
            readBlockRenderer(),
            new ColorOutlineBufferSource.SuppliedBufferSource(ShaderRegistry::getBufferSource),
            readAtlasManager(),
            NoOpOutlineBufferSource.INSTANCE,
            DiscardingBufferSource.INSTANCE,
            ClientAccess.argusclientMinecraft.font
         );
      }

      return featureDispatcher;
   }

   private static BlockRenderDispatcher readBlockRenderer() {
      return ClientAccess.argusclientMinecraft.getBlockRenderer();
   }

   private static AtlasManager readAtlasManager() {
      return ClientAccess.argusclientMinecraft.getAtlasManager();
   }

   public static ColorSubmitNodeStorage getSubmitNodes() {
      return submitNodes;
   }

   public static ColorOutlineBufferSource getBufferSource() {
      if (bufferSource == null) {
         bufferSource = new ColorOutlineBufferSource();
      }

      return bufferSource;
   }

   public ShaderRegistry() {
      this.register("outline", new ESPOutlineShaderPass());
      this.register("solid", new SolidShaderPass());
      this.register("rainbow", new RainbowShaderPass());
      this.register("gradient", new GradientShaderPass());
      this.register("bloom", new BloomShaderPass());
      this.register("custom", new CustomShaderPass());
      this.register("sky", new SkyboxShaderPass());
      this.register("dof", new DepthOfFieldPipeline());
      this.register("blur_mask", new BlurMaskPipeline());
      this.register("bloom_mask", new BloomMaskPipeline());
      this.register("glowy", new GlowyShaderPipeline());
   }

   public ShaderPass getPass(String id) {
      return (ShaderPass)this.passes.get(id);
   }

   public void reloadAll() {
      ObjectIterator var1 = this.passes.values().iterator();

      while (var1.hasNext()) {
         ShaderPass pass = (ShaderPass)var1.next();
         if (pass instanceof ReloadableShaderPass reloadable) {
            reloadable.reload((BaseShaderPass)pass);
         }
      }
   }

   private void register(String id, ShaderPass pass) {
      this.passes.put(id, pass);
   }

   public void render(String id, Runnable draw) {
      ShaderPass pass = this.getPass(id);
      pass.render(draw);
   }

   public static void finishFeatureRendering() {
      FeatureRenderDispatcher dispatcher = getFeatureDispatcher();
      dispatcher.renderAllFeatures();
      ColorSubmitNodeStorage nodes = getSubmitNodes();
      nodes.endFrame();
   }

   public static void setRenderColor(int color) {
      getBufferSource().setColorArgb(color);
      getSubmitNodes().setColorArgb(color);
   }
}
