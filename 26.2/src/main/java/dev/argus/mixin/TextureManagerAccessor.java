package dev.argus.mixin;

import java.util.Map;
import net.minecraft.client.renderer.texture.TextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TextureManager.class)
public interface TextureManagerAccessor {
   @Accessor("byPath")
   Map<net.minecraft.resources.Identifier, net.minecraft.client.renderer.texture.AbstractTexture> getAllTextures();
}
