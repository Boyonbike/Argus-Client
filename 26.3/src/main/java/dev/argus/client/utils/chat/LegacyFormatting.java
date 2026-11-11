package dev.argus.client.utils.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextColor;

/** Compatibility helpers for the formatting accessors removed from ChatFormatting in 26.2. */
public final class LegacyFormatting {
   private LegacyFormatting() {
   }

   /** The RGB colour of a colour format, or {@code null} for a style-only format. */
   public static Integer rgb(ChatFormatting format) {
      if (format == null) {
         return null;
      }
      TextColor color = TextColor.fromLegacyFormat(format);
      return color == null ? null : color.getValue();
   }

   /** The legacy formatting character for a format. */
   public static char code(ChatFormatting format) {
      for (int i = 0; i < 128; i++) {
         char candidate = (char)i;
         if (ChatFormatting.getByCode(candidate) == format) {
            return candidate;
         }
      }
      return '\0';
   }
}
