package dev.argus.client.modules.player.blockcoat;

import dev.argus.client.TestBootstrap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TntFireSafetyTest {
    private static final BlockPos TARGET = new BlockPos(10, 64, -10);

    @BeforeAll static void bootstrapMinecraft() {
        TestBootstrap.bootStrap();
    }

    private static boolean canPlace(Map<BlockPos, BlockState> blocks, BlockPos target) {
        return TntFireSafety.canPlace(true, target, pos -> false,
            pos -> blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState()));
    }

    @Test void ordinaryTerrainIsSafe() {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        blocks.put(TARGET.below(), Blocks.STONE.defaultBlockState());
        blocks.put(TARGET.east(2), Blocks.WATER.defaultBlockState());
        assertTrue(canPlace(blocks, TARGET));
    }

    @Test void fireSoulFireAndLavaNearbyAreUnsafe() {
        for (BlockState hazard : new BlockState[] {Blocks.FIRE.defaultBlockState(), Blocks.SOUL_FIRE.defaultBlockState(),
            Blocks.LAVA.defaultBlockState(), Blocks.LAVA.defaultBlockState().setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL, 3)}) {
            Map<BlockPos, BlockState> blocks = new HashMap<>();
            blocks.put(TARGET.east(), hazard);
            assertFalse(canPlace(blocks, TARGET), () -> "Missed hazard: " + hazard);
        }
    }

    @Test void lavaWellBelowStillCounts() {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        blocks.put(TARGET.below(TntFireSafety.BELOW).east(TntFireSafety.HORIZONTAL), Blocks.LAVA.defaultBlockState());
        assertFalse(canPlace(blocks, TARGET));
    }

    @Test void hazardsOutsideTheBufferAreIgnored() {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        blocks.put(TARGET.east(TntFireSafety.HORIZONTAL + 1), Blocks.LAVA.defaultBlockState());
        blocks.put(TARGET.below(TntFireSafety.BELOW + 1), Blocks.FIRE.defaultBlockState());
        blocks.put(TARGET.above(TntFireSafety.ABOVE + 1), Blocks.FIRE.defaultBlockState());
        assertTrue(canPlace(blocks, TARGET));
    }

    @Test void unknownTerrainIsUnsafe() {
        assertFalse(TntFireSafety.canPlace(true, TARGET, pos -> false, pos -> null));
        assertFalse(TntFireSafety.canPlace(true, TARGET, pos -> false, pos -> Blocks.AIR.defaultBlockState(),
            (x, y, z) -> TntRedstoneSafety.Section.UNKNOWN));
    }

    @Test void otherBlocksAreUnaffected() {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        blocks.put(TARGET.east(), Blocks.FIRE.defaultBlockState());
        assertTrue(TntFireSafety.canPlace(false, TARGET, pos -> false, blocks::get));
    }
}
