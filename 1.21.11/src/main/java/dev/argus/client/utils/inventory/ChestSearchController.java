package dev.argus.client.utils.inventory;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.misc.ChestSearchBarModule;
import dev.argus.client.render.ColorUtils;
import dev.argus.mixin.AbstractContainerScreenAccessor;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.awt.Color;
import java.util.Locale;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AfterInit;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public class ChestSearchController {
   private static final int SLOT_SIZE = 16;
   private static final int DIM_COLOR = -1442840576;
   private static final int BACKGROUND_COLOR = 1258291200;
   private static final int BORDER_COLOR = 1895825408;
   private static final int NO_MATCH_TEXT_COLOR = -43691;
   private static final boolean replaymodLoaded = FabricLoader.getInstance().isModLoaded("replaymod");
   public static final ChestSearchController INSTANCE = new ChestSearchController();
   private static ChestSearchBarModule chestSearchBarModule = ClientContext.getModule(ChestSearchBarModule.class);
   public static EditBox searchBox;
   public static String text = "";
   public static int matchedSlotCount = 0;

   public void init() {
      searchBox = null;
      text = "";
      ScreenEvents.AFTER_INIT.register(new ChestSearchController.SearchScreenInitializer());
   }

   public static boolean isFull(ItemStack stack, String query) {
      if (!chestSearchBarModule.highlightFullSetting.getValue()) {
         return false;
      }

      query = normalize(query);
      if (query != null && !query.isEmpty() && !stack.isEmpty()) {
         if (!stack.getItem().getDescriptionId().contains("shulker_box")) {
            return false;
         }

         ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
         if (contents != null && contents.stream().toList().size() == 27) {
            for (ItemStack containedStack : contents.stream().toList()) {
               if (!namesMatch(containedStack, query)) {
                  return false;
               }
            }

            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean namesMatch(ItemStack stack, String query) {
      query = normalize(query);
      if (query == null || query.isEmpty()) {
         return true;
      }

      if (stack.isEmpty()) {
         return false;
      }

      Item item = stack.getItem();
      if (matchesContainerContents(stack, query)) {
         return true;
      }

      if (matchesBundleContents(stack, query)) {
         return true;
      }

      String hoverName = normalize(stack.getHoverName().getString());
      BiPredicate<String, String> predicate = String::contains;
      if (query.length() >= 3 && query.startsWith("\"") && query.endsWith("\"")) {
         query = query.substring(1, query.length() - 1);
         predicate = String::equals;
      }

      if (query.length() >= 3 && query.startsWith("/") && query.endsWith("/")) {
         query = query.substring(1, query.length() - 1);
         predicate = (candidate, expression) -> Pattern.compile(expression).matcher(candidate).find();
      }

      ItemEnchantments storedEnchantments = stack.get(DataComponents.STORED_ENCHANTMENTS);
      if (storedEnchantments != null) {
         for (Entry<Holder<Enchantment>> entry : storedEnchantments.entrySet()) {
            if (matchesEnchantment(predicate, query, entry)) {
               return true;
            }
         }
      }

      if (stack.isEnchanted()) {
         for (Entry<Holder<Enchantment>> entry : stack.getEnchantments().entrySet()) {
            if (matchesEnchantment(predicate, query, entry)) {
               return true;
            }
         }
      }

      if (predicate.test(I18n.get(item.getDescriptionId()).toLowerCase(Locale.ROOT), query)) {
         return true;
      }

      PotionContents potionContents = stack.get(DataComponents.POTION_CONTENTS);
      if (potionContents != null) {
         for (MobEffectInstance effect : potionContents.getAllEffects()) {
            if (predicate.test(I18n.get(effect.getDescriptionId()).toLowerCase(Locale.ROOT), query)) {
               return true;
            }
         }
      }

      return predicate.test(hoverName, query);
   }

   private static String normalize(String value) {
      return ChatFormatting.stripFormatting(value.trim().toLowerCase(Locale.ROOT));
   }

   private static boolean matchesContainerContents(ItemStack stack, String query) {
      ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
      if (contents != null) {
         for (ItemStack containedStack : contents.nonEmptyItems()) {
            if (namesMatch(containedStack, query)) {
               return true;
            }
         }
      }

      return false;
   }

   private static boolean matchesBundleContents(ItemStack stack, String query) {
      BundleContents contents = stack.get(DataComponents.BUNDLE_CONTENTS);
      if (contents != null) {
         for (ItemStack containedStack : contents.itemsCopy()) {
            if (namesMatch(containedStack, query)) {
               return true;
            }
         }
      }

      return false;
   }

   private static boolean matchesEnchantment(BiPredicate<String, String> predicate, String query, Entry<Holder<Enchantment>> entry) {
      if (entry == null) {
         return false;
      }

      MutableComponent name = (MutableComponent)Enchantment.getFullname((Holder<Enchantment>)entry.getKey(), entry.getIntValue());
      String searchableName = name.toString().toLowerCase(Locale.ROOT).replaceAll("enchantment", "");
      return predicate.test(searchableName, query);
   }

   public void onDrawGui(GuiGraphics graphics, Screen screen, PoseStack poseStack, float mouseX, float mouseY, float partialTick) {
      if (chestSearchBarModule.isToggled() && searchBox != null) {
         poseStack.pushPose();
         graphics.nextStratum();
         AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor)screen;
         if (!searchBox.getValue().isBlank() && screen instanceof AbstractContainerScreen containerScreen) {
            AbstractContainerMenu menu = containerScreen.getMenu();
            int containerX = accessor.getX();
            int containerY = accessor.getY();
            matchedSlotCount = 0;

            for (Slot slot : menu.slots) {
               int slotX = containerX + slot.x;
               int slotY = containerY + slot.y;
               ItemStack stack = slot.getItem();
               if (!namesMatch(stack, searchBox.getValue())) {
                  graphics.fill(slotX, slotY, slotX + 16, slotY + 16, -1442840576);
               } else {
                  matchedSlotCount++;
               }

               if (isFull(stack, searchBox.getValue())) {
                  int color = ColorUtils.toArgb(Color.green, 100);
                  graphics.fill(slotX, slotY, slotX + 16, slotY + 16, color);
               }
            }
         }

         if (matchedSlotCount == 0 && !searchBox.getValue().isEmpty()) {
            searchBox.setTextColor(-43691);
         } else {
            searchBox.setTextColor(-1);
         }

         graphics.fill(searchBox.getX() + 2, searchBox.getY(), searchBox.getRight(), searchBox.getBottom(), 1258291200);
         graphics.hLine(searchBox.getX() + 2, searchBox.getRight(), searchBox.getBottom(), -1);
         graphics.vLine(searchBox.getRight(), searchBox.getY(), searchBox.getBottom(), -1);
         graphics.nextStratum();
         graphics.hLine(searchBox.getX() + 2, searchBox.getRight(), searchBox.getY(), 1895825408);
         graphics.vLine(searchBox.getX() + 2, searchBox.getY(), searchBox.getBottom() + 1, 1895825408);
         searchBox.render(graphics, (int)mouseX, (int)mouseY, partialTick);
         poseStack.popPose();
      }
   }

   public static void bindReferencedModules(ModuleRegistry registry) {
      chestSearchBarModule = (ChestSearchBarModule)registry.getModule(ChestSearchBarModule.class);
   }

   public static class SearchScreenInitializer implements AfterInit {
      private static final String REPLAYMOD_CONTAINER_PACKAGE = "com.replaymod.lib.de.johni0702.minecraft.gui.container";

      public void afterInit(Minecraft minecraft, Screen screen, int scaledWidth, int scaledHeight) {
         if (!ChestSearchController.chestSearchBarModule.isToggled()) {
            ChestSearchController.searchBox = null;
         } else if (!ChestSearchController.replaymodLoaded
            || !screen.getClass().getPackageName().startsWith("com.replaymod.lib.de.johni0702.minecraft.gui.container")) {
            if (!(screen instanceof ContainerScreen) && !(screen instanceof ShulkerBoxScreen) && !(screen instanceof HopperScreen)) {
               ChestSearchController.searchBox = null;
            } else {
               Minecraft client = Minecraft.getInstance();
               AbstractContainerScreen containerScreen = (AbstractContainerScreen)screen;
               AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor)containerScreen;
               Font font = client.font;
               int boxX = accessor.getX() + 77;
               int boxY = accessor.getY() + 4;
               ChestSearchController.searchBox = new EditBox(font, boxX, boxY, 91, 10, Component.literal(""));
               ChestSearchController.searchBox.setValue(ChestSearchController.text);
               ChestSearchController.searchBox.setFocused(false);
               ChestSearchController.searchBox.setMaxLength(32);
               ChestSearchController.searchBox.setBordered(true);
               ChestSearchController.searchBox.setResponder(newText -> ChestSearchController.text = newText);
               Screens.getButtons(screen).add(ChestSearchController.searchBox);
            }
         }
      }
   }
}
