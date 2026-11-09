package dev.argus.client.gui.screens;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.modules.render.ViewModelModule;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.render.GuiScaleUtils;
import dev.argus.client.render.RenderUtils;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.KeyUtils;
import dev.argus.client.utils.math.MathUtils;
import java.awt.Color;
import java.util.LinkedList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;

public final class ViewModelEditorScreen extends ClientScreen {
   private static final Color OVERLAY_COLOR = new Color(255, 255, 255, 100);
   private final ViewModelModule viewmodel;
   private final LinkedList<ViewModelSnapshot> undoHistory = new LinkedList<>();
   private final ElapsedTimer scrollSnapshotTimer = new ElapsedTimer();
   private ViewModelEditMode editMode;
   private ViewModelSnapshot dragStartSnapshot;
   private double dragStartMouseX;
   private double dragStartMouseY;
   private boolean editingOffhand;

   public ViewModelEditorScreen(ViewModelModule module) {
      this.viewmodel = module;
      this.dragStartSnapshot = ViewModelSnapshot.capture(module);
      this.undoHistory.add(this.dragStartSnapshot);
   }

   @Override
   public void renderContents(GuiGraphics graphics, PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
      ViewModelSnapshot snapshot = this.dragStartSnapshot;
      if (this.getEditMode() != ViewModelEditMode.NONE) {
         float horizontalDelta = (float)MathUtils.floorToDecimals((this.dragStartMouseX - mouseX) * 0.005F, 2) * (this.editingOffhand ? -1 : 1);
         float verticalDelta = (float)MathUtils.floorToDecimals((this.dragStartMouseY - mouseY) * 0.005F, 2);
         if (KeyUtils.isShiftDown()) {
            if (Math.abs(horizontalDelta) > Math.abs(verticalDelta)) {
               verticalDelta = 0.0F;
            } else {
               horizontalDelta = 0.0F;
            }
         }

         if (this.getEditMode() == ViewModelEditMode.TRANSLATE) {
            if (KeyUtils.isControlDown()) {
               if (this.editingOffhand) {
                  this.viewmodel.offXSetting.setValue(snapshot.offX() + horizontalDelta);
                  this.viewmodel.offYSetting.setValue(snapshot.offY() + verticalDelta);
               } else {
                  this.viewmodel.mainXSetting.setValue(snapshot.mainX() + horizontalDelta);
                  this.viewmodel.mainYSetting.setValue(snapshot.mainY() + verticalDelta);
               }
            } else {
               this.viewmodel.offXSetting.setValue(snapshot.offX() + horizontalDelta);
               this.viewmodel.offYSetting.setValue(snapshot.offY() + verticalDelta);
               this.viewmodel.mainXSetting.setValue(snapshot.mainX() + horizontalDelta);
               this.viewmodel.mainYSetting.setValue(snapshot.mainY() + verticalDelta);
            }
         } else if (this.getEditMode() == ViewModelEditMode.SCALE) {
            if (KeyUtils.isShiftDown()) {
               horizontalDelta = Math.abs(Math.max(horizontalDelta, verticalDelta)) * Math.signum(horizontalDelta);
               verticalDelta = Math.abs(Math.max(horizontalDelta, verticalDelta)) * Math.signum(verticalDelta);
            }

            horizontalDelta = -horizontalDelta;
            if (KeyUtils.isControlDown()) {
               if (this.editingOffhand) {
                  this.viewmodel.offScaleXSetting.setValue(snapshot.offScaleX() + horizontalDelta);
                  this.viewmodel.offScaleYSetting.setValue(snapshot.offScaleY() + verticalDelta);
               } else {
                  this.viewmodel.mainScaleXSetting.setValue(snapshot.mainScaleX() + horizontalDelta);
                  this.viewmodel.mainScaleYSetting.setValue(snapshot.mainScaleY() + verticalDelta);
               }
            } else {
               this.viewmodel.offScaleXSetting.setValue(snapshot.offScaleX() + horizontalDelta);
               this.viewmodel.offScaleYSetting.setValue(snapshot.offScaleY() + verticalDelta);
               this.viewmodel.mainScaleXSetting.setValue(snapshot.mainScaleX() + horizontalDelta);
               this.viewmodel.mainScaleYSetting.setValue(snapshot.mainScaleY() + verticalDelta);
            }
         } else if (this.getEditMode() == ViewModelEditMode.ROTATE) {
            horizontalDelta /= 0.005F;
            verticalDelta /= 0.005F;
            if (KeyUtils.isControlDown()) {
               if (this.editingOffhand) {
                  this.viewmodel.offRotateXSetting.setValue(MathUtils.wrapAngle(snapshot.offRotateX() + verticalDelta));
                  this.viewmodel.offRotateYSetting.setValue(MathUtils.wrapAngle(snapshot.offRotateY() + horizontalDelta));
               } else {
                  this.viewmodel.mainRotateXSetting.setValue(MathUtils.wrapAngle(snapshot.mainRotateX() + verticalDelta));
                  this.viewmodel.mainRotateYSetting.setValue(MathUtils.wrapAngle(snapshot.mainRotateY() + horizontalDelta));
               }
            } else {
               this.viewmodel.offRotateXSetting.setValue(MathUtils.wrapAngle(snapshot.offRotateX() + verticalDelta));
               this.viewmodel.offRotateYSetting.setValue(MathUtils.wrapAngle(snapshot.offRotateY() + horizontalDelta));
               this.viewmodel.mainRotateXSetting.setValue(MathUtils.wrapAngle(snapshot.mainRotateX() + verticalDelta));
               this.viewmodel.mainRotateYSetting.setValue(MathUtils.wrapAngle(snapshot.mainRotateY() + horizontalDelta));
            }
         }
      }
   }

   @Override
   public void onMouseClick(double mouseX, double mouseY, int button) {
      this.setEditMode(this.editModeForButton(button));
      if (this.getEditMode() != ViewModelEditMode.NONE) {
         this.dragStartSnapshot = ViewModelSnapshot.capture(this.viewmodel);
         this.undoHistory.add(this.dragStartSnapshot);
         this.editingOffhand = mouseX * GuiScaleUtils.calculateScaleMultiplier(this.guiScale)
            < ClientAccess.argusclientMinecraft.getWindow().getGuiScaledWidth() / 2.0F;
         this.dragStartMouseX = mouseX;
         this.dragStartMouseY = mouseY;
      }
   }

   @Override
   public void onMouseRelease(double mouseX, double mouseY, int button) {
      this.setEditMode(ViewModelEditMode.NONE);
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalScroll, double verticalScroll) {
      if (verticalScroll > -1.0 && verticalScroll < 1.0) {
         return super.mouseScrolled(mouseX, mouseY, horizontalScroll, verticalScroll);
      }

      boolean overOffhand = mouseX * GuiScaleUtils.calculateScaleMultiplier(this.guiScale)
         < ClientAccess.argusclientMinecraft.getWindow().getGuiScaledWidth() / 2.0F;
      float depthDelta = (float)(verticalScroll * -0.1F);
      if (this.scrollSnapshotTimer.hasElapsed(100L)) {
         this.undoHistory.add(ViewModelSnapshot.capture(this.viewmodel));
      }

      this.scrollSnapshotTimer.reset();
      if (KeyUtils.isShiftDown()) {
         float offRotateZ = MathUtils.wrapAngle(this.viewmodel.offRotateZSetting.getValue() + (float)verticalScroll * 10.0F);
         float mainRotateZ = MathUtils.wrapAngle(this.viewmodel.mainRotateZSetting.getValue() + (float)verticalScroll * 10.0F);
         if (KeyUtils.isControlDown()) {
            if (overOffhand) {
               this.viewmodel.offRotateZSetting.setValue(offRotateZ);
            } else {
               this.viewmodel.mainRotateZSetting.setValue(mainRotateZ);
            }
         } else {
            this.viewmodel.offRotateZSetting.setValue(offRotateZ);
            this.viewmodel.mainRotateZSetting.setValue(mainRotateZ);
         }

         return super.mouseScrolled(mouseX, mouseY, horizontalScroll, verticalScroll);
      } else if (KeyUtils.isAltDown()) {
         this.adjustDepthSettings(this.viewmodel.mainScaleZSetting, this.viewmodel.offScaleZSetting, overOffhand, depthDelta);
         return super.mouseScrolled(mouseX, mouseY, horizontalScroll, verticalScroll);
      } else {
         this.adjustDepthSettings(this.viewmodel.mainZSetting, this.viewmodel.offZSetting, overOffhand, depthDelta);
         return super.mouseScrolled(mouseX, mouseY, horizontalScroll, verticalScroll);
      }
   }

   @Override
   public boolean keyPressed(KeyEvent event) {
      if (KeyUtils.isControlDown() && event.key() == 90 && !this.undoHistory.isEmpty()) {
         this.undoHistory.getLast().apply(this.viewmodel);
         if (this.undoHistory.size() != 1) {
            this.undoHistory.removeLast();
         }
      }

      if (event.key() == 261) {
         this.dragStartSnapshot = ViewModelSnapshot.createDefaults();
         this.dragStartSnapshot.apply(this.viewmodel);
         this.undoHistory.add(this.dragStartSnapshot);
      }

      return super.keyPressed(event);
   }

   @Override
   public void renderBackgroundOverlay(PoseStack poseStack) {
      float screenWidth = ClientAccess.argusclientMinecraft.getWindow().getGuiScaledWidth();
      float screenHeight = ClientAccess.argusclientMinecraft.getWindow().getScreenHeight();
      RenderUtils.INSTANCE.fillRect(poseStack, screenWidth / 2.0F - 1.0F, 0.0F, screenWidth / 2.0F + 1.0F, 48.0F, OVERLAY_COLOR);
      List instructions = List.of(
         "Left Click - Rotate",
         "Right Click - Move",
         "Middle Click - Scale",
         "Mouse Scroll - Move by Z",
         "Mouse Scroll + Shift - Rotate by Z",
         "Mouse Scroll + Alt - Scale by Z",
         "Hold Ctrl - Modify one",
         "Hold Shift - Lock axis",
         "Ctrl + Z - Undo",
         "Delete - Reset to defaults"
      );
      int textY = 2;

      for (Object instructionObject : instructions) {
         String instruction = (String)instructionObject;
         float textWidth = FontRenderer.INSTANCE.getWidth(instruction);
         FontRenderer.INSTANCE.drawWithShadow(poseStack, instruction, (screenWidth - textWidth) / 2.0F, 50 + textY, ColorUtils.withAlpha(Color.white, 180));
         textY += FontRenderer.INSTANCE.getHeight() + 1;
      }

      RenderUtils.INSTANCE.fillRect(poseStack, screenWidth / 2.0F - 1.0F, textY + 52, screenWidth / 2.0F + 1.0F, screenHeight, OVERLAY_COLOR);
   }

   public ViewModelEditMode getEditMode() {
      return this.editMode;
   }

   private ViewModelEditMode editModeForButton(int button) {
      return switch (button) {
         case 0 -> ViewModelEditMode.ROTATE;
         case 1 -> ViewModelEditMode.TRANSLATE;
         case 2 -> ViewModelEditMode.SCALE;
         default -> ViewModelEditMode.NONE;
      };
   }

   private void adjustDepthSettings(Setting<Float> mainSetting, Setting<Float> offSetting, boolean overOffhand, float delta) {
      if (KeyUtils.isControlDown()) {
         if (overOffhand) {
            offSetting.setValue(offSetting.getValue() + delta);
         } else {
            mainSetting.setValue(mainSetting.getValue() + delta);
         }
      } else {
         mainSetting.setValue(mainSetting.getValue() + delta);
         offSetting.setValue(offSetting.getValue() + delta);
      }
   }

   public void setEditMode(ViewModelEditMode mode) {
      this.editMode = mode;
   }
}
