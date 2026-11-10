package dev.argus.client.modules.hud;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.managers.ServerStateManager;
import dev.argus.client.managers.WorldStateManager;
import dev.argus.client.modules.HudWidgetModule;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.HudAnchor;
import dev.argus.client.utils.FrameTiming;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

public class MetricsModule extends HudWidgetModule {
   private final Setting<Boolean> speedSetting = this.add(new BooleanSetting("Speed", true).markVisibilityDependency());
   private final Setting<Boolean> bpsSetting = this.add(new BooleanSetting("BPS", false).dependsOn(this.speedSetting));
   private final Setting<Boolean> tpsSetting = this.add(new BooleanSetting("TPS", true));
   private final Setting<Boolean> pingSetting = this.add(new BooleanSetting("Ping", true));
   private final Setting<Boolean> fpsSetting = this.add(new BooleanSetting("FPS", true));
   private final Setting<Boolean> serverBrandSetting = this.add(new BooleanSetting("ServerBrand", true));
   private final Setting<Boolean> durabilitySetting = this.add(new BooleanSetting("Durability", false));
   private final Setting<Boolean> shulkersSetting = this.add(new BooleanSetting("Shulkers", false));
   private final Setting<Boolean> chestSetting = this.add(new BooleanSetting("Chest", false).markVisibilityDependency());
   private final Setting<Boolean> doubleChestSetting = this.add(new BooleanSetting("Double", false).dependsOn(this.chestSetting));
   private final List<HudTextRow> metricRows = new ArrayList<>();
   private Component cachedDurabilityText;
   private int chestAndBarrelCount;
   private int shulkerCount;

   private static Component formatStackDurability(ItemStack stack) {
      int maximumDamage = stack.getMaxDamage();
      int remainingDurability = maximumDamage - stack.getDamageValue();
      float remainingFraction = (float)remainingDurability / maximumDamage;
      Color durabilityColor = ColorUtils.redToGreen(remainingFraction);
      int textColor = durabilityColor.hashCode();
      double fractionForNaNCheck = remainingFraction;
      if (Double.isNaN(fractionForNaNCheck)) {
         textColor = -1;
      }

      String text = String.valueOf(remainingDurability);
      MutableComponent component = Component.literal(text);
      int colorArgument = textColor;
      return component.withColor(colorArgument);
   }

   private Component getHeldItemDurability() {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      ItemStack heldStack = player.getMainHandItem();
      Component durabilityText = formatStackDurability(heldStack);
      if (heldStack.getMaxDamage() == 0 && this.cachedDurabilityText != null) {
         return this.cachedDurabilityText;
      }

      this.cachedDurabilityText = durabilityText;
      return durabilityText;
   }

   @Override
   public void onEnable() {
      // Built once: the rows keep their identity so lines moved on their own in the HUD editor stay put.
      if (!this.metricRows.isEmpty()) return;
      List<HudTextRow> rowsForRegistration = this.metricRows;
      HudTextRow speedRow = new HudTextRow(() -> {
         Setting<Boolean> bpsOptionForSuffix = this.bpsSetting;
         Boolean useBpsSuffix = bpsOptionForSuffix.getValue();
         return formatSpeedMetric(this, useBpsSuffix);
      }, this.speedSetting::getValue);
      HudTextRow tpsRow = new HudTextRow(() -> {
         Object[] formatArguments = new Object[]{ChatFormatting.WHITE, null};
         ServerStateManager networkTracker = ClientContext.serverStateManager;
         float ticksPerSecond = networkTracker.getTickRate();
         formatArguments[1] = ticksPerSecond;
         String format = "TPS %s%.2f";
         String formattedText = format.formatted(formatArguments);
         String text = formattedText.replace(",", ".");
         return Component.literal(text);
      }, this.tpsSetting::getValue);
      HudTextRow pingRow = new HudTextRow(() -> {
         Object[] formatArguments = new Object[]{ChatFormatting.WHITE, null};
         ServerStateManager networkTracker = ClientContext.serverStateManager;
         int pingMillis = networkTracker.getLatencyMillis();
         formatArguments[1] = pingMillis;
         String format = "Ping %s%dms";
         String text = format.formatted(formatArguments);
         return Component.literal(text);
      }, this.pingSetting::getValue);
      HudTextRow fpsRow = new HudTextRow(() -> {
         Object[] formatArguments = new Object[]{ChatFormatting.WHITE, null};
         FrameTiming frameTiming = ClientContext.frameTiming;
         int framesPerSecond = frameTiming.getFramesPerSecond();
         formatArguments[1] = framesPerSecond;
         String format = "FPS %s%d";
         String text = format.formatted(formatArguments);
         return Component.literal(text);
      }, this.fpsSetting::getValue);
      HudTextRow serverBrandRow = new HudTextRow(() -> {
         Object[] formatArguments = new Object[]{ChatFormatting.WHITE, null};
         ClientPacketListener connection = ClientAccess.argusclientMinecraft.player.connection;
         formatArguments[1] = connection.serverBrand();
         String format = "ServerBrand %s%s";
         String text = format.formatted(formatArguments);
         return Component.literal(text);
      }, this.serverBrandSetting::getValue);
      HudTextRow durabilityRow = new HudTextRow(() -> formatDurabilityMetric(this), () -> {
         Setting<Boolean> durabilityOption = this.durabilitySetting;
         Boolean showDurability = durabilityOption.getValue();
         if (!showDurability) {
            return false;
         }

         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         ItemStack heldStack = player.getMainHandItem();
         return heldStack.isDamageableItem();
      });
      HudTextRow chestRow = new HudTextRow(() -> {
         Object[] formatArguments = new Object[]{ChatFormatting.WHITE, null};
         int count = this.chestAndBarrelCount;
         formatArguments[1] = count;
         String format = "Chests %s%d";
         String text = format.formatted(formatArguments);
         return Component.literal(text);
      }, this.chestSetting::getValue);
      HudTextRow shulkerRow = new HudTextRow(() -> {
         Object[] formatArguments = new Object[]{ChatFormatting.WHITE, null};
         int count = this.shulkerCount;
         formatArguments[1] = count;
         String format = "Shulkers %s%d";
         String text = format.formatted(formatArguments);
         return Component.literal(text);
      }, this.shulkersSetting::getValue);
      speedRow.withKey("speed"); tpsRow.withKey("tps"); pingRow.withKey("ping"); fpsRow.withKey("fps");
      serverBrandRow.withKey("brand"); durabilityRow.withKey("durability"); chestRow.withKey("chests"); shulkerRow.withKey("shulkers");
      rowsForRegistration.addAll(List.of(speedRow, tpsRow, pingRow, fpsRow, serverBrandRow, durabilityRow, chestRow, shulkerRow));
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent ignoredEvent) {
      List<HudTextRow> rowsToSort = this.metricRows;
      Function<HudTextRow, Float> metricWidth = row -> {
         FontRenderer textRenderer = FontRenderer.INSTANCE;
         return measureMetricWidth(textRenderer, row);
      };
      Comparator<HudTextRow> metricComparator = Comparator.comparing(metricWidth);
      rowsToSort.sort(metricComparator);
      this.shulkerCount = this.chestAndBarrelCount = 0;
      WorldStateManager worldTracker = ClientContext.worldStateManager;
      countContainers(this, worldTracker);
   }

   private static Component formatSpeedMetric(MetricsModule owner, boolean useBpsSuffix) {
      String speedTemplate = "Speed %s%.2f" + (useBpsSuffix ? "b/s" : "km/h");
      Object[] formatArguments = new Object[]{ChatFormatting.WHITE, null};
      double displaySpeed = ClientContext.movementSpeedTracker.getDisplaySpeed();
      Setting<Boolean> bpsOptionForValue = owner.bpsSetting;
      Boolean useBpsValue = bpsOptionForValue.getValue();
      double unitDivisor;
      if (useBpsValue) {
         unitDivisor = 3.6;
      } else {
         unitDivisor = 1.0;
      }

      double speedValue = displaySpeed / unitDivisor;
      formatArguments[1] = speedValue;
      String formattedText = speedTemplate.formatted(formatArguments);
      String text = formattedText.replace(",", ".");
      return Component.literal(text);
   }

   private static Component formatDurabilityMetric(MetricsModule owner) {
      String label = "Durability ";
      MutableComponent labelComponent = Component.literal(label);
      Component durabilityText = owner.getHeldItemDurability();
      return labelComponent.append(durabilityText);
   }

   private static float measureMetricWidth(FontRenderer textRenderer, HudTextRow row) {
      Component rowText = row.getText();
      return -textRenderer.getWidth(rowText.getString());
   }

   private static void countContainers(MetricsModule owner, WorldStateManager worldTracker) {
      for (BlockEntity blockEntity : worldTracker.getBlockEntities()) {
         if (blockEntity instanceof ChestBlockEntity || blockEntity instanceof BarrelBlockEntity) {
            owner.chestAndBarrelCount++;
         }

         if (blockEntity instanceof ShulkerBoxBlockEntity) {
            owner.shulkerCount++;
         }
      }

      Setting<Boolean> doubleChestOption = owner.doubleChestSetting;
      Boolean combineChestPairs = doubleChestOption.getValue();
      if (combineChestPairs) {
         int combinedContainerCount = owner.chestAndBarrelCount;
         owner.chestAndBarrelCount = combinedContainerCount / 2;
      }
   }

   private static String[] createAliases() {
      return new String[]{"infohud", "info"};
   }

   public MetricsModule() {
      super("Metrics", createAliases());
      this.setRenderer(new HudTextListRenderer(this, this.metricRows).splittable());
      this.getRenderer().setAnchor(HudAnchor.BOTTOM_RIGHT);
   }
}
