package dev.argus.client.gui.components;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientContext;
import dev.argus.client.config.PresetData;
import dev.argus.client.gui.GuiElement;
import dev.argus.client.gui.panels.Panel;
import dev.argus.client.gui.screens.PresetScreen;

public class PresetListPanel extends Panel {
   private final PresetScreen screen;
   private boolean layoutUpdatePending;

   public PresetListPanel(PresetScreen screen) {
      super("List");
      this.screen = screen;
   }

   @Override
   public void render(PoseStack poses, double mouseX, double mouseY) {
      if (this.layoutUpdatePending) {
         for (GuiElement child : this.children) {
            child.update(mouseX, mouseY);
         }

         this.updateLayout();
         this.layoutUpdatePending = false;
      }

      super.render(poses, mouseX, mouseY);
   }

   public void refreshPresets() {
      this.layoutUpdatePending = true;
      this.children.clear();
      ClientContext.configurationManager
         .getPresetRegistry(this.screen.getSelectedCategory())
         .forEachPreset(preset -> this.addChild(new PresetListPanel.PresetButton(this.screen, this, preset)));
   }

   private static final class PresetButton extends ActionButtonComponent {
      private final PresetScreen screen;
      private final PresetData preset;

      public PresetButton(PresetScreen screen, Panel panel, PresetData preset) {
         super(panel, preset.getName(), () -> {
            screen.getPresetPanel().editPreset(preset);
            screen.getPresetPanel().updateLayout();
         });
         this.preset = preset;
         this.screen = screen;
      }

      @Override
      public boolean isHighlighted() {
         return this.preset.equals(this.screen.getPresetPanel().getSelectedPreset());
      }
   }
}
