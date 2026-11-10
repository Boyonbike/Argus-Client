package dev.argus.mixin;

import dev.argus.client.render.ShulkerIconRenderState;
import net.minecraft.client.gui.render.state.GuiItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(GuiItemRenderState.class)
public abstract class ShulkerIconRenderStateMixin implements ShulkerIconRenderState {
   @Unique
   private Integer argus$colour;

   @Override
   public Integer argus$iconColour() {
      return this.argus$colour;
   }

   @Override
   public void argus$iconColour(Integer colour) {
      this.argus$colour = colour;
   }
}
