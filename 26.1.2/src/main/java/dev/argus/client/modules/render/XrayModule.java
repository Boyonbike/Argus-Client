package dev.argus.client.modules.render;

import dev.argus.client.ClientAccess;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.render.GameOptionsOverrides;
import dev.argus.client.render.IrisShaderBridge;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.RegistrySetSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.ListFilterMode;
import dev.argus.client.utils.world.WorldUtils;
import java.util.Set;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class XrayModule extends Module {
   private static XrayModule instance;
   private final Setting<Set<Block>> blocksSetting = this.add(
      new RegistrySetSetting<>(
         "Blocks",
         BuiltInRegistries.BLOCK,
         Blocks.COAL_ORE,
         Blocks.DEEPSLATE_COAL_ORE,
         Blocks.IRON_ORE,
         Blocks.DEEPSLATE_IRON_ORE,
         Blocks.GOLD_ORE,
         Blocks.DEEPSLATE_GOLD_ORE,
         Blocks.LAPIS_ORE,
         Blocks.DEEPSLATE_LAPIS_ORE,
         Blocks.REDSTONE_ORE,
         Blocks.DEEPSLATE_REDSTONE_ORE,
         Blocks.DIAMOND_ORE,
         Blocks.DEEPSLATE_DIAMOND_ORE,
         Blocks.EMERALD_ORE,
         Blocks.DEEPSLATE_EMERALD_ORE,
         Blocks.NETHER_QUARTZ_ORE,
         Blocks.ANCIENT_DEBRIS
      )
   );
   private final Setting<ListFilterMode> selectionSetting = this.add(new EnumSetting<>("Selection", ListFilterMode.WHITELIST));
   public final Setting<Integer> alphaSetting = this.add(new NumberSetting<>("Alpha", 0, 0, 100).withUnit("%"));
   private final Setting<Boolean> bypassSetting = this.add(new BooleanSetting("Bypass", false));
   private double previousGamma;

   @Override
   public void onEnable() {
      OptionInstance<Double> previousGammaOption = ClientAccess.argusclientMinecraft.options.gamma();
      this.previousGamma = previousGammaOption.get();
      OptionInstance<Double> gammaOption = ClientAccess.argusclientMinecraft.options.gamma();
      GameOptionsOverrides.forceSetOptionValue(gammaOption, 1000.0);
   }

   @Override
   public void onToggle() {
      WorldUtils.reloadRenderer();
   }

   @Override
   public void onDisable() {
      Options options = ClientAccess.argusclientMinecraft.options;
      OptionInstance<Double> gammaOption = options.gamma();
      double savedGamma = this.previousGamma;
      GameOptionsOverrides.forceSetOptionValue(gammaOption, savedGamma);
   }

   private void reloadRendererIfEnabled() {
      if (this.isToggled()) {
         WorldUtils.reloadRenderer();
      }
   }

   public boolean shouldRenderBlock(BlockPos position, Block block) {
      if (block != null && position != null && !this.isWorldUnavailable()) {
         ListFilterMode selectionMode = this.selectionSetting.getValue();
         if (selectionMode.isAny()) {
            return false;
         }

         ListFilterMode mode = this.selectionSetting.getValue();
         Setting<Set<Block>> blocks = this.blocksSetting;
         if (!ListFilterMode.matches(mode, block, blocks)) {
            return false;
         }

         Setting<Boolean> bypass = this.bypassSetting;
         return !bypass.getValue() ? true : this.hasNonOccludingNeighbor(position);
      } else {
         return false;
      }
   }

   public boolean shouldHideUnselectedBlocks() {
      Setting<Integer> alpha = this.alphaSetting;
      return !alpha.isAtMaximum() && IrisShaderBridge.isHandRendererAvailable() && IrisShaderBridge.isShaderPackPresent()
         ? true
         : this.alphaSetting.getValue() == 0;
   }

   public static XrayModule getInstance() {
      return instance;
   }

   private boolean hasNonOccludingNeighbor(BlockPos position) {
      for (Direction direction : Direction.values()) {
         ClientLevel level = ClientAccess.argusclientMinecraft.level;
         BlockPos neighborPosition = position.relative(direction);
         BlockState neighborState = level.getBlockState(neighborPosition);
         if (!neighborState.canOcclude()) {
            return true;
         }
      }

      return false;
   }

   public XrayModule() {
      super("Xray", "Wallhack on ores.", ModuleCategory.RENDER);
      instance = this;
      this.selectionSetting.setChangeCallback(this::reloadRendererIfEnabled);
      this.blocksSetting.setChangeCallback(this::reloadRendererIfEnabled);
      this.bypassSetting.setChangeCallback(this::reloadRendererIfEnabled);
      this.alphaSetting.setChangeCallback(this::reloadRendererIfEnabled);
   }
}
