package dev.argus.client.modules.render;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.AbstractContainerScreenRenderTooltipEvent;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.FloatAnimation;
import dev.argus.client.utils.KeyUtils;
import dev.argus.client.utils.inventory.ContainerTooltipComponent;
import dev.argus.client.utils.network.PacketSender;
import dev.argus.client.utils.world.MapDataCache;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundSelectBundleItemPacket;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.apache.commons.lang3.math.Fraction;
import org.joml.Matrix3x2fStack;

public record BundleTooltipComponent(List entries, BundleContents bundleContents, Slot slot) implements ClientAccess, ClientTooltipComponent {
   private static final int CELL_SIZE = 20;
   private static final int MAX_COLUMNS = 6;
   private static final int LEFT_ALT_KEY = 342;
   private static final TooltipsModule TOOLTIPS = ClientContext.getModule(TooltipsModule.class);
   private static final FloatAnimation weightWidthAnimation = new FloatAnimation();
   private static final FloatAnimation weightAlphaAnimation = new FloatAnimation();

   public static BundleTooltipComponent fromSlot(Slot slot) {
      BundleContents contents = slot.getItem().get(DataComponents.BUNDLE_CONTENTS);
      if (contents == null) {
         return null;
      }

      List<BundleTooltipComponent.BundleEntry> entries = new ArrayList<>();
      int index = 0;

      for (ItemStack item : contents.items()) {
         boolean selected = contents.hasSelectedItem() && contents.getSelectedItem() == index;
         entries.add(new BundleTooltipComponent.BundleEntry(item, selected));
         index++;
      }

      return new BundleTooltipComponent(entries, contents, slot);
   }

   @Override
   public void renderImage(Font font, int x, int y, int tooltipWidth, int tooltipHeight, GuiGraphicsExtractor graphics) {
      int columns = this.columns();
      x += Math.max((tooltipWidth - this.getWidth(font)) / 2, 0);
      int mouseX = AbstractContainerScreenRenderTooltipEvent.mouseX;
      int mouseY = AbstractContainerScreenRenderTooltipEvent.mouseY;
      ItemStack hoveredItem = null;

      for (int index = 0; index < this.entries.size(); index++) {
         BundleTooltipComponent.BundleEntry entry = (BundleTooltipComponent.BundleEntry)this.entries.get(index);
         int itemX = x + index % columns * 20;
         int itemY = y + index / columns * 20;
         if (mouseX >= itemX && mouseX < itemX + 20 && mouseY >= itemY && mouseY < itemY + 20) {
            hoveredItem = entry.item();
            if (this.bundleContents.getSelectedItem() != index) {
               BundleItem.toggleSelectedItem(this.slot.getItem(), index);
               PacketSender.send(new ServerboundSelectBundleItemPacket(this.slot.index, index));
            }
         }

         this.renderEntry(font, graphics, entry.item(), itemX, itemY);
         if (entry.selected()) {
            this.renderSelection(graphics, font, itemX, itemY);
         }
      }

      this.renderWeightBar(graphics, x, y + (int)Math.ceil((double)this.entries.size() / columns) * 20);
      if (hoveredItem != null && !hoveredItem.isEmpty()) {
         graphics.setTooltipForNextFrame(argusclientMinecraft.font, hoveredItem, mouseX, mouseY);
      }
   }

   private void renderEntry(Font font, GuiGraphicsExtractor graphics, ItemStack item, int x, int y) {
      MapId mapIdComponent = item.get(DataComponents.MAP_ID);
      int mapId = mapIdComponent == null ? -1 : mapIdComponent.id();
      MapDataCache mapCache = ClientContext.mapDataCache;
      MapItemSavedData mapData = mapId == -1 ? null : mapCache.getMapData(item, mapId);
      Setting<Boolean> mapOverlay = TOOLTIPS.mapOverlaySetting;
      if (mapOverlay.getValue() && mapData != null) {
         ContainerTooltipComponent.renderMapOverlay(graphics, mapData, mapId, x + 1, y + 1);
         if (!KeyUtils.isPhysicalKeyDown(342)) {
            graphics.renderItemDecorations(font, item, x + 1, y + 1);
         }
      } else {
         graphics.renderItem(item, x + 2, y + 2);
         graphics.renderItemDecorations(font, item, x + 2, y + 2);
      }
   }

   private void renderSelection(GuiGraphicsExtractor graphics, Font font, int x, int y) {
      float progress = (float)(System.currentTimeMillis() % 2000L) / 1000.0F;
      if (progress >= 1.0F) {
         progress = 2.0F - progress;
      }

      Color color = ColorUtils.blend(Color.RED, Color.RED.darker(), progress);
      graphics.nextStratum();
      Matrix3x2fStack poses = graphics.pose();
      poses.pushMatrix();

      try {
         graphics.drawString(font, "x", x + 3, y + 1, color.hashCode(), true);
      } finally {
         poses.popMatrix();
      }
   }

   private void renderWeightBar(GuiGraphicsExtractor graphics, int x, int y) {
      int barWidth = this.columns() * 20 - 2;
      Fraction weight = this.bundleContents.weight();
      int filledWidth = Mth.mulAndTruncate(weight, barWidth);
      weightWidthAnimation.setTarget(filledWidth, 150L);
      int animatedWidth = (int)weightWidthAnimation.getValue();
      weightAlphaAnimation.setTarget(filledWidth != 0, 250L);
      float alpha = weightAlphaAnimation.getValue();
      int color = ARGB.color(alpha, this.slot.getItem().getBarColor());
      Color barColor = new Color(color, true);
      graphics.fill(x + 4, y + 2, x + 4 + barWidth, y + 8, ColorUtils.darken(barColor, 0.3F).hashCode());
      graphics.fill(x + 2, y, x + 2 + animatedWidth, y + 6, color);
   }

   private int columns() {
      return this.entries.size() > 16 ? 6 : 5;
   }

   @Override
   public int getHeight(Font font) {
      return (int)Math.ceil((double)this.entries.size() / this.columns()) * 20 + 12;
   }

   @Override
   public int getWidth(Font font) {
      return this.columns() * 20 + 2;
   }

   record BundleEntry(ItemStack item, boolean selected) {
   }
}
