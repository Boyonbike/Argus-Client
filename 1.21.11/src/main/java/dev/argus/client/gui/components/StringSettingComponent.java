package dev.argus.client.gui.components;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.gui.CloseState;
import dev.argus.client.gui.CursorType;
import dev.argus.client.gui.panels.Panel;
import dev.argus.client.gui.screens.ClientScreen;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.render.GuiDrawUtils;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.ElapsedTimer;
import java.util.regex.Matcher;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

public class StringSettingComponent extends SettingComponent<String> {
   private final ElapsedTimer cursorBlinkTimer = new ElapsedTimer();
   private boolean cursorVisible = false;
   private String editingText;
   private int cursorIndex = 0;

   public String getCursorMarker() {
      if (this.cursorBlinkTimer.hasElapsed(500L)) {
         this.cursorVisible = !this.cursorVisible;
         this.cursorBlinkTimer.reset();
      }

      return this.cursorVisible ? "_" : "";
   }

   @Override
   public void keyPressed(int key) {
      if (!this.isUnavailable() && this.editing) {
         ClientContext.getClickGui().suppressEscapeClose();
         switch (key) {
            case 86:
               if (GLFW.glfwGetKey(ClientAccess.argusclientMinecraft.getWindow().handle(), 341) == 1) {
                  this.editingText = new StringBuilder(this.editingText)
                     .insert(this.cursorIndex, ClientAccess.argusclientMinecraft.keyboardHandler.getClipboard())
                     .toString();
               }
               break;
            case 256:
               this.editing = false;
               break;
            case 257:
               this.editing = false;
               this.setting.setValue(this.editingText);
               this.cursorIndex = this.editingText.length();
               break;
            case 259:
               if (this.editingText.length() == 0 || this.cursorIndex == 0) {
                  return;
               }

               int deletionIndex = this.cursorIndex - 1;
               if (this.cursorIndex == this.editingText.length()) {
                  this.editingText = this.editingText.substring(0, this.editingText.length() - 1);
               } else if (deletionIndex >= 0 && deletionIndex <= this.editingText.length()) {
                  this.editingText = new StringBuilder(this.editingText).deleteCharAt(deletionIndex).toString();
               }

               this.cursorIndex--;
               break;
            case 262:
               if (this.cursorIndex < this.editingText.length()) {
                  this.cursorIndex++;
               }
               break;
            case 263:
               if (this.cursorIndex > 0) {
                  this.cursorIndex--;
               }
         }
      }
   }

   public String getText() {
      return this.editing ? this.editingText : this.setting.getValue();
   }

   public StringSettingComponent(Panel panel, CloseState closeState, Setting<String> setting) {
      super(panel, closeState, setting);
   }

   public void beginEditing() {
      if (!this.editing) {
         this.editing = true;
         this.editingText = this.setting.getValue();
      }
   }

   @Override
   public void render(PoseStack poseStack, double mouseX, double mouseY) {
      if (this.isUnavailable()) {
         if (this.editing && this.getClickGui().getEditingSetting() == this) {
            this.getClickGui().setEditingSetting(null);
         }

         this.editing = false;
      } else {
         super.render(poseStack, mouseX, mouseY);
         if (this.hovered) {
            ClientScreen.cursor = CursorType.INPUT;
         }

         GuiDrawUtils.fillRect(
            poseStack,
            this.panel.getX() + this.getHorizontalPadding(),
            this.panel.getY() + this.yOffset + 0.5F,
            this.panel.getX() + this.panel.getWidth() - 1,
            this.panel.getY() + this.yOffset + this.getHeight() - 0.5F,
            this.getUiModule().colorSetting.getValue()
         );
         if (this.editingText != null) {
            this.cursorIndex = Mth.clamp(this.cursorIndex, 0, this.editingText.length());
         }

         if (this.editing) {
            poseStack.pushPose();
            String cursorMarker = this.getCursorMarker();
            float caretWidth = FontRenderer.INSTANCE.getWidth(this.editingText.substring(0, this.cursorIndex) + "_");
            if (caretWidth > this.getPanel().getWidth() - this.getHorizontalPadding() * 2) {
               poseStack.translate(this.getPanel().getWidth() - this.getHorizontalPadding() * 2 - caretWidth, 0.0F, 0.0F);
            }

            if (!this.editingText.isEmpty()) {
               FontRenderer.INSTANCE
                  .drawWithShadow(
                     poseStack,
                     this.editingText,
                     this.panel.getX() + 4,
                     this.panel.getY() + this.getTextYOffset() - this.getHoverAnimationValue() + this.yOffset,
                     this.getUiModule().textColorSetting.getValue()
                  );
            }

            if (!cursorMarker.isBlank()) {
               FontRenderer.INSTANCE
                  .drawWithShadow(
                     poseStack,
                     cursorMarker,
                     this.panel.getX() + 4 + FontRenderer.INSTANCE.getWidth(this.editingText.substring(0, this.cursorIndex)),
                     this.panel.getY() + this.getTextYOffset() - this.getHoverAnimationValue() + this.yOffset,
                     this.getUiModule().textColorSetting.getValue()
                  );
            }

            poseStack.popPose();
         } else {
            String labelText = this.getDisplayText();
            this.renderWithTextScroll(
               poseStack,
               labelText,
               () -> FontRenderer.INSTANCE
                  .drawWithShadow(
                     poseStack,
                     this.getDisplayText(),
                     this.panel.getX() + 4,
                     this.panel.getY() + this.getTextYOffset() - this.getHoverAnimationValue() + this.yOffset,
                     this.getUiModule().textColorSetting.getValue()
                  )
            );
         }
      }
   }

   private String getDisplayText() {
      String name = this.setting.getName();
      String value = this.setting.getValue();
      return "\u0001: \u0001"
         .replaceFirst("\u0001", Matcher.quoteReplacement(String.valueOf(name)))
         .replaceFirst("\u0001", Matcher.quoteReplacement(String.valueOf(value)));
   }

   @Override
   public void mouseClicked(double mouseX, double mouseY, int button) {
      if (this.isUnavailable()) {
         this.editing = false;
      } else {
         super.mouseClicked(mouseX, mouseY, button);
         if (this.isMouseOver(mouseX, mouseY) && button == 0) {
            this.editing = !this.editing;
            if (this.editing) {
               if (this.getClickGui().getEditingSetting() != null && this.getClickGui().getEditingSetting() != this) {
                  this.getClickGui().getEditingSetting().editing = false;
               }

               this.getClickGui().setEditingSetting(this);
            } else if (this.getClickGui().getEditingSetting() == this) {
               this.getClickGui().setEditingSetting(null);
            }

            this.editingText = this.setting.getValue();
            if (this.editingText != null && !this.editingText.isEmpty()) {
               this.cursorIndex = this.editingText.length();
            }
         }
      }
   }

   @Override
   public void charTyped(char character) {
      if (!this.isUnavailable() && this.editing) {
         this.editingText = new StringBuilder(this.editingText).insert(this.cursorIndex, character).toString();
         this.cursorIndex++;
      }
   }
}
