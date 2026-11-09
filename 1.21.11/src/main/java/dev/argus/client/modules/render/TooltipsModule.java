package dev.argus.client.modules.render;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult.Success;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.AbstractContainerScreenRenderTooltipEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.MinecraftDisconnectEvent;
import dev.argus.client.events.MinecraftRunTickEvent;
import dev.argus.client.events.ScreenChangeEvent;
import dev.argus.client.events.SlotSetEvent;
import dev.argus.client.gui.screens.ShulkerPreviewScreen;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
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
import net.minecraft.client.gui.screens.inventory.tooltip.ClientBundleTooltip;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Bees;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity.Occupant;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

public class TooltipsModule extends Module {
   private final Setting<Boolean> coloredSetting = this.add(new BooleanSetting("Colored", true));
   public final Setting<Boolean> majorityItemSetting = this.add(new BooleanSetting("MajorityItem", false));
   private final Setting<Boolean> keepTextSetting = this.add(new BooleanSetting("KeepText", true));
   private final Setting<Boolean> shulkerContentSetting = this.add(new BooleanSetting("ShulkerContent", true));
   public final Setting<Boolean> bundleContentSetting = this.add(new BooleanSetting("BundleContent", true));
   private final Setting<Boolean> beeNestSetting = this.add(new BooleanSetting("BeeNest", false));
   public final Setting<Boolean> mapOverlaySetting = this.add(new BooleanSetting("MapOverlay", true));
   private final Setting<Boolean> sizeSetting = this.add(new BooleanSetting("Size", false));
   public final List<ShulkerBoxItemPreview> inventoryContainerSnapshots = Collections.synchronizedList(new ArrayList<>());
   private Color tooltipColor;
   private boolean inventorySnapshotDirty;
   private static final int SHULKER_SLOT_COUNT = 27;
   private static final float ITEM_PREVIEW_SCALE = 0.625F;
   private static final float ITEM_PREVIEW_OFFSET_X = 11.0F;

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
   }

   @EventHandler
   private void onAbstractContainerScreenRenderTooltipEvent(AbstractContainerScreenRenderTooltipEvent event) {
      if (event.getScreen() instanceof AbstractContainerScreen) {
         Slot focusedSlot = ((AbstractContainerScreenAccessor)event.getScreen()).argusclient$getFocusedSlot();
         if (focusedSlot != null) {
            ItemStack hoveredStack = focusedSlot.getItem();
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
            if (!containerItems.stream().allMatch(ItemStack::isEmpty)) {
               Boolean contentsEnabled = this.shulkerContentSetting.getValue();
               if (contentsEnabled) {
                  long windowHandle = ClientAccess.argusclientMinecraft.getWindow().handle();
                  if (GLFW.glfwGetMouseButton(windowHandle, 2) == 1 && !(event.getScreen() instanceof ShulkerPreviewScreen)) {
                     Minecraft capturedScreenClient = ClientAccess.argusclientMinecraft;
                     Inventory menuInventory = ClientAccess.argusclientMinecraft.player.getInventory();
                     ShulkerBoxMenu previewMenu = new ShulkerBoxMenu(1337, menuInventory, new SimpleContainer(containerItems.toArray(new ItemStack[27])));
                     Inventory previewInventory = ClientAccess.argusclientMinecraft.player.getInventory();
                     Component previewTitle = hoveredStack.getHoverName();
                     ShulkerPreviewScreen previewScreen = new ShulkerPreviewScreen(previewMenu, previewInventory, previewTitle, event.getScreen());
                     capturedScreenClient.setScreen(previewScreen);
                  }

                  Boolean keepContentsText = this.keepTextSetting.getValue();
                  if (keepContentsText) {
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
               if (mapData != null) {
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

   public void renderItemPreview(GuiGraphics graphics, ItemStack stack, float x, float y) {
      Matrix3x2fStack pose = graphics.pose();
      pose.pushMatrix();
      pose.scale(0.625F, 0.625F);
      pose.translate(11.0F, 0.0F);
      int itemX = (int)(x / 0.625F);
      int itemY = (int)(y / 0.625F);
      graphics.renderItem(stack, itemX, itemY);
      pose.popMatrix();
   }

   @EventHandler
   private void onScreenChange(ScreenChangeEvent event) {
      if (event.getNewScreen() instanceof AbstractContainerScreen) {
         this.inventorySnapshotDirty = true;
      }
   }

   @EventHandler
   private void onSlotSetEvent(SlotSetEvent event) {
      this.inventorySnapshotDirty = true;
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      Screen screen = ClientAccess.argusclientMinecraft.screen;
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

   @EventHandler
   private void onMinecraftRunTickEvent(MinecraftRunTickEvent event) {
      if (this.inventorySnapshotDirty && ClientAccess.argusclientMinecraft.player != null) {
         this.inventoryContainerSnapshots.clear();

         for (Slot slot : ClientAccess.argusclientMinecraft.player.containerMenu.slots) {
            ItemStack capturedStack = slot.getItem();
            ShulkerBoxItemPreview snapshot = ShulkerBoxItemPreview.create(capturedStack);
            if (snapshot != null) {
               this.inventoryContainerSnapshots.add(snapshot);
            }
         }

         this.inventorySnapshotDirty = false;
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
         + "Middle Click a shulker to open the preview screen.\n"
         + inspectColor
         + "Hold Left Alt when hovering over a shulker to inspect it's items.";
   }

   public TooltipsModule() {
      super("Tooltips", createTooltipDescription(), ModuleCategory.RENDER);
      this.setDrawn(false);
   }
}
