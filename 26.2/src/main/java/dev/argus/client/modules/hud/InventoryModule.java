package dev.argus.client.modules.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.modules.HudWidgetModule;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.render.GuiDrawUtils;
import dev.argus.client.render.PostProcessShaderPass;
import dev.argus.client.render.RenderTargetUtils;
import dev.argus.client.render.RenderUtils;
import dev.argus.client.render.ResizableHudWidgetRenderer;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.RegistrySetSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.ListFilterMode;
import dev.argus.client.utils.FloatAnimation;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public class InventoryModule extends HudWidgetModule {
   private final Setting<ListFilterMode> selectionSetting = this.add(new EnumSetting<>("Selection", ListFilterMode.ANY));
   private final Setting<Set<Item>> itemsSetting = this.add(new RegistrySetSetting<>("Items", BuiltInRegistries.ITEM));
   private final Setting<Boolean> hideEmptySetting = this.add(new BooleanSetting("HideEmpty", true));
   private final Setting<Integer> rowsSetting = this.add(new NumberSetting<>("Rows", 9, 1, 9));
   private final Setting<Color> backgroundSetting = this.add(new ColorSetting("Background", new Color(10, 10, 10, 50)));
   private final Setting<Color> outlineSetting = this.add(new ColorSetting("Outline", new Color(10, 10, 10, 100)));
   private boolean lastStackWasBlockItem;
   private final FloatAnimation visibilityAnimation = new FloatAnimation();

   private Collection<ItemStack> getDisplayedStacks() {
      Setting<ListFilterMode> selectionOption = this.selectionSetting;
      Collection<ItemStack> stacks;
      if (selectionOption.getValue() == ListFilterMode.ANY) {
         stacks = this.getSlotStacks();
      } else {
         stacks = this.getSelectedItemTotals();
      }

      return stacks;
   }

   private Collection<ItemStack> getSlotStacks() {
      ArrayList stacks = new ArrayList();
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      Inventory inventory = player.getInventory();
      NonNullList inventorySlots = inventory.getNonEquipmentItems();
      int index = 0;

      while (true) {
         int slotCount = inventorySlots.size();
         if (index >= slotCount - 9) {
            return stacks;
         }

         int slotIndex = index + 9;
         stacks.add((ItemStack)inventorySlots.get(slotIndex));
         index++;
      }
   }

   private Collection<ItemStack> getSelectedItemTotals() {
      HashMap<Item, Integer> itemCounts = new HashMap();
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;

      for (ItemStack stack : player.getInventory()) {
         Item item = stack.getItem();
         if (!stack.isEmpty()) {
            Setting<ListFilterMode> selectionOption = this.selectionSetting;
            ListFilterMode selectionMode = selectionOption.getValue();
            Setting<Set<Item>> itemsOption = this.itemsSetting;
            if (ListFilterMode.matches(selectionMode, item, itemsOption)) {
               byte zeroCount = 0;
               Integer previousCount = itemCounts.getOrDefault(item, Integer.valueOf(zeroCount));
               int previousCountValue = previousCount;
               int totalCount = previousCountValue + stack.getCount();
               itemCounts.put(item, totalCount);
            }
         }
      }

      return itemCounts.entrySet()
         .stream()
         .map(entry -> new ItemStack((ItemLike)entry.getKey(), (Integer)entry.getValue()))
         .sorted(Comparator.comparing(stackx -> {
            Item itemx = stackx.getItem();
            return itemx.getDescriptionId();
         }))
         .toList();
   }

   private String formatStackCount(ItemStack stack) {
      int thresholdCount = stack.getCount();
      if (thresholdCount > 9999) {
         return ">10k";
      } else {
         thresholdCount = stack.getCount();
         if (thresholdCount > 999) {
            Object[] formatArguments = new Object[1];
            float countAsFloat = stack.getCount();
            float countInThousands = countAsFloat / 1000.0F;
            formatArguments[0] = countInThousands;
            String countFormat = "%.1fk";
            return countFormat.formatted(formatArguments);
         } else {
            int count = stack.getCount();
            return String.valueOf(count);
         }
      }
   }

   private boolean isEmpty() {
      for (ItemStack stack : this.getDisplayedStacks()) {
         if (!stack.isEmpty()) {
            Setting<ListFilterMode> selectionOption = this.selectionSetting;
            ListFilterMode selectionMode = selectionOption.getValue();
            Item item = stack.getItem();
            Setting<Set<Item>> itemsOption = this.itemsSetting;
            if (ListFilterMode.matches(selectionMode, item, itemsOption)) {
               return false;
            }
         }
      }

      return true;
   }

   @Override
   public float[] calculateDimensions() {
      Setting<ListFilterMode> sizeSelectionOption = this.selectionSetting;
      float selectedSlotSize;
      if (sizeSelectionOption.getValue() == ListFilterMode.ANY) {
         selectedSlotSize = 18.0F;
      } else {
         selectedSlotSize = 20.0F;
      }

      float slotSize = selectedSlotSize;
      Setting<ListFilterMode> layoutSelectionOption = this.selectionSetting;
      if (layoutSelectionOption.getValue() != ListFilterMode.ANY) {
         Setting<Integer> rowsOption = this.rowsSetting;
         Integer configuredColumns = rowsOption.getValue();
         int maximumColumns = configuredColumns;
         int stackCount = this.getDisplayedStacks().size();
         byte minimumColumns = 1;
         float columnCount = Mth.clamp(stackCount, minimumColumns, maximumColumns);
         double fractionalRows = (float)stackCount / maximumColumns;
         double roundedRows = Math.ceil(fractionalRows);
         float rowCount = (float)Math.max(roundedRows, 1.0);
         int dimensionCount = 2;
         float[] selectedDimensions = new float[dimensionCount];
         selectedDimensions[0] = slotSize * columnCount;
         selectedDimensions[1] = slotSize * rowCount;
         return selectedDimensions;
      } else {
         int dimensionCount = 2;
         float[] slotDimensions = new float[dimensionCount];
         slotDimensions[0] = slotSize * 9.0F;
         slotDimensions[1] = slotSize * 3.0F;
         return slotDimensions;
      }
   }

   @Override
   public void renderContent(PoseStack poses) {
      Setting<ListFilterMode> strideSelectionOption = this.selectionSetting;
      int slotStride;
      if (strideSelectionOption.getValue() == ListFilterMode.ANY) {
         slotStride = 18;
      } else {
         slotStride = 20;
      }

      Setting<ListFilterMode> paddingSelectionOption = this.selectionSetting;
      float selectedPadding;
      if (paddingSelectionOption.getValue() == ListFilterMode.ANY) {
         selectedPadding = 0.5F;
      } else {
         selectedPadding = 1.5F;
      }

      float contentPadding = selectedPadding;
      Setting<Integer> rowsOption = this.rowsSetting;
      Integer configuredColumns = rowsOption.getValue();
      int columns = configuredColumns;
      Setting<ListFilterMode> layoutSelectionOption = this.selectionSetting;
      if (layoutSelectionOption.getValue() == ListFilterMode.ANY) {
         columns = 9;
      }

      FloatAnimation animationForTarget = this.visibilityAnimation;
      boolean hasItems = !this.isEmpty();
      animationForTarget.setTarget(hasItems, 200L);
      FloatAnimation animationForOpacity = this.visibilityAnimation;
      float opacity = animationForOpacity.getValue();
      Setting<Boolean> hideEmptyOption = this.hideEmptySetting;
      Boolean hideEmpty = hideEmptyOption.getValue();
      if (!hideEmpty) {
         opacity = 1.0F;
      }

      Setting<Color> backgroundOption = this.backgroundSetting;
      Color background = backgroundOption.getValue();
      Setting<Color> backgroundAlphaOption = this.backgroundSetting;
      Color backgroundForAlpha = backgroundAlphaOption.getValue();
      float backgroundAlpha = backgroundForAlpha.getAlpha();
      Color backgroundColor = ColorUtils.withAlpha(background, backgroundAlpha / 255.0F * opacity);
      Setting<Color> outlineOption = this.outlineSetting;
      Color outline = outlineOption.getValue();
      Setting<Color> outlineAlphaOption = this.outlineSetting;
      Color outlineForAlpha = outlineAlphaOption.getValue();
      float outlineAlpha = outlineForAlpha.getAlpha();
      Color outlineColor = ColorUtils.withAlpha(outline, outlineAlpha / 255.0F * opacity);
      float[] dimensions = this.calculateDimensions();
      GuiDrawUtils.fillRect(poses, 0.0F, 0.0F, dimensions[0], dimensions[1], backgroundColor);
      float outlineX = -1.0F;
      GuiDrawUtils.drawRectOutline(poses, outlineX, -1.0F, dimensions[0], dimensions[1], outlineColor);
      GuiDrawUtils.flush();
      RenderUtils.clearColor(PostProcessShaderPass.POST_TARGET, Color.black);
      RenderUtils.clearDepth(PostProcessShaderPass.POST_TARGET);
      RenderTargetUtils.overrideRenderTargets(PostProcessShaderPass.POST_TARGET);
      poses.pushPose();
      float contentDepth = 0.0F;
      poses.translate(contentPadding, contentPadding, contentDepth);
      int displayIndex = 0;

      for (ItemStack stack : this.getDisplayedStacks()) {
         int x = displayIndex % columns * slotStride;
         int y = displayIndex / columns * slotStride;
         String countText = this.formatStackCount(stack);
         this.renderStack(poses, stack, countText, x, y);
         displayIndex++;
      }

      poses.popPose();
      RenderUtils.INSTANCE.flushEntities();
      RenderTargetUtils.restoreRenderTargets();
      RenderUtils.clearDepth(PostProcessShaderPass.POST_TARGET);
      RenderUtils.blitToMain(PostProcessShaderPass.POST_TARGET);
   }

   private void renderStack(PoseStack poses, ItemStack stack, String countText, int x, int y) {
      boolean blockItem = stack.getItem() instanceof BlockItem;
      if (blockItem != this.lastStackWasBlockItem) {
         RenderUtils.INSTANCE.flushEntities();
      }

      this.lastStackWasBlockItem = blockItem;
      poses.pushPose();
      GuiDrawUtils.drawItem(poses, stack, x, y, 0.0F, false);
      Setting<ListFilterMode> selectionOption = this.selectionSetting;
      if (selectionOption.getValue() == ListFilterMode.ANY) {
         GuiDrawUtils.drawDurabilityBar(poses, stack, x, y);
      }

      if (stack.getCount() > 1) {
         FontRenderer textRenderer = FontRenderer.INSTANCE;
         float countX = x + 17 - FontRenderer.INSTANCE.getWidth(countText);
         textRenderer.drawWithShadow(poses, countText, countX, y + 9, Color.white);
      }

      GuiDrawUtils.drawItemCooldown(poses, stack, x, y);
      poses.popPose();
   }

   public InventoryModule() {
      super("Inventory");
      Setting<?> settingForVisibility = this.itemsSetting;
      Predicate showForFilteredSelection = setting -> {
         Setting<ListFilterMode> selectionForVisibility = this.selectionSetting;
         return selectionForVisibility.getValue() != ListFilterMode.ANY;
      };
      settingForVisibility.addVisibilityPredicate(showForFilteredSelection);
      settingForVisibility = this.rowsSetting;
      showForFilteredSelection = setting -> {
         Setting<ListFilterMode> selectionForVisibility = this.selectionSetting;
         return selectionForVisibility.getValue() != ListFilterMode.ANY;
      };
      settingForVisibility.addVisibilityPredicate(showForFilteredSelection);
      ResizableHudWidgetRenderer widget = new ResizableHudWidgetRenderer(this);
      widget.enableResizing(this);
      this.setRenderer(widget);
   }
}
