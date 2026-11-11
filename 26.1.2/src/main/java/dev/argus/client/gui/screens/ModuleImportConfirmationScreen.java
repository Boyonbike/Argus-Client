package dev.argus.client.gui.screens;

import com.google.gson.JsonObject;
import dev.argus.client.ClientAccess;
import dev.argus.client.gui.GuiElement;
import dev.argus.client.gui.components.ActionButtonComponent;
import dev.argus.client.gui.components.ButtonComponent;
import dev.argus.client.gui.panels.Panel;
import dev.argus.client.modules.Module;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;

public class ModuleImportConfirmationScreen extends ClientScreen {
   private final Screen returnScreen;
   private final Module module;
   private final JsonObject moduleJson;

   public ModuleImportConfirmationScreen(Screen previousScreen, Module targetModule, JsonObject importedJson) {
      this.returnScreen = previousScreen;
      this.module = targetModule;
      this.moduleJson = importedJson;
      Panel confirmationPanel = new Panel("Confirmation");
      ButtonComponent moduleLabel = new ButtonComponent(confirmationPanel, "Module: " + targetModule.getName());
      ActionButtonComponent importButton = new ActionButtonComponent(confirmationPanel, "Import", this::importModule);
      ActionButtonComponent cancelButton = new ActionButtonComponent(confirmationPanel, "Cancel", this::closeWithTransition);
      GuiElement[] children = new GuiElement[]{moduleLabel, importButton, cancelButton};
      confirmationPanel.addChildren(children);
      this.getPanels().add(confirmationPanel);
   }

   private void importModule() {
      this.module.fromJson(this.moduleJson);
      this.closeWithTransition();
   }

   @Override
   public boolean keyPressed(KeyEvent event) {
      if (event.key() == 257) {
         this.importModule();
      }

      return super.keyPressed(event);
   }

   @Override
   public void closeWithTransition() {
      if (this.returnScreen instanceof ClientScreen clientScreen) {
         clientScreen.open();
      } else {
         ClientAccess.argusclientMinecraft.setScreen(this.returnScreen);
      }
   }
}
