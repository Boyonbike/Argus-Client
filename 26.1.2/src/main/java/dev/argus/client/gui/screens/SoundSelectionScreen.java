package dev.argus.client.gui.screens;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.audio.BuiltinSound;
import dev.argus.client.gui.components.ButtonComponent;
import dev.argus.client.gui.panels.Panel;
import dev.argus.client.managers.ConfigurationManager;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.collections.NamedIdentifier;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.gui.screens.Screen;

public class SoundSelectionScreen extends ClientScreen {
   private final Setting<NamedIdentifier> selectedSetting;
   private final Screen parentScreen;

   public SoundSelectionScreen(Setting<NamedIdentifier> selectedSetting, Screen parentScreen) {
      this.selectedSetting = selectedSetting;
      this.parentScreen = parentScreen;
      this.rebuildSoundPanels();
   }

   @Override
   public void closeWithTransition() {
      if (this.parentScreen instanceof ClientScreen clientScreen) {
         clientScreen.open();
      } else {
         ClientAccess.argusclientMinecraft.setScreen(this.parentScreen);
      }
   }

   @Override
   public void onFilesDrop(List paths) {
      for (Object pathElement : paths) {
         Path droppedFile = (Path)pathElement;
         if (droppedFile.toFile().getName().endsWith(".ogg")) {
            Path customSoundsDirectory = ConfigurationManager.SOUNDS_DIRECTORY.resolve("custom");
            customSoundsDirectory.toFile().mkdirs();

            try {
               Files.copy(droppedFile, customSoundsDirectory.resolve(droppedFile.getFileName()));
            } catch (IOException exception) {
               exception.printStackTrace();
            }
         }
      }

      ClientContext.soundManager.reload();
      this.rebuildSoundPanels();
   }

   private void rebuildSoundPanels() {
      this.getPanels().clear();
      synchronized (ClientContext.soundManager.getSoundIds()) {
         AtomicInteger panelIndex = new AtomicInteger(0);
         ClientContext.soundManager
            .getSoundIds()
            .stream()
            .map(NamedIdentifier::getCategory)
            .filter(category -> !category.isEmpty())
            .distinct()
            .forEach(category -> {
               Panel categoryPanel = new Panel(FontRenderer.capitalize(category));
               this.getPanels().add(categoryPanel);
               categoryPanel.setX(categoryPanel.getX() + (categoryPanel.getWidth() + 3) * panelIndex.getAndIncrement());
            });

         for (Object soundElement : ClientContext.soundManager.getSoundIds()) {
            NamedIdentifier soundId = (NamedIdentifier)soundElement;

            for (Panel panel : this.getPanels()) {
               if (panel.getName().equalsIgnoreCase(soundId.getCategory()) && !soundId.equals(BuiltinSound.HORROR.getIdentifier())) {
                  panel.getChildren().add(new SoundOptionButton(this, panel, soundId));
                  break;
               }
            }
         }

         for (Panel panel : this.getPanels()) {
            panel.getChildren().sort(Comparator.comparing(child -> child instanceof ButtonComponent button ? button.getLabel() : ""));
         }
      }
   }

   public Setting<NamedIdentifier> getSelectedSetting() {
      return this.selectedSetting;
   }
}
