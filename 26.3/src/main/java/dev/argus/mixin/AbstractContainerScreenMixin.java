package dev.argus.mixin;

import dev.argus.client.utils.InputButtons;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.access.TooltipComponentCapture;
import dev.argus.client.events.AbstractContainerScreenEvent;
import dev.argus.client.events.AbstractContainerScreenRenderTooltipEvent;
import dev.argus.client.modules.misc.ChestSearchBarModule;
import dev.argus.client.modules.player.InventoryTweaksModule;
import dev.argus.client.modules.render.TooltipsModule;
import dev.argus.client.utils.KeyUtils;
import dev.argus.client.utils.inventory.ChestSearchController;
import dev.argus.client.utils.inventory.ContainerTooltipComponent;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin implements ClientAccess {
   private static final ChestSearchBarModule argusclient$chestSearchBar = ClientContext.getModule(ChestSearchBarModule.class);
   private static final TooltipsModule argusclient$tooltips = ClientContext.getModule(TooltipsModule.class);
   private static final InventoryTweaksModule argusclient$inventoryTweaks = ClientContext.getModule(InventoryTweaksModule.class);
   @Shadow
   @Nullable
   protected Slot hoveredSlot;
   @Shadow
   private Slot getHoveredSlot(double x, double y) { throw new AssertionError(); }
   @Unique
   private int argusclient$previewButton = -1;
   @Unique
   private int argusclient$mouseX;
   @Unique
   private int argusclient$mouseY;
   @Unique
   private int argusclient$tooltipX;
   @Unique
   private int argusclient$tooltipY;
   @Unique
   private Slot argusclient$cachedHoveredSlot;
   @Unique
   private MapItemSavedData argusclient$slotMapData;
   @Unique
   private int argusclient$slotMapId;
   @Unique
   private final AbstractContainerScreenEvent argusclient$containerScreenEvent = new AbstractContainerScreenEvent((AbstractContainerScreen)(Object)this);

   @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
   public void argusclient$preventInventoryCloseDuringSearch(KeyEvent keyEvent, CallbackInfoReturnable<Boolean> callback) {
      if (argusclient$chestSearchBar != null
         && argusclient$chestSearchBar.isToggled()
         && ChestSearchController.searchBox != null
         && ChestSearchController.searchBox.isFocused()
         && ClientAccess.argusclientMinecraft.options.keyInventory.matches(keyEvent)) {
         callback.setReturnValue(true);
      }
   }

   @Inject(method = "tick", at = @At("HEAD"))
   private void argusclient$runContainerTickCallback(CallbackInfo callback) {
      this.argusclient$containerScreenEvent.runTickCallback();
   }

   @Inject(method = "extractTooltip", at = @At("HEAD"))
   private void argusclient$postContainerScreenEvent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo callback) {
      this.argusclient$containerScreenEvent.setGraphics(graphics);
      this.argusclient$containerScreenEvent.setMouseX(mouseX);
      this.argusclient$containerScreenEvent.setMouseY(mouseY);
      ClientAccess.eventBus.post(this.argusclient$containerScreenEvent);
   }

   @Inject(method = "extractContents", at = @At("TAIL"))
   private void argusclient$captureHoveredSlotAndMouse(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo callback) {
      if (this.argusclient$cachedHoveredSlot == null
         || !argusclient$tooltips.shouldPinTooltip(this.argusclient$cachedHoveredSlot.getItem())) {
         this.argusclient$cachedHoveredSlot = this.hoveredSlot;
         this.argusclient$tooltipX = mouseX;
         this.argusclient$tooltipY = mouseY;
      }

      this.argusclient$mouseX = mouseX;
      this.argusclient$mouseY = mouseY;
   }

   @WrapMethod(method = "extractTooltip")
   private void argusclient$pinTooltipSlot(GuiGraphicsExtractor graphics, int mouseX, int mouseY, Operation<Void> original) {
      Slot hovered = this.hoveredSlot;
      if (this.argusclient$cachedHoveredSlot != null && argusclient$tooltips.shouldPinTooltip(this.argusclient$cachedHoveredSlot.getItem())) {
         this.hoveredSlot = this.argusclient$cachedHoveredSlot;
      }

      try {
         original.call(graphics, mouseX, mouseY);
      } finally {
         this.hoveredSlot = hovered;
      }
   }

   @Inject(method = "getHoveredSlot", at = @At("HEAD"), cancellable = true)
   private void argusclient$useInspectedHoveredSlot(double mouseX, double mouseY, CallbackInfoReturnable<Slot> callback) {
      if (this.argusclient$cachedHoveredSlot != null && argusclient$tooltips.shouldInspectContents(this.argusclient$cachedHoveredSlot.getItem())) {
         callback.setReturnValue(this.argusclient$cachedHoveredSlot);
      }
   }

   @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
   private void argusclient$consumeShulkerPreviewClick(MouseButtonEvent event, boolean doubled, CallbackInfoReturnable<Boolean> callback) {
      Slot hovered = this.getHoveredSlot(event.x(), event.y());
      if (hovered != null && ((AbstractContainerScreen<?>)(Object)this).getMenu().getCarried().isEmpty()
         && argusclient$tooltips.isShulkerPreviewClick(hovered.getItem(), InputButtons.glfw(event))) {
         this.argusclient$previewButton = InputButtons.glfw(event);
         callback.setReturnValue(true);
      }
   }

   @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
   private void argusclient$consumeShulkerPreviewRelease(MouseButtonEvent event, CallbackInfoReturnable<Boolean> callback) {
      if (InputButtons.glfw(event) == this.argusclient$previewButton) {
         this.argusclient$previewButton = -1;
         callback.setReturnValue(true);
      }
   }

   @ModifyExpressionValue(
      method = "mouseClicked",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;hasClickedOutside(DDII)Z")
   )
   public boolean argusclient$keepInspectedClickInside(boolean originalInside) {
      return (this.argusclient$cachedHoveredSlot == null || !argusclient$tooltips.shouldInspectContents(this.argusclient$cachedHoveredSlot.getItem()))
         && originalInside;
   }

   @ModifyArgs(
      method = "mouseClicked",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;slotClicked(Lnet/minecraft/world/inventory/Slot;ILnet/minecraft/client/input/MouseButtonEvent;Lnet/minecraft/world/inventory/ContainerInput;)V",
         ordinal = 1
      )
   )
   private void argusclient$useBundleInspectButton(Args arguments) {
      net.minecraft.client.input.MouseButtonEvent original = (net.minecraft.client.input.MouseButtonEvent)arguments.get(2);
      int mouseButton = original.button();
      if (this.argusclient$cachedHoveredSlot != null
         && argusclient$tooltips.shouldInspectContents(this.argusclient$cachedHoveredSlot.getItem())
         && mouseButton == 1
         && this.argusclient$cachedHoveredSlot.getItem().is(ItemTags.BUNDLES)) {
         arguments.set(2, argusclient$withButton(original, 2));
      }
   }

   @ModifyArgs(
      method = "mouseReleased",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;slotClicked(Lnet/minecraft/world/inventory/Slot;ILnet/minecraft/client/input/MouseButtonEvent;Lnet/minecraft/world/inventory/ContainerInput;)V",
         ordinal = 3 // last call: releasing a click while carrying an item (PICKUP / QUICK_MOVE)
      )
   )
   private void argusclient$swapBundleReleaseButton(Args arguments) {
      Slot slot = (Slot)arguments.get(0);
      net.minecraft.client.input.MouseButtonEvent original = (net.minecraft.client.input.MouseButtonEvent)arguments.get(2);
      int mouseButton = original.button();
      if (argusclient$inventoryTweaks.isToggled() && argusclient$inventoryTweaks.shouldSwapBundleClickButton(slot, mouseButton == 1 ? 0 : mouseButton == 0 ? 1 : mouseButton)) {
         arguments.set(2, argusclient$withButton(original, mouseButton == 1 ? 0 : 1));
      }
   }

   @WrapWithCondition(
      method = "extractTooltip",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/resources/Identifier;Z)V"
      )
   )
   private boolean argusclient$renderTooltipWithEvent(
      GuiGraphicsExtractor graphics, Font font, List text, Optional image, int tooltipX, int tooltipY, Identifier background, boolean elideText
   ) {
      if (this.argusclient$cachedHoveredSlot != null && argusclient$tooltips.shouldPinTooltip(this.argusclient$cachedHoveredSlot.getItem())) {
         tooltipX = this.argusclient$tooltipX;
         tooltipY = this.argusclient$tooltipY;
      } else {
         this.argusclient$tooltipX = tooltipX;
         this.argusclient$tooltipY = tooltipY;
      }

      List components = TooltipComponentCapture.captureComponents(graphics, text, image);
      AbstractContainerScreenRenderTooltipEvent event = new AbstractContainerScreenRenderTooltipEvent(
         (Screen)(Object)this, components, tooltipX, tooltipY, this.argusclient$mouseX, this.argusclient$mouseY
      );
      ClientAccess.eventBus.post(event);
      if (!event.isCancelled()) {
         ((GuiGraphicsAccessor)graphics).drawTooltipsHook(font, event.getTooltipComponents(), tooltipX, tooltipY, DefaultTooltipPositioner.INSTANCE, null, false);
      }

      return false;
   }

   @Inject(method = "extractSlot", at = @At("HEAD"))
   private void argusclient$captureSlotMapData(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo callback) {
      this.argusclient$slotMapData = null;
      ItemStack stack = slot.getItem();
      if (stack.is(Items.FILLED_MAP)) {
         int mapId = stack.getOrDefault(DataComponents.MAP_ID, new MapId(-1)).id();
         if (mapId != -1) {
            this.argusclient$slotMapId = mapId;
            this.argusclient$slotMapData = ClientContext.mapDataCache.getMapData(stack, mapId);
         }
      }
   }

   @WrapWithCondition(
      method = "extractSlot",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;item(Lnet/minecraft/world/item/ItemStack;III)V")
   )
   private boolean argusclient$shouldRenderSlotItem(GuiGraphicsExtractor graphics, ItemStack stack, int itemX, int itemY, int seed) {
      return !argusclient$tooltips.isToggled() || !argusclient$tooltips.mapOverlaySetting.getValue() || this.argusclient$slotMapData == null;
   }

   @WrapWithCondition(
      method = "extractSlot",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V"
      )
   )
   private boolean argusclient$renderMapOverlay(
      GuiGraphicsExtractor graphics, Font font, ItemStack stack, int itemX, int itemY, String countText, @Local(argsOnly = true) Slot slot
   ) {
      if (argusclient$tooltips.isToggled() && argusclient$tooltips.mapOverlaySetting.getValue() && this.argusclient$slotMapData != null) {
         ContainerTooltipComponent.renderMapOverlay(graphics, this.argusclient$slotMapData, this.argusclient$slotMapId, itemX, itemY);
         if (!KeyUtils.isPhysicalKeyDown(342)) {
            graphics.itemDecorations(font, stack, itemX, itemY);
         }

         return false;
      } else {
         return true;
      }
   }

   @Unique
   private static net.minecraft.client.input.MouseButtonEvent argusclient$withButton(
      net.minecraft.client.input.MouseButtonEvent event, int button
   ) {
      return new net.minecraft.client.input.MouseButtonEvent(
         event.x(), event.y(), new net.minecraft.client.input.MouseButtonInfo(button, event.modifiers()));
   }
}
