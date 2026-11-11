package dev.argus.client.gui.screens;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.gui.components.TradeEditorPanel;
import dev.argus.client.render.GuiDrawUtils;
import dev.argus.client.utils.inventory.TradeDefinition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

public class TradeEditorScreen extends ClientScreen {
   private final TradeEditorPanel editorPanel;
   private ItemStack tooltipStack;

   public TradeEditorScreen(String tradeName) {
      TradeDefinition trade = ClientContext.tradeRegistry.createDefault(tradeName);
      this.editorPanel = new TradeEditorPanel(this, trade, "Create");
      this.getPanels().add(this.editorPanel);
   }

   public TradeEditorScreen(TradeDefinition trade) {
      this.editorPanel = new TradeEditorPanel(this, trade, "Edit");
      this.getPanels().add(this.editorPanel);
   }

   @Override
   public void renderContents(GuiGraphics graphics, PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
      GuiDrawUtils.flush();
      if (this.tooltipStack != null) {
         graphics.setTooltipForNextFrame(ClientAccess.argusclientMinecraft.font, this.tooltipStack, mouseX, mouseY);
      }

      this.tooltipStack = null;
   }

   public void setTooltipStack(ItemStack stack) {
      this.tooltipStack = stack;
   }
}
