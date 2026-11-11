package dev.argus.client.gui.components;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.gui.CloseState;
import dev.argus.client.gui.CursorType;
import dev.argus.client.gui.GuiElement;
import dev.argus.client.gui.panels.Panel;
import dev.argus.client.gui.screens.ClientScreen;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.render.GuiDrawUtils;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.ClampedInterpolatedFloat;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.InterpolatedFloat;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

public class ColorSettingComponent extends SettingComponent<Color> implements CloseState {
   private static final int SLIDER_HEIGHT = 6;
   private static final int CONTROL_GAP = 5;
   private final List<GuiElement> controls = new ArrayList<>(4);
   public final ElapsedTimer invalidPasteTimer = new ElapsedTimer();
   public final ElapsedTimer pasteTimer = new ElapsedTimer();
   public final ElapsedTimer copyTimer = new ElapsedTimer();
   private boolean expanded;
   private boolean draggingSaturationBrightness;
   private boolean draggingHue;
   private boolean draggingAlpha;
   private final float[] hsb = new float[3];
   protected final InterpolatedFloat saturationMarkerX = new InterpolatedFloat(0.35F, true);
   protected final InterpolatedFloat brightnessMarkerY = new InterpolatedFloat(0.35F, true);
   protected final InterpolatedFloat hueMarkerX = new InterpolatedFloat(0.35F, true);
   protected final InterpolatedFloat alphaMarkerX = new InterpolatedFloat(0.35F, true);
   private final ClampedInterpolatedFloat markerContrast = new ClampedInterpolatedFloat(0.35F, false);
   private final ClampedInterpolatedFloat markerExpansion = new ClampedInterpolatedFloat(0.35F, true);

   public ColorSettingComponent(Panel panel, CloseState parent, Setting<Color> setting) {
      super(panel, parent, setting);
      int controlsY = this.yOffset + this.getBaseHeight() + 3 + this.pickerSize() + this.getControlSectionOffset();
      this.controls.add(new ColorSettingComponent.CopyControl(controlsY, this));
      this.controls.add(new ColorSettingComponent.PasteControl(controlsY, this));
      if (!((ColorSetting)setting).isThemeColor()) {
         this.controls.add(new ColorSettingComponent.SyncControl(this));
      }

      this.controls.add(new ColorSettingComponent.RainbowControl(this));
   }

   @Override
   public void init() {
      this.updateHsbFromColor(this.setting.getValue());
      this.controls.forEach(GuiElement::init);
   }

   @Override
   public boolean isClosed() {
      return this.parentCloseState.isClosed() || !this.expanded;
   }

   private Color getSelectedColor() {
      int alpha = this.setting.getValue().getAlpha();
      return ColorUtils.withAlpha(Color.getHSBColor(this.hsb[0], this.hsb[1], this.hsb[2]), alpha);
   }

   @Override
   public int getHeight() {
      if (this.expanded && !this.isUnavailable()) {
         int toggleRows = this.controls.size() - 2;
         int controlsHeight = this.getBaseHeight() + toggleRows * (this.getBaseHeight() + 3);
         return this.getBaseHeight() + 3 + this.pickerSize() + this.getControlSectionOffset() + controlsHeight;
      } else {
         return super.getHeight();
      }
   }

   @Override
   public void keyPressed(int keyCode) {
      if (!this.isUnavailable()) {
         this.controls.forEach(control -> control.keyPressed(keyCode));
         super.keyPressed(keyCode);
      }
   }

   private float getPickerY() {
      return this.panel.getY() + this.yOffset;
   }

   private float getPickerX() {
      return this.panel.getX();
   }

   private int pickerSize() {
      return Math.max(0, this.panel.getWidth() - 10);
   }

   private int getControlSectionOffset() {
      int hueAndGap = 11;
      int alphaAndGap = this.setting.opaqueColor ? 0 : 11;
      return hueAndGap + alphaAndGap + 5;
   }

   private Color getAccentColor() {
      return ColorUtils.withAlpha(this.getUiModule().colorSetting.getValue(), 255);
   }

   private void renderPreviewCheckerboard(PoseStack poses) {
      int x = (int)this.getPickerX() + this.panel.getWidth() - 11;
      int y = (int)this.getPickerY() + 2;
      int light = -1;
      int dark = -5592406;

      for (int row = 0; row < 2; row++) {
         for (int column = 0; column < 2; column++) {
            int color = (row + column) % 2 == 0 ? light : dark;
            GuiDrawUtils.fillRect(poses, x + column * 4, y + row * 4, x + column * 4 + 4, y + row * 4 + 4, color);
         }
      }
   }

   private int squareY() {
      return (int)this.getPickerY() + this.getBaseHeight() + 3;
   }

   private int hueY() {
      return this.squareY() + this.pickerSize() + 5;
   }

   private int alphaY() {
      return this.hueY() + 6 + 5;
   }

   private int controlsY() {
      int lastSliderBottom = this.setting.opaqueColor ? this.hueY() + 6 : this.alphaY() + 6;
      return lastSliderBottom + 5;
   }

   private void renderAlphaSlider(PoseStack poses, int x, int y, int width, Color color, float alpha) {
      int cell = 4;

      for (int left = 0; left < width; left += cell) {
         for (int top = 0; top < 6; top += cell) {
            int checker = (left / cell + top / cell & 1) == 0 ? -1 : -6710887;
            GuiDrawUtils.fillRect(poses, x + left, y + top, Math.min(x + width, x + left + cell), Math.min(y + 6, y + top + cell), checker);
         }
      }

      int rgb = color.getRGB() & 16777215;
      GuiDrawUtils.fillHorizontalGradient(poses, x, y, width, 6.0F, rgb | 0xFF000000, rgb);
      this.alphaMarkerX.setTarget((width - 1) * (1.0F - alpha));
      float markerX = x + this.alphaMarkerX.updateAndGet();
      Color outline = this.draggingAlpha ? Color.WHITE : this.getAccentColor();
      GuiDrawUtils.fillRect(poses, markerX - 1.0F, y - 1.0F, markerX + 2.0F, y + 6 + 1.0F, outline);
      GuiDrawUtils.fillRect(poses, markerX, y, markerX + 1.0F, y + 6, Color.WHITE);
   }

   private void updateControls(double mouseX, double mouseY) {
      int controlY = this.controlsY();

      for (GuiElement control : this.controls) {
         control.setYOffset(controlY - this.panel.getY());
         control.update(mouseX, mouseY);
         if (!(control instanceof ColorActionButton)) {
            controlY += this.getBaseHeight() + 3;
         }
      }
   }

   @Override
   public void render(PoseStack poses, double mouseX, double mouseY) {
      super.render(poses, mouseX, mouseY);
      if (this.isUnavailable() || !this.expanded) {
         this.saturationMarkerX.setValue(0.0F);
         this.brightnessMarkerY.setValue(0.0F);
         this.hueMarkerX.setValue(0.0F);
         this.alphaMarkerX.setValue(0.0F);
      }

      if (!this.isUnavailable()) {
         this.renderWithTextScroll(
            poses,
            this.setting.getName(),
            () -> FontRenderer.INSTANCE
               .drawWithShadow(
                  poses,
                  this.setting.getName(),
                  this.panel.getX() + 4,
                  this.panel.getY() + this.getTextYOffset() - this.getHoverAnimationValue() + this.yOffset,
                  this.getUiModule().textColorSetting.getValue()
               )
         );
         float previewX = this.getPickerX() + this.panel.getWidth() - 11.0F;
         float previewY = this.getPickerY() + 2.0F;
         if (!this.setting.opaqueColor) {
            this.renderPreviewCheckerboard(poses);
         }

         GuiDrawUtils.fillRect(poses, previewX, previewY, previewX + 8.0F, previewY + 8.0F, this.setting.getValue());
         if (this.expanded) {
            int size = this.pickerSize();
            int squareX = (int)this.getPickerX() + 5;
            int squareY = this.squareY();
            int alpha = this.setting.getValue().getAlpha();
            if (this.draggingSaturationBrightness) {
               ClientScreen.cursor = CursorType.POINTER;
               this.hsb[1] = (float)Mth.clamp((mouseX - squareX) / Math.max(size - 1, 1), 0.0, 1.0);
               this.hsb[2] = 1.0F - (float)Mth.clamp((mouseY - squareY) / Math.max(size - 1, 1), 0.0, 1.0);
            } else if (this.draggingHue) {
               ClientScreen.cursor = CursorType.POINTER;
               this.hsb[0] = (float)Mth.clamp((mouseX - squareX) / Math.max(size - 1, 1), 0.0, 1.0);
            } else if (this.draggingAlpha && !this.setting.opaqueColor) {
               ClientScreen.cursor = CursorType.POINTER;
               alpha = Math.round((1.0F - (float)Mth.clamp((mouseX - squareX) / Math.max(size - 1, 1), 0.0, 1.0)) * 255.0F);
            }

            int hueColor = Color.HSBtoRGB(this.hsb[0], 1.0F, 1.0F);
            GuiDrawUtils.fillRect(poses, squareX, squareY, squareX + size, squareY + size, hueColor);
            GuiDrawUtils.fillGradientRect(poses.last().pose(), squareX, squareY, size, size, -1, hueColor, -16777216, -16777216);
            Matrix4f matrix = poses.last().pose();
            int hueX = squareX;
            int hueY = this.hueY();
            int[] hueColors = new int[]{-65536, -256, -16711936, -16711681, -16776961, -65281, -65536};
            GuiDrawUtils.fillHorizontalGradient(matrix, hueX, hueY, size, 6.0F, hueColors);
            this.hueMarkerX.setTarget(this.hsb[0] * Math.max(size - 1, 0));
            float hueMarker = hueX + this.hueMarkerX.updateAndGet();
            Color hueOutline = this.draggingHue ? Color.WHITE : this.getAccentColor();
            GuiDrawUtils.fillRect(poses, hueMarker - 1.0F, hueY - 1.0F, hueMarker + 2.0F, hueY + 6 + 1.0F, hueOutline);
            GuiDrawUtils.fillRect(poses, hueMarker, hueY, hueMarker + 1.0F, hueY + 6, Color.WHITE);
            Color selectedColor = Color.getHSBColor(this.hsb[0], this.hsb[1], this.hsb[2]);
            if (!this.setting.opaqueColor) {
               this.renderAlphaSlider(poses, hueX, this.alphaY(), size, selectedColor, alpha / 255.0F);
            }

            this.saturationMarkerX.setTarget(this.hsb[1] * Math.max(size - 1, 0));
            this.brightnessMarkerY.setTarget((1.0F - this.hsb[2]) * Math.max(size - 1, 0));
            float markerX = squareX + this.saturationMarkerX.updateAndGet();
            float markerY = squareY + this.brightnessMarkerY.updateAndGet();
            this.markerExpansion.setTarget(this.draggingSaturationBrightness ? 1.0F : 0.0F);
            float markerSize = 3.0F + this.markerExpansion.updateAndGet();
            float luminance = (0.2126F * selectedColor.getRed() + 0.7152F * selectedColor.getGreen() + 0.0722F * selectedColor.getBlue()) / 255.0F;
            this.markerContrast.setTarget(luminance > 0.5F ? 1.0F : 0.0F);
            int markerOutline = this.markerContrast.updateAndGet() > 0.5F ? Color.BLACK.getRGB() : Color.WHITE.getRGB();
            GuiDrawUtils.fillRect(poses, markerX - markerSize, markerY - markerSize, markerX + markerSize + 1.0F, markerY + markerSize + 1.0F, markerOutline);
            GuiDrawUtils.fillRect(
               poses, markerX - markerSize + 1.0F, markerY - markerSize + 1.0F, markerX + markerSize, markerY + markerSize, selectedColor.darker()
            );
            this.updateControls(mouseX, mouseY);
            this.controls.forEach(control -> control.render(poses, mouseX, mouseY));
            Color value = ColorUtils.withAlpha(selectedColor, this.setting.opaqueColor ? 255 : alpha);
            this.setting.setValue(value);
            if (((ColorSetting)this.setting).isRainbow()) {
               Color displayed = this.setting.getValue();
               this.hsb[0] = Color.RGBtoHSB(displayed.getRed(), displayed.getGreen(), displayed.getBlue(), null)[0];
            }
         }
      }
   }

   @Override
   public boolean isMouseOver(double mouseX, double mouseY) {
      return super.isMouseOver(mouseX, mouseY) || this.draggingHue || this.draggingSaturationBrightness || this.draggingAlpha;
   }

   @Override
   public void mouseClicked(double mouseX, double mouseY, int button) {
      if (!this.isUnavailable()) {
         if (button == 1 && this.isHeaderHovered(mouseX, mouseY)) {
            this.expanded = !this.expanded;
         }

         if (!this.expanded) {
            super.mouseClicked(mouseX, mouseY, button);
         } else {
            this.controls.forEach(control -> control.mouseClicked(mouseX, mouseY, button));
            if (button == 0) {
               int left = (int)this.getPickerX() + 5;
               int top = this.squareY();
               int size = this.pickerSize();
               if (inside(mouseX, mouseY, left, top, size, size)) {
                  this.draggingSaturationBrightness = true;
               } else if (inside(mouseX, mouseY, left, this.hueY(), size, 6)) {
                  this.draggingHue = true;
               } else if (!this.setting.opaqueColor && inside(mouseX, mouseY, left, this.alphaY(), size, 6)) {
                  this.draggingAlpha = true;
               }
            }

            super.mouseClicked(mouseX, mouseY, button);
         }
      }
   }

   private static boolean inside(double x, double y, int left, int top, int width, int height) {
      return x >= left && x < left + width && y >= top && y < top + height;
   }

   public boolean isHeaderHovered(double mouseX, double mouseY) {
      return mouseX > this.panel.getX()
         && mouseX < this.panel.getX() + this.panel.getWidth()
         && mouseY > this.panel.getY() + this.yOffset
         && mouseY < this.panel.getY() + this.yOffset + this.getBaseHeight();
   }

   @Override
   public void charTyped(char character) {
      if (!this.isUnavailable()) {
         this.controls.forEach(control -> control.charTyped(character));
         super.charTyped(character);
      }
   }

   @Override
   public void mouseReleased(double mouseX, double mouseY, int button) {
      if (!this.isUnavailable()) {
         this.controls.forEach(control -> control.mouseReleased(mouseX, mouseY, button));
         super.mouseReleased(mouseX, mouseY, button);
         this.draggingSaturationBrightness = false;
         this.draggingHue = false;
         this.draggingAlpha = false;
      }
   }

   private void updateHsbFromColor(Color color) {
      float[] components = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
      System.arraycopy(components, 0, this.hsb, 0, this.hsb.length);
   }

   private static void drawCenteredLabel(PoseStack poses, String text, Panel panel, int yOffset, float textYOffset, float hoverOffset, Color color) {
      FontRenderer.INSTANCE
         .drawWithShadow(
            poses,
            text,
            panel.getX() + panel.getWidth() / 2.0F - FontRenderer.INSTANCE.getWidth(text) / 2.0F,
            panel.getY() + textYOffset - hoverOffset + yOffset,
            color
         );
   }

   private static class CopyControl extends ColorActionButton {
      CopyControl(int yOffset, ColorSettingComponent editor) {
         super(yOffset, editor, "Copy", () -> {
            ClientAccess.argusclientMinecraft.keyboardHandler.setClipboard(ColorUtils.toHex(editor.getSelectedColor(), true));
            editor.copyTimer.reset();
         });
      }

      @Override
      public float getLabelOffsetX() {
         return this.panel.getWidth() * 0.25F - FontRenderer.INSTANCE.getWidth(this.getName()) / 2.0F;
      }

      @Override
      public String getName() {
         return this.editor.copyTimer.hasElapsed(1000L) ? super.getName() : "Copied";
      }

      @Override
      public void render(PoseStack poses, double mouseX, double mouseY) {
         if (!this.editor.isUnavailable()) {
            GuiDrawUtils.fillRect(
               poses,
               this.panel.getX(),
               this.panel.getY() + this.yOffset,
               this.panel.getX() + this.panel.getWidth() / 2.0F,
               this.panel.getY() + this.yOffset + this.getHeight(),
               this.getUiModule().colorSetting.getValue()
            );
            super.render(poses, mouseX, mouseY);
         }
      }
   }

   private static class PasteControl extends ColorActionButton {
      PasteControl(int yOffset, ColorSettingComponent editor) {
         super(yOffset, editor, "Paste", () -> {
            try {
               Color pasted = ColorUtils.parseHexColor(ClientAccess.argusclientMinecraft.keyboardHandler.getClipboard().trim());
               editor.updateHsbFromColor(pasted);
               editor.setting.setValue(pasted);
               editor.pasteTimer.reset();
            } catch (RuntimeException failure) {
               editor.invalidPasteTimer.reset();
            }
         });
      }

      @Override
      public float getLabelOffsetX() {
         return this.panel.getWidth() * 0.75F - FontRenderer.INSTANCE.getWidth(this.getName()) / 2.0F;
      }

      @Override
      public String getName() {
         if (!this.editor.invalidPasteTimer.hasElapsed(1000L)) {
            return "Invalid";
         } else {
            return this.editor.pasteTimer.hasElapsed(1000L) ? super.getName() : "Pasted";
         }
      }

      @Override
      public void render(PoseStack poses, double mouseX, double mouseY) {
         if (!this.editor.isUnavailable()) {
            GuiDrawUtils.fillRect(
               poses,
               this.panel.getX() + this.panel.getWidth() / 2.0F,
               this.panel.getY() + this.yOffset,
               this.panel.getX() + this.panel.getWidth(),
               this.panel.getY() + this.yOffset + this.getHeight(),
               this.getUiModule().colorSetting.getValue()
            );
            super.render(poses, mouseX, mouseY);
         }
      }
   }

   private static class RainbowControl extends BooleanSettingComponent {
      RainbowControl(ColorSettingComponent editor) {
         super(editor.panel, editor, new BooleanSetting("", ((ColorSetting)editor.setting).isRainbow()));
         this.setting.setChangeCallback(() -> ((ColorSetting)editor.setting).setRainbow(this.setting.getValue()));
         this.addFlags(ComponentFlag.PADDING_SHIFT);
      }

      @Override
      public void render(PoseStack poses, double mouseX, double mouseY) {
         super.render(poses, mouseX, mouseY);
         ColorSettingComponent.drawCenteredLabel(
            poses, "Rainbow", this.panel, this.yOffset, this.getTextYOffset(), this.getHoverAnimationValue(), this.getUiModule().textColorSetting.getValue()
         );
      }
   }

   private static class SyncControl extends BooleanSettingComponent {
      SyncControl(ColorSettingComponent editor) {
         super(editor.panel, editor, new BooleanSetting("", ((ColorSetting)editor.setting).isSynced()));
         this.setting.setChangeCallback(() -> ((ColorSetting)editor.setting).setSynced(this.setting.getValue()));
         this.addFlags(ComponentFlag.PADDING_SHIFT);
      }

      @Override
      public void init() {
         ColorSettingComponent editor = (ColorSettingComponent)this.parentCloseState;
         this.setting.setValue(((ColorSetting)editor.getSetting()).isSynced());
      }

      @Override
      public void render(PoseStack poses, double mouseX, double mouseY) {
         super.render(poses, mouseX, mouseY);
         ColorSettingComponent.drawCenteredLabel(
            poses, "Sync", this.panel, this.yOffset, this.getTextYOffset(), this.getHoverAnimationValue(), this.getUiModule().textColorSetting.getValue()
         );
      }
   }
}
