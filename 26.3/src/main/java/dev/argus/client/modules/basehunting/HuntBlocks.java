package dev.argus.client.modules.basehunting;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

/** Looks up registry ids, skipping any that this Minecraft version does not have. */
final class HuntBlocks {
   private HuntBlocks() {
   }

   /** Containers people keep things in: chests, barrels, shulker boxes, ender chests, hoppers, dispensers and droppers. */
   static boolean isStorage(BlockEntity blockEntity) {
      return blockEntity instanceof ChestBlockEntity
         || blockEntity instanceof BarrelBlockEntity
         || blockEntity instanceof ShulkerBoxBlockEntity
         || blockEntity instanceof EnderChestBlockEntity
         || blockEntity instanceof HopperBlockEntity
         || blockEntity instanceof DispenserBlockEntity;
   }

   static Block[] blocks(String... ids) {
      List<Block> found = new ArrayList<>();
      for (String id : ids) {
         Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(Identifier.withDefaultNamespace(id));
         if (block.isPresent() && !found.contains(block.get())) {
            found.add(block.get());
         }
      }

      return found.toArray(Block[]::new);
   }

   static Item[] items(String... ids) {
      List<Item> found = new ArrayList<>();
      for (String id : ids) {
         Optional<Item> item = BuiltInRegistries.ITEM.getOptional(Identifier.withDefaultNamespace(id));
         if (item.isPresent() && !found.contains(item.get())) {
            found.add(item.get());
         }
      }

      return found.toArray(Item[]::new);
   }

   static EntityType<?>[] entities(String... ids) {
      List<EntityType<?>> found = new ArrayList<>();
      for (String id : ids) {
         Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.withDefaultNamespace(id));
         if (type.isPresent() && !found.contains(type.get())) {
            found.add(type.get());
         }
      }

      return found.toArray(EntityType<?>[]::new);
   }
}
