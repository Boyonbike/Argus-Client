package dev.argus.client.integrations.baritone;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Ported from Comet. What Baritone Elytra changes inside Baritone, where its public API doesn't reach. Read by the mixins on Baritone's elytra
 * classes, on Baritone's pathfinder thread as well as the game thread. Refers to no Baritone class, so it loads without one.
 */
public final class BaritoneElytraOverrides {
    private static volatile boolean noFireworks;
    private static volatile boolean lavaPassable;
    private static volatile boolean thinLavaOnly = true;
    private static volatile boolean lavaHookRan;
    private static volatile boolean lavaHookFailed;

    private BaritoneElytraOverrides() {}

    static void setNoFireworks(boolean enabled) {
        noFireworks = enabled;
    }

    public static boolean noFireworks() {
        return noFireworks;
    }

    /** @return whether this changed anything */
    static boolean setLavaPassable(boolean enabled) {
        if (lavaPassable == enabled) return false;
        lavaPassable = enabled;
        return true;
    }

    public static boolean lavaPassable() {
        return lavaPassable;
    }

    /** @return whether this changed anything */
    static boolean setThinLavaOnly(boolean enabled) {
        if (thinLavaOnly == enabled) return false;
        thinLavaOnly = enabled;
        return true;
    }

    public static boolean thinLavaOnly() {
        return thinLavaOnly;
    }

    /** Baritone wrote a chunk and the lava hook saw it, which is the proof the hook fits this Baritone build. */
    public static void lavaHookRan() {
        lavaHookRan = true;
    }

    public static void lavaHookFailed() {
        lavaHookFailed = true;
    }

    /**
     * Baritone shuts its writer down when a trip ends, and a chunk still being written then gets refused. That isn't a fault in
     * the hook, and the next trip's writer works.
     */
    public static boolean writerClosing(Throwable failure) {
        Throwable cause = failure instanceof InvocationTargetException wrapped ? wrapped.getCause() : failure;
        return cause instanceof RejectedExecutionException;
    }

    /** False once Baritone has written chunks without the hook running, or the hook couldn't reach Baritone's terrain. */
    static boolean lavaHookWorks() {
        return lavaHookRan && !lavaHookFailed;
    }

    /** Lava to keep solid: deep and wide in both directions, unlike a lavafall, a thin wall or a shallow pool. */
    static final int SEA_DEPTH = 3, SEA_WIDTH = 8;

    @FunctionalInterface
    public interface LavaAt {
        boolean at(int x, int y, int z);
    }

    /** Whether this lava block is part of a sea, which routes keep out of. It only looks as far as it needs to. */
    public static boolean isSea(int x, int y, int z, LavaAt lava) {
        return run(x, y, z, 0, 1, 0, SEA_DEPTH, lava) >= SEA_DEPTH
            && run(x, y, z, 1, 0, 0, SEA_WIDTH, lava) >= SEA_WIDTH
            && run(x, y, z, 0, 0, 1, SEA_WIDTH, lava) >= SEA_WIDTH;
    }

    private static int run(int x, int y, int z, int dx, int dy, int dz, int enough, LavaAt lava) {
        int length = 1;
        for (int i = 1; length < enough && lava.at(x + dx * i, y + dy * i, z + dz * i); i++) length++;
        for (int i = 1; length < enough && lava.at(x - dx * i, y - dy * i, z - dz * i); i++) length++;
        return length;
    }

    /** The same block changes with the given ones replaced as air, so the pathfinder sees lava open as it flows and is placed. */
    public static <B> List<B> withLavaAsAir(List<B> blocks, Predicate<B> open, Function<B, B> asAir) {
        List<B> changed = new ArrayList<>(blocks.size());
        for (B block : blocks) changed.add(open.test(block) ? asAir.apply(block) : block);
        return changed;
    }

    /**
     * The lava blocks to open in a chunk column: real height, but x and z inside the chunk, which is all Baritone's block
     * update reads of them. Baritone's own block update then drops any height its terrain doesn't hold.
     */
    public static List<BlockPos> lavaIn(LevelChunkSection[] sections, int minY, boolean thinOnly) {
        List<BlockPos> open = new ArrayList<>();
        boolean[] lava = null;
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section == null || section.hasOnlyAir() || !section.maybeHas(state -> state.is(Blocks.LAVA))) continue;
            if (lava == null) lava = new boolean[sections.length * 4096];
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (section.getBlockState(x, y, z).is(Blocks.LAVA)) lava[((index << 4) + y) << 8 | z << 4 | x] = true;
                    }
                }
            }
        }
        if (lava == null) return open;
        boolean[] cells = lava;
        int height = sections.length << 4;
        LavaAt at = (x, y, z) -> x >= 0 && x < 16 && z >= 0 && z < 16 && y >= 0 && y < height && cells[y << 8 | z << 4 | x];
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (cells[y << 8 | z << 4 | x] && !(thinOnly && isSea(x, y, z, at))) open.add(new BlockPos(x, minY + y, z));
                }
            }
        }
        return open;
    }
}
