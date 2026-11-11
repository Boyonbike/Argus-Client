package dev.argus.client.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.argus.client.ClientContext;
import dev.argus.client.modules.Keybind;
import dev.argus.client.modules.Module;
import dev.argus.client.utils.chat.ChatUtils;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MeteorCommand extends Command {
   public MeteorCommand() {
      super("meteor");
   }

   @Override
   public void exec(LiteralArgumentBuilder var1) {
      LiteralArgumentBuilder var2 = Command.literal("binds");
      var2.executes(var1x -> {
         this.importModules(false);
         return 1;
      });
      LiteralArgumentBuilder var3 = Command.literal("enabled");
      var3.executes(var1x -> {
         this.importModules(true);
         return 1;
      });
      var1.then(var2);
      var1.then(var3);
      var1.executes(var1x -> {
         this.say("Usage: meteor binds | meteor enabled. Put Meteor's modules.nbt in <game dir>/meteor-client/. Use 'friend sync meteor' for friends.");
         return 1;
      });
   }

   private void say(String var1) {
      ChatUtils.printChat(this.formatText(var1), -1);
   }

   private static String normalize(String var0) {
      return var0.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
   }

   private Module findModule(String var1) {
      String var2 = normalize(var1);

      for (Object var4 : ClientContext.moduleRegistry.getRegistry()) {
         Module var5 = (Module)var4;
         if (normalize(var5.getName()).equals(var2)) {
            return var5;
         }

         for (String var9 : var5.getAliases()) {
            if (var9 != null && normalize(var9).equals(var2)) {
               return var5;
            }
         }
      }

      return null;
   }

   private static int asInt(Object var0, int var1) {
      return var0 instanceof Number var2 ? var2.intValue() : var1;
   }

   private void importModules(boolean var1) {
      Path var2 = Paths.get("meteor-client", "modules.nbt");

      try {
         if (!Files.isRegularFile(var2) || Files.size(var2) == 0L) {
            throw new FileNotFoundException();
         }

         Map var3 = MeteorNbtReader.readCompound(var2);
         if (!(var3.get("modules") instanceof List var4)) {
            this.say("modules.nbt has no module list");
            return;
         }

         int var21 = 0;
         int var6 = 0;
         ArrayList var7 = new ArrayList();

         for (Object var9 : var4) {
            if (var9 instanceof Map var10 && var10.get("name") instanceof String var11) {
               Module var23 = this.findModule(var11);
               if (var23 == null) {
                  var7.add(var11);
               } else {
                  if (var10.get("keybind") instanceof Map var13) {
                     int var25 = asInt(var13.get("value"), -1);
                     boolean var15 = !(var13.get("isKey") instanceof Number var16 && var16.intValue() == 0);
                     if (var25 >= 0) {
                        boolean var27 = var10.get("toggleOnKeyRelease") instanceof Number var28 && var28.intValue() != 0;
                        var23.setKeybind(new Keybind(var25, var27 ? Keybind.Mode.HOLD : Keybind.Mode.TOGGLE, !var15));
                        var21++;
                     }
                  }

                  if (var1 && var10.get("active") instanceof Number var24 && var24.intValue() != 0 && !var23.isToggled()) {
                     var23.enable();
                     var6++;
                  }
               }
            }
         }

         String var22 = "Imported " + var21 + " Meteor keybinds" + (var1 ? ", enabled " + var6 + " modules" : "");
         if (!var7.isEmpty()) {
            var22 = var22 + " (" + var7.size() + " Meteor modules have no match here)";
         }

         this.say(var22);
      } catch (FileNotFoundException var19) {
         this.say("Couldn't find meteor-client/modules.nbt in the game directory");
      } catch (Exception var20) {
         var20.printStackTrace();
         this.say("Couldn't import Meteor modules, check your log file");
      }
   }
}
