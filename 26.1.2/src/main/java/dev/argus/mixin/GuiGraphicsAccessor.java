package dev.argus.mixin;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsAccessor {
   @Invoker("tooltip")
   void drawTooltipsHook(Font var1, List var2, int var3, int var4, ClientTooltipPositioner var5, @Nullable Identifier var6);

   @Accessor("hoveredTextStyle")
   void argusclient$hoverStyle(Style var1);

   @Accessor("clickableTextStyle")
   void argusclient$clickStyle(Style var1);

   @Accessor("mouseX")
   int argusclient$mouseX();

   @Accessor("mouseY")
   int argusclient$mouseY();
}
