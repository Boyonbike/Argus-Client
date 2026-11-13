package dev.argus.client.modules.basehunting;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.chat.ChatUtils;
import dev.argus.client.utils.world.Waypoint;
import dev.argus.client.utils.world.WorldUtils;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * One press writes up the stash around you (dubs, shulkers, barrels and so on, with coordinates, dimension, server and
 * time), copies it to your clipboard to paste in your clan's stash channel, and turns itself off again. Comet's
 * version posted it with a Discord webhook; Argus does not send webhooks, so this copies it instead.
 */
public class StashReportModule extends Module {
   private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
   private final Setting<Integer> radiusSetting = this.add(new NumberSetting<>("Radius", 4, 1, 16).withUnit(" chunks"));
   private final Setting<Boolean> coordsSetting = this.add(new BooleanSetting("Coords", true));
   private final Setting<Boolean> serverSetting = this.add(new BooleanSetting("Server", true));
   private final Setting<Boolean> waypointSetting = this.add(new BooleanSetting("Waypoint", false));

   public StashReportModule() {
      super("StashReport", "Copies a write-up of the stash around you to your clipboard for your clan's stash channel.", ModuleCategory.BASE_HUNTING);
   }

   @Override
   public void onEnable() {
      if (!this.isWorldUnavailable()) {
         String report = this.buildReport();
         ClientAccess.argusclientMinecraft.keyboardHandler.setClipboard(report);
         ChatUtils.printChat(Component.literal("Stash report copied to your clipboard: ").append(ChatUtils.watermarkText(report)), ChatUtils.moduleSignature(this));
         if (this.waypointSetting.getValue()) {
            LocalPlayer player = ClientAccess.argusclientMinecraft.player;
            String dimension = WorldUtils.getDimension().getDisplayName().toLowerCase();
            String name = "Stash " + LocalDateTime.now().format(TIME);
            ClientContext.waypointRegistry.registerWaypoint(new Waypoint(name, player.position(), dimension, HuntCompat.serverKey()));
         }
      }

      this.disable();
   }

   private String buildReport() {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      ChunkPos center = player.chunkPosition();
      int radius = this.radiusSetting.getValue();
      int chests = 0;
      int shulkers = 0;
      int barrels = 0;
      int enderChests = 0;
      int hoppers = 0;

      for (LevelChunk chunk : WorldUtils.getLoadedChunks()) {
         ChunkPos position = chunk.getPos();
         if (Math.max(Math.abs(HuntCompat.chunkX(position) - HuntCompat.chunkX(center)), Math.abs(HuntCompat.chunkZ(position) - HuntCompat.chunkZ(center))) > radius) {
            continue;
         }

         for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            if (blockEntity.isRemoved()) {
               continue;
            }

            if (blockEntity.getBlockState().is(Blocks.CHEST)) {
               chests++;
            } else if (blockEntity instanceof ShulkerBoxBlockEntity) {
               shulkers++;
            } else if (blockEntity instanceof BarrelBlockEntity) {
               barrels++;
            } else if (blockEntity instanceof EnderChestBlockEntity) {
               enderChests++;
            } else if (blockEntity instanceof HopperBlockEntity) {
               hoppers++;
            }
         }
      }

      int frames = 0;
      int chestCarts = 0;
      double reach = (radius + 0.5) * 16.0;
      for (Entity entity : ClientAccess.argusclientMinecraft.level.entitiesForRendering()) {
         if (Math.abs(entity.getX() - player.getX()) > reach || Math.abs(entity.getZ() - player.getZ()) > reach) {
            continue;
         }

         if (entity instanceof ItemFrame) {
            frames++;
         } else if (entity instanceof MinecartChest) {
            chestCarts++;
         }
      }

      List<String> parts = new ArrayList<>();
      parts.add(formatDubs(chests) + " dubs");
      addCount(parts, shulkers, "shulker box", "shulker boxes");
      addCount(parts, barrels, "barrel", "barrels");
      addCount(parts, enderChests, "ender chest", "ender chests");
      addCount(parts, hoppers, "hopper", "hoppers");
      addCount(parts, chestCarts, "chest minecart", "chest minecarts");
      addCount(parts, frames, "item frame", "item frames");

      StringBuilder report = new StringBuilder("Stash: ").append(String.join(", ", parts));
      if (this.coordsSetting.getValue()) {
         BlockPos position = player.blockPosition();
         report.append(" at ").append(position.getX()).append(' ').append(position.getY()).append(' ').append(position.getZ());
      }

      report.append(" (").append(WorldUtils.getDimension().getDisplayName()).append(')');
      if (this.serverSetting.getValue()) {
         report.append(" on ").append(HuntCompat.serverKey());
      }

      report.append(", ").append(LocalDateTime.now().format(TIME));
      return report.toString();
   }

   /** A double chest is two chest blocks, so an odd count leaves half a dub. */
   static String formatDubs(int chests) {
      return chests % 2 == 0 ? String.valueOf(chests / 2) : chests / 2 + ".5";
   }

   private static void addCount(List<String> parts, int count, String one, String many) {
      if (count > 0) {
         parts.add(count + " " + (count == 1 ? one : many));
      }
   }
}
