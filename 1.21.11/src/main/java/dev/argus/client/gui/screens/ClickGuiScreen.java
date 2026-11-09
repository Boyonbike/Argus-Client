package dev.argus.client.gui.screens;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.config.ModulePresetCodec;
import dev.argus.client.gui.GuiElement;
import dev.argus.client.gui.components.ModuleComponent;
import dev.argus.client.gui.components.SettingComponent;
import dev.argus.client.gui.panels.GamePanel;
import dev.argus.client.gui.panels.ModuleCategoryPanel;
import dev.argus.client.gui.panels.Panel;
import dev.argus.client.gui.panels.SearchPanel;
import dev.argus.client.managers.ConfigurationManager;
import dev.argus.client.modules.HudWidgetModule;
import dev.argus.client.modules.Keybind;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.client.HUDModule;
import dev.argus.client.modules.client.UIModule;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.render.GuiDrawUtils;
import dev.argus.client.render.GuiScaleUtils;
import dev.argus.client.render.TextUtils;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.FloatAnimation;
import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributeView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import org.lwjgl.glfw.GLFW;

public class ClickGuiScreen extends ClientScreen implements ClientAccess {
   private static final int[] GAME_PANEL_UNLOCK_SEQUENCE = new int[]{265, 265, 264, 264, 263, 262, 263, 262, 66, 65};
   private static final HUDModule HUD = ClientContext.getModule(HUDModule.class);
   private SettingComponent<?> editingSetting = null;
   private int secretSequenceIndex = 0;
   private boolean gamePanelUnlocked;
   private boolean searchCursorVisible;
   private final FloatAnimation searchFocusAnimation = new FloatAnimation();
   private final ElapsedTimer searchCursorTimer = new ElapsedTimer();
   public EditBox searchBox;
   private String searchCompletion;
   public List<ModuleComponent> openModuleComponents = new ArrayList();

   private void handleSecretSequence(int keyCode) {
      if (!this.gamePanelUnlocked && GAME_PANEL_UNLOCK_SEQUENCE[this.secretSequenceIndex] == keyCode) {
         this.secretSequenceIndex++;
         if (this.secretSequenceIndex == GAME_PANEL_UNLOCK_SEQUENCE.length) {
            try {
               Path markerPath = ConfigurationManager.USER_HOME_MARKER_PATH;
               markerPath.toFile().createNewFile();
               DosFileAttributeView dos = Files.getFileAttributeView(markerPath, DosFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
               if (dos != null) {
                  try {
                     dos.setHidden(true);
                  } catch (UnsupportedOperationException var5) {
                  }
               }
            } catch (IOException var6) {
            }

            this.gamePanelUnlocked = true;
            this.secretSequenceIndex = 0;
            this.addGamePanel();
            this.resetPanelPositions();
         }
      } else {
         this.secretSequenceIndex = 0;
      }
   }

   private static boolean isControlDown() {
      long window = ClientAccess.argusclientMinecraft.getWindow().handle();
      return GLFW.glfwGetKey(window, 341) == 1 || GLFW.glfwGetKey(window, 345) == 1;
   }

   private void handleSearchKey(int keyCode) {
      String searchText = this.searchBox.getValue();
      switch (keyCode) {
         case 70:
            if (isControlDown()) {
               if (this.searchBox.isFocused()) {
                  this.reset();
               } else {
                  this.setInitialFocus(this.searchBox);
               }
            }
            break;
         case 257:
         case 335:
            boolean foundModule = false;

            for (Panel panel : this.panels) {
               for (Object childEntry : panel.getChildren()) {
                  GuiElement element = (GuiElement)childEntry;
                  if (element instanceof ModuleComponent moduleComponent) {
                     String moduleName = moduleComponent.getModule().getName();
                     String completedSearch = TextUtils.normalizeKeyboardLayout(searchText + this.searchCompletion);
                     if (moduleName.equalsIgnoreCase(completedSearch)) {
                        moduleComponent.setOpen(true);
                        moduleComponent.startSearchHighlight();
                        foundModule = true;
                        break;
                     }
                  }
               }

               if (foundModule) {
                  break;
               }
            }

            this.reset();
            break;
         case 258:
            if (this.searchBox.isFocused()) {
               EditBox completionBox = this.searchBox;
               String currentCompletion = this.searchCompletion;
               completionBox.setValue(TextUtils.normalizeKeyboardLayout(searchText + currentCompletion));
            }
            break;
         case 259:
         case 261:
            if (this.searchBox.isFocused() && !searchText.isEmpty()) {
               this.searchBox.setValue(isControlDown() ? "" : searchText.substring(0, searchText.length() - 1));
            }
      }
   }

   private String getSearchCursor() {
      if (this.searchCursorTimer.hasElapsed(500L)) {
         this.searchCursorVisible = !this.searchCursorVisible;
         this.searchCursorTimer.reset();
      }

      return this.searchCursorVisible ? "_" : "";
   }

   private void handlePresetPaste(int keyCode) {
      if (isControlDown() && keyCode == 86) {
         ModulePresetCodec presetCodec = ClientContext.configurationManager.getModulePresetCodec();

         try {
            ModulePresetCodec.DecodedPreset decodedPreset = presetCodec.decode(ClientAccess.argusclientMinecraft.keyboardHandler.getClipboard());
            ModuleImportConfirmationScreen presetScreen = new ModuleImportConfirmationScreen(
               ClientContext.getClickGui(), decodedPreset.module(), decodedPreset.json()
            );
            presetScreen.open();
         } catch (Exception pasteException) {
            pasteException.printStackTrace();
         }
      }
   }

   public EditBox getSearchBox() {
      return this.searchBox;
   }

   public SettingComponent<?> getEditingSetting() {
      return this.editingSetting;
   }

   public ClickGuiScreen() {
      this.gamePanelUnlocked = ConfigurationManager.USER_HOME_MARKER_PATH.toFile().exists();
      int panelX = 10;

      for (ModuleCategory category : ModuleCategory.values()) {
         if (category != ModuleCategory.HUD) {
            ModuleCategoryPanel categoryPanel = new ModuleCategoryPanel(category);
            categoryPanel.setX(panelX);
            this.panels.add(categoryPanel);
            panelX += categoryPanel.getWidth() + 3;
         }
      }

      SearchPanel var7 = new SearchPanel();
      var7.setX(panelX);
      this.panels.add(var7);
      if (this.gamePanelUnlocked) {
         this.addGamePanel();
      }
   }

   @Override
   protected void reset() {
      super.reset();
      this.setFocused(null);
      this.searchBox.setFocused(false);
      this.searchBox.setValue("");
      this.searchBox.setSuggestion("");
   }

   @Override
   public void onFilesDrop(List paths) {
      PresetImportScreen.openForDroppedFiles(this, paths);
   }

   public void setEditingSetting(SettingComponent<?> component) {
      this.editingSetting = component;
   }

   @Override
   public void renderContents(GuiGraphics graphics, PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
      GuiDrawUtils.fillRect(poseStack, GuiScaleUtils.getScaledWidth() / 2 - 70, 4, GuiScaleUtils.getScaledWidth() / 2 + 70, 18, -938208236);
      FontRenderer.INSTANCE.drawWithShadow(poseStack, "Search: ".concat(this.searchBox.getValue()), GuiScaleUtils.getScaledWidth() / 2 - 66, 7, Color.white);
      this.searchFocusAnimation.setTarget(this.searchBox.isFocused(), 300L);
      this.drawSearchDimmer(poseStack);
      int fontHeight = FontRenderer.INSTANCE.getHeight();
      if (this.openModuleComponents.size() >= 3) {
         ((ModuleComponent)this.openModuleComponents.getFirst()).setOpen(false);
      }

      this.openModuleComponents.removeIf(ModuleComponent::isClosed);
      if (this.searchBox.isFocused()) {
         FontRenderer fontRenderer = FontRenderer.INSTANCE;
         String searchText = this.searchBox.getValue();
         String completionText = this.searchCompletion;
         float searchTextWidth = fontRenderer.getWidth(searchText + completionText + "_");
         completionText = this.searchBox.getValue();
         String completionFormatting = String.valueOf(ChatFormatting.GRAY);
         String completionSuffix = this.searchCompletion;
         String cursor = this.getSearchCursor();
         String cursorFormatting = String.valueOf(ChatFormatting.WHITE);
         String searchLabel = completionText + completionFormatting + completionSuffix + cursorFormatting + cursor;
         fontRenderer.drawWithShadow(
            poseStack,
            searchLabel,
            GuiScaleUtils.getScaledWidth() / 2.0F - searchTextWidth / 2.0F,
            GuiScaleUtils.getScaledHeight() / 2.0F - fontHeight / 2.0F,
            Color.white
         );
      } else {
         String searchHint = ClientContext.languageTable.getTranslationOrDefault("ui.Search", "Ctrl + F to activate search");
         float hintY = GuiScaleUtils.getScaledHeight() - fontHeight - 1;
         if (this.getParentScreen() instanceof TitleScreen) {
            hintY -= fontHeight + 1;
         }

         Color hintColor = ColorUtils.pulse(Color.GRAY, Color.WHITE, 2000.0, 250.0);
         int hudMargin = HUD.getSafeX();
         FontRenderer.INSTANCE
            .drawWithShadow(
               poseStack, searchHint, GuiScaleUtils.getScaledWidth() - FontRenderer.INSTANCE.getWidth(searchHint) - hudMargin, hintY - 1.0F, hintColor
            );
      }
   }

   private void drawSearchDimmer(PoseStack poseStack) {
      float dimmerOpacity = this.searchFocusAnimation.getValue() * 0.2F;
      int dimmerColor = ARGB.color(dimmerOpacity, 0);
      GuiDrawUtils.fillRect(
         poseStack,
         0.0F,
         0.0F,
         ClientAccess.argusclientMinecraft.getWindow().getGuiScaledWidth(),
         ClientAccess.argusclientMinecraft.getWindow().getGuiScaledHeight(),
         dimmerColor
      );
   }

   @Override
   public void init() {
      super.init();
      this.searchCompletion = "";
      this.searchBox = new EditBox(ClientAccess.argusclientMinecraft.font, 7, -100, 100, 9, Component.literal("Search"));
      this.searchBox.setVisible(true);
      this.searchBox.setEditable(true);
      this.searchBox.setMaxLength(64);
      this.searchBox
         .setResponder(
            searchText -> {
               if (!searchText.isEmpty()) {
                  for (Object moduleEntry : ClientContext.moduleRegistry.getRegistry()) {
                     Module module = (Module)moduleEntry;
                     if (!(module instanceof HUDModule)
                        && !(module instanceof HudWidgetModule)
                        && module.getName().toLowerCase(Locale.ROOT).startsWith(TextUtils.normalizeKeyboardLayout(searchText.toLowerCase(Locale.ROOT)))) {
                        this.searchCompletion = module.getName().substring(this.searchBox.getCursorPosition());
                        return;
                     }
                  }
               }

               this.searchCompletion = "";
            }
         );
      this.addRenderableWidget(this.searchBox);
      this.setInitialFocus(this.searchBox);
   }

   @Override
   public boolean keyPressed(KeyEvent event) {
      super.keyPressed(event);
      Keybind toggleKeybind = UIModule.INSTANCE.getKeybind();
      if (toggleKeybind.code() == event.key() && !toggleKeybind.mouse() && this.escapeCloseAllowed && !this.searchBox.isFocused()) {
         this.closeWithTransition();
         return true;
      } else {
         this.handleSecretSequence(event.key());
         this.handleSearchKey(event.key());
         this.handlePresetPaste(event.key());
         return true;
      }
   }

   public void addGamePanel() {
      GamePanel gamePanel = new GamePanel();
      this.panels.add(gamePanel);
      this.resetPanelPositions();
   }

   @Override
   public void closeWithTransition() {
      super.closeWithTransition();
      ClientAccess.executorService.submit(() -> ClientContext.configurationManager.save());
   }

   public boolean isGamePanelUnlocked() {
      return this.gamePanelUnlocked;
   }
}
