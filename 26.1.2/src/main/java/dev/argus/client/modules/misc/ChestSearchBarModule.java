package dev.argus.client.modules.misc;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.events.AbstractContainerScreenEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.render.RenderUtils;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.KeyUtils;
import dev.argus.client.utils.inventory.ChestSearchController;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

public class ChestSearchBarModule extends Module {
   public final Setting<Boolean> highlightFullSetting;
   private final Setting<Boolean> chestStealerSetting;
   private final Setting<Boolean> onlyFullSetting;

   public boolean match(ItemStack stack, boolean requireFull) {
      if (ChestSearchController.searchBox == null) {
         return true;
      } else {
         EditBox searchBox = ChestSearchController.searchBox;
         String query = searchBox.getValue();
         if (query.isEmpty()) {
            return true;
         } else {
            return requireFull && !ChestSearchController.isFull(stack, query) ? false : ChestSearchController.namesMatch(stack, query);
         }
      }
   }

   public boolean shouldSkip(ItemStack stack) {
      if (!this.isToggled()) {
         return false;
      }

      Setting<Boolean> chestStealerSettingSnapshot = this.chestStealerSetting;
      Boolean filterChestStealer = chestStealerSettingSnapshot.getValue();
      if (!filterChestStealer) {
         return false;
      }

      Setting<Boolean> onlyFullSettingSnapshot = this.onlyFullSetting;
      boolean requireFullSnapshot = onlyFullSettingSnapshot.getValue();
      return !this.match(stack, requireFullSnapshot);
   }

   @EventHandler
   private void onRender(AbstractContainerScreenEvent event) {
      ChestSearchController searchControllerSnapshot = ChestSearchController.INSTANCE;
      GuiGraphicsExtractor graphicsSnapshot = event.getGraphics();
      Screen screenSnapshot = event.getScreen();
      PoseStack poseSnapshot = event.getPoseStack();
      float mouseXSnapshot = event.getMouseX();
      float mouseYSnapshot = event.getMouseY();
      float renderValueSnapshot = RenderUtils.getPartialTick();
      searchControllerSnapshot.onDrawGui(graphicsSnapshot, screenSnapshot, poseSnapshot, mouseXSnapshot, mouseYSnapshot, renderValueSnapshot);
      if (ChestSearchController.searchBox != null) {
         if (KeyUtils.isPhysicalKeyDown(70) && KeyUtils.isControlDown()) {
            Screen focusScreen = ClientAccess.argusclientMinecraft.screen;
            EditBox focusTarget = ChestSearchController.searchBox;
            focusScreen.setFocused(focusTarget);
         }

         if (KeyUtils.isPhysicalKeyDown(257)) {
            Screen focusScreen = ClientAccess.argusclientMinecraft.screen;
            focusScreen.setFocused(null);
         }
      }
   }

   public ChestSearchBarModule() {
      super("ChestSearchBar", "Highlights items in chests and shulker boxes.", ModuleCategory.MISC);
      Boolean highlightFullDefault = false;
      this.highlightFullSetting = this.add(new BooleanSetting("HighlightFull", highlightFullDefault));
      Boolean chestStealerDefault = false;
      BooleanSetting chestStealerOption = new BooleanSetting("ChestStealer", chestStealerDefault);
      chestStealerOption.markVisibilityDependency();
      this.chestStealerSetting = this.add(chestStealerOption);
      Boolean onlyFullDefault = false;
      BooleanSetting onlyFullOption = new BooleanSetting("OnlyFull", onlyFullDefault);
      this.onlyFullSetting = this.add(onlyFullOption.dependsOn(this.chestStealerSetting));
      ChestSearchController searchController = ChestSearchController.INSTANCE;
      searchController.init();
      this.setDrawn(false);
   }
}
