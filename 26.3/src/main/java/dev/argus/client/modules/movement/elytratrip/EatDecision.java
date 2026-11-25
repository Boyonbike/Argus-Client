package dev.argus.client.modules.movement.elytratrip;

/** What ElytraTrip eats, if anything. Pure. */
public final class EatDecision {
   public enum Choice { NONE, GAPPLE, FOOD }

   private EatDecision() {
   }

   /** Low health eats a gapple first, hunger eats food first; each falls back to the other when it is all there is. */
   public static Choice choose(float health, int hunger, int eatAtHealth, int eatAtHunger, boolean hasGapple, boolean hasFood) {
      if (health <= eatAtHealth) {
         if (hasGapple) return Choice.GAPPLE;
         if (hasFood) return Choice.FOOD;
      }
      if (hunger <= eatAtHunger) {
         if (hasFood) return Choice.FOOD;
         if (hasGapple) return Choice.GAPPLE;
      }
      return Choice.NONE;
   }
}
