package dev.argus.client.modules.render;

import dev.argus.client.ClientAccess;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.utils.combat.BoundingBoxUtils;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ESPItemGroup implements ClientAccess {
   private final Map<String, Integer> itemCounts = new TreeMap<>(Comparator.reverseOrder());
   private final AABB groupingBounds;
   private AABB renderBounds;
   private float maximumTextWidth;

   public ESPItemGroup(ItemEntity itemEntity) {
      float partialTick = ClientAccess.argusclientMinecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
      this.groupingBounds = AABB.ofSize(itemEntity.getPosition(partialTick), 5.0, 5.0, 5.0);
      this.renderBounds = BoundingBoxUtils.getRenderBounds(itemEntity, partialTick);
      this.tryAddItem(itemEntity);
   }

   public AABB getGroupingBounds() {
      return this.groupingBounds;
   }

   public AABB getRenderBounds() {
      return this.renderBounds;
   }

   public boolean tryAddItem(ItemEntity itemEntity) {
      if (!this.groupingBounds.intersects(itemEntity.getBoundingBox())) {
         return false;
      }

      float partialTick = ClientAccess.argusclientMinecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
      String itemName = itemEntity.getName().getString();
      this.itemCounts.merge(itemName, itemEntity.getItem().getCount(), Integer::sum);
      float formattedTextWidth = FontRenderer.INSTANCE.getWidth(formatItemCount(itemName, this.itemCounts.get(itemName)));
      this.maximumTextWidth = Math.max(formattedTextWidth, this.maximumTextWidth);
      Vec3 movement = new Vec3(itemEntity.getX() - itemEntity.xo, itemEntity.getY() - itemEntity.yo, itemEntity.getZ() - itemEntity.zo);
      if (movement.lengthSqr() < 1.0E-4) {
         this.renderBounds = this.renderBounds.minmax(BoundingBoxUtils.getRenderBounds(itemEntity, partialTick));
      }

      return true;
   }

   public Map<String, Integer> getItemCounts() {
      return this.itemCounts;
   }

   public float getMaximumTextWidth() {
      return this.maximumTextWidth;
   }

   public static String formatItemCount(String name, int count) {
      return count <= 1 ? name : name + " x" + count;
   }
}
