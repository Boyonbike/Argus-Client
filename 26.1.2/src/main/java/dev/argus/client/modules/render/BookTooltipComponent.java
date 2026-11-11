package dev.argus.client.modules.render;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fStack;

/**
 * Shows the first page of a book on the vanilla book background.
 * Feature ported from Meteor Client's BetterTooltips book preview (GPL-3.0); see THIRD_PARTY_NOTICES.md.
 */
public record BookTooltipComponent(Component page) implements ClientTooltipComponent {
   private static final int WIDTH = 112;
   private static final int HEIGHT = 134;
   /** The 256px book texture drawn at 179px, so its 192px page lands at about 134px. */
   private static final int TEXTURE_SIZE = 179;
   private static final float TEXT_SCALE = 0.7F;
   private static final int LINE_HEIGHT = 8;
   private static final int MAX_LINES = 18;

   @Override
   public int getHeight(Font font) {
      return HEIGHT;
   }

   @Override
   public int getWidth(Font font) {
      return WIDTH;
   }

   @Override
   public void renderImage(Font font, int x, int y, int tooltipWidth, int tooltipHeight, GuiGraphics graphics) {
      graphics.blit(RenderPipelines.GUI_TEXTURED, BookViewScreen.BOOK_LOCATION, x - 10, y, 0.0F, 0.0F, 128, 128, TEXTURE_SIZE, TEXTURE_SIZE);
      Matrix3x2fStack poses = graphics.pose();
      poses.pushMatrix();

      try {
         poses.translate(x + 16, y + 12);
         poses.scale(TEXT_SCALE, TEXT_SCALE);
         List<FormattedCharSequence> lines = font.split(this.page, WIDTH);
         int count = Math.min(lines.size(), MAX_LINES);

         for (int index = 0; index < count; index++) {
            graphics.drawString(font, lines.get(index), 0, index * LINE_HEIGHT, 0xFF000000, false);
         }
      } finally {
         poses.popMatrix();
      }
   }
}
