package dev.argus.client.render;

import dev.argus.client.ClientAccess;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.gui.Font.GlyphVisitor;
import net.minecraft.client.gui.font.EmptyArea;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.TextRenderable.Styled;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.FormattedCharSequence;

/** Draws vanilla-font text by replaying its glyphs into Argus's immediate mesh renderer. */
public final class VanillaTextRenderer implements ClientAccess {
   private VanillaTextRenderer() {
   }

   public static void draw(
      PoseStack poses, FormattedCharSequence sequence, float x, float y, int color, boolean shadow, DisplayMode displayMode, int light
   ) {
      Font font = ClientAccess.argusclientMinecraft.font;
      if (font == null) {
         return;
      }
      Font.PreparedText prepared = font.prepareText(sequence, x, y, color, shadow, false, 0);
      Map<RenderType, MeshBuffer> buffers = new LinkedHashMap<>();
      prepared.visit(new GlyphVisitor() {
         @Override
         public void acceptGlyph(Styled styled) {
            emit(styled);
         }

         @Override
         public void acceptEffect(TextRenderable renderable) {
            emit(renderable);
         }

         @Override
         public void acceptRenderable(TextRenderable renderable) {
            emit(renderable);
         }

         @Override
         public void acceptEmptyArea(EmptyArea area) {
         }

         private void emit(TextRenderable renderable) {
            RenderType type = renderable.renderType(displayMode);
            MeshBuffer buffer = buffers.computeIfAbsent(type, key -> MeshBuffer.create().setFormat(key.primitiveTopology(), key.format()));
            renderable.render(poses.last().pose(), buffer, light, false);
         }
      });
      for (Map.Entry<RenderType, MeshBuffer> entry : buffers.entrySet()) {
         entry.getValue().draw(entry.getKey());
      }
   }
}
