package dev.argus.client.gui.panels;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientContext;
import dev.argus.client.NamedFeature;
import dev.argus.client.gui.components.ModuleComponent;
import dev.argus.client.gui.screens.ClickGuiScreen;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.render.TextUtils;
import java.util.ArrayList;
import java.util.Comparator;

public class SearchPanel extends Panel {
   private static final int MAX_RESULTS = 14;
   private String lastQuery = null;

   public SearchPanel() {
      super("Search");
   }

   @Override
   public void init() {
      this.rebuild(this.currentQuery());
      super.init();
   }

   @Override
   public void render(PoseStack var1, double var2, double var4) {
      String var6 = this.currentQuery();
      if (!var6.equals(this.lastQuery)) {
         this.rebuild(var6);
      }

      super.render(var1, var2, var4);
   }

   private String currentQuery() {
      ClickGuiScreen var1 = ClientContext.getClickGui();
      return var1 != null && var1.getSearchBox() != null ? var1.getSearchBox().getValue().trim() : "";
   }

   private void rebuild(String var1) {
      this.lastQuery = var1;
      this.children.clear();
      if (!var1.isEmpty()) {
         String var2 = TextUtils.normalizeKeyboardLayout(var1);
         ArrayList<Module> var3 = new ArrayList();

         for (Object var5 : ClientContext.moduleRegistry.getRegistry()) {
            Module var6 = (Module)var5;
            if (var6.getCategory() != ModuleCategory.HUD && matches(var6, var2)) {
               var3.add(var6);
            }
         }

         var3.sort(Comparator.comparing(NamedFeature::getName, String.CASE_INSENSITIVE_ORDER));
         int var7 = 0;

         for (Module var9 : var3) {
            if (var7++ >= 14) {
               break;
            }

            this.children.add(new ModuleComponent(var9, this, 0));
         }
      }

      this.updateLayout();
   }

   private static boolean matches(Module var0, String var1) {
      if (TextUtils.containsIgnoreCase(var0.getName(), var1)) {
         return true;
      }

      for (String var5 : var0.getAliases()) {
         if (TextUtils.containsIgnoreCase(var5, var1)) {
            return true;
         }
      }

      return false;
   }
}
