package dev.argus.client.modules.movement.elytratrip;

import dev.argus.client.render.WorldBoxRenderer;
import dev.argus.mixin.AbstractContainerScreenAccessor;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import com.mojang.blaze3d.vertex.PoseStack;

/**
 * Setup mode for the station: role buttons on chest screens, and role-coloured outlines on tagged chests in the world.
 * The trip module binds itself as the {@link Host}.
 */
public final class StationChests {
   /** What the buttons and outlines need from the trip module. */
   public interface Host {
      boolean setupMode();

      StationStore stations();

      /** The server tags are kept under, or null when not on a server. */
      String serverKey();

      void stationsChanged();
   }

   private static final int BUTTON_W = 62;
   private static final int BUTTON_H = 18;
   private static final double SHOW_WITHIN_SQ = 16 * 16;

   private static volatile Host host;
   /** The block last right-clicked, which is the chest whose screen opens next. */
   private static BlockPos lastClicked;

   private StationChests() {
   }

   public static void bind(Host bound) {
      host = bound;
   }

   public static void clicked(BlockPos pos) {
      lastClicked = pos == null ? null : pos.immutable();
   }

   /** The position tags for this chest are stored under, or null when it isn't a chest or barrel. */
   public static BlockPos canonical(Level level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      if (state.getBlock() instanceof ChestBlock) {
         BlockPos other = state.getValue(ChestBlock.TYPE) == ChestType.SINGLE ? null : ChestBlock.getConnectedBlockPos(pos, state);
         return StationStore.canonical(pos, other);
      }
      return state.getBlock() instanceof BarrelBlock ? pos.immutable() : null;
   }

   /** Registered with ScreenEvents.AFTER_INIT: adds the role buttons to a chest or barrel screen while setup mode is on. */
   public static void addSetupButtons(Minecraft mc, Screen screen, int width, int height) {
      Host bound = host;
      if (bound == null || !bound.setupMode() || mc.level == null || bound.serverKey() == null) return;
      if (!(screen instanceof AbstractContainerScreen<?> container) || !(container.getMenu() instanceof ChestMenu)) return;
      BlockPos clicked = lastClicked;
      BlockPos chest = clicked == null ? null : canonical(mc.level, clicked);
      if (chest == null) return;
      var bounds = (AbstractContainerScreenAccessor)container;
      int left = bounds.getX();
      int top = bounds.getY();
      int x = left >= BUTTON_W + 4 ? left - BUTTON_W - 3 : Math.min(width - BUTTON_W - 2, left + bounds.getBgWidth() + 3);
      int y = Math.max(2, Math.min(top, height - (ChestRole.values().length + 1) * (BUTTON_H + 2) - 2));
      String server = bound.serverKey();
      for (ChestRole role : ChestRole.values()) {
         Button button = Button.builder(label(bound, server, chest, role), ignored -> {
            bound.stations().toggle(server, chest, role);
            bound.stationsChanged();
         }).bounds(x, y, BUTTON_W, BUTTON_H).build();
         Screens.getButtons(screen).add(button);
         ScreenEvents.beforeRender(screen).register((s, graphics, mx, my, delta) -> button.setMessage(label(bound, server, chest, role)));
         y += BUTTON_H + 2;
      }
      Button clear = Button.builder(Component.literal("Clear"), ignored -> {
         bound.stations().clear(server, chest);
         bound.stationsChanged();
      }).bounds(x, y, BUTTON_W, BUTTON_H).build();
      Screens.getButtons(screen).add(clear);
   }

   private static Component label(Host bound, String server, BlockPos chest, ChestRole role) {
      boolean tagged = bound.stations().roles(server, chest).contains(role);
      return Component.literal(tagged ? "✔ " + role.label() : role.label()).withColor(tagged ? role.colour() : 0xFFFFFFFF);
   }

   /** Outlines tagged chests in their first role's colour, while setup mode is on or within 16 blocks. */
   public static void render(PoseStack poses, Minecraft mc) {
      Host bound = host;
      if (bound == null || mc.player == null || mc.level == null || bound.serverKey() == null) return;
      boolean setup = bound.setupMode();
      for (Map.Entry<BlockPos, Set<ChestRole>> entry : bound.stations().all(bound.serverKey()).entrySet()) {
         BlockPos pos = entry.getKey();
         if (!setup && mc.player.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > SHOW_WITHIN_SQ) continue;
         ChestRole first = entry.getValue().iterator().next();
         AABB box = new AABB(pos);
         BlockState state = mc.level.getBlockState(pos);
         if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            box = box.minmax(new AABB(ChestBlock.getConnectedBlockPos(pos, state)));
         }
         WorldBoxRenderer.outlineBox(poses, box.inflate(0.02), first.colour(), 2.0F);
      }
   }
}
