package dev.argus.client.commands;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.argus.client.modules.Module;

public final class ToggleCommand extends Command {
   public ToggleCommand() {
      super("toggle");
   }

   @Override
   public void exec(LiteralArgumentBuilder builder) {
      ModuleArgumentType moduleType = new ModuleArgumentType();
      RequiredArgumentBuilder moduleArgument = Command.argument("module", moduleType);
      BoolArgumentType stateType = BoolArgumentType.bool();
      RequiredArgumentBuilder stateArgument = Command.argument("state", stateType);
      com.mojang.brigadier.Command<Object> setState = context -> {
         Class<Module> moduleClass = Module.class;
         Module module = (Module)context.getArgument("module", moduleClass);
         Boolean state = (Boolean)context.getArgument("state", Boolean.class);
         boolean toggled = state;
         module.setToggled(toggled);
         return 1;
      };
      ArgumentBuilder stateCommand = stateArgument.executes(setState);
      RequiredArgumentBuilder moduleWithState = (RequiredArgumentBuilder)moduleArgument.then(stateCommand);
      com.mojang.brigadier.Command<Object> toggle = context -> {
         Class<Module> moduleClass = Module.class;
         Module module = (Module)context.getArgument("module", moduleClass);
         module.toggle();
         return 1;
      };
      ArgumentBuilder moduleCommand = moduleWithState.executes(toggle);
      builder.then(moduleCommand);
   }
}
