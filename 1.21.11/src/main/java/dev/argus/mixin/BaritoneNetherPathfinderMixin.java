package dev.argus.mixin;

import baritone.api.event.events.BlockChangeEvent;
import baritone.api.utils.Pair;
import dev.argus.client.integrations.baritone.BaritoneElytraOverrides;
import java.lang.ref.SoftReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Baritone's elytra pathfinder knows only air and solid, and writes lava as solid. These open it again after a chunk is
 * written and when a block changes. Baritone's release jars keep class names but scramble method names, so its methods are
 * found by their parameters: the chunk writer is the only one taking a SoftReference, and queueBlockUpdate the only public
 * one taking a BlockChangeEvent. Baritone is optional, hence Pseudo and require. Ported from Comet. Needs Meteor's
 * baritone-meteor build: the official release scrambles this class's name too.
 */
@Pseudo
@Mixin(targets = "baritone.process.elytra.NetherPathfinderContext", remap = false)
public abstract class BaritoneNetherPathfinderMixin {
    @Unique private static final Logger ARGUS$LOG = LoggerFactory.getLogger("Argus Client");
    @Unique private static final ThreadLocal<SoftReference<?>> ARGUS$WRITING = new ThreadLocal<>();
    @Unique private static Method argus$queueBlockUpdate;

    /** Baritone's optimiser stores the chunk over this argument's slot, so it can only be read at the start. */
    @Inject(method = "*(Ljava/lang/ref/SoftReference;)V", at = @At("HEAD"), require = 0)
    private void argus$rememberChunk(SoftReference<?> reference, CallbackInfo ci) {
        ARGUS$WRITING.set(reference);
    }

    /** Runs on Baritone's writer thread after it has written the chunk, so this update is queued behind that write. */
    @Inject(method = "*(Ljava/lang/ref/SoftReference;)V", at = @At("RETURN"), require = 0)
    private void argus$openLava(CallbackInfo ci) {
        BaritoneElytraOverrides.lavaHookRan();
        SoftReference<?> reference = ARGUS$WRITING.get();
        ARGUS$WRITING.remove();
        if (!BaritoneElytraOverrides.lavaPassable() || reference == null || !(reference.get() instanceof LevelChunk chunk)) return;
        List<BlockPos> lava = BaritoneElytraOverrides.lavaIn(chunk.getSections(), chunk.getMinY(), BaritoneElytraOverrides.thinLavaOnly());
        if (lava.isEmpty()) return;
        List<Pair<BlockPos, BlockState>> blocks = new ArrayList<>(lava.size());
        // Baritone compares against this exact state.
        for (BlockPos pos : lava) blocks.add(new Pair<>(pos, Blocks.AIR.defaultBlockState()));
        try {
            argus$queueBlockUpdate(new BlockChangeEvent(chunk.getPos(), blocks));
        } catch (ReflectiveOperationException | RuntimeException failure) {
            if (BaritoneElytraOverrides.writerClosing(failure)) return;
            BaritoneElytraOverrides.lavaHookFailed();
            ARGUS$LOG.error("Baritone Elytra couldn't open lava in this Baritone build", failure);
        }
    }

    @Unique
    private void argus$queueBlockUpdate(BlockChangeEvent event) throws ReflectiveOperationException {
        if (argus$queueBlockUpdate == null) {
            for (Method method : getClass().getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (Modifier.isPublic(method.getModifiers()) && parameters.length == 1 && parameters[0] == BlockChangeEvent.class) {
                    argus$queueBlockUpdate = method;
                    break;
                }
            }
            if (argus$queueBlockUpdate == null) throw new NoSuchMethodException("queueBlockUpdate");
        }
        argus$queueBlockUpdate.invoke(this, event);
    }

    @ModifyVariable(method = "*(Lbaritone/api/event/events/BlockChangeEvent;)V", at = @At("HEAD"), argsOnly = true, require = 0)
    private BlockChangeEvent argus$openChangedLava(BlockChangeEvent event) {
        if (!BaritoneElytraOverrides.lavaPassable()) return event;
        boolean thinOnly = BaritoneElytraOverrides.thinLavaOnly();
        ClientLevel level = Minecraft.getInstance().level;
        return new BlockChangeEvent(event.getChunkPos(), BaritoneElytraOverrides.withLavaAsAir(event.getBlocks(),
            block -> block.second().is(Blocks.LAVA) && !(thinOnly && level != null && BaritoneElytraOverrides.isSea(
                block.first().getX(), block.first().getY(), block.first().getZ(),
                (x, y, z) -> level.getBlockState(new BlockPos(x, y, z)).is(Blocks.LAVA))),
            // Baritone compares against this exact state.
            block -> new Pair<>(block.first(), Blocks.AIR.defaultBlockState())));
    }
}
