package dev.argus.client.gui.components;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.config.ModulePresetCodec;
import dev.argus.client.gui.CloseState;
import dev.argus.client.gui.GuiElement;
import dev.argus.client.gui.ScissorStack;
import dev.argus.client.gui.panels.Panel;
import dev.argus.client.modules.HudWidgetModule;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.PermanentModule;
import dev.argus.client.modules.client.FontsModule;
import dev.argus.client.modules.client.UIModule;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.render.GuiDrawUtils;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.IdentifierSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.RegistrySetSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.StringSetting;
import dev.argus.client.utils.ClampedInterpolatedFloat;
import dev.argus.client.utils.FloatAnimation;
import dev.argus.client.utils.InterpolatedFloat;
import dev.argus.client.utils.KeyUtils;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

public class ModuleComponent extends AbstractComponent implements CloseState {
   public final ArrayList<GuiElement> children = new ArrayList<>();
   public final InterpolatedFloat heightAnimation = new InterpolatedFloat(() -> this.getUiModule().animSpeedSetting.getValue() * 2.0F, true);
   private final ClampedInterpolatedFloat enabledAnimation = new ClampedInterpolatedFloat(() -> 2.0F * UIModule.INSTANCE.animSpeedSetting.getValue(), true);
   private final FloatAnimation searchHighlightAnimation = new FloatAnimation();
   protected final Module module;
   private int searchHighlightCycle = 0;
   private boolean copyShortcutHovered;
   private boolean requestedOpen;
   public float animatedHeight;

   @Override
   public void setYOffset(int var1) {
      this.yOffset = var1;
      int var2 = this.yOffset + this.getBaseHeight();
      if (this.requestedOpen) {
         for (GuiElement var4 : this.children) {
            var4.setYOffset(var2);
            var2 += var4.getHeight();
         }
      }
   }

   @Override
   public int getHeight() {
      return (int)this.animatedHeight;
   }

   public void setOpen(boolean var1) {
      this.requestedOpen = var1;
   }

   public void startSearchHighlight() {
      this.searchHighlightAnimation.setValue(0.0F);
      this.searchHighlightAnimation.setTarget(1.0F, 150L);
      this.searchHighlightCycle = 1;
   }

   public Module getModule() {
      return this.module;
   }

   @Override
   public void keyPressed(int var1) {
      this.handleCopyShortcut(var1);
      this.children.forEach(var1x -> var1x.keyPressed(var1));
   }

   public void toggleOpen() {
      this.setOpen(!this.requestedOpen);
      if (this.requestedOpen && !ClientContext.getClickGui().openModuleComponents.contains(this)) {
         ClientContext.getClickGui().openModuleComponents.add(this);
      }

      this.panel.updateLayout();
   }

   private int calculateTargetHeight() {
      int var1 = this.getBaseHeight();
      if (this.requestedOpen) {
         for (GuiElement var3 : this.children) {
            var1 += var3.getHeight();
         }
      }

      return var1;
   }

   private void handleCopyShortcut(int var1) {
      if (this.copyShortcutHovered && KeyUtils.isControlDown() && var1 == 67) {
         ModulePresetCodec var2 = ClientContext.configurationManager.getModulePresetCodec();
         String var3 = var2.encode(this.module);
         ClientAccess.argusclientMinecraft.keyboardHandler.setClipboard(var3);
      }
   }

   public ModuleComponent(Module var1, Panel var2, int var3) {
      super(var2, var3);
      this.module = var1;
      int var4 = this.yOffset + this.getBaseHeight();

      for (Object var6 : var1.getSettings()) {
         Setting var7 = (Setting)var6;
         SettingComponent var8 = this.createSettingComponent(var2, var7);
         if (var8 != null) {
            var8.setYOffset(var4);
            var8.addFlags(ComponentFlag.PADDING_SHIFT);
            this.children.add(var8);
         }

         var4 += 11;
      }

      if (!(var1 instanceof HudWidgetModule) && !(var1 instanceof PermanentModule)) {
         this.children.add(new KeybindComponent(var4, this));
      }

      this.heightAnimation.setValue(this.calculateTargetHeight());
   }

   @Override
   public boolean equals(Object var1) {
      if (this == var1) {
         return true;
      } else if (var1 != null && this.getClass() == var1.getClass()) {
         ModuleComponent var2 = (ModuleComponent)var1;
         return Objects.equals(this.module, var2.module);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hashCode(this.module);
   }

   @Override
   public void init() {
      for (GuiElement var2 : this.children) {
         var2.init();
      }
   }

   @Override
   public boolean isClosed() {
      return !(this.animatedHeight > this.getBaseHeight() + 1) && !this.requestedOpen;
   }

   private void renderButtonBackground(PoseStack var1, float var2) {
      if (this.getUiModule().elementGradientSetting.getValue()) {
         Color var3 = this.getUiModule().buttonSetting.getValue();
         var3 = ColorUtils.withAlpha(var3, var3.getAlpha() / 255.0F * 0.1F);
         GuiDrawUtils.fillVerticalGradient(
            var1,
            this.panel.getX() + 1,
            this.panel.getY() + this.yOffset + 0.5F,
            (this.panel.getWidth() - 2) * var2,
            this.getBaseHeight() - 0.5F,
            var3.hashCode(),
            ColorUtils.toArgb(var3, 0)
         );
      } else {
         float var5 = this.panel.getX() + this.panel.getWidth() - 1.0F;
         GuiDrawUtils.fillRect(
            var1,
            Math.min(this.panel.getX() + 1 + this.panel.getWidth() * var2, var5),
            this.panel.getY() + this.yOffset + 0.5F,
            var5,
            this.panel.getY() + this.yOffset + this.getBaseHeight() - 0.5F,
            this.getUiModule().buttonSetting.getValue()
         );
      }
   }

   @Nullable
   private SettingComponent<?> createSettingComponent(Panel var1, Setting<?> var2) {
      return switch (var2) {
         case RegistrySetSetting var5 -> new RegistrySetSettingComponent(var1, this, var5);
         case BooleanSetting var6 -> new BooleanSettingComponent(var1, this, var6);
         case NumberSetting var7 -> new NumericSettingComponent(var1, this, var7);
         case ColorSetting var8 -> new ColorSettingComponent(var1, this, var8);
         case StringSetting var9 -> new StringSettingComponent(var1, this, var9);
         case EnumSetting var10 -> new EnumSettingComponent(var1, this, var10);
         case IdentifierSetting var11 -> new SoundSettingComponent(var1, this, var11);
         default -> null;
      };
   }

   @Override
   public void render(PoseStack var1, double var2, double var4) {
      super.render(var1, var2, var4);
      this.enabledAnimation.setTarget(this.module.isToggled());
      if (this.module instanceof UIModule) {
         this.enabledAnimation.setValue(true);
      }

      boolean var6 = this.getUiModule().elementGradientSetting.getValue();
      float var7 = this.enabledAnimation.updateAndGet();
      float var8 = this.getHoverAnimationValue();
      int var9 = this.getBaseHeight();
      if (this.module.isToggled() || var7 > 0.01) {
         GuiDrawUtils.fillRect(
            var1,
            this.panel.getX() + 1.0F,
            this.panel.getY() + this.yOffset + 0.5F,
            this.panel.getX() + (this.panel.getWidth() - 1.0F) * var7,
            this.panel.getY() + this.yOffset + var9 - 0.5F,
            this.getUiModule().enabledSetting.getValue()
         );
      }

      boolean var10 = this.getUiModule().styleSetting.getValue() == UIModule.StyleChoice.METEOR;
      if (var10 && var7 > 0.01F) {
         GuiDrawUtils.fillRect(
            var1,
            this.panel.getX() + 1.0F,
            this.panel.getY() + this.yOffset + 0.5F,
            this.panel.getX() + 3.0F,
            this.panel.getY() + this.yOffset + var9 - 0.5F,
            ColorUtils.withAlpha(this.getUiModule().enabledColorSetting.getValue(), var7)
         );
      }

      if (var6) {
         this.renderButtonBackground(var1, 1.0F);
      } else if (var7 < 1.0F) {
         this.renderButtonBackground(var1, var7);
      }

      if (this.searchHighlightCycle > 0) {
         float var11 = this.searchHighlightAnimation.getValue();
         GuiDrawUtils.fillRect(
            var1,
            this.panel.getX() + 1.0F,
            this.panel.getY() + this.yOffset + 0.5F,
            this.panel.getX() + this.panel.getWidth() - 1.0F,
            this.panel.getY() + this.yOffset + var9 - 0.5F,
            ColorUtils.packArgb(1.0F, 0.0F, 0.0F, var11)
         );
         if (var11 == 1.0F || var11 == 0.0F) {
            this.searchHighlightAnimation.setTarget((var11 + 1.0F) % 2.0F, 150L);
         }

         if (var11 == 0.0F) {
            this.searchHighlightCycle = (this.searchHighlightCycle + 1) % 4;
         }
      }

      FontRenderer.INSTANCE
         .drawWithShadow(
            var1,
            this.module.getName(),
            this.panel.getX() + (var10 ? 7 : 3),
            this.panel.getY() + this.getTextYOffset() - var8 + this.yOffset,
            this.module.isToggled() ? this.getUiModule().enabledColorSetting.getValue() : this.getUiModule().textColorSetting.getValue()
         );
      if (this.getUiModule().gearSetting.getValue() && !this.children.isEmpty()) {
         String var14 = this.requestedOpen ? "-" : "+";
         FontRenderer.INSTANCE
            .drawWithShadow(
               var1,
               var14,
               this.panel.getX() + this.panel.getWidth() - 4 - FontRenderer.INSTANCE.getWidth(var14),
               this.panel.getY() + this.getTextYOffset() + this.yOffset,
               this.getUiModule().textColorSetting.getValue()
            );
      }

      MutableComponent var15 = Component.empty();
      if (this.module.isWip()) {
         var15.append("beta ");
      }

      if (this.getUiModule().bindsSetting.getValue() && this.module.getKeybind().code() != -1) {
         String var12 = this.module.getKeybind().getDisplayName();
         var15.append("[").append(var12.toUpperCase(Locale.ROOT)).append("]");
      }

      if (!var15.getSiblings().isEmpty()) {
         float var16 = FontsModule.INSTANCE.isToggled() ? 0.65F : 0.5F;
         float var13 = FontsModule.INSTANCE.isToggled() ? 0.0F : 1.0F;
         FontRenderer.INSTANCE
            .drawWithShadow(
               var1,
               var15,
               this.panel.getX() + FontRenderer.INSTANCE.getWidth(this.module.getName()) + (var10 ? 7.0F : 3.0F),
               this.panel.getY() + this.getTextYOffset() - var8 + this.yOffset + var13,
               var16,
               this.getUiModule().textColorSetting.getValue()
            );
      }

      var1.pushPose();
      if (this.animatedHeight > var9 || this.requestedOpen) {
         GuiDrawUtils.flush();
         ScissorStack.pushIntersection(this.panel.getX(), this.panel.getY() + this.yOffset + var9, this.panel.getWidth() - 1, (int)this.animatedHeight - var9);

         for (GuiElement var18 : this.children) {
            var18.render(var1, var2, var4);
         }

         GuiDrawUtils.flush();
         ScissorStack.pop();
      }

      var1.popPose();
      if (this.getUiModule().descriptionsSetting.getValue() && this.isMouseOver(var2, var4)) {
         ClientContext.getClickGui().setTooltipText(this.module.getDescription());
      }
   }

   @Override
   public boolean isOpen() {
      return this.requestedOpen;
   }

   @Override
   public void mouseClicked(double var1, double var3, int var5) {
      super.mouseClicked(var1, var3, var5);
      if (this.isMouseOver(var1, var3) && !ClientContext.getClickGui().getSearchBox().getValue().isEmpty()) {
         this.startSearchHighlight();
      }

      if (this.isMouseOver(var1, var3) && var5 == 0 && this.isLeftClickEligible()) {
         this.module.toggle();
      }

      if (this.isMouseOver(var1, var3) && var5 == 1) {
         this.toggleOpen();
      }

      this.children.forEach(var5x -> var5x.mouseClicked(var1, var3, var5));
   }

   @Override
   public boolean isMouseOver(double var1, double var3) {
      return var1 > this.panel.getX()
         && var1 < this.panel.getX() + this.panel.getWidth()
         && var3 > this.clampYToPanel(this.panel.getY() + this.yOffset)
         && var3 < this.clampYToPanel(this.panel.getY() + this.getBaseHeight() + this.yOffset);
   }

   @Override
   public void update(double var1, double var3) {
      if (FontRenderer.INSTANCE.isFontLoading()) {
         this.heightAnimation.setValue(this.calculateTargetHeight());
      } else {
         this.heightAnimation.setTarget(this.calculateTargetHeight());
      }

      this.animatedHeight = this.heightAnimation.updateAndGet();
      this.copyShortcutHovered = super.isMouseOver(var1, var3);
      if (this.requestedOpen) {
         this.children.forEach(var4 -> var4.update(var1, var3));
      }
   }

   @Override
   public void charTyped(char var1) {
      this.children.forEach(var1x -> var1x.charTyped(var1));
   }

   @Override
   public void mouseScrolled(double var1, double var3, double var5) {
      for (GuiElement var8 : this.children) {
         var8.mouseScrolled(var1, var3, var5);
      }
   }

   @Override
   public void mouseReleased(double var1, double var3, int var5) {
      this.children.forEach(var5x -> var5x.mouseReleased(var1, var3, var5));
   }
}
