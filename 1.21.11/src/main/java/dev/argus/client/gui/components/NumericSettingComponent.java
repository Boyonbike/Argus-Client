package dev.argus.client.gui.components;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientContext;
import dev.argus.client.gui.CloseState;
import dev.argus.client.gui.CursorType;
import dev.argus.client.gui.panels.Panel;
import dev.argus.client.gui.screens.ClientScreen;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.render.GuiDrawUtils;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.InterpolatedFloat;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.regex.Matcher;
import net.minecraft.util.Mth;

public class NumericSettingComponent<N extends Number> extends SettingComponent<N> {
   private static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance();
   private final InterpolatedFloat sliderFillAnimation = new InterpolatedFloat(6.0F, false);
   private final ElapsedTimer cursorBlinkTimer = new ElapsedTimer();
   private int sliderX;
   private int sliderY;
   private boolean dragging;
   private boolean cursorVisible = false;
   private float sliderFillWidth;
   private String inputText = "";
   private final NumberSetting<N> numberSetting;

   private String getBlinkingCursor() {
      if (this.cursorBlinkTimer.hasElapsed(500L)) {
         this.cursorVisible = !this.cursorVisible;
         this.cursorBlinkTimer.reset();
      }

      return this.cursorVisible && this.editing ? "_" : "";
   }

   @Override
   public void keyPressed(int keyCode) {
      if (!this.isUnavailable() && this.editing && this.parentCloseState.isOpen()) {
         switch (keyCode) {
            case 256:
               this.editing = false;
               ClientContext.getClickGui().suppressEscapeClose();
               break;
            case 257:
            case 335:
               this.commitEditedValue();
               break;
            case 259:
               if (!this.inputText.isEmpty()) {
                  this.inputText = this.inputText.substring(0, this.inputText.length() - 1);
               }
         }
      }
   }

   private static double roundSliderValue(double value) {
      return new BigDecimal(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
   }

   public NumericSettingComponent(Panel panel, CloseState parentCloseState, NumberSetting<N> setting) {
      super(panel, parentCloseState, setting);
      this.numberSetting = setting;
   }

   private void commitEditedValue() {
      this.editing = false;
      boolean inputBlank = this.inputText.isBlank();

      try {
         String valueTypeName = this.setting.getValue().getClass().getSimpleName();

         int valueTypeCase = switch (valueTypeName) {
            case "Integer" -> 0;
            case "Float" -> 1;
            case "Double" -> 2;
            default -> -1;
         };
         switch (valueTypeCase) {
            case 0:
               this.numberSetting.setNumber((Number)(inputBlank ? this.setting.getValue() : Math.round(Double.parseDouble(this.inputText))));
               break;
            case 1:
               NumberSetting<N> floatSetting = this.numberSetting;
               Number floatResult;
               if (inputBlank) {
                  floatResult = this.setting.getValue();
               } else {
                  String floatInputText = this.inputText;
                  floatResult = Float.parseFloat(floatInputText + "f");
               }

               floatSetting.setNumber(floatResult);
               break;
            case 2:
               NumberSetting<N> doubleSetting = this.numberSetting;
               Number doubleResult;
               if (inputBlank) {
                  doubleResult = this.setting.getValue();
               } else {
                  String doubleInputText = this.inputText;
                  doubleResult = Double.parseDouble(doubleInputText + "d");
               }

               doubleSetting.setNumber(doubleResult);
         }
      } catch (Exception var9) {
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
         if (this.hovered && this.editing) {
            ClientScreen.cursor = CursorType.INPUT;
         }

         GuiDrawUtils.fillRect(
            poseStack,
            this.sliderX + this.getHorizontalPadding(),
            this.sliderY + 0.5F,
            (int)(this.sliderX + Mth.clamp(this.sliderFillAnimation.updateAndGet(), 0.0F, this.panel.getWidth() - 3) + 2.0F),
            this.sliderY + this.getHeight() - 0.5F,
            this.getUiModule().colorSetting.getValue()
         );
         String unitText = this.numberSetting.getUnit();
         String valueText = (NUMBER_FORMAT.format(this.setting.getValue().doubleValue()) + "")
            .replaceFirst("\u0001", Matcher.quoteReplacement(String.valueOf(unitText)));
         if (this.setting.shouldDisplayBoundaryLabel()) {
            valueText = this.setting.getBoundaryLabel();
         }

         String displayText;
         if (this.editing) {
            displayText = this.inputText;
         } else {
            displayText = String.format("%s: %s", this.setting.getName(), valueText);
         }

         String editingCursorText = this.editing ? "_" : "";
         this.renderWithTextScroll(
            poseStack,
            (displayText + "").replaceFirst("\u0001", editingCursorText),
            () -> FontRenderer.INSTANCE
               .drawWithShadow(
                  poseStack,
                  (displayText + "").replaceFirst("\u0001", this.getBlinkingCursor()),
                  this.panel.getX() + 4,
                  this.panel.getY() + this.getTextYOffset() - this.getHoverAnimationValue() + this.yOffset,
                  this.getUiModule().textColorSetting.getValue()
               )
         );
      }
   }

   @Override
   public void mouseClicked(double mouseX, double mouseY, int button) {
      if (this.isUnavailable()) {
         this.editing = false;
      } else {
         super.mouseClicked(mouseX, mouseY, button);
         if (this.isMouseOver(mouseX, mouseY) && button == 0 && this.parentCloseState.isOpen()) {
            if (this.editing) {
               this.commitEditedValue();
            } else {
               this.dragging = true;
            }
         }

         if (this.isMouseOver(mouseX, mouseY) && button == 1 && this.parentCloseState.isOpen()) {
            this.editing = !this.editing;
            if (this.editing) {
               if (this.getClickGui().getEditingSetting() != null && this.getClickGui().getEditingSetting() != this) {
                  this.getClickGui().getEditingSetting().editing = false;
               }

               this.getClickGui().setEditingSetting(this);
               this.inputText = this.setting.getValue().toString();
            } else if (this.getClickGui().getEditingSetting() == this) {
               this.commitEditedValue();
               this.getClickGui().setEditingSetting(null);
            }
         }
      }
   }

   @Override
   public boolean isMouseOver(double mouseX, double mouseY) {
      return super.isMouseOver(mouseX, mouseY) || this.dragging;
   }

   @Override
   public void update(double mouseX, double mouseY) {
      if (this.isUnavailable()) {
         InterpolatedFloat sliderAnimation = this.sliderFillAnimation;
         sliderAnimation.setValue(0.0F);
      }

      this.sliderY = this.panel.getY() + this.yOffset;
      this.sliderX = this.panel.getX();
      double sliderWidth = this.getPanel().getWidth() - 2;
      double mouseOffset = Math.min(sliderWidth, Math.max(0.0, mouseX - this.sliderX));
      double minimumValue = this.setting.getMinimumValue().doubleValue();
      double maximumValue = this.setting.getMaximumValue().doubleValue();
      double roundingStep = this.setting.getValue().getClass().getSimpleName().equalsIgnoreCase("Integer") ? 1.0 : 0.1;
      this.sliderFillWidth = (float)(
         sliderWidth
            * ((roundingStep == 1.0 ? this.setting.getValue().intValue() : this.setting.getValue().floatValue()) - minimumValue)
            / (maximumValue - minimumValue)
      );
      if (this.dragging) {
         if (mouseOffset == 0.0) {
            if (roundingStep == 1.0) {
               this.numberSetting.setNumber((int)this.setting.getMinimumValue().floatValue());
            } else {
               this.numberSetting.setNumber(this.setting.getMinimumValue().floatValue());
            }
         } else {
            float mappedValue = (float)(mouseOffset / sliderWidth * (maximumValue - minimumValue) + minimumValue);
            float roundingFactor = (float)(1.0 / roundingStep);
            double clampedValue = Math.max(minimumValue, Math.min(maximumValue, mappedValue));
            if (roundingStep == 0.1) {
               this.numberSetting.setNumber(roundSliderValue((float)Math.round(clampedValue * roundingFactor) / roundingFactor));
            } else {
               this.numberSetting.setNumber(roundSliderValue(clampedValue));
            }
         }
      }

      if (this.setting.getValue().floatValue() >= this.setting.getMaximumValue().floatValue()) {
         this.sliderFillWidth = this.getPanel().getWidth() - 2;
      }

      this.sliderFillAnimation.setTarget(this.sliderFillWidth);
   }

   @Override
   public void mouseReleased(double mouseX, double mouseY, int button) {
      this.dragging = false;
   }

   @Override
   public void charTyped(char character) {
      int characterCode = character;
      if (!this.isUnavailable()) {
         String allowedCharacters = "1234567890.,-";
         if (characterCode == 44) {
            characterCode = 46;
         }

         if (this.editing && this.parentCloseState.isOpen() && FontRenderer.containsCharacter(allowedCharacters, characterCode)) {
            if (characterCode != 46 || !FontRenderer.containsCharacter(this.inputText, characterCode)) {
               String previousInputText = this.inputText;
               this.inputText = (previousInputText + "").replaceFirst("\u0001", String.valueOf((char)characterCode));
            }
         }
      }
   }

   static {
      NUMBER_FORMAT.setMinimumFractionDigits(1);
      NUMBER_FORMAT.setMaximumFractionDigits(4);
      NUMBER_FORMAT.setGroupingUsed(false);
   }
}
