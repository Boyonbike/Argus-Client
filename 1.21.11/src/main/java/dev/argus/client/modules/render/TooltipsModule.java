package dev.argus.client.modules.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult.Success;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.AbstractContainerScreenRenderTooltipEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.MinecraftDisconnectEvent;
import dev.argus.client.events.MinecraftRunTickEvent;
import dev.argus.client.events.MultiPlayerGameModeUseItemOnEvent;
import dev.argus.client.events.ScreenChangeEvent;
import dev.argus.client.events.SlotSetEvent;
import dev.argus.client.gui.screens.BookPreviewScreen;
import dev.argus.client.gui.screens.ShulkerPreviewScreen;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.KeyBindSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.render.ShulkerIconRenderer;
import dev.argus.client.utils.inventory.ShulkerIconSelector;
import dev.argus.client.utils.inventory.ShulkerIconSelector.Rule;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.KeyUtils;
import dev.argus.client.utils.inventory.ContainerTooltipComponent;
import dev.argus.client.utils.inventory.ItemUtils;
import dev.argus.client.utils.inventory.ShulkerBoxItemPreview;
import dev.argus.client.utils.world.MapDataCache;
import dev.argus.mixin.AbstractContainerScreenAccessor;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen.BookAccess;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientBundleTooltip;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.NonNullList;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.RegistryOps;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.item.component.Bees;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity.Occupant;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.joml.Matrix3x2fStack;

public class TooltipsModule extends Module {
   // General
   private final EnumSetting<DisplayWhen> displaySetting = this.add(new EnumSetting<>("Display", DisplayWhen.ALWAYS));
   private final KeyBindSetting previewKeySetting = this.add(
      new KeyBindSetting("PreviewKey", "Left Alt", value -> this.displaySetting.getValue() == DisplayWhen.KEY_HELD)
   );
   private final KeyBindSetting openKeySetting = this.add(new KeyBindSetting("OpenKey", "Middle Click", value -> true));
   private final Setting<Boolean> keepTextSetting = this.add(new BooleanSetting("KeepText", true));
   private final Setting<Boolean> coloredSetting = this.add(new BooleanSetting("Colored", true));
   // Previews: what the tooltip shows for containers and other special items.
   private final Setting<Boolean> previewsGroup = this.add(new BooleanSetting("Previews", true).markAsGroup().markVisibilityDependency());
   private final Setting<Boolean> shulkerContentSetting = this.add(new BooleanSetting("ShulkerContent", true).dependsOn(this.previewsGroup));
   public final Setting<Boolean> bundleContentSetting = this.add(new BooleanSetting("BundleContent", true).dependsOn(this.previewsGroup));
   private final Setting<Boolean> enderChestSetting = this.add(new BooleanSetting("EnderChest", true).dependsOn(this.previewsGroup));
   public final Setting<Boolean> mapOverlaySetting = this.add(new BooleanSetting("MapOverlay", true).dependsOn(this.previewsGroup));
   private final Setting<Boolean> booksSetting = this.add(new BooleanSetting("Books", true).dependsOn(this.previewsGroup));
   private final Setting<Boolean> bannersSetting = this.add(new BooleanSetting("Banners", true).dependsOn(this.previewsGroup));
   private final Setting<Boolean> bucketEntitiesSetting = this.add(new BooleanSetting("BucketEntities", true).dependsOn(this.previewsGroup));
   private final Setting<Boolean> beeNestSetting = this.add(new BooleanSetting("BeeNest", false).dependsOn(this.previewsGroup));
   // Details: extra lines added to item tooltips. The settings below are ported from Meteor Client's BetterTooltips (GPL-3.0); see THIRD_PARTY_NOTICES.md.
   private final Setting<Boolean> detailsGroup = this.add(new BooleanSetting("Details", true).markAsGroup().markVisibilityDependency());
   private final Setting<Boolean> foodInfoSetting = this.add(new BooleanSetting("FoodInfo", true).dependsOn(this.detailsGroup));
   private final Setting<Boolean> statusEffectsSetting = this.add(new BooleanSetting("StatusEffects", true).dependsOn(this.detailsGroup));
   private final Setting<Boolean> sizeSetting = this.add(new BooleanSetting("Size", false).dependsOn(this.detailsGroup));
   // Shulker icons: a small item drawn over shulker boxes in every inventory slot. Combos are edited from the module page.
   private final Setting<Boolean> shulkerIconsGroup = this.add(new BooleanSetting("ShulkerIcons", true).markAsGroup().markVisibilityDependency());
   public final Setting<Boolean> shulkerIconsSetting = this.add(new BooleanSetting("Overlay", false).dependsOn(this.shulkerIconsGroup));
   public final Setting<Float> iconScaleSetting = this.add(new NumberSetting<>("IconScale", 0.5F, 0.25F, 1.0F).dependsOn(this.shulkerIconsGroup));
   public final Setting<Corner> iconCornerSetting = this.add(new EnumSetting<>("IconCorner", Corner.TOP_RIGHT).dependsOn(this.shulkerIconsGroup));
   private final NonNullList<ItemStack> enderChestItems = NonNullList.withSize(27, ItemStack.EMPTY);
   private boolean enderChestKnown;
   private long enderChestClickedAt;
   private ContainerScreen enderChestScreen;
   private ItemStack bucketStack = ItemStack.EMPTY;
   private Mob bucketEntity;
   private static final Color ENDER_CHEST_COLOR = new Color(0.0F, 0.2F, 0.2F, 0.75F);
   private static final long ENDER_CHEST_OPEN_WINDOW_MS = 3000L;
   private static final int LEFT_ALT = 342;
   private static final int MIDDLE_MOUSE = 2;
   private Color tooltipColor;
   private static final int SHULKER_SLOT_COUNT = 27;

   public boolean shouldInspectContents(ItemStack stack) {
      if (!this.isToggled()) {
         return false;
      }

      if (!KeyUtils.isPhysicalKeyDown(342)) {
         return false;
      }

      Boolean containersEnabled = this.shulkerContentSetting.getValue();
      if (containersEnabled) {
         Item item = stack.getItem();
         if (ItemUtils.isShulkerBox(item)) {
            return true;
         }
      }

      if (this.enderChestKnown && this.enderChestSetting.getValue() && stack.is(Items.ENDER_CHEST)) {
         return true;
      }

      Boolean bundlesEnabled = this.bundleContentSetting.getValue();
      if (bundlesEnabled) {
         TagKey bundleTag = ItemTags.BUNDLES;
         if (stack.is(bundleTag)) {
            return true;
         }
      }

      return false;
   }

   public Color getTooltipColor() {
      return !this.coloredSetting.getValue() ? null : this.tooltipColor;
   }

   public void setTooltipColor(Color color) {
      this.tooltipColor = color;
   }

   @EventHandler
   private void onMinecraftDisconnectEvent(MinecraftDisconnectEvent event) {
      this.setTooltipColor(null);
      Collections.fill(this.enderChestItems, ItemStack.EMPTY);
      this.enderChestKnown = false;
      this.enderChestScreen = null;
      this.bucketStack = ItemStack.EMPTY;
      this.bucketEntity = null;
   }

   @EventHandler
   private void onAbstractContainerScreenRenderTooltipEvent(AbstractContainerScreenRenderTooltipEvent event) {
      if (event.getScreen() instanceof AbstractContainerScreen) {
         Slot focusedSlot = ((AbstractContainerScreenAccessor)event.getScreen()).argusclient$getFocusedSlot();
         if (focusedSlot != null) {
            ItemStack hoveredStack = focusedSlot.getItem();
            boolean previewsShown = this.previewsShown();
            boolean previewHidden = false;
            boolean openPressed = this.openKeyPressed() && !(event.getScreen() instanceof ShulkerPreviewScreen);
            this.addItemInfo(hoveredStack, event.getTooltipComponents());
            NonNullList<ItemStack> containerItems = NonNullList.withSize(27, ItemStack.EMPTY);
            if (hoveredStack.getItem() instanceof BlockItem blockItem
               && blockItem.getBlock() instanceof ShulkerBoxBlock shulkerBlock
               && shulkerBlock.getColor() != null) {
               Color baseColor = new Color(shulkerBlock.getColor().getMapColor().col, false);
               float[] rgbComponents = new float[3];
               baseColor.getColorComponents(rgbComponents);
               Color capturedTooltipColor = new Color(rgbComponents[0], rgbComponents[1], rgbComponents[2], 0.75F);
               this.setTooltipColor(capturedTooltipColor);
            } else {
               this.setTooltipColor(null);
            }

            BundleTooltipComponent bundleComponent = BundleTooltipComponent.fromSlot(focusedSlot);
            if (bundleComponent != null) {
               Boolean bundlesEnabled = this.bundleContentSetting.getValue();
               if (bundlesEnabled) {
                  event.getTooltipComponents().removeIf(component -> component instanceof ClientBundleTooltip);
                  event.getTooltipComponents().add(bundleComponent);
               }
            }

            DataComponentType<ItemContainerContents> containerComponentType = DataComponents.CONTAINER;
            ItemContainerContents containedItems = hoveredStack.get(containerComponentType);
            if (containedItems != null) {
               List<ItemStack> contentList = containedItems.stream().toList();
               int slotCount = Math.min(contentList.size(), containerItems.size());

               for (int contentIndex = 0; contentIndex < slotCount; contentIndex++) {
                  containerItems.set(contentIndex, contentList.get(contentIndex));
               }
            }

            this.readLegacyContainerItems(hoveredStack, containerItems);
            boolean enderChestHovered = hoveredStack.is(Items.ENDER_CHEST) && this.enderChestSetting.getValue();
            if (enderChestHovered) {
               if (this.enderChestKnown) {
                  for (int slotIndex = 0; slotIndex < containerItems.size(); slotIndex++) {
                     containerItems.set(slotIndex, this.enderChestItems.get(slotIndex));
                  }

                  this.setTooltipColor(ENDER_CHEST_COLOR);
               } else if (previewsShown) {
                  MutableComponent unknownText = Component.literal("Open your ender chest once to preview it.").withStyle(ChatFormatting.DARK_RED);
                  event.getTooltipComponents().add(ClientTooltipComponent.create(unknownText.getVisualOrderText()));
               } else {
                  previewHidden = true;
               }
            }

            boolean enderChestShown = enderChestHovered && this.enderChestKnown;
            if (enderChestShown || !enderChestHovered && !containerItems.stream().allMatch(ItemStack::isEmpty)) {
               Boolean contentsEnabled = enderChestShown || this.shulkerContentSetting.getValue();
               if (contentsEnabled) {
                  if (openPressed) {
                     Minecraft capturedScreenClient = ClientAccess.argusclientMinecraft;
                     Inventory menuInventory = ClientAccess.argusclientMinecraft.player.getInventory();
                     ShulkerBoxMenu previewMenu = new ShulkerBoxMenu(1337, menuInventory, new SimpleContainer(containerItems.toArray(new ItemStack[27])));
                     Inventory previewInventory = ClientAccess.argusclientMinecraft.player.getInventory();
                     Component previewTitle = hoveredStack.getHoverName();
                     ShulkerPreviewScreen previewScreen = new ShulkerPreviewScreen(previewMenu, previewInventory, previewTitle, event.getScreen());
                     capturedScreenClient.setScreen(previewScreen);
                  }

                  Boolean keepContentsText = this.keepTextSetting.getValue();
                  if (!previewsShown) {
                     previewHidden = true;
                  } else if (keepContentsText) {
                     event.getTooltipComponents().add(new ContainerTooltipComponent(containerItems));
                  } else {
                     event.getTooltipComponents().clear();
                     List<ClientTooltipComponent> containerTooltipComponents = event.getTooltipComponents();
                     Component containerTitle = hoveredStack.getHoverName();
                     FormattedCharSequence containerTitleText = containerTitle.getVisualOrderText();
                     ClientTooltipComponent capturedContainerTitle = ClientTooltipComponent.create(containerTitleText);
                     ContainerTooltipComponent containerComponent = new ContainerTooltipComponent(containerItems);
                     containerTooltipComponents.addAll(List.of(capturedContainerTitle, containerComponent));
                  }
               }
            }

            if (hoveredStack.getItem() == Items.FILLED_MAP) {
               DataComponentType<MapId> mapComponentType = DataComponents.MAP_ID;
               MapId mapIdData = hoveredStack.get(mapComponentType);
               int resolvedMapId;
               if (mapIdData == null) {
                  resolvedMapId = -1;
               } else {
                  resolvedMapId = mapIdData.id();
               }

               int mapId = resolvedMapId;
               MapDataCache mapCache = ClientContext.mapDataCache;
               MapItemSavedData mapData = mapCache.getMapData(hoveredStack, mapId);
               if (mapData != null && !previewsShown) {
                  previewHidden = true;
               } else if (mapData != null) {
                  Boolean keepMapText = this.keepTextSetting.getValue();
                  if (!keepMapText) {
                     event.getTooltipComponents().clear();
                     List<ClientTooltipComponent> mapTooltipComponents = event.getTooltipComponents();
                     Component mapTitle = hoveredStack.getHoverName();
                     FormattedCharSequence mapTitleText = mapTitle.getVisualOrderText();
                     ClientTooltipComponent capturedMapTitle = ClientTooltipComponent.create(mapTitleText);
                     MapTooltipComponent mapComponent = new MapTooltipComponent(mapId, mapData);
                     mapTooltipComponents.addAll(List.of(capturedMapTitle, mapComponent));
                  } else {
                     event.getTooltipComponents().add(new MapTooltipComponent(mapId, mapData));
                  }

                  Boolean overlayEnabled = this.mapOverlaySetting.getValue();
                  if (overlayEnabled) {
                     String overlayHintText = "Hold Left-Alt to hide item amount.";
                     MutableComponent overlayHint = Component.literal(overlayHintText);
                     UnaryOperator<Style> hintStyle = style -> {
                        ChatFormatting formatting = ChatFormatting.YELLOW;
                        return style.applyFormat(formatting);
                     };
                     overlayHint = overlayHint.withStyle(hintStyle);
                     List<ClientTooltipComponent> hintTooltipComponents = event.getTooltipComponents();
                     FormattedCharSequence hintText = overlayHint.getVisualOrderText();
                     hintTooltipComponents.add(ClientTooltipComponent.create(hintText));
                  }
               }
            }

            if (this.booksSetting.getValue() && (hoveredStack.is(Items.WRITABLE_BOOK) || hoveredStack.is(Items.WRITTEN_BOOK))) {
               BookAccess book = BookAccess.fromItem(hoveredStack);
               if (book != null && book.getPageCount() > 0) {
                  if (openPressed) {
                     ClientAccess.argusclientMinecraft.setScreen(new BookPreviewScreen(book, event.getScreen()));
                  }

                  if (previewsShown) {
                     String pageCount = book.getPageCount() == 1 ? " (1 page)" : " (" + book.getPageCount() + " pages)";
                     MutableComponent firstPage = book.getPage(0).copy().append(Component.literal(pageCount).withStyle(ChatFormatting.GRAY));
                     event.getTooltipComponents().add(new BookTooltipComponent(firstPage));
                  } else {
                     previewHidden = true;
                  }
               }
            }

            if (this.bannersSetting.getValue()) {
               BannerTooltipComponent bannerComponent = this.createBannerPreview(hoveredStack);
               if (bannerComponent != null && previewsShown) {
                  event.getTooltipComponents().add(bannerComponent);
               } else if (bannerComponent != null) {
                  previewHidden = true;
               }
            }

            if (this.bucketEntitiesSetting.getValue() && hoveredStack.getItem() instanceof MobBucketItem) {
               if (!previewsShown) {
                  previewHidden = true;
               } else {
                  Mob bucketMob = this.getBucketEntity(hoveredStack);
                  if (bucketMob != null) {
                     event.getTooltipComponents().add(new EntityTooltipComponent(bucketMob));
                  }
               }
            }

            if (previewHidden) {
               String hintText = "Hold " + ChatFormatting.YELLOW + this.previewKeyName() + ChatFormatting.RESET + " to preview";
               event.getTooltipComponents().add(ClientTooltipComponent.create(Component.literal(hintText).getVisualOrderText()));
            }

            DataComponentType<Bees> beesComponentType = DataComponents.BEES;
            Bees bees = hoveredStack.get(beesComponentType);
            if (bees != null) {
               Boolean beesEnabled = this.beeNestSetting.getValue();
               if (beesEnabled) {
                  for (Occupant beeOccupant : bees.bees()) {
                     TypedEntityData entityData = beeOccupant.entityData();
                     CompoundTag beeTag = entityData.copyTagWithoutId();
                     String flowerCheckKey = "FlowerPos";
                     if (beeTag.contains(flowerCheckKey)) {
                        Codec<BlockPos> flowerPositionCodec = BlockPos.CODEC;
                        Optional<BlockPos> flowerResult = beeTag.read("flower_pos", flowerPositionCodec);
                        BlockPos flowerPosition = flowerResult.orElse(null);
                        if (flowerPosition != null) {
                           List<ClientTooltipComponent> flowerTooltipComponents = event.getTooltipComponents();
                           String flowerText = "Position " + flowerPosition.toShortString();
                           MutableComponent flowerComponent = Component.literal(flowerText);
                           FormattedCharSequence flowerTextSequence = flowerComponent.getVisualOrderText();
                           flowerTooltipComponents.add(ClientTooltipComponent.create(flowerTextSequence));
                        }
                     }
                  }
               }
            }

            Boolean serializedSizeEnabled = this.sizeSetting.getValue();
            if (serializedSizeEnabled) {
               CountingDataOutput counter = new CountingDataOutput();
               Provider tooltipRegistries = ClientAccess.argusclientMinecraft.player.registryAccess();
               NbtOps tooltipNbtOps = NbtOps.INSTANCE;
               RegistryOps registryOps = tooltipRegistries.createSerializationContext(tooltipNbtOps);
               Codec<ItemStack> itemCodec = ItemStack.CODEC;
               if (itemCodec.encodeStart(registryOps, hoveredStack) instanceof Success encodedItem) {
                  try {
                     Tag serializedItem = (Tag)encodedItem.value();
                     serializedItem.write(counter);
                     long serializedBytes = counter.getByteCount();
                     String formattedSize = this.formatSize(serializedBytes);
                     MutableComponent sizeComponent = Component.literal(formattedSize);
                     FormattedCharSequence sizeText = sizeComponent.getVisualOrderText();
                     List<ClientTooltipComponent> sizeTooltipComponents = event.getTooltipComponents();
                     sizeTooltipComponents.add(ClientTooltipComponent.create(sizeText));
                  } catch (Throwable var24) {
                  }
               }
            }
         }
      }
   }

   /** True while the contents grid is being shown, which makes the vanilla list of contents redundant. */
   public boolean replacesContainerText() {
      return this.isToggled()
         && this.shulkerContentSetting.getValue()
         && ClientAccess.argusclientMinecraft.screen instanceof AbstractContainerScreen
         && this.previewsShown();
   }

   private boolean previewsShown() {
      if (this.displaySetting.getValue() == DisplayWhen.ALWAYS) {
         return true;
      }

      return this.previewKeySetting.followsDefault()
         ? KeyUtils.isPhysicalKeyDown(LEFT_ALT)
         : KeyUtils.isPhysicallyDown(this.previewKeySetting.getValue());
   }

   private String previewKeyName() {
      return this.previewKeySetting.followsDefault() ? "Left Alt" : this.previewKeySetting.getValue().getDisplayName();
   }

   private boolean openKeyPressed() {
      return this.openKeySetting.followsDefault()
         ? KeyUtils.isPhysicalMouseButtonDown(MIDDLE_MOUSE)
         : KeyUtils.isPhysicallyDown(this.openKeySetting.getValue());
   }

   /** Adds hunger, saturation and consume-effect lines under the item's name. */
   private void addItemInfo(ItemStack stack, List<ClientTooltipComponent> components) {
      List<Component> lines = new ArrayList<>();
      FoodProperties food = stack.get(DataComponents.FOOD);
      if (food != null && this.foodInfoSetting.getValue()) {
         String foodText = String.format("Hunger %d, saturation %.1f", food.nutrition(), food.saturation());
         lines.add(Component.literal(foodText).withStyle(ChatFormatting.GRAY));
      }

      if (this.statusEffectsSetting.getValue()) {
         SuspiciousStewEffects stewEffects = stack.get(DataComponents.SUSPICIOUS_STEW_EFFECTS);
         Consumable consumable = stack.get(DataComponents.CONSUMABLE);
         if (stewEffects != null && stack.is(Items.SUSPICIOUS_STEW)) {
            for (SuspiciousStewEffects.Entry stewEffect : stewEffects.effects()) {
               lines.add(this.describeEffect(stewEffect.createEffectInstance(), 1.0F));
            }
         } else if (consumable != null) {
            for (ConsumeEffect consumeEffect : consumable.onConsumeEffects()) {
               if (consumeEffect instanceof ApplyStatusEffectsConsumeEffect applied) {
                  for (MobEffectInstance effect : applied.effects()) {
                     lines.add(this.describeEffect(effect, applied.probability()));
                  }
               }
            }
         }
      }

      int insertAt = Math.min(1, components.size());
      for (Component line : lines) {
         components.add(insertAt++, ClientTooltipComponent.create(line.getVisualOrderText()));
      }
   }

   private Component describeEffect(MobEffectInstance effect, float probability) {
      float tickRate = ClientAccess.argusclientMinecraft.level == null ? 20.0F : ClientAccess.argusclientMinecraft.level.tickRateManager().tickrate();
      MutableComponent text = Component.translatable(effect.getDescriptionId());
      if (effect.getAmplifier() != 0) {
         text.append(" " + (effect.getAmplifier() + 1));
      }

      text.append(" (" + MobEffectUtil.formatDuration(effect, 1.0F, tickRate).getString() + ")");
      if (probability < 1.0F) {
         text.append(" " + Math.round(probability * 100.0F) + "%");
      }

      return text.withStyle(effect.getEffect().value().isBeneficial() ? ChatFormatting.BLUE : ChatFormatting.RED);
   }

   /** A banner, a shield carrying a banner design, or a banner pattern item; null for anything else. */
   private BannerTooltipComponent createBannerPreview(ItemStack stack) {
      if (stack.getItem() instanceof BannerItem banner) {
         return new BannerTooltipComponent(banner.getColor(), stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY));
      }

      TagKey<BannerPattern> providedPatterns = stack.get(DataComponents.PROVIDES_BANNER_PATTERNS);
      if (providedPatterns != null) {
         Optional<HolderSet.Named<BannerPattern>> patterns = ClientAccess.argusclientMinecraft.player
            .registryAccess()
            .lookupOrThrow(Registries.BANNER_PATTERN)
            .get(providedPatterns);
         if (patterns.isEmpty() || patterns.get().size() == 0) {
            return null;
         }

         Holder<BannerPattern> pattern = patterns.get().get(0);
         return new BannerTooltipComponent(DyeColor.GRAY, new BannerPatternLayers.Builder().add(pattern, DyeColor.WHITE).build());
      }

      if (stack.is(Items.SHIELD)) {
         BannerPatternLayers shieldPatterns = stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
         DyeColor shieldColor = stack.get(DataComponents.BASE_COLOR);
         if (shieldColor != null || !shieldPatterns.layers().isEmpty()) {
            return new BannerTooltipComponent(shieldColor == null ? DyeColor.WHITE : shieldColor, shieldPatterns);
         }
      }

      return null;
   }

   /** Builds the mob a bucket holds, reusing it while the same bucket stays hovered. */
   private Mob getBucketEntity(ItemStack stack) {
      if (this.bucketEntity != null
         && this.bucketEntity.level() == ClientAccess.argusclientMinecraft.level
         && ItemStack.isSameItemSameComponents(this.bucketStack, stack)) {
         return this.bucketEntity;
      }

      this.bucketStack = stack.copy();
      this.bucketEntity = null;

      try {
         Mob mob = ((MobBucketItem)stack.getItem()).type.create(ClientAccess.argusclientMinecraft.level, EntitySpawnReason.BUCKET);
         if (mob != null) {
            mob.applyComponentsFromItemStack(stack);
            CustomData bucketData = stack.get(DataComponents.BUCKET_ENTITY_DATA);
            if (bucketData != null && mob instanceof Bucketable bucketable) {
               bucketable.loadFromBucketTag(bucketData.copyTag());
            }

            mob.wasTouchingWater = true;
            this.bucketEntity = mob;
         }
      } catch (RuntimeException ignored) {
         // A bucket with data this client cannot turn into a mob simply gets no preview.
      }

      return this.bucketEntity;
   }

   @EventHandler
   private void onUseItemOn(MultiPlayerGameModeUseItemOnEvent event) {
      if (ClientAccess.argusclientMinecraft.level != null
         && ClientAccess.argusclientMinecraft.level.getBlockState(event.getHitResult().getBlockPos()).getBlock() instanceof EnderChestBlock) {
         this.enderChestClickedAt = System.currentTimeMillis();
      }
   }

   /** Remembers the ender chest's contents while its screen is open, so the item can be previewed later. */
   private void rememberEnderChest(Screen screen) {
      if (!(screen instanceof ContainerScreen container) || container.getMenu().getRowCount() != 3) {
         this.enderChestScreen = null;
         return;
      }

      if (container != this.enderChestScreen) {
         if (System.currentTimeMillis() - this.enderChestClickedAt > ENDER_CHEST_OPEN_WINDOW_MS) {
            return;
         }

         this.enderChestScreen = container;
         this.enderChestClickedAt = 0L;
      }

      for (int slotIndex = 0; slotIndex < this.enderChestItems.size(); slotIndex++) {
         this.enderChestItems.set(slotIndex, container.getMenu().getContainer().getItem(slotIndex).copy());
      }

      this.enderChestKnown = true;
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      Screen screen = ClientAccess.argusclientMinecraft.screen;
      this.rememberEnderChest(screen);
      if (!(screen instanceof AbstractContainerScreen) || ((AbstractContainerScreenAccessor)screen).argusclient$getFocusedSlot() == null) {
         this.setTooltipColor(null);
      }
   }

   private String formatSize(long bytes) {
      long kilobytes = bytes / 1024L;
      long megabytes = kilobytes / 1024L;
      if (megabytes > 0L) {
         return String.format("%,d MB", megabytes);
      } else {
         return kilobytes > 0L ? String.format("%,d KB", kilobytes) : String.format("%,d B", bytes);
      }
   }

   private void readLegacyContainerItems(ItemStack stack, List<ItemStack> slots) {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      Provider registries = player.registryAccess();
      RegistryOps registryOps = registries.createSerializationContext(NbtOps.INSTANCE);
      CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
      if (customData != null) {
         CompoundTag rootTag = customData.copyTag();
         if (rootTag.contains("BlockEntityTag")) {
            CompoundTag blockEntityTag = rootTag.getCompoundOrEmpty("BlockEntityTag");
            if (blockEntityTag.contains("Items")) {
               ListTag itemTags = blockEntityTag.getListOrEmpty("Items");
               int slotCount = Math.min(itemTags.size(), slots.size());

               for (int index = 0; index < slotCount; index++) {
                  CompoundTag itemTag = itemTags.getCompoundOrEmpty(index);
                  String itemId = itemTag.getStringOr("id", "minecraft:air");
                  if (!itemId.equals("minecraft:air") && ItemStack.CODEC.parse(registryOps, itemTag) instanceof Success parsed) {
                     ItemStack item = (ItemStack)parsed.value();
                     if (itemTag.contains("Count")) {
                        byte count = itemTag.getByteOr("Count", (byte)0);
                        if (count <= 0) {
                           continue;
                        }

                        item.setCount(count);
                     }

                     slots.set(index, item);
                  }
               }
            }
         }
      }
   }

   private static String createTooltipDescription() {
      String previewColor = String.valueOf(ChatFormatting.YELLOW);
      String inspectColor = String.valueOf(ChatFormatting.YELLOW);
      return "Draws advanced tool tips on items.\n"
         + previewColor
         + "Press the open key (Middle Click by default) on a shulker, ender chest or book to open it.\n"
         + inspectColor
         + "Hold Left Alt when hovering over a shulker to inspect it's items.";
   }

   public enum DisplayWhen implements NamedEntry {
      ALWAYS("Always"),
      KEY_HELD("KeyHeld");

      private final String name;

      DisplayWhen(String name) {
         this.name = name;
      }

      @Override
      public String getName() {
         return this.name;
      }
   }

   // ---- Shulker icons ---------------------------------------------------------------------------

   private record RenderedIcon(ItemStack stack, Integer colour) {}

   // Immutable container components are suitable keys; never retain an unbounded history of boxes.
   private final Map<ItemContainerContents, RenderedIcon> iconCache = new LinkedHashMap<>(256, 0.75F, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<ItemContainerContents, RenderedIcon> eldest) {
         return this.size() > 256;
      }
   };
   private List<Rule<Item>> iconRules = List.of();
   private boolean drawingOverlay;

   public void drawShulkerIcon(GuiGraphics graphics, ItemStack shulker, int x, int y) {
      if (!this.isToggled() || !this.shulkerIconsSetting.getValue() || this.drawingOverlay || shulker.isEmpty()
         || !(shulker.getItem() instanceof BlockItem block) || !(block.getBlock() instanceof ShulkerBoxBlock)) {
         return;
      }

      ItemContainerContents contents = shulker.get(DataComponents.CONTAINER);
      if (contents == null) {
         return;
      }

      RenderedIcon icon = this.iconCache.computeIfAbsent(contents, this::selectIcon);
      if (icon.stack().isEmpty()) {
         return;
      }

      float size = this.iconScaleSetting.getValue();
      float offset = 16.0F * (1.0F - size);
      Corner corner = this.iconCornerSetting.getValue();
      boolean right = corner == Corner.TOP_RIGHT || corner == Corner.BOTTOM_RIGHT;
      boolean bottom = corner == Corner.BOTTOM_LEFT || corner == Corner.BOTTOM_RIGHT;
      this.drawingOverlay = true;
      try {
         ShulkerIconRenderer.drawAt(graphics, icon.stack(), icon.colour(), x + (right ? offset : 0.0F), y + (bottom ? offset : 0.0F), size);
      } finally {
         this.drawingOverlay = false;
      }
   }

   /** The first enabled matching combo wins; otherwise the item with the greatest total quantity (ties: first slot). */
   private RenderedIcon selectIcon(ItemContainerContents contents) {
      List<ShulkerIconSelector.Entry<Item>> entries = new ArrayList<>();
      contents.nonEmptyStream().forEach(stack -> entries.add(new ShulkerIconSelector.Entry<>(stack.getItem(), stack.getCount())));
      ShulkerIconSelector.Icon<Item> selected = ShulkerIconSelector.selectIcon(entries, this.iconRules);
      if (selected == null) {
         return new RenderedIcon(ItemStack.EMPTY, null);
      }

      return new RenderedIcon(selected.item().getDefaultInstance(), selected.style() == ShulkerIconSelector.Style.SolidColour ? Integer.valueOf(selected.colour()) : null);
   }

   public List<Rule<Item>> getIconRules() {
      return this.iconRules;
   }

   public void setIconRules(List<Rule<Item>> rules) {
      this.iconRules = List.copyOf(rules);
      this.iconCache.clear();
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.iconCache.clear();
   }

   @Override
   public JsonElement toJson() {
      JsonElement json = super.toJson();
      if (json instanceof JsonObject object) {
         JsonArray list = new JsonArray();
         for (Rule<Item> rule : this.iconRules) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", rule.name());
            entry.addProperty("enabled", rule.enabled());
            entry.addProperty("icon", BuiltInRegistries.ITEM.getKey(rule.icon()).toString());
            entry.addProperty("solid", rule.style() == ShulkerIconSelector.Style.SolidColour);
            entry.addProperty("colour", rule.colour());
            JsonArray required = new JsonArray();
            for (Item item : rule.required()) {
               required.add(BuiltInRegistries.ITEM.getKey(item).toString());
            }

            entry.add("required", required);
            list.add(entry);
         }

         object.add("combos", list);
      }

      return json;
   }

   @Override
   public void fromJson(JsonElement json) {
      super.fromJson(json);
      List<Rule<Item>> loaded = new ArrayList<>();
      if (json instanceof JsonObject object && object.get("combos") instanceof JsonArray list) {
         for (JsonElement element : list) {
            if (!(element instanceof JsonObject entry)) {
               continue;
            }

            try {
               Item icon = resolveItem(entry.get("icon").getAsString());
               List<Item> required = new ArrayList<>();
               boolean valid = icon != null;
               for (JsonElement id : entry.getAsJsonArray("required")) {
                  Item item = resolveItem(id.getAsString());
                  if (item == null) {
                     valid = false;
                     break;
                  }

                  required.add(item);
               }

               // Never turn an unknown requirement into a broader matching rule.
               if (valid) {
                  boolean solid = entry.has("solid") && entry.get("solid").getAsBoolean();
                  int colour = entry.has("colour") ? entry.get("colour").getAsInt() : 0xFFFFFFFF;
                  String name = entry.has("name") ? entry.get("name").getAsString() : "Combo";
                  boolean enabled = !entry.has("enabled") || entry.get("enabled").getAsBoolean();
                  loaded.add(new Rule<>(name, enabled, required, icon, solid ? ShulkerIconSelector.Style.SolidColour : ShulkerIconSelector.Style.Normal, colour));
               }
            } catch (RuntimeException skipped) {
            }
         }
      }

      this.setIconRules(loaded);
   }

   private static Item resolveItem(String text) {
      Identifier id = Identifier.tryParse(text);
      if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
         return null;
      }

      Item item = BuiltInRegistries.ITEM.getValue(id);
      return item == Items.AIR ? null : item;
   }

   public enum Corner implements NamedEntry {
      TOP_LEFT("TopLeft"),
      TOP_RIGHT("TopRight"),
      BOTTOM_LEFT("BottomLeft"),
      BOTTOM_RIGHT("BottomRight");

      private final String displayName;

      Corner(String displayName) {
         this.displayName = displayName;
      }

      @Override
      public String getName() {
         return this.displayName;
      }
   }

   public TooltipsModule() {
      super("Tooltips", createTooltipDescription(), ModuleCategory.RENDER);
      this.setDrawn(false);
      // Replaces the old MajorityItem option, so saved configs keep their value.
      this.shulkerIconsSetting.setConfigName("MajorityItem");
   }
}
