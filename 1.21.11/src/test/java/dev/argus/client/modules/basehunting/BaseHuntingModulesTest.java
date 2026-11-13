package dev.argus.client.modules.basehunting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.render.TunnelsModule;
import dev.argus.client.settings.Setting;
import java.util.HashSet;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class BaseHuntingModulesTest {
   @BeforeAll
   static void bootstrapMinecraft() {
      SharedConstants.tryDetectVersion();
      Bootstrap.bootStrap();
   }

   @Test
   void everyModuleBuildsInTheBaseHuntingWindowWithAUniqueName() {
      List<Module> modules = BaseHuntingModules.create();
      assertEquals(16, modules.size());
      Set<String> names = new HashSet<>();
      for (Module module : modules) {
         assertEquals(ModuleCategory.BASE_HUNTING, module.getCategory(), module.getName());
         assertTrue(names.add(module.getName().toLowerCase()), "Duplicate module name " + module.getName());
      }
   }

   @Test
   void noModuleSavesTwoSettingsUnderOneName() {
      List<Module> modules = new java.util.ArrayList<>(BaseHuntingModules.create());
      modules.add(new TunnelsModule());
      for (Module module : modules) {
         Set<String> configNames = new HashSet<>();
         for (Setting<?> setting : module.getSettings()) {
            assertTrue(configNames.add(setting.getConfigName()), module.getName() + " has two settings saved as " + setting.getConfigName());
         }
      }
   }

   @Test
   void baseFinderDefaultListsResolveOnThisVersion() {
      assertTrue(HuntBlocks.blocks(BaseFinderBlocks.PLAYER_ONLY).length > 300);
      assertEquals(5, HuntBlocks.blocks(BaseFinderBlocks.UNCOMMON).length);
      assertEquals(2, HuntBlocks.blocks(BaseFinderBlocks.REDSTONE).length);
   }
}
