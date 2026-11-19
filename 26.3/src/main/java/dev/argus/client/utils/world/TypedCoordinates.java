package dev.argus.client.utils.world;

import java.util.regex.Pattern;

/** Coordinates as a player types them: any whole or decimal number, with or without a leading + or -. */
public final class TypedCoordinates {
   /** The farthest a coordinate can sensibly be from 0; the world border stops at 29,999,984. */
   public static final double LIMIT = 30_000_000.0;
   private static final Pattern NUMBER = Pattern.compile("[+-]?(\\d+\\.?\\d*|\\.\\d+)");
   /** What a coordinate box accepts while it is being typed: a sign on its own, or a number in progress. */
   private static final Pattern PARTIAL = Pattern.compile("[+-]?\\d*\\.?\\d*");

   private TypedCoordinates() {
   }

   /** True when {@code text} can still become a coordinate, so the box lets it through. */
   public static boolean typing(String text) {
      return text != null && PARTIAL.matcher(text.trim()).matches();
   }

   /** The number typed, or null when {@code text} isn't one (blank, a lone sign, letters, or past {@link #LIMIT}). */
   public static Double parse(String text) {
      if (text == null) {
         return null;
      }

      String trimmed = text.trim().replace(",", "");
      if (!NUMBER.matcher(trimmed).matches()) {
         return null;
      }

      double value = Double.parseDouble(trimmed.startsWith("+") ? trimmed.substring(1) : trimmed);
      return Math.abs(value) > LIMIT ? null : value;
   }
}
