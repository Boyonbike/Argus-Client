package dev.argus.client.gui.registry;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.gui.ScissorStack;
import dev.argus.client.gui.components.ButtonComponent;
import dev.argus.client.gui.components.ComponentFlag;
import dev.argus.client.gui.panels.Panel;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.render.GuiDrawUtils;
import dev.argus.client.utils.ClampedInterpolatedFloat;
import dev.argus.client.utils.FloatAnimation;
import java.util.Locale;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

class RegistryEntryComponent<E> extends ButtonComponent {
   public final ClampedInterpolatedFloat visibilityAnimation = new ClampedInterpolatedFloat(() -> this.getUiModule().animSpeedSetting.getValue() * 2.0F, true);
   protected final FloatAnimation hoverScrollAnimation = new FloatAnimation();
   private final RegistrySetScreen<E> screen;
   private final E entry;
   private final Item iconItem;
   private final ItemStack iconStack;
   private final boolean availableEntry;
   private boolean resetVisibilityAnimation = true;
   private boolean visible;

   public RegistryEntryComponent(RegistrySetScreen<E> screen, Panel panel, Identifier identifier, E entry, boolean availableEntry) {
      super(panel, createLabel(identifier));
      this.screen = screen;
      this.entry = entry;
      this.availableEntry = availableEntry;
      this.iconStack = createIconStack(identifier, entry);
      this.iconItem = this.iconStack.getItem();
      this.addFlags(ComponentFlag.SILENT_HOVER);
   }

   @Override
   public void update(double mouseX, double mouseY) {
      this.visible = this.screen.registrySetting.getValue().contains(this.entry);
      if (this.availableEntry) {
         this.visible = !this.visible;
         String searchText = this.screen.searchComponent.getText();
         if (!searchText.isEmpty() && !this.getLabel().toLowerCase().contains(searchText.toLowerCase(Locale.ROOT))) {
            this.visible = false;
            this.resetVisibilityAnimation = true;
            this.visibilityAnimation.setValue(false);
            return;
         }
      }

      if (this.resetVisibilityAnimation) {
         this.visibilityAnimation.setValue(this.visible);
         this.resetVisibilityAnimation = false;
      } else {
         this.visibilityAnimation.setTarget(this.visible);
         this.visibilityAnimation.updateAndGet();
      }
   }

   @Override
   public void mouseClicked(double mouseX, double mouseY, int button) {
      super.mouseClicked(mouseX, mouseY, button);
      if (this.isMouseOver(mouseX, mouseY) && button == 0) {
         if (this.availableEntry) {
            this.screen.registrySetting.addEntry(this.entry);
         } else {
            this.screen.registrySetting.removeEntry(this.entry);
         }
      }
   }

   @Override
   public void render(PoseStack poseStack, double mouseX, double mouseY) {
      if (this.visible) {
         float iconOffset = 0.0F;
         if (this.iconItem != null && this.iconItem != Items.AIR) {
            GuiDrawUtils.drawItem(
               poseStack, this.iconStack, this.panel.getX() + 3, (int)(this.panel.getY() + this.yOffset + this.getTextYOffset() - 2.0F), 0.0F
            );
            iconOffset = 20.0F;
         }

         poseStack.pushPose();
         poseStack.translate(iconOffset, 1.5F, 0.0F);
         boolean hovered = this.isMouseOver(mouseX, mouseY);
         this.hoverScrollAnimation.setTarget(hovered, 500L);
         this.renderClippedLabel(poseStack, iconOffset + 3.0F, mouseX, mouseY);
         poseStack.popPose();
      }
   }

   @Override
   public int getHeight() {
      return Math.max((int)(16.0F * this.visibilityAnimation.getValue()), this.visible ? 1 : 0);
   }

   private void renderClippedLabel(PoseStack poseStack, float textOffset, double mouseX, double mouseY) {
      float textWidth = FontRenderer.INSTANCE.getWidth(this.getLabel()) + textOffset;
      float scrollProgress = this.hoverScrollAnimation.getValue();
      int rightInset = scrollProgress > 0.0F ? 0 : 2;
      if (textWidth >= this.panel.getWidth() && scrollProgress > 0.0F) {
         GuiDrawUtils.flush();
         ScissorStack.pushIntersection(
            (int)(this.panel.getX() + textOffset), this.panel.getY() + this.yOffset, (int)(this.panel.getWidth() - textOffset - rightInset), this.getHeight()
         );
         poseStack.translate(-(textWidth - this.panel.getWidth()) * scrollProgress, 0.0F, 0.0F);
         super.render(poseStack, mouseX, mouseY);
         GuiDrawUtils.flush();
         ScissorStack.pop();
      } else {
         super.render(poseStack, mouseX, mouseY);
      }
   }

   private static ItemStack createIconStack(Identifier identifier, Object entry) {
      if (entry instanceof EntityType entityType) {
         if (entityType == EntityTypes.ITEM) {
            return Items.STICK.getDefaultInstance();
         }

         var spawnEgg = SpawnEggItem.byId(entityType);
         if (spawnEgg.isPresent()) {
            return spawnEgg.get().value().getDefaultInstance();
         }
      }

      if (entry instanceof Potion potion) {
         Holder potionHolder = BuiltInRegistries.POTION.wrapAsHolder(potion);
         return PotionContents.createItemStack(Items.SPLASH_POTION, potionHolder);
      } else {
         return BuiltInRegistries.ITEM.getValue(identifier).getDefaultInstance();
      }
   }

   private static String createLabel(Identifier identifier) {
      return FontRenderer.capitalizeWords(identifier.toShortLanguageKey().replace("_", " "));
   }
}
