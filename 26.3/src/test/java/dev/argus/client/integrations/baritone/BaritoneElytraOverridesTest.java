package dev.argus.client.integrations.baritone;

import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BaritoneElytraOverridesTest {
    @BeforeAll static void bootstrapMinecraft() {
        dev.argus.client.TestBootstrap.bootStrap();
    }

    @AfterEach void clearFlags() {
        BaritoneElytraOverrides.setLavaPassable(false);
        BaritoneElytraOverrides.setNoFireworks(false);
    }

    private static LevelChunkSection section() {
        return new LevelChunkSection(new PalettedContainer<>(Blocks.AIR.defaultBlockState(),
            Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY)), null);
    }

    @Test void changingLavaRoutingIsReportedOnlyWhenItChanges() {
        assertTrue(BaritoneElytraOverrides.setLavaPassable(true));
        assertTrue(BaritoneElytraOverrides.lavaPassable());
        assertFalse(BaritoneElytraOverrides.setLavaPassable(true));
        assertTrue(BaritoneElytraOverrides.setLavaPassable(false));
    }

    @Test void onlyTheChosenChangedBlocksBecomeAir() {
        BlockState stone = Blocks.STONE.defaultBlockState(), lava = Blocks.LAVA.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        assertEquals(List.of(stone, air), BaritoneElytraOverrides.withLavaAsAir(List.of(stone, lava), block -> block.is(Blocks.LAVA), block -> air));
    }

    private static LevelChunkSection filled(int layers, int width) {
        LevelChunkSection section = section();
        for (int y = 0; y < layers; y++) for (int z = 0; z < width; z++) for (int x = 0; x < width; x++) section.setBlockState(x, y, z, Blocks.LAVA.defaultBlockState());
        return section;
    }

    @Test void aSeaStaysSolidWhenOnlyThinLavaOpens() {
        LevelChunkSection[] sea = {filled(5, 16)};
        assertEquals(List.of(), BaritoneElytraOverrides.lavaIn(sea, 0, true));
        assertEquals(5 * 16 * 16, BaritoneElytraOverrides.lavaIn(sea, 0, false).size());
    }

    @Test void aShallowPoolOpens() {
        assertEquals(2 * 16 * 16, BaritoneElytraOverrides.lavaIn(new LevelChunkSection[] {filled(2, 16)}, 0, true).size());
    }

    @Test void aLavafallOpens() {
        LevelChunkSection fall = section();
        for (int y = 0; y < 16; y++) for (int z = 0; z < 3; z++) fall.setBlockState(4, y, z, Blocks.LAVA.defaultBlockState());
        assertEquals(16 * 3, BaritoneElytraOverrides.lavaIn(new LevelChunkSection[] {fall}, 0, true).size());
    }

    @Test void aSmallDeepPoolOpens() {
        assertEquals(5 * 6 * 6, BaritoneElytraOverrides.lavaIn(new LevelChunkSection[] {filled(5, 6)}, 0, true).size());
    }

    @Test void findsLavaAtItsRealHeightWithXAndZInsideTheChunk() {
        LevelChunkSection lavaSection = section();
        lavaSection.setBlockState(3, 4, 5, Blocks.LAVA.defaultBlockState());
        lavaSection.setBlockState(4, 4, 5, Blocks.STONE.defaultBlockState());
        LevelChunkSection stoneOnly = section();
        stoneOnly.setBlockState(1, 1, 1, Blocks.STONE.defaultBlockState());
        LevelChunkSection[] sections = {null, stoneOnly, lavaSection, section()};
        assertEquals(List.of(new BlockPos(3, 2 * 16 + 4, 5)), BaritoneElytraOverrides.lavaIn(sections, 0, true));
        // The Overworld's sections start below zero.
        assertEquals(List.of(new BlockPos(3, -64 + 2 * 16 + 4, 5)), BaritoneElytraOverrides.lavaIn(sections, -64, true));
    }

    @Test void aChunkWithoutLavaHasNothingToOpen() {
        assertEquals(List.of(), BaritoneElytraOverrides.lavaIn(new LevelChunkSection[] {section(), null}, 0, true));
    }

    @Test void seaDetectionIgnoresChunkBoundaries() {
        assertTrue(BaritoneElytraOverrides.isSea(0, 0, 0, (x, y, z) -> true));
        assertFalse(BaritoneElytraOverrides.isSea(0, 0, 0, (x, y, z) -> y == 0));
        assertFalse(BaritoneElytraOverrides.isSea(0, 0, 0, (x, y, z) -> x == 0));
    }

    @Test void routingWorksOnlyOnceTheHookHasRunAndNotFailed() {
        // Runs in this order within one test because the flags are one-way for the game session.
        assertFalse(BaritoneElytraOverrides.lavaHookWorks());
        BaritoneElytraOverrides.lavaHookRan();
        assertTrue(BaritoneElytraOverrides.lavaHookWorks());
        BaritoneElytraOverrides.lavaHookFailed();
        assertFalse(BaritoneElytraOverrides.lavaHookWorks());
    }

    /** Baritone shuts its writer down when a trip ends, and a chunk still being written then is no fault of the hook. */
    @Test void aWriterThatsShuttingDownIsntAHookFailure() {
        var rejected = new java.util.concurrent.RejectedExecutionException();
        assertTrue(BaritoneElytraOverrides.writerClosing(new java.lang.reflect.InvocationTargetException(rejected)));
        assertTrue(BaritoneElytraOverrides.writerClosing(rejected));
    }

    @Test void otherFailuresAreStillHookFailures() {
        assertFalse(BaritoneElytraOverrides.writerClosing(new NoSuchMethodException("queueBlockUpdate")));
        assertFalse(BaritoneElytraOverrides.writerClosing(new java.lang.reflect.InvocationTargetException(new IllegalStateException())));
        assertFalse(BaritoneElytraOverrides.writerClosing(new IllegalStateException()));
    }
}
