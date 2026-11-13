package dev.argus.client.modules.basehunting;

/** Default block lists for BaseFinder, as registry ids so blocks missing from a Minecraft version are just skipped. */
final class BaseFinderBlocks {
   private BaseFinderBlocks() {
   }

   static final String[] PLAYER_ONLY = {
      "crafter", "spruce_sapling", "oak_sapling", "birch_sapling", "jungle_sapling", "cherry_sapling", "bamboo_sapling", "cherry_button",
      "cherry_door", "cherry_fence", "cherry_fence_gate", "cherry_planks", "cherry_pressure_plate", "cherry_stairs", "cherry_wood",
      "cherry_trapdoor", "cherry_slab", "mangrove_planks", "mangrove_button", "mangrove_door", "mangrove_fence", "mangrove_fence_gate",
      "mangrove_stairs", "mangrove_slab", "mangrove_trapdoor", "birch_door", "birch_fence_gate", "birch_button", "acacia_button", "dark_oak_button",
      "polished_blackstone_button", "spruce_button", "bamboo_block", "bamboo_button", "bamboo_door", "bamboo_fence", "bamboo_fence_gate",
      "bamboo_mosaic", "bamboo_mosaic_slab", "bamboo_mosaic_stairs", "bamboo_planks", "bamboo_pressure_plate", "bamboo_slab", "bamboo_stairs",
      "bamboo_trapdoor", "chiseled_bookshelf", "black_concrete", "blue_concrete", "cyan_concrete", "brown_concrete", "orange_concrete",
      "magenta_concrete", "light_blue_concrete", "yellow_concrete", "lime_concrete", "pink_concrete", "gray_concrete", "light_gray_concrete",
      "purple_concrete", "green_concrete", "black_concrete_powder", "blue_concrete_powder", "cyan_concrete_powder", "brown_concrete_powder",
      "white_concrete_powder", "orange_concrete_powder", "magenta_concrete_powder", "light_blue_concrete_powder", "yellow_concrete_powder",
      "lime_concrete_powder", "pink_concrete_powder", "gray_concrete_powder", "light_gray_concrete_powder", "purple_concrete_powder",
      "green_concrete_powder", "red_concrete_powder", "purple_terracotta", "magenta_terracotta", "pink_terracotta", "magenta_glazed_terracotta",
      "pink_glazed_terracotta", "gray_glazed_terracotta", "blue_glazed_terracotta", "brown_glazed_terracotta", "green_glazed_terracotta",
      "oxidized_copper", "cut_copper", "exposed_cut_copper", "weathered_cut_copper", "cut_copper_slab", "cut_copper_stairs",
      "exposed_cut_copper_slab", "exposed_cut_copper_stairs", "weathered_cut_copper_slab", "weathered_cut_copper_stairs", "oxidized_cut_copper_slab",
      "oxidized_cut_copper_stairs", "copper_bulb", "exposed_copper_bulb", "weathered_copper_bulb", "oxidized_copper_bulb", "chiseled_copper",
      "exposed_chiseled_copper", "weathered_chiseled_copper", "oxidized_chiseled_copper", "copper_door", "exposed_copper_door",
      "weathered_copper_door", "oxidized_copper_door", "copper_grate", "exposed_copper_grate", "weathered_copper_grate", "oxidized_copper_grate",
      "copper_trapdoor", "exposed_copper_trapdoor", "weathered_copper_trapdoor", "waxed_exposed_copper", "waxed_weathered_copper",
      "waxed_exposed_cut_copper", "waxed_weathered_cut_copper", "waxed_exposed_cut_copper_slab", "waxed_exposed_cut_copper_stairs",
      "waxed_weathered_cut_copper_slab", "waxed_weathered_cut_copper_stairs", "waxed_exposed_chiseled_copper", "waxed_weathered_chiseled_copper",
      "waxed_exposed_copper_door", "waxed_weathered_copper_door", "waxed_exposed_copper_grate", "waxed_weathered_copper_grate",
      "waxed_copper_trapdoor", "waxed_exposed_copper_trapdoor", "waxed_weathered_copper_trapdoor", "soul_torch", "soul_wall_torch",
      "potted_mangrove_propagule", "potted_azalea", "potted_cherry_sapling", "potted_fern", "potted_acacia_sapling", "potted_warped_fungus",
      "potted_warped_roots", "potted_crimson_fungus", "potted_crimson_roots", "potted_oak_sapling", "potted_wither_rose", "wither_rose", "cake",
      "candle_cake", "blue_candle_cake", "black_candle_cake", "brown_candle_cake", "cyan_candle_cake", "gray_candle_cake", "green_candle_cake",
      "light_blue_candle_cake", "light_gray_candle_cake", "lime_candle_cake", "magenta_candle_cake", "orange_candle_cake", "pink_candle_cake",
      "purple_candle_cake", "red_candle_cake", "white_candle_cake", "yellow_candle_cake", "blue_candle", "black_candle", "brown_candle",
      "cyan_candle", "gray_candle", "green_candle", "light_blue_candle", "light_gray_candle", "lime_candle", "magenta_candle", "orange_candle",
      "pink_candle", "purple_candle", "yellow_candle", "smooth_red_sandstone", "chiseled_red_sandstone", "cut_red_sandstone",
      "smooth_red_sandstone_slab", "smooth_red_sandstone_stairs", "cut_red_sandstone_slab", "red_sandstone_slab", "red_sandstone_stairs",
      "red_sandstone_wall", "andesite_stairs", "andesite_slab", "andesite_wall", "polished_andesite_slab", "polished_andesite_stairs",
      "polished_granite_slab", "polished_granite_stairs", "polished_diorite_slab", "polished_diorite_stairs", "tuff_slab", "tuff_stairs",
      "tuff_wall", "tuff_brick_slab", "tuff_brick_stairs", "tuff_brick_wall", "cracked_nether_bricks", "chiseled_nether_bricks", "red_nether_bricks",
      "nether_brick_slab", "nether_brick_wall", "red_nether_brick_slab", "red_nether_brick_stairs", "red_nether_brick_wall", "orange_stained_glass",
      "light_blue_stained_glass", "yellow_stained_glass", "lime_stained_glass", "pink_stained_glass", "cyan_stained_glass", "purple_stained_glass",
      "blue_stained_glass", "green_stained_glass", "red_stained_glass", "crimson_pressure_plate", "crimson_button", "crimson_door", "crimson_fence",
      "crimson_fence_gate", "crimson_planks", "crimson_sign", "crimson_wall_sign", "crimson_slab", "crimson_stairs", "crimson_trapdoor",
      "warped_pressure_plate", "warped_button", "warped_door", "warped_fence", "warped_fence_gate", "warped_planks", "warped_sign",
      "warped_wall_sign", "warped_slab", "warped_stairs", "warped_trapdoor", "scaffolding", "cherry_sign", "cherry_wall_sign", "oak_sign",
      "spruce_sign", "acacia_sign", "acacia_wall_sign", "birch_sign", "birch_wall_sign", "dark_oak_sign", "dark_oak_wall_sign", "jungle_sign",
      "jungle_wall_sign", "mangrove_sign", "mangrove_wall_sign", "slime_block", "sponge", "tinted_glass", "acacia_hanging_sign",
      "acacia_wall_hanging_sign", "bamboo_hanging_sign", "bamboo_wall_hanging_sign", "birch_hanging_sign", "birch_wall_hanging_sign",
      "cherry_hanging_sign", "cherry_wall_hanging_sign", "crimson_hanging_sign", "crimson_wall_hanging_sign", "dark_oak_hanging_sign",
      "dark_oak_wall_hanging_sign", "jungle_hanging_sign", "jungle_wall_hanging_sign", "mangrove_hanging_sign", "mangrove_wall_hanging_sign",
      "oak_hanging_sign", "oak_wall_hanging_sign", "spruce_hanging_sign", "spruce_wall_hanging_sign", "warped_hanging_sign",
      "warped_wall_hanging_sign", "chiseled_quartz_block", "quartz_pillar", "quartz_bricks", "quartz_stairs", "ochre_froglight",
      "pearlescent_froglight", "verdant_froglight", "petrified_oak_slab", "stripped_bamboo_block", "stripped_cherry_log", "stripped_cherry_wood",
      "stripped_acacia_wood", "birch_wood", "stripped_birch_log", "stripped_birch_wood", "crimson_hyphae", "stripped_crimson_hyphae",
      "stripped_crimson_stem", "dark_oak_wood", "stripped_dark_oak_log", "stripped_dark_oak_wood", "stripped_jungle_log", "stripped_jungle_wood",
      "stripped_mangrove_log", "stripped_mangrove_wood", "warped_hyphae", "stripped_warped_hyphae", "stripped_warped_stem", "shulker_box",
      "black_shulker_box", "blue_shulker_box", "brown_shulker_box", "cyan_shulker_box", "gray_shulker_box", "green_shulker_box",
      "light_blue_shulker_box", "light_gray_shulker_box", "lime_shulker_box", "magenta_shulker_box", "orange_shulker_box", "pink_shulker_box",
      "purple_shulker_box", "red_shulker_box", "white_shulker_box", "yellow_shulker_box", "lava_cauldron", "powder_snow_cauldron", "activator_rail",
      "beacon", "beehive", "repeating_command_block", "command_block", "chain_command_block", "emerald_block", "iron_block", "netherite_block",
      "raw_gold_block", "conduit", "daylight_detector", "detector_rail", "dried_kelp_block", "dropper", "enchanting_table", "piglin_head",
      "piglin_wall_head", "creeper_head", "creeper_wall_head", "dragon_wall_head", "dragon_head", "player_head", "player_wall_head", "zombie_head",
      "zombie_wall_head", "skeleton_wall_skull", "wither_skeleton_skull", "wither_skeleton_wall_skull", "heavy_core", "honey_block",
      "honeycomb_block", "jukebox", "lodestone", "observer", "powered_rail", "heavy_weighted_pressure_plate", "light_weighted_pressure_plate",
      "polished_blackstone_pressure_plate", "birch_pressure_plate", "jungle_pressure_plate", "dark_oak_pressure_plate", "mangrove_pressure_plate",
      "respawn_anchor", "calibrated_sculk_sensor", "sniffer_egg", "resin_block", "resin_bricks", "resin_brick_slab", "resin_brick_wall",
      "resin_brick_stairs", "chiseled_resin_bricks", "potted_closed_eyeblossom", "potted_open_eyeblossom", "potted_pale_oak_sapling",
      "pale_oak_sapling", "pale_oak_button", "pale_oak_door", "pale_oak_fence", "pale_oak_fence_gate", "pale_oak_planks", "pale_oak_pressure_plate",
      "pale_oak_hanging_sign", "pale_oak_sign", "pale_oak_wall_sign", "pale_oak_wall_hanging_sign", "pale_oak_slab", "pale_oak_stairs",
      "pale_oak_trapdoor", "pale_oak_wood", "stripped_pale_oak_wood", "copper_bars", "copper_chain", "copper_lantern", "copper_chest",
      "exposed_copper_chest", "oxidized_copper_chest", "weathered_copper_chest", "waxed_copper_chest", "waxed_exposed_copper_chest",
      "waxed_oxidized_copper_chest", "waxed_weathered_copper_chest", "copper_golem_statue", "exposed_copper_golem_statue",
      "weathered_copper_golem_statue", "oxidized_copper_golem_statue", "waxed_copper_golem_statue", "waxed_exposed_copper_golem_statue",
      "waxed_weathered_copper_golem_statue", "waxed_oxidized_copper_golem_statue", "copper_torch", "copper_wall_torch", "lightning_rod",
      "exposed_lightning_rod", "weathered_lightning_rod", "oxidized_lightning_rod", "waxed_lightning_rod", "waxed_exposed_lightning_rod",
      "waxed_weathered_lightning_rod", "waxed_oxidized_lightning_rod", "oak_shelf", "dark_oak_shelf", "pale_oak_shelf", "acacia_shelf",
      "bamboo_shelf", "birch_shelf", "cherry_shelf", "crimson_shelf", "jungle_shelf", "mangrove_shelf", "spruce_shelf", "warped_shelf",
      "golden_dandelion", "potted_golden_dandelion"
   };

   static final String[] UNCOMMON = {
      "spruce_wall_sign", "polished_diorite", "note_block", "mangrove_wood", "weathered_copper"
   };

   static final String[] UTILITY = {
      "crafting_table", "brewing_stand", "ender_chest", "smooth_quartz", "redstone_block", "diamond_block", "brown_stained_glass"
   };

   static final String[] BUILDING = {
      "oak_wall_sign", "trapped_chest", "iron_trapdoor", "lapis_block"
   };

   static final String[] COMMON = {
      "quartz_block", "furnace", "black_bed", "gray_bed", "light_blue_bed", "light_gray_bed", "pink_bed", "red_bed", "white_bed", "yellow_bed",
      "orange_bed", "blue_bed", "cyan_bed", "green_bed", "lime_bed", "purple_bed", "magenta_bed", "brown_bed", "white_concrete"
   };

   static final String[] REDSTONE = {
      "redstone_torch", "hopper"
   };
}
