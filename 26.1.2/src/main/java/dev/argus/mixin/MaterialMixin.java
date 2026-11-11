package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.render.NoRenderModule;
import dev.argus.client.render.RenderUtils;
import java.util.function.Function;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.sprite.Material;
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

   @ModifyReturnValue(method = "renderType", at = @At("RETURN"))
   private RenderType applyTranslucentHandMaterial(RenderType original, Function renderTypeFactory) {
      return noRenderModule.getHandOpacity() != 1.0F && RenderUtils.getRenderColor()[3] != 255
         ? RenderTypes.entityTranslucent(this.atlasLocation)
         : original;
   }

   @Unique
   private static void argusclient$bindMaterialModules(ModuleRegistry moduleRegistry) {
      noRenderModule = (NoRenderModule)moduleRegistry.getModule(NoRenderModule.class);
   }
}
