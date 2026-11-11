package dev.argus.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.modules.client.FontsModule;
import dev.argus.client.modules.player.NameProtectModule;
import java.awt.Color;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;

public final class FontRenderer implements ClientAccess {
   private static final NameProtectModule nameProtect = ClientContext.getModule(NameProtectModule.class);
   public static final FontRenderer INSTANCE = new FontRenderer();
   private AtlasTextRenderer renderer;

   public AtlasTextRenderer createRenderer() {
      FontsModule fonts = FontsModule.INSTANCE;
      return AtlasTextRenderer.create(fonts.fontSetting.getValue(), fonts.sizeSetting.getValue() * 2, fonts.styleSetting.getValue(), fonts.getLanguages());
   }

   public int getHeight() {
      if (FontsModule.INSTANCE.isToggled() && this.renderer != null) {
         return this.getRenderer().getHeight() - 1;
      } else {
         return ClientAccess.argusclientMinecraft.font == null ? 9 : 9;
      }
   }

   public void flush() {
      this.getRenderer().flush();
   }

   public boolean isFontLoading() {
      return FontsModule.INSTANCE.hasReloadCooldown();
   }

   public AtlasTextRenderer getRenderer() {
      if (this.renderer == null) {
         this.renderer = this.createRenderer();
      }

      return this.renderer;
   }

   public static int getZOffset() {
      return 0;
   }

   public static int getTranslate() {
      return FontsModule.INSTANCE.translateSetting.getValue();
   }

   public static int getShift() {
      return FontsModule.INSTANCE.shiftSetting.getValue();
   }

   public static float getShadowOffset() {
      return FontsModule.INSTANCE.shadowSetting.getValue();
   }

   private FontRenderer() {
   }

   public float getWidth(String text) {
      if (nameProtect.isToggled() && !this.isWorldUnavailable()) {
         text = nameProtect.protectNames(text);
      }

      return FontsModule.INSTANCE.isToggled() && this.renderer != null ? this.getRenderer().getWidth(text) : ClientAccess.argusclientMinecraft.font.width(text);
   }

   public static String capitalize(Object value) {
      return Character.toUpperCase(value.toString().charAt(0)) + value.toString().toLowerCase().substring(1);
   }

   public static String sanitizeFileName(String name) {
      return name.replaceAll("[^a-zA-Z0-9.\\-]", "_");
   }

   public void drawWithoutShadow(PoseStack poses, String text, float x, float y, Color color) {
      this.drawWithoutShadow(poses, Component.nullToEmpty(text), x, y, color);
   }

   public void setRenderer(AtlasTextRenderer renderer) {
      if (renderer != null) {
         this.renderer = renderer;
      }
   }

   public static boolean containsCharacter(String text, int character) {
      for (int index = 0; index < text.length(); index++) {
         if (text.charAt(index) == character) {
            return true;
         }
      }

      return false;
   }

   public float getWidth(FormattedCharSequence sequence) {
      StringBuilder text = new StringBuilder();
      sequence.accept((index, style, codePoint) -> {
         text.appendCodePoint(codePoint);
         return true;
      });
      return this.getWidth(text.toString());
   }

   public void drawWithShadow(PoseStack poses, Component text, float x, float y, int argb) {
      this.drawWithShadow(poses, text, x, y, argb, DisplayMode.NORMAL);
   }

   public void drawWithShadow(PoseStack poses, Component text, float x, float y, int argb, DisplayMode displayMode) {
      if (ARGB.alpha(argb) != 0) {
         if (FontsModule.INSTANCE.isToggled()) {
            this.getRenderer().draw(poses, text.getVisualOrderText(), x, y, argb, true);
         } else {
            ClientAccess.argusclientMinecraft
               .font
               .drawInBatch(text, (int)x, (int)y, argb, true, poses.last().pose(), this.getRenderer().getBufferSource(), displayMode, 0, 15728880);
         }
      }
   }

   public void drawWithoutShadow(PoseStack poses, Component text, float x, float y, Color color) {
      this.drawWithoutShadow(poses, text, x, y, color.hashCode(), DisplayMode.NORMAL);
   }

   public void drawWithoutShadow(PoseStack poses, String text, float x, float y, int argb, DisplayMode displayMode) {
      this.drawWithoutShadow(poses, Component.nullToEmpty(text), x, y, argb, displayMode);
   }

   public void drawWithoutShadow(PoseStack poses, Component text, float x, float y, int argb, DisplayMode displayMode) {
      if (ARGB.alpha(argb) != 0) {
         if (FontsModule.INSTANCE.isToggled()) {
            this.getRenderer().draw(poses, text.getVisualOrderText(), x, y, argb, false);
         } else {
            ClientAccess.argusclientMinecraft
               .font
               .drawInBatch(text, (int)x, (int)y, argb, false, poses.last().pose(), this.getRenderer().getBufferSource(), displayMode, 0, 15728880);
         }
      }
   }

   public float getWidth(Component component) {
      return this.getWidth(component.getString());
   }

   public void drawWithShadow(PoseStack poses, Component text, float x, float y, float scale, Color color) {
      poses.pushPose();

      try {
         poses.scale(scale, scale, scale);
         this.drawWithShadow(poses, text, x / scale, y / scale, color);
      } finally {
         poses.popPose();
      }
   }

   public void drawWithShadow(PoseStack poses, String text, float x, float y, float scale, Color color) {
      this.drawWithShadow(poses, Component.nullToEmpty(text), x, y, scale, color);
   }

   public void drawWithShadow(PoseStack poses, Component text, float x, float y, Color color) {
      this.drawWithShadow(poses, text, x, y, color.hashCode());
   }

   public void drawWithShadow(PoseStack poses, Component text, float x, float y, Color color, DisplayMode displayMode) {
      this.drawWithShadow(poses, text, x, y, color.hashCode(), displayMode);
   }

   public void drawWithShadow(PoseStack poses, String text, float x, float y, Color color, DisplayMode displayMode) {
      this.drawWithShadow(poses, Component.nullToEmpty(text), x, y, color.hashCode(), displayMode);
   }

   public void drawWithShadow(PoseStack poses, String text, float x, float y, Color color) {
      this.drawWithShadow(poses, Component.nullToEmpty(text), x, y, color);
   }

   public static String capitalizeWords(Object value) {
      StringBuilder result = new StringBuilder();
      String text = value.toString();

      for (String word : text.split(" ")) {
         result.append(capitalize(word));
         result.append(" ");
      }

      result.deleteCharAt(Math.max(result.length() - 1, 0));
      return result.toString();
   }
}
