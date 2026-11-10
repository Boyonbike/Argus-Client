package dev.argus.mixin;

import com.mojang.blaze3d.platform.InputConstants.Key;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
   @Accessor("key")
   Key getKey();

   /** The key's own pressed state, bypassing {@code isDown()} overrides such as Sprint's forced press. */
   @Accessor("isDown")
   boolean argus$isDownRaw();
}
