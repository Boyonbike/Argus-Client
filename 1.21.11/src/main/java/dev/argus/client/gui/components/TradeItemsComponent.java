package dev.argus.client.gui.components;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.gui.screens.ClientScreen;
import dev.argus.client.gui.screens.TradeEditorScreen;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.render.GuiDrawUtils;
import dev.argus.client.utils.ClampedInterpolatedFloat;
import dev.argus.client.utils.inventory.EnchantmentUtils;
import dev.argus.client.utils.inventory.TradeDefinition;
import dev.argus.client.utils.inventory.TradeEnchantmentEntry;
import dev.argus.client.utils.math.FloatBounds;
import java.awt.Color;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class TradeItemsComponent extends AbstractComponent {
   private final ClampedInterpolatedFloat fromHoverAnimation = new ClampedInterpolatedFloat(() -> 4.0F * this.getUiModule().getAnimationSpeed(), false);
   private final ClampedInterpolatedFloat toHoverAnimation = new ClampedInterpolatedFloat(() -> 4.0F * this.getUiModule().getAnimationSpeed(), false);
   private final TradeEditorScreen screen;
   private boolean selectorOpen;
   private ItemStack fromStack;
   private ItemStack toStack;

   public TradeItemsComponent(TradeEditorPanel editor, TradeDefinition trade) {
      super(editor);
      this.screen = editor.getScreen();
      this.fromStack = trade.from().getDefaultInstance();
      this.toStack = trade.to().getDefaultInstance();
      this.applyConfiguredEnchantments(this.fromStack.is(Items.EMERALD) ? this.toStack : this.fromStack, trade);
   }

   @Override
   public void render(PoseStack poses, double mouseX, double mouseY) {
      super.render(poses, mouseX, mouseY);
      GuiDrawUtils.flush();
      poses.pushPose();
      poses.translate(this.panel.getX() + 1, this.panel.getY() + this.yOffset, 0.0F);
      GuiDrawUtils.drawItem(poses, this.fromStack, 1.0F, -Math.round(this.fromHoverAnimation.getValue()), 0.0F);
      GuiDrawUtils.drawItem(poses, this.toStack, this.panel.getWidth() - 4 - 16, -Math.round(this.toHoverAnimation.getValue()), 0.0F);
      float arrowWidth = FontRenderer.INSTANCE.getWidth(">");
      FontRenderer.INSTANCE.drawWithoutShadow(poses, ">", (this.panel.getWidth() - arrowWidth) / 2.0F, 4.0F, Color.white);
      FloatBounds fromHoverBounds = FloatBounds.fromPositionAndSize(this.panel.getX() + 2, this.panel.getY() + this.yOffset, 16.0F, 16.0F);
      if (fromHoverBounds.contains(mouseX, mouseY)) {
         this.screen.setTooltipStack(this.fromStack);
         this.fromHoverAnimation.setTarget(true);
      } else {
         this.fromHoverAnimation.setTarget(false);
      }

      FloatBounds toHoverBounds = FloatBounds.fromPositionAndSize(
         this.panel.getX() + this.panel.getWidth() - 2 - 16, this.panel.getY() + this.yOffset, 16.0F, 16.0F
      );
      if (toHoverBounds.contains(mouseX, mouseY)) {
         this.screen.setTooltipStack(this.toStack);
         this.toHoverAnimation.setTarget(true);
      } else {
         this.toHoverAnimation.setTarget(false);
      }

      this.fromHoverAnimation.updateAndGet();
      this.toHoverAnimation.updateAndGet();
      this.drawHoverGradient(poses, 0.0F, 0.0F, 32.0F, this.getBaseHeight() - 0.5F, true);
      poses.translate(this.panel.getWidth() - 34, 0.0F, 0.0F);
      this.drawHoverGradient(poses, 0.0F, 0.0F, 32.0F, this.getBaseHeight() - 0.5F, false);
      poses.popPose();
   }

   @Override
   public void mouseClicked(double mouseX, double mouseY, int button) {
      super.mouseClicked(mouseX, mouseY, button);
      if (this.isMouseOver(mouseX, mouseY) && (button == 0 || button == 1)) {
         if (this.selectorOpen) {
            this.selectorOpen = false;
         } else {
            int relativeMouseX = (int)(mouseX - this.panel.getX());
            TradeSelectorPanel<?> selector;
            if (relativeMouseX < this.panel.getWidth() / 2) {
               selector = this.createSelector(button, true);
               selector.setX(this.panel.getX() - selector.getWidth() - 4);
               selector.setY(this.panel.getY());
            } else {
               selector = this.createSelector(button, false);
               selector.setX(this.panel.getX() + this.panel.getWidth() + 4);
               selector.setY(this.panel.getY());
            }

            this.selectorOpen = true;
            ((ClientScreen)ClientAccess.argusclientMinecraft.screen).getPanels().add(selector);
         }
      }
   }

   private void drawHoverGradient(PoseStack poses, float x, float y, float width, float height, boolean fromSide) {
      ClampedInterpolatedFloat hoverAnimation = fromSide ? this.fromHoverAnimation : this.toHoverAnimation;
      Color color = this.getUiModule().colorSetting.getValue();
      int transparentColor = ColorUtils.toArgb(color, 0);
      int hoverColor = ColorUtils.toArgb(color, hoverAnimation.getValue() * 0.2F);
      if (fromSide) {
         GuiDrawUtils.fillHorizontalGradient(poses, x, y, width, height, hoverColor, transparentColor);
      } else {
         GuiDrawUtils.fillHorizontalGradient(poses, x, y, width, height, transparentColor, hoverColor);
      }
   }

   private TradeSelectorPanel<?> createSelector(int button, boolean fromSide) {
      ItemStack stack = this.toStack;
      if (fromSide) {
         stack = this.fromStack;
      }

      return button != 0 && !stack.is(Items.EMERALD) ? new TradeEnchantmentSelectorPanel(this, fromSide) : new TradeItemSelectorPanel(this, fromSide);
   }

   private void applyConfiguredEnchantments(ItemStack stack, TradeDefinition trade) {
      for (Object entry : trade.enchants()) {
         TradeEnchantmentEntry enchantment = (TradeEnchantmentEntry)entry;
         stack.enchant(EnchantmentUtils.getHolder(enchantment.getName()), enchantment.getLevel());
      }
   }

   @Override
   public int getBaseHeight() {
      return 16;
   }

   public ItemStack getFromStack() {
      return this.fromStack;
   }

   public ItemStack getToStack() {
      return this.toStack;
   }

   public void setFromStack(ItemStack stack) {
      this.fromStack = stack;
   }

   public void setToStack(ItemStack stack) {
      this.toStack = stack;
   }

   public void closeSelector() {
      this.selectorOpen = false;
   }

   public boolean isSelectorOpen() {
      return this.selectorOpen;
   }
}
