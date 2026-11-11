package dev.argus.client.settings.options;

import dev.argus.client.settings.NamedEntry;

public enum TracersHitboxChoice implements NamedEntry {
   HEAD("Head", 0.95F),
   BODY("Body", 0.5F),
   LEGS("Legs", 0.0F);

   private final String displayName;
   private final float heightFraction;

   @Override
   public String getName() {
      return this.displayName;
   }

   TracersHitboxChoice(String displayName, float heightFraction) {
      this.displayName = displayName;
      this.heightFraction = heightFraction;
   }

   public float getHeightFraction() {
      return this.heightFraction;
   }
}
