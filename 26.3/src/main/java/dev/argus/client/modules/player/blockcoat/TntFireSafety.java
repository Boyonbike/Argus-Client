package dev.argus.client.modules.player.blockcoat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Function;
import java.util.function.Predicate;

/** Keeps TNT out of reach of fire and lava, which can light it without any redstone. */
public final class TntFireSafety {
    /** Fire lights adjacent TNT and spreads one block sideways and up to four up; lava starts fires up to three up. */
    public static final int HORIZONTAL = 3;
    /** How far below the TNT a hazard still counts: fire or lava rising toward it. */
    public static final int BELOW = 5;
    /** How far above the TNT a hazard still counts: fire spreads at most one block down. */
    public static final int ABOVE = 2;

    private TntFireSafety() {}

    /** A null loadedState result means unknown terrain; never treat it as clear of fire. */
    public static boolean canPlace(boolean tnt, BlockPos target, Predicate<BlockPos> outsideBuildHeight,
                                   Function<BlockPos, BlockState> loadedState, TntRedstoneSafety.Sections sections) {
        if (!tnt) return true;
        TntRedstoneSafety.Section section = null;
        int sectionX = 0, sectionY = 0, sectionZ = 0;
        for (BlockPos pos : BlockPos.betweenClosed(target.offset(-HORIZONTAL, -BELOW, -HORIZONTAL),
                                                  target.offset(HORIZONTAL, ABOVE, HORIZONTAL))) {
            if (outsideBuildHeight.test(pos)) continue;
            int x = pos.getX() >> 4, y = pos.getY() >> 4, z = pos.getZ() >> 4;
            if (section == null || x != sectionX || y != sectionY || z != sectionZ) {
                section = sections.at(x, y, z);
                sectionX = x;
                sectionY = y;
                sectionZ = z;
            }
            if (section == TntRedstoneSafety.Section.UNKNOWN) return false;
            if (section == TntRedstoneSafety.Section.CLEAR) continue;
            BlockState state = loadedState.apply(pos);
            if (state == null || hazard(state)) return false;
        }
        return true;
    }

    public static boolean canPlace(boolean tnt, BlockPos target, Predicate<BlockPos> outsideBuildHeight,
                                   Function<BlockPos, BlockState> loadedState) {
        return canPlace(tnt, target, outsideBuildHeight, loadedState, (x, y, z) -> TntRedstoneSafety.Section.MAYBE);
    }

    /** Fire, soul fire, and lava sources or flows. */
    public static boolean hazard(BlockState state) {
        // Block types rather than tags: tags can be unbound before a server sends them.
        return state.getBlock() instanceof BaseFireBlock || state.is(Blocks.LAVA);
    }
}
