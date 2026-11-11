package dev.argus.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
   @Accessor("resourcePool")
   CrossFrameResourcePool getPool();

   @Accessor("globalSettingsUniform")
   GlobalSettingsUniform getGlobalSettings();

   @Mutable
   @Accessor("mainRenderTarget")
   void argusclient$setFramebuffer(RenderTarget target);

   @Invoker("bobView")
   void argusclient$bobView(net.minecraft.client.renderer.state.level.CameraRenderState var1, PoseStack var2);

   @Invoker("bobHurt")
   void argusclient$bobViewHurt(net.minecraft.client.renderer.state.level.CameraRenderState var1, PoseStack var2);
}
