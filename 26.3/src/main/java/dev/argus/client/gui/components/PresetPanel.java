package dev.argus.client.gui.components;

import com.mojang.blaze3d.Blaze3D;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientContext;
import dev.argus.client.config.PresetData;
import dev.argus.client.config.PresetUndoAction;
import dev.argus.client.gui.CloseState;
import dev.argus.client.gui.panels.Panel;
import dev.argus.client.gui.screens.PresetScreen;
import dev.argus.client.managers.PresetRegistry;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.StringSetting;
import dev.argus.client.settings.options.PresetCategory;
import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.util.Util;
import org.apache.commons.lang3.SystemUtils;

public class PresetPanel extends Panel {
   private final StringSetting saveNameSetting = new StringSetting("Save", "");
   private final EnumSetting<PresetCategory> categorySetting = new EnumSetting<>("Category", PresetCategory.MODULES);
   private final PresetScreen screen;
   private PresetPanelMode mode = PresetPanelMode.NEW;
   private PresetData selectedPreset;
   private boolean listRefreshPending;
   private boolean controlsRebuildPending;

   public PresetPanel(PresetScreen screen) {
      super("Preset");
      this.screen = screen;
      this.saveNameSetting.setChangeCallback(() -> {
         try {
            PresetUndoAction action = PresetUndoAction.beforeSave(this.getSelectedCategoryPresets(), this.saveNameSetting.getValue());
            this.getSelectedCategoryPresets().savePreset(this.saveNameSetting.getValue());
            ClientContext.configurationManager.getPresetUndoState().push(action);
            this.requestListRefresh();
         } catch (IOException saveFailure) {
            saveFailure.printStackTrace();
         }

         this.saveNameSetting.setValue("");
      });
      this.categorySetting.setChangeCallback(() -> {
         screen.setSelectedCategory(this.categorySetting.getValue());
         this.requestListRefresh();
      });
   }

   @Override
   public void init() {
      this.rebuildControls();
   }

   @Override
   public void render(PoseStack poses, double mouseX, double mouseY) {
      if (this.controlsRebuildPending) {
         this.rebuildControls();
         this.controlsRebuildPending = false;
      }

      if (this.listRefreshPending) {
         this.screen.getListPanel().refreshPresets();
         this.listRefreshPending = false;
      }

      super.render(poses, mouseX, mouseY);
   }

   public void rebuildControls() {
      this.children.clear();
      switch (this.mode) {
         case NEW:
            this.addChild(new EnumSettingComponent<>(this, CloseState.ALWAYS_OPEN, this.categorySetting));
            this.addChild(new StringSettingComponent(this, CloseState.ALWAYS_OPEN, this.saveNameSetting));
            this.addChild(new ActionButtonComponent(this, "Refresh", this::requestListRefresh));
            this.addChild(new ActionButtonComponent(this, "Undo", () -> {
               try {
                  ClientContext.configurationManager.getPresetUndoState().undo();
                  this.requestListRefresh();
               } catch (IOException undoFailure) {
                  undoFailure.printStackTrace();
               }
            }));
            break;
         case EDIT:
            this.addChild(new ButtonComponent(this, "Preset " + this.selectedPreset.getName()));
            this.addChild(new ActionButtonComponent(this, "Load", () -> {
               PresetUndoAction action = PresetUndoAction.beforeLoad(this.selectedPreset.getName());
               if (this.getSelectedCategoryPresets().applyPreset(this.selectedPreset.getName())) {
                  ClientContext.configurationManager.getPresetUndoState().push(action);
               }
            }));
            this.addChild(new ActionButtonComponent(this, "Save", () -> {
               try {
                  PresetUndoAction action = PresetUndoAction.beforeSave(this.getSelectedCategoryPresets(), this.selectedPreset.getName());
                  this.getSelectedCategoryPresets().savePreset(this.selectedPreset.getName());
                  ClientContext.configurationManager.getPresetUndoState().push(action);
                  this.requestListRefresh();
               } catch (IOException var2) {
               }
            }));
            this.addChild(new ActionButtonComponent(this, "Delete", () -> {
               PresetUndoAction action = PresetUndoAction.beforeDelete(this.getSelectedCategoryPresets(), this.selectedPreset.getName());
               if (this.getSelectedCategoryPresets().deletePreset(this.selectedPreset.getName())) {
                  ClientContext.configurationManager.getPresetUndoState().push(action);
               }

               this.setMode(PresetPanelMode.NEW);
            }));
            this.addChild(new ActionButtonComponent(this, "Open", this::openPresetLocation));
            this.addChild(new ActionButtonComponent(this, "Back", () -> this.setMode(PresetPanelMode.NEW)));
      }

      this.requestListRefresh();
   }

   private void requestListRefresh() {
      this.listRefreshPending = true;
   }

   private PresetRegistry getSelectedCategoryPresets() {
      return ClientContext.configurationManager.getPresetRegistry(this.screen.getSelectedCategory());
   }

   public void editPreset(PresetData preset) {
      this.selectedPreset = preset;
      this.setMode(PresetPanelMode.EDIT);
   }

   public PresetData getSelectedPreset() {
      return this.mode != PresetPanelMode.EDIT ? null : this.selectedPreset;
   }

   public void setMode(PresetPanelMode mode) {
      this.mode = mode;
      this.controlsRebuildPending = true;
   }

   private void openPresetLocation() {
      try {
         if (!SystemUtils.IS_OS_WINDOWS_10 && !SystemUtils.OS_NAME.startsWith("Windows 11")) {
            Blaze3D.openPath(this.screen.getSelectedCategory().getDirectory());
         } else {
            Path path = this.screen.getSelectedCategory().getDirectory().toAbsolutePath();
            PresetData preset = this.selectedPreset;
            path = path.resolve(preset.getName() + ".json");
            new ProcessBuilder(createExplorerCommand(path)).start();
         }
      } catch (Throwable var3) {
      }
   }

   private static String[] createExplorerCommand(Path path) {
      return new String[]{"explorer.exe", "/select,", path.toString()};
   }
}
