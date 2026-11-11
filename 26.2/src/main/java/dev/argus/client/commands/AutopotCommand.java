package dev.argus.client.commands;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.TaskScheduler;
import dev.argus.client.modules.combat.AutoPotModule;
import java.util.Collections;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.alchemy.Potion;

public final class AutopotCommand extends Command {
   private static final AutoPotModule AUTO_POT = ClientContext.getModule(AutoPotModule.class);

   public AutopotCommand() {
      super("autopot");
   }

   @Override
   public void exec(LiteralArgumentBuilder builder) {
      LiteralArgumentBuilder throwBranch = Command.literal("throw");
      Registry potionRegistry = BuiltInRegistries.POTION;
      ArgumentType potionArgument = RegistryEntryArgument.registry(potionRegistry);
      RequiredArgumentBuilder effectArgument = Command.argument("effect", potionArgument);
      com.mojang.brigadier.Command<Object> throwHandler = this::executeThrow;
      ArgumentBuilder effectBranch = effectArgument.executes(throwHandler);
      ArgumentBuilder configuredThrowBranch = throwBranch.then(effectBranch);
      builder.then(configuredThrowBranch);
   }

   private int executeThrow(CommandContext context) {
      Potion potion = (Potion)context.getArgument("effect", Potion.class);
      TaskScheduler scheduler = ClientContext.taskScheduler;
      Runnable throwTask = () -> {
         AutoPotModule autoPot = AUTO_POT;
         if (!autoPot.hasNearbySplashPotion()) {
            autoPot = AUTO_POT;
            Set<Potion> allowedPotions = Collections.singleton(potion);
            autoPot.throwPotion(allowedPotions);
         }
      };
      scheduler.scheduleRotationTask(throwTask, 0);
      return 1;
   }
}
