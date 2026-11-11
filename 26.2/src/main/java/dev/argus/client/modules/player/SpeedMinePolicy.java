package dev.argus.client.modules.player;

/** SpeedMine's swap, rotation and Alternative rules, free of the client so they can be unit tested. */
final class SpeedMinePolicy {
   /** At or below this health, a held totem is never swapped away for a tool. */
   static final float TOTEM_GUARD_HEALTH = 4.0F;

   private SpeedMinePolicy() {
   }

   /**
    * Alternative (inventory swap) is blocked in 2b2t mode unless item use is restricted or was active
    * in the last 150 ms; otherwise it follows the setting.
    */
   static boolean isAlternativeAllowed(boolean is2b2tMode, boolean itemUseRestricted, boolean itemUseRecent, boolean alternative) {
      if (is2b2tMode && !itemUseRestricted && !itemUseRecent) {
         return false;
      }
      return alternative;
   }

   /**
    * Whether to rotate to the block this tick. With a Limit below 1, rotation waits until progress reaches
    * that fraction of the damage threshold, and with HoldingBest and no AutoSwap, until the tool is held.
    */
   static boolean shouldRotate(float limit, float progress, float damage, boolean autoSwapNone, boolean holdingBest, boolean holdingTool) {
      if (limit == 1.0F) {
         return true;
      }
      if (!(progress / damage >= limit)) {
         return false;
      }
      return !(autoSwapNone && holdingBest && !holdingTool);
   }

   /** Tools may be swapped in unless health is low and a totem is in the main hand. */
   static boolean canSwapTools(float health, boolean holdingTotem) {
      return health > TOTEM_GUARD_HEALTH || !holdingTotem;
   }
}
