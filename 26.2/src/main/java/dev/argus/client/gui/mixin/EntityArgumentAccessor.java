package dev.argus.client.gui.mixin;

import net.minecraft.commands.arguments.EntityArgument;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets Chat Tools tell a players-only entity argument (as in /msg) from one that also takes mobs. */
@Mixin(EntityArgument.class)
public interface EntityArgumentAccessor {
    @Accessor("playersOnly") boolean argus$playersOnly();
}
