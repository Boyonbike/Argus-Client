package dev.argus.client.modules.movement.elytratrip;

/** What a station chest is for. A chest can have several roles and a role can be on several chests. */
public enum ChestRole {
   ELYTRAS("Elytras", 0xFF8B5CF6),
   DUMP("Dump", 0xFF6B7280),
   ARMOUR("Armour", 0xFF3B82F6),
   TOTEMS("Totems", 0xFFEAB308),
   GAPPLES("Gapples", 0xFFF59E0B),
   FOOD("Food", 0xFF22C55E);

   private final String label;
   private final int colour;

   ChestRole(String label, int colour) {
      this.label = label;
      this.colour = colour;
   }

   public String label() {
      return label;
   }

   /** ARGB. */
   public int colour() {
      return colour;
   }
}
