package dev.argus.client.modules.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class SpeedMinePolicyTest {
   @Test
   void alternativeFollowsSettingOutside2b2t() {
      assertTrue(SpeedMinePolicy.isAlternativeAllowed(false, false, false, true));
      assertFalse(SpeedMinePolicy.isAlternativeAllowed(false, false, false, false));
   }

   @Test
   void alternativeOn2b2tNeedsRestrictedOrRecentItemUse() {
      assertFalse(SpeedMinePolicy.isAlternativeAllowed(true, false, false, true));
      assertTrue(SpeedMinePolicy.isAlternativeAllowed(true, true, false, true));
      assertTrue(SpeedMinePolicy.isAlternativeAllowed(true, false, true, true));
      assertFalse(SpeedMinePolicy.isAlternativeAllowed(true, true, true, false));
   }

   @Test
   void limitOffAlwaysRotates() {
      assertTrue(SpeedMinePolicy.shouldRotate(1.0F, 0.0F, 1.0F, true, true, false));
   }

   @Test
   void limitWaitsForProgressFraction() {
      assertFalse(SpeedMinePolicy.shouldRotate(0.9F, 0.8F, 1.0F, false, false, true));
      assertTrue(SpeedMinePolicy.shouldRotate(0.9F, 0.9F, 1.0F, false, false, true));
      assertTrue(SpeedMinePolicy.shouldRotate(0.9F, 0.63F, 0.7F, false, false, true));
   }

   @Test
   void holdingBestWithoutAutoSwapWaitsForTool() {
      assertFalse(SpeedMinePolicy.shouldRotate(0.9F, 1.0F, 1.0F, true, true, false));
      assertTrue(SpeedMinePolicy.shouldRotate(0.9F, 1.0F, 1.0F, true, true, true));
      assertTrue(SpeedMinePolicy.shouldRotate(0.9F, 1.0F, 1.0F, false, true, false));
   }

   @Test
   void totemIsKeptAtLowHealth() {
      assertFalse(SpeedMinePolicy.canSwapTools(4.0F, true));
      assertTrue(SpeedMinePolicy.canSwapTools(4.0F, false));
      assertTrue(SpeedMinePolicy.canSwapTools(4.5F, true));
   }
}
