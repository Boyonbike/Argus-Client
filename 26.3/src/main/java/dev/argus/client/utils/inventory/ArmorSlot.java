package dev.argus.client.utils.inventory;

public enum ArmorSlot {
   HELMET(39, 3),
   CHESTPLATE(38, 2),
   LEGGINGS(37, 1),
   BOOTS(36, 0);

   private final int inventoryIndex;
   private final int armorIndex;

   ArmorSlot(int inventoryIndex, int armorIndex) {
      this.inventoryIndex = inventoryIndex;
      this.armorIndex = armorIndex;
   }

   public int getInventoryIndex() {
      return this.inventoryIndex;
   }

   public int getArmorIndex() {
      return this.armorIndex;
   }

   public int getContainerSlot() {
      return 8 - this.getArmorIndex();
   }
}
