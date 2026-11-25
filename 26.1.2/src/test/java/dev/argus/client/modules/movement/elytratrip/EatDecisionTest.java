package dev.argus.client.modules.movement.elytratrip;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.argus.client.modules.movement.elytratrip.EatDecision.Choice;
import org.junit.jupiter.api.Test;

class EatDecisionTest {
   @Test
   void gappleWhenHealthLow() {
      assertEquals(Choice.GAPPLE, EatDecision.choose(14.0F, 20, 14, 14, true, true));
   }

   @Test
   void foodWhenHungry() {
      assertEquals(Choice.FOOD, EatDecision.choose(20.0F, 14, 14, 14, true, true));
   }

   @Test
   void foodWhenHealthLowWithoutGapple() {
      assertEquals(Choice.FOOD, EatDecision.choose(10.0F, 20, 14, 14, false, true));
   }

   @Test
   void hungryWithoutFoodEatsGapple() {
      assertEquals(Choice.GAPPLE, EatDecision.choose(20.0F, 10, 14, 14, true, false));
   }

   @Test
   void noneAboveThresholds() {
      assertEquals(Choice.NONE, EatDecision.choose(14.5F, 15, 14, 14, true, true));
   }

   @Test
   void noneWithNothingToEat() {
      assertEquals(Choice.NONE, EatDecision.choose(4.0F, 4, 14, 14, false, false));
   }
}
