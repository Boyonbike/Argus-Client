package dev.argus.client.gui.screens;

import dev.argus.client.utils.InputKeys;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.config.EnumJsonConverter;
import dev.argus.client.gui.components.ActionButtonComponent;
import dev.argus.client.gui.components.ButtonComponent;
import dev.argus.client.gui.panels.Panel;
import dev.argus.client.settings.options.PresetCategory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;

public final class PresetImportScreen extends ClientScreen {
   private final PresetCategory detectedCategory;
   private final Panel importPanel;
   private final Screen returnScreen;
   private final Path sourceFile;

   public PresetImportScreen(Screen previousScreen, Path sourcePath) {
      this.returnScreen = previousScreen;
      this.detectedCategory = this.readPresetCategory(sourcePath);
      this.importPanel = new Panel(this.getCategoryTitle(this.detectedCategory));
      this.sourceFile = sourcePath;
      this.importPanel.getChildren().add(new ButtonComponent(this.importPanel, "Preset: " + this.getPresetName(sourcePath)));
      if (this.detectedCategory != null) {
         this.importPanel.registerChild(new ActionButtonComponent(this.importPanel, "Import", () -> this.importPreset(sourcePath, this.detectedCategory)));
      } else {
         for (PresetCategory category : PresetCategory.values()) {
            this.importPanel
               .registerChild(new ActionButtonComponent(this.importPanel, EnumJsonConverter.getName(category), () -> this.importPreset(sourcePath, category)));
         }
      }

      this.importPanel.registerChild(new ActionButtonComponent(this.importPanel, "Cancel", this::closeWithTransition));
      this.getPanels().add(this.importPanel);
   }

   @Override
   public boolean keyPressed(KeyEvent event) {
      if (InputKeys.glfw(event) == 257 && this.detectedCategory != null) {
         this.importPreset(this.sourceFile, this.detectedCategory);
      }

      return super.keyPressed(event);
   }

   @Override
   public void closeWithTransition() {
      ClientAccess.argusclientMinecraft.gui.setScreen(this.returnScreen);
   }

   private String getPresetName(Path sourcePath) {
      return sourcePath.toFile().getName().replace(".json", "");
   }

   private void importPreset(Path sourcePath, PresetCategory targetCategory) {
      String fileName = sourcePath.toFile().getName();

      try {
         Files.copy(sourcePath, targetCategory.getDirectory().resolve(fileName));
      } catch (IOException copyFailure) {
         copyFailure.printStackTrace();
      }

      ClientContext.configurationManager.getPresetRegistry(targetCategory).loadPresets();
      ClientAccess.argusclientMinecraft.gui.setScreen(this.returnScreen);
   }

   private PresetCategory readPresetCategory(Path sourcePath) {
      try {
         String jsonText = Files.readString(sourcePath);
         JsonElement parsedJson = JsonParser.parseString(jsonText);
         if (!parsedJson.isJsonObject()) {
            return null;
         }

         JsonObject jsonObject = parsedJson.getAsJsonObject();
         if (!jsonObject.has("category")) {
            return null;
         }

         String categoryName = jsonObject.get("category").getAsString();
         return PresetCategory.findByName(categoryName);
      } catch (IOException readFailure) {
         return null;
      }
   }

   public static boolean openForDroppedFiles(Screen previousScreen, List droppedPaths) {
      ArrayList jsonPaths = new ArrayList();
      droppedPaths.stream().filter(path -> path.toString().endsWith(".json")).forEach(jsonPaths::add);
      if (jsonPaths.size() != 1) {
         return false;
      }

      ClientAccess.argusclientMinecraft.gui.setScreen(new PresetImportScreen(previousScreen, (Path)jsonPaths.getFirst()));
      return true;
   }

   private String getCategoryTitle(PresetCategory category) {
      return category == null ? "Unknown" : category.getName();
   }
}
