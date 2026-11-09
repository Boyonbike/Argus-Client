package dev.argus.mixin;

import dev.argus.client.ClientContext;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.render.NoRenderModule;
import dev.argus.client.render.RenderUtils;
import java.util.function.Function;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Material.class)
public class MaterialMixin {
   @Shadow
   @Final
   private Identifier atlasLocation;
   private static NoRenderModule noRenderModule = ClientContext.getModule(NoRenderModule.class);

   @Inject(method = "renderType", at = @At("HEAD"), cancellable = true)
   private void applyTranslucentHandMaterial(Function renderTypeFactory, CallbackInfoReturnable<RenderType> callback) {
      if (noRenderModule.getHandOpacity() != 1.0F && RenderUtils.getRenderColor()[3] != 255) {
         callback.setReturnValue(RenderTypes.entityTranslucent(this.atlasLocation));
         callback.cancel();
      }
   }

   @Unique
   private static void argusclient$bindMaterialModules(ModuleRegistry moduleRegistry) {
      noRenderModule = (NoRenderModule)moduleRegistry.getModule(NoRenderModule.class);
   }
}
