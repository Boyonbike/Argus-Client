package dev.argus.client.commands;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.HudWidgetModule;
import dev.argus.client.modules.Module;
import dev.argus.client.utils.chat.ChatUtils;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.Component;

public final class AlloffCommand extends Command {
   private final Set previouslyEnabledModules = new HashSet();

   public AlloffCommand() {
      super("alloff");
   }

   @Override
   public void exec(LiteralArgumentBuilder commandBuilder) {
      com.mojang.brigadier.Command<Object> disableCommand = context -> {
         this.previouslyEnabledModules.clear();
         ModuleRegistry registry = ClientContext.moduleRegistry;

         for (Module module : (List<Module>)registry.getRegistry()) {
            if (!(module instanceof HudWidgetModule) && module.isToggled()) {
               module.toggle();
               this.previouslyEnabledModules.add(module);
            }
         }

         this.scheduleRotationTask(
            () -> ChatUtils.printChat(
               Component.literal("Disabled ").append(this.formatText(String.valueOf(this.previouslyEnabledModules.size()))).append(" modules."),
               ChatUtils.DEFAULT_SIGNATURE
            )
         );
         return 1;
      };
      commandBuilder.executes(disableCommand);
      String[] aliasArray = new String[]{"restore", "backup"};
      ArgumentBuilder restoreBuilder = Command.aliasedLiteral(aliasArray);
      com.mojang.brigadier.Command<Object> restoreCommand = context -> {
         ModuleRegistry registry = ClientContext.moduleRegistry;

         for (Module module : (List<Module>)registry.getRegistry()) {
            if (!(module instanceof HudWidgetModule)) {
               boolean wasEnabled = this.previouslyEnabledModules.contains(module);
               module.setToggled(wasEnabled);
            }
         }

         this.scheduleRotationTask(
            () -> ChatUtils.printChat(
               Component.literal("Restored ").append(this.formatText(String.valueOf(this.previouslyEnabledModules.size()))).append(" modules."),
               ChatUtils.DEFAULT_SIGNATURE
            )
         );
         return 1;
      };
      ArgumentBuilder restoreBranch = restoreBuilder.executes(restoreCommand);
      commandBuilder.then(restoreBranch);
   }
}
