package dev.argus.client.modules.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.modules.HudWidgetModule;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.render.ResizableHudWidgetRenderer;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.RegistrySetSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.ListFilterMode;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class EntityListModule extends HudWidgetModule {
   private final Setting<Set<EntityType<?>>> entitiesSetting = this.add(new RegistrySetSetting<>("Entities", BuiltInRegistries.ENTITY_TYPE, EntityTypes.PLAYER));
   private final Setting<Set<Item>> itemsSetting = this.add(new RegistrySetSetting<>("Items", BuiltInRegistries.ITEM, Items.OBSIDIAN));
   private final Setting<ListFilterMode> selectionSetting = this.add(new EnumSetting<>("Selection", ListFilterMode.BLACKLIST));
   private final Setting<ListFilterMode> itemSelectionSetting = this.add(new EnumSetting<>("ItemSelection", ListFilterMode.ANY));
   private final Setting<EntityListModule.SortMode> sortingSetting = this.add(new EnumSetting<>("Sorting", EntityListModule.SortMode.ALPHABET));
   private final Setting<Boolean> customNamesSetting;
   private final Setting<Boolean> coloredCountSetting;
   private final Map<String, Integer> countsByName;
   private final List<EntityListModule.EntityCount> entries;

   @Override
   public float[] calculateDimensions() {
      float totalHeight = 0.0F;
      float maximumWidth = 0.0F;

      for (EntityListModule.EntityCount entry : this.entries) {
         FontRenderer textRenderer = FontRenderer.INSTANCE;
         boolean coloredCount = false;
         float width = textRenderer.getWidth(entry.formatText(coloredCount));
         totalHeight += FontRenderer.INSTANCE.getHeight() + 1;
         if (width > maximumWidth) {
            maximumWidth = width;
         }
      }

      return new float[]{maximumWidth, totalHeight};
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent ignoredEvent) {
      this.countsByName.clear();
      ClientLevel level = ClientAccess.argusclientMinecraft.level;

      for (Entity entity : level.entitiesForRendering()) {
         Setting<ListFilterMode> selectionOption = this.selectionSetting;
         ListFilterMode selection = selectionOption.getValue();
         EntityType entityType = entity.getType();
         Setting<Set<EntityType<?>>> entityTypesOption = this.entitiesSetting;
         Collection<?> selectedEntityTypes = entityTypesOption.getValue();
         if (ListFilterMode.matches(selection, entityType, selectedEntityTypes)) {
            if (entity instanceof ItemEntity itemEntity) {
               ItemStack stackForEmptyCheck = itemEntity.getItem();
               if (stackForEmptyCheck.isEmpty()) {
                  continue;
               }

               ItemStack stackForItem = itemEntity.getItem();
               Item item = stackForItem.getItem();
               Setting<ListFilterMode> itemSelectionOption = this.itemSelectionSetting;
               selection = itemSelectionOption.getValue();
               Setting<Set<Item>> selectedItemsOption = this.itemsSetting;
               ListFilterMode itemSelection = selection;
               if (!ListFilterMode.matches(itemSelection, item, selectedItemsOption)) {
                  continue;
               }
            }

            Boolean useCustomNames = this.customNamesSetting.getValue();
            String entityName;
            if (useCustomNames) {
               Component nameText = entity.getName();
               entityName = nameText.getString();
            } else {
               EntityType typeForName = entity.getType();
               Component description = typeForName.getDescription();
               entityName = description.getString();
            }

            String name = entityName;
            if (entity instanceof ItemEntity namedItemEntity) {
               ItemStack stackForName = namedItemEntity.getItem();
               Component hoverName = stackForName.getHoverName();
               name = hoverName.getString();
            }

            int countedAmount;
            if (entity instanceof ItemEntity countedItemEntity) {
               ItemStack stackForCount = countedItemEntity.getItem();
               countedAmount = stackForCount.getCount();
            } else {
               countedAmount = 1;
            }

            int countContribution = countedAmount;
            this.countsByName.compute(name, (ignoredName, previousCount) -> previousCount == null ? countContribution : previousCount + countContribution);
         }
      }

      this.entries.clear();
      this.entries.addAll(this.countsByName.entrySet().stream().map(EntityListModule.EntityCount::fromEntry).toList());
      Setting<EntityListModule.SortMode> sortingOption = this.sortingSetting;
      EntityListModule.SortMode sorting = sortingOption.getValue();
      switch (sorting) {
         case ALPHABET:
            this.entries.sort(Comparator.comparing(EntityListModule.EntityCount::displayName));
            break;
         case COUNT:
            this.entries.sort(Comparator.comparing(entry -> -entry.count()));
            break;
         case LENGTH:
            this.entries.sort(Comparator.comparing(entry -> {
               FontRenderer textRenderer = FontRenderer.INSTANCE;
               boolean coloredCount = false;
               float width = textRenderer.getWidth(entry.formatText(coloredCount));
               return width;
            }));
      }
   }

   @Override
   public void renderContent(PoseStack poses) {
      float hudScale = hud.getTextScale();
      float y = this.renderer.getContentY(FontRenderer.INSTANCE.getHeight() * hudScale) - this.renderer.getY();
      y /= hudScale;

      for (EntityListModule.EntityCount entry : this.entries) {
         Color color = this.getHudColor(y);
         Boolean coloredCountEnabled = this.coloredCountSetting.getValue();
         boolean coloredCount = coloredCountEnabled;
         String text = entry.formatText(coloredCount);
         float scaledWidth = FontRenderer.INSTANCE.getWidth(text) * hudScale;
         float x = (this.renderer.getContentX(scaledWidth) - this.renderer.getX()) / hudScale;
         FontRenderer.INSTANCE.drawWithShadow(poses, text, x, y, color);
         y += (FontRenderer.INSTANCE.getHeight() + 1) * this.renderer.getVerticalDirection();
      }
   }

   public EntityListModule() {
      super("EntityList");
      Boolean defaultCustomNames = true;
      this.customNamesSetting = this.add(new BooleanSetting("CustomNames", defaultCustomNames));
      boolean defaultColoredCount = true;
      this.coloredCountSetting = this.add(new BooleanSetting("ColoredCount", defaultColoredCount));
      this.countsByName = new HashMap<>();
      this.entries = new ArrayList<>();
      Setting<ListFilterMode> itemSelectionVisibilityOption = this.itemSelectionSetting;
      Predicate<Object> itemSelectionVisible = ignoredValue -> {
         Setting<ListFilterMode> visibilitySelectionOption = this.selectionSetting;
         ListFilterMode visibilitySelection = visibilitySelectionOption.getValue();
         EntityType visibilityItemType = EntityTypes.ITEM;
         Setting<Set<EntityType<?>>> visibilityEntityTypesOption = this.entitiesSetting;
         Collection<?> visibleEntityTypes = visibilityEntityTypesOption.getValue();
         return ListFilterMode.matches(visibilitySelection, visibilityItemType, visibleEntityTypes);
      };
      itemSelectionVisibilityOption.addVisibilityPredicate(itemSelectionVisible);
      ResizableHudWidgetRenderer widget = new ResizableHudWidgetRenderer(this);
      widget.markAlignsContent();
      widget.enableHudScale();
      this.setRenderer(widget);
   }

   private record EntityCount(String displayName, int count) {
      static EntityListModule.EntityCount fromEntry(Entry<String, Integer> entry) {
         return new EntityListModule.EntityCount(entry.getKey(), entry.getValue());
      }

      public String formatText(boolean coloredCount) {
         if (coloredCount) {
            String nameForColoredText = this.displayName;
            ChatFormatting whiteFormatting = ChatFormatting.WHITE;
            String whiteCode = String.valueOf(whiteFormatting);
            int coloredCountValue = this.count;
            String countText = String.valueOf(coloredCountValue);
            return "\u0001\u0001 x\u0001"
               .replaceFirst("\u0001", Matcher.quoteReplacement(String.valueOf(nameForColoredText)))
               .replaceFirst("\u0001", Matcher.quoteReplacement(String.valueOf(whiteCode)))
               .replaceFirst("\u0001", Matcher.quoteReplacement(countText));
         } else {
            String nameForPlainText = this.displayName;
            int plainCount = this.count;
            String countText = String.valueOf(plainCount);
            return "\u0001 x\u0001"
               .replaceFirst("\u0001", Matcher.quoteReplacement(String.valueOf(nameForPlainText)))
               .replaceFirst("\u0001", Matcher.quoteReplacement(countText));
         }
      }
   }

   public enum SortMode implements NamedEntry {
      ALPHABET("Alphabet"),
      COUNT("Count"),
      LENGTH("Length");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      SortMode(String displayName) {
         this.displayName = displayName;
      }
   }
}
