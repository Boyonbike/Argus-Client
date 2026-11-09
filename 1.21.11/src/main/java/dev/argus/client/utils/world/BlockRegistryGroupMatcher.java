package dev.argus.client.utils.world;

import dev.argus.client.utils.collections.NamedRegistryEntry;
import dev.argus.client.utils.collections.RegistryGroupMatcher;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.ConcretePowderBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.TrapDoorBlock;

public class BlockRegistryGroupMatcher implements RegistryGroupMatcher {
   private static final Map<String, List<NamedRegistryEntry>> groups = new HashMap<>();

   @Override
   public Identifier getRegistryId() {
      return Registries.BLOCK.identifier();
   }

   @Override
   public Collection getGroupNames() {
      Map<String, List<NamedRegistryEntry>> availableGroups = groups;
      return availableGroups.keySet();
   }

   private static void registerGroup(String groupName, Stream<Block> blocks) {
      Map<String, List<NamedRegistryEntry>> targetGroups = groups;
      Function<Block, NamedRegistryEntry> toEntry = block -> new NamedRegistryEntry(getBlockName(block), block);
      Stream<NamedRegistryEntry> entries = blocks.map(toEntry);
      List<NamedRegistryEntry> groupEntries = entries.toList();
      targetGroups.put(groupName, groupEntries);
   }

   private static void registerGroup(String groupName, Predicate<String> nameFilter) {
      Stream<Reference<Block>> holders = BuiltInRegistries.BLOCK.listElements();
      Predicate<Reference<Block>> holderFilter = holder -> {
         Predicate<ResourceKey<Block>> keyFilter = registryKey -> {
            Identifier id = registryKey.identifier();
            String name = id.toShortLanguageKey();
            return nameFilter.test(name);
         };
         return holder.is(keyFilter);
      };
      Stream<Reference<Block>> matchingHolders = holders.filter(holderFilter);
      Function<Reference<Block>, Block> holderValue = Reference::value;
      Stream<Block> matchingBlocks = matchingHolders.map(holderValue);
      registerGroup(groupName, matchingBlocks);
   }

   @Override
   public Collection getGroupEntries(String groupName) {
      Map<String, List<NamedRegistryEntry>> availableGroups = groups;
      String lowerCaseName = groupName.toLowerCase();
      List<NamedRegistryEntry> emptyGroup = Collections.emptyList();
      return availableGroups.getOrDefault(lowerCaseName, emptyGroup);
   }

   private static String getBlockName(Block block) {
      Identifier id = BuiltInRegistries.BLOCK.getKey(block);
      return id.toShortLanguageKey();
   }

   static {
      Stream<Block> bedsBlocks = BuiltInRegistries.BLOCK.stream();
      Predicate<Block> bedsFilter = block -> block instanceof BedBlock;
      Stream<Block> bedsMatches = bedsBlocks.filter(bedsFilter);
      registerGroup("beds", bedsMatches);
      Stream<Block> bannersBlocks = BuiltInRegistries.BLOCK.stream();
      Predicate<Block> bannersFilter = block -> block instanceof BannerBlock;
      Stream<Block> bannersMatches = bannersBlocks.filter(bannersFilter);
      registerGroup("banners", bannersMatches);
      Stream<Block> doorsBlocks = BuiltInRegistries.BLOCK.stream();
      Predicate<Block> doorsFilter = block -> block instanceof DoorBlock;
      Stream<Block> doorsMatches = doorsBlocks.filter(doorsFilter);
      registerGroup("doors", doorsMatches);
      Stream<Block> trapdoorsBlocks = BuiltInRegistries.BLOCK.stream();
      Predicate<Block> trapdoorsFilter = block -> block instanceof TrapDoorBlock;
      Stream<Block> trapdoorsMatches = trapdoorsBlocks.filter(trapdoorsFilter);
      registerGroup("trapdoors", trapdoorsMatches);
      Stream<Block> slabsBlocks = BuiltInRegistries.BLOCK.stream();
      Predicate<Block> slabsFilter = block -> block instanceof SlabBlock;
      Stream<Block> slabsMatches = slabsBlocks.filter(slabsFilter);
      registerGroup("slabs", slabsMatches);
      Stream<Block> shulkersBlocks = BuiltInRegistries.BLOCK.stream();
      Predicate<Block> shulkersFilter = block -> block instanceof ShulkerBoxBlock;
      Stream<Block> shulkersMatches = shulkersBlocks.filter(shulkersFilter);
      registerGroup("shulkers", shulkersMatches);
      Stream<Block> carpetsBlocks = BuiltInRegistries.BLOCK.stream();
      Predicate<Block> carpetsFilter = block -> block instanceof CarpetBlock;
      Stream<Block> carpetsMatches = carpetsBlocks.filter(carpetsFilter);
      registerGroup("carpets", carpetsMatches);
      Stream<Block> glassesBlocks = BuiltInRegistries.BLOCK.stream();
      Predicate<Block> glassesFilter = block -> block instanceof StainedGlassBlock || block == Blocks.GLASS;
      Stream<Block> glassesMatches = glassesBlocks.filter(glassesFilter);
      registerGroup("glasses", glassesMatches);
      Stream<Block> glassPanesBlocks = BuiltInRegistries.BLOCK.stream();
      Predicate<Block> glassPanesFilter = block -> block instanceof StainedGlassPaneBlock || block == Blocks.GLASS_PANE;
      Stream<Block> glassPanesMatches = glassPanesBlocks.filter(glassPanesFilter);
      registerGroup("glass_panes", glassPanesMatches);
      Stream<Block> signsBlocks = BuiltInRegistries.BLOCK.stream();
      Predicate<Block> signsFilter = block -> block instanceof SignBlock;
      Stream<Block> signsMatches = signsBlocks.filter(signsFilter);
      registerGroup("signs", signsMatches);
      Stream<Block> railsBlocks = BuiltInRegistries.BLOCK.stream();
      Predicate<Block> railsFilter = block -> block instanceof BaseRailBlock;
      Stream<Block> railsMatches = railsBlocks.filter(railsFilter);
      registerGroup("rails", railsMatches);
      Stream<Block> concretePowderBlocks = BuiltInRegistries.BLOCK.stream();
      Predicate<Block> concretePowderFilter = block -> block instanceof ConcretePowderBlock;
      Stream<Block> concretePowderMatches = concretePowderBlocks.filter(concretePowderFilter);
      registerGroup("concrete_powder", concretePowderMatches);
      Predicate<String> oresFilter = name -> name.contains("_ore");
      registerGroup("ores", oresFilter);
      Predicate<String> planksFilter = name -> name.contains("_plank");
      registerGroup("planks", planksFilter);
      Predicate<String> logsFilter = name -> name.contains("_log");
      registerGroup("logs", logsFilter);
      Predicate<String> bricksFilter = name -> name.contains("brick");
      registerGroup("bricks", bricksFilter);
      Predicate<String> headsFilter = name -> name.contains("skull") || name.contains("head");
      registerGroup("heads", headsFilter);
      Predicate<String> candlesFilter = name -> name.contains("candle");
      registerGroup("candles", candlesFilter);
      Predicate<String> concreteFilter = name -> name.contains("_concrete");
      registerGroup("concrete", concreteFilter);
   }
}
