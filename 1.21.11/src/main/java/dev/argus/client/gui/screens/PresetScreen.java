package dev.argus.client.gui.screens;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.gui.components.PresetListPanel;
import dev.argus.client.gui.components.PresetPanel;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.render.RenderUtils;
import dev.argus.client.settings.options.PresetCategory;
import dev.argus.client.utils.FloatAnimation;
import java.awt.Color;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;

public final class PresetScreen extends ClientScreen {
   private final PresetListPanel listPanel = new PresetListPanel(this);
   private final PresetPanel presetPanel = new PresetPanel(this);
   private PresetCategory selectedCategory = PresetCategory.MODULES;
   private final FloatAnimation fileDropHintAnimation = new FloatAnimation();

   public PresetScreen() {
      this.panels.add(this.listPanel);
      this.presetPanel.setX(this.listPanel.getX() + this.listPanel.getWidth() + 3);
      this.panels.add(this.presetPanel);
   }

   @Override
   public void renderContents(GuiGraphics graphics, PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
      this.fileDropHintAnimation.setTarget(!ClientAccess.argusclientMinecraft.isWindowActive(), 250L);
      float hintOpacity = this.fileDropHintAnimation.getValue();
      if (hintOpacity != 0.0F) {
         RenderUtils.setColorAlpha(0.75F * hintOpacity);
         String hintText = "Drop preset files here";
         float hintWidth = FontRenderer.INSTANCE.getWidth(hintText);
         FontRenderer.INSTANCE
            .drawWithShadow(
               poseStack,
               hintText,
               (ClientAccess.argusclientMinecraft.getWindow().getGuiScaledWidth() - hintWidth) / 2.0F,
               ClientAccess.argusclientMinecraft.getWindow().getGuiScaledHeight() / 2.0F,
               Color.white
            );
         RenderUtils.setColorAlpha(1.0F);
      }
   }

   @Override
   public void onFilesDrop(List files) {
      PresetImportScreen.openForDroppedFiles(this, files);
   }

   public PresetListPanel getListPanel() {
      return this.listPanel;
   }

   public PresetPanel getPresetPanel() {
      return this.presetPanel;
   }

   public PresetCategory getSelectedCategory() {
      return this.selectedCategory;
   }

   public void setSelectedCategory(PresetCategory category) {
      this.selectedCategory = category;
   }
}
