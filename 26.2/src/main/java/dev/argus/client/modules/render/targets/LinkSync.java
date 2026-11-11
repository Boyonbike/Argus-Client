package dev.argus.client.modules.render.targets;

import java.util.List;
import java.util.Objects;

/**
 * Keeps pairs of values identical once linked. Each pair remembers the value it last agreed on, so a sync can tell
 * which side the player changed and copy only that side across: no ping-pong, and no reliance on change callbacks
 * (set settings are edited in place and never fire one). If both sides changed since the last sync, the left wins.
 */
public final class LinkSync {
   /** One side of a pair, as text (settings use their JSON form). */
   public interface Field {
      String read();

      void write(String value);
   }

   public record Pair(Field left, Field right) {
   }

   private final List<Pair> pairs;
   private final String[] agreed;

   public LinkSync(List<Pair> pairs) {
      this.pairs = List.copyOf(pairs);
      this.agreed = new String[this.pairs.size()];
   }

   /** Copies every value from one side to the other once, without linking them. */
   public static void copy(List<Pair> pairs, boolean leftToRight) {
      for (Pair pair : pairs) {
         Field from = leftToRight ? pair.left() : pair.right();
         Field to = leftToRight ? pair.right() : pair.left();
         String value = from.read();
         if (!Objects.equals(value, to.read())) {
            to.write(value);
         }
      }
   }

   /** Starts the link: the chosen side's values become the agreed values on both sides. */
   public void link(boolean leftWins) {
      copy(this.pairs, leftWins);
      for (int i = 0; i < this.pairs.size(); i++) {
         this.agreed[i] = this.pairs.get(i).left().read();
      }
   }

   /** Copies whichever side changed since the last sync. Returns how many values were copied. */
   public int sync() {
      int copied = 0;
      for (int i = 0; i < this.pairs.size(); i++) {
         Pair pair = this.pairs.get(i);
         String left = pair.left().read();
         String right = pair.right().read();
         if (Objects.equals(left, right)) {
            this.agreed[i] = left;
            continue;
         }

         if (!Objects.equals(left, this.agreed[i])) {
            pair.right().write(left);
            this.agreed[i] = left;
         } else {
            pair.left().write(right);
            this.agreed[i] = right;
         }

         copied++;
      }

      return copied;
   }

   public List<Pair> pairs() {
      return this.pairs;
   }
}
