package dev.argus.client.render;

import dev.argus.client.utils.chat.LegacyFormatting;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import dev.argus.client.ClientContext;
import dev.argus.client.modules.client.FontsModule;
import dev.argus.client.settings.options.FontsStyleChoice;
import it.unimi.dsi.fastutil.chars.Char2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.chars.CharArraySet;
import java.awt.Font;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.FormattedCharSequence;

public final class AtlasTextRenderer {
   private static final float ATLAS_SCALE = 0.25F;
   private static final Map<Character, ChatFormatting> FORMATTING_CODES = new Char2ObjectOpenHashMap();
   private static final FontsModule FONTS = ClientContext.getModule(FontsModule.class);
   protected static final MeshBuffer glyphMesh = MeshBuffer.create();
   private final AtlasStringSplitter splitter = new AtlasStringSplitter(this);
   private final FontAtlas atlas;
   private float cursorX;
   private float cursorY;

   public AtlasTextRenderer(FontAtlas atlas) {
      this.atlas = atlas;
   }

   public void flush() {
      if (glyphMesh.isBuilding()) {
         boolean antiAlias = FONTS.antiAliasSetting.getValue();
         MeshDrawContext.setRenderType(ClientRenderTypes.font(FontAtlas.ATLAS_TEXTURE, antiAlias));
         MeshBuffer.draw(glyphMesh);
      }

   }

   public AtlasStringSplitter getSplitter() {
      return this.splitter;
   }

   public int drawCentered(PoseStack poses, String text, double x, double y, int argb) {
      return this.draw(poses, text, (float)x - this.getWidth(text) / 2.0F, (float)y, argb, true);
   }

   public int getHeight() {
      return (int)(this.atlas.getHeight() * 0.25F);
   }

   public int draw(PoseStack poses, String text, float x, float y, int argb) {
      return this.draw(poses, text, x, y, argb, true);
   }

   public int draw(PoseStack poses, String text, double x, double y, int argb) {
      return this.draw(poses, text, (float)x, (float)y, argb, true);
   }

   public int draw(PoseStack poses, FormattedCharSequence sequence, float x, float y, int argb, boolean shadow) {
      int endX;
      if (shadow) {
         endX = new FormattedTextRenderer(poses, argb, 0.25F, this.atlas)
            .draw(sequence, x + FontRenderer.getShadowOffset(), y + FontRenderer.getShadowOffset());
         endX = Math.max(endX, new FormattedTextRenderer(poses, argb, 1.0F, this.atlas).draw(sequence, x, y));
      } else {
         endX = new FormattedTextRenderer(poses, argb, 1.0F, this.atlas).draw(sequence, x, y);
      }

      return endX;
   }

   public int draw(PoseStack poses, String text, float x, float y, int argb, boolean shadow) {
      if (!shadow) {
         return this.drawPass(poses, text, x, y, argb, false);
      }

      int shadowEnd = this.drawPass(poses, text, x + FontRenderer.getShadowOffset(), y + FontRenderer.getShadowOffset(), argb, true);
      return Math.max(shadowEnd, this.drawPass(poses, text, x, y, argb, false));
   }

   public static AtlasTextRenderer create(String fontName, int fontSize, FontsStyleChoice style, Set<FontLanguage> languages) {
      CharArraySet characters = new CharArraySet(estimateCharacterCount(languages));

      for (FontLanguage language : languages) {
         language.getRange().addTo(characters);
      }
      int awtStyle = switch (style) {
         case PLAIN -> 0;
         case BOLD -> 1;
         case ITALIC -> 2;
         case BOLDITALIC -> 3;
      };
      FontAtlas atlas = new FontAtlas(new Font(fontName, awtStyle, fontSize));
      atlas.buildAtlas(characters);
      atlas.uploadTexture();
      return new AtlasTextRenderer(atlas);
   }

   private void renderString(PoseStack poses, String text, int baseColor) {
      int color = baseColor;
      poses.pushPose();

      try {
         poses.translate(FontRenderer.getTranslate(), FontRenderer.getShift(), 0.0F);
         poses.scale(0.25F, 0.25F, 1.0F);
         if (!glyphMesh.isBuilding()) {
            glyphMesh.begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
         }

         boolean formattingCode = false;

         for (char character : text.toCharArray()) {
            if (formattingCode) {
               formattingCode = false;
               ChatFormatting format = FORMATTING_CODES.get(Character.toLowerCase(character));
               if (format == null || format == ChatFormatting.RESET) {
                  color = baseColor;
               } else if (LegacyFormatting.rgb(format) != null) {
                  color = LegacyFormatting.rgb(format);
               }
            } else if (character == 167) {
               formattingCode = true;
            } else {
               this.cursorX = this.cursorX + this.atlas.drawGlyph(poses, glyphMesh, character, this.cursorX, this.cursorY, color);
            }
         }
      } finally {
         poses.popPose();
      }
   }

   public float getCharacterWidth(char character) {
      return this.atlas.getGlyphWidth(character) * 0.25F;
   }

   private int drawPass(PoseStack poses, String text, float x, float y, int argb, boolean shadowPass) {
      if (text == null) {
         return 0;
      }

      if (argb >>> 24 == 0) {
         argb |= -16777216;
      }

      if (shadowPass) {
         int alpha = argb & 0xFF000000;
         argb = alpha | (argb & 16579836) >>> 2;
      }

      this.cursorX = x / 0.25F;
      this.cursorY = y / 0.25F;
      this.renderString(poses, text, argb);
      return (int)(this.cursorX * 0.25F);
   }

   public int drawWithoutShadow(PoseStack poses, String text, float x, float y, int argb) {
      return this.draw(poses, text, x, y, argb, false);
   }

   public int drawWithoutShadow(PoseStack poses, String text, double x, double y, int argb) {
      return this.draw(poses, text, (float)x, (float)y, argb, false);
   }

   private static int estimateCharacterCount(Collection<FontLanguage> languages) {
      int count = 512;
      if (languages.contains(FontLanguage.CHINESE)) {
         count += 2500;
      }

      if (languages.contains(FontLanguage.JAPANESE)) {
         count += 3000;
      }

      return count;
   }

   public int getWidth(String text) {
      if (text == null) {
         return 0;
      }

      float width = 0.0F;
      boolean formattingCode = false;

      for (char character : text.toCharArray()) {
         if (formattingCode) {
            formattingCode = false;
         } else if (character == 167) {
            formattingCode = true;
         } else {
            width += this.atlas.getGlyphWidth(character);
         }
      }

      return (int)(width * 0.25F + 1.0F);
   }

   public int drawCenteredWithoutShadow(PoseStack poses, String text, double x, double y, int argb) {
      return this.draw(poses, text, (float)x - this.getWidth(text) / 2.0F, (float)y, argb, false);
   }

   static {
      for (ChatFormatting format : ChatFormatting.values()) {
         FORMATTING_CODES.put(LegacyFormatting.code(format), format);
      }
   }
}
