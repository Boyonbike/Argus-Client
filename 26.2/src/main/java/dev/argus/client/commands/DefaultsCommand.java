package dev.argus.client.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.Keybind;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.client.FontsModule;
import dev.argus.client.modules.client.UIModule;
import dev.argus.client.modules.render.BlurModule;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.chat.ChatUtils;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

public final class DefaultsCommand extends Command {
   public DefaultsCommand() {
      super("defaults");
   }

   @Override
   public void exec(LiteralArgumentBuilder commandBuilder) {
      com.mojang.brigadier.Command<Object> resetCommand = context -> {
         ConfirmScreen confirmationScreen = new ConfirmScreen(confirmed -> {
            if (confirmed) {
               ModuleRegistry moduleRegistry = ClientContext.moduleRegistry;

               for (Module module : (List<Module>)moduleRegistry.getRegistry()) {
                  for (Setting<?> setting : module.getSettings()) {
                     setting.reset();
                  }

                  if (module instanceof UIModule) {
                     Keybind defaultUiBinding = new Keybind(defaultUiKeyCode(), Keybind.Mode.TOGGLE, false);
                     module.setKeybind(defaultUiBinding);
                  } else {
                     Keybind defaultBinding = Keybind.UNBOUND;
                     module.setKeybind(defaultBinding);
                  }

                  if (!(module instanceof FontsModule) && !(module instanceof BlurModule)) {
                     module.setToggled(false);
                  }
               }

               this.scheduleRotationTask(() -> ChatUtils.printChat(Component.literal("All modules have been reset to defaults."), ChatUtils.DEFAULT_SIGNATURE));
            }

            Minecraft client = ClientAccess.argusclientMinecraft;
            client.gui.setScreen(null);
         }, resetConfirmationTitle(), Component.nullToEmpty("Continue?"));
         Runnable showConfirmation = () -> {
            Minecraft client = ClientAccess.argusclientMinecraft;
            client.gui.setScreen(confirmationScreen);
         };
         this.scheduleRotationTask(showConfirmation);
         return 1;
      };
      commandBuilder.executes(resetCommand);
   }

   private static Component resetConfirmationTitle() {
      return Component.nullToEmpty("Reset to defaults.");
   }

   private static int defaultUiKeyCode() {
      return 344;
   }
}
