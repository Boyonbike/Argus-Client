package dev.argus.client.gui.panels;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.NamedFeature;
import dev.argus.client.gui.GuiElement;
import dev.argus.client.gui.components.ModuleComponent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.render.TextUtils;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.components.EditBox;

public class ModuleCategoryPanel extends Panel implements ClientAccess {
   public ModuleCategory category;

   private void populateModules() {
      List<Module> modules = (List<Module>)ClientContext.moduleRegistry.getRegistry();
      modules.stream().filter(module -> module.getCategory() == this.category).sorted(Comparator.comparing(NamedFeature::getName)).forEach(module -> {
         ModuleComponent component = new ModuleComponent(module, this, 0);
         this.children.add(component);
      });
   }

   public ModuleCategoryPanel(ModuleCategory category) {
      super(FontRenderer.capitalize(category.getName()));
      this.category = category;
      this.populateModules();
   }

   @Override
   public void init() {
      this.updateLayout();
      super.init();
   }

   @Override
   protected boolean isChildVisible(GuiElement child) {
      EditBox searchBox = ClientContext.getClickGui().getSearchBox();
      if (searchBox == null) {
         return true;
      }

      if (child instanceof ModuleComponent moduleComponent && !searchBox.getValue().isEmpty()) {
         for (String alias : moduleComponent.getModule().getAliases()) {
            if (TextUtils.containsIgnoreCase(alias, TextUtils.normalizeKeyboardLayout(searchBox.getValue()))) {
               return true;
            }
         }

         return false;
      } else {
         return true;
      }
   }
}
