package dev.eggclient.client.gui.panels;

import dev.eggclient.client.ClientContext;
import dev.eggclient.client.gui.components.ModuleComponent;
import dev.eggclient.client.modules.Module;
import dev.eggclient.client.modules.ModuleCategory;
import dev.eggclient.client.render.TextUtils;
// Mojang name for net.minecraft.class_4587. If your project uses Yarn, import MatrixStack instead.
import com.mojang.blaze3d.vertex.PoseStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A "Search" panel that sits in the click GUI row next to the category panels
 * (like Meteor's search window). It lists every module that matches whatever is
 * typed into the GUI's existing search box (ClickGuiScreen#getSearchBox), so the
 * results are gathered in one place instead of being highlighted across panels.
 *
 * Reuses the same pieces ModuleCategoryPanel uses:
 *   new ModuleComponent(module, panel, 0) added to Panel.children, then updateLayout().
 *
 * HOOK (ClickGuiScreen constructor, right after the loop that creates the
 * ModuleCategoryPanels, using the same running x value the loop uses):
 *
 *     SearchPanel searchPanel = new SearchPanel();
 *     searchPanel.setX(x);
 *     this.panels.add(searchPanel);
 */
public class SearchPanel extends Panel {

    private static final int MAX_RESULTS = 14;

    private String lastQuery = null;

    public SearchPanel() {
        super("Search");
    }

    @Override
    public void init() {
        rebuild(currentQuery());
        super.init();
    }

    @Override
    public void render(PoseStack poseStack, double mouseX, double mouseY) {
        String query = currentQuery();
        if (!query.equals(lastQuery)) {
            rebuild(query);
        }
        super.render(poseStack, mouseX, mouseY);
    }

    /** Current text of the GUI's search box, or "" if the GUI/box is not available. */
    private String currentQuery() {
        var gui = ClientContext.getClickGui();
        if (gui == null || gui.getSearchBox() == null) {
            return "";
        }
        // class_342#method_1882 == EditBox#getValue (Mojang) / TextFieldWidget#getText (Yarn)
        return gui.getSearchBox().getValue().trim();
    }

    private void rebuild(String query) {
        lastQuery = query;
        children.clear();

        if (!query.isEmpty()) {
            String normalized = TextUtils.normalizeKeyboardLayout(query);
            List<Module> matches = new ArrayList<>();

            for (Object entry : ClientContext.moduleRegistry.getRegistry()) {
                Module module = (Module) entry;
                // The HUD widgets are not shown in the click GUI panels, so skip them here too.
                if (module.getCategory() == ModuleCategory.HUD) {
                    continue;
                }
                if (matches(module, normalized)) {
                    matches.add(module);
                }
            }

            matches.sort(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER));

            int shown = 0;
            for (Module module : matches) {
                if (shown++ >= MAX_RESULTS) {
                    break;
                }
                children.add(new ModuleComponent(module, this, 0));
            }
        }

        updateLayout();
    }

    private static boolean matches(Module module, String normalizedQuery) {
        if (TextUtils.containsIgnoreCase(module.getName(), normalizedQuery)) {
            return true;
        }
        // Same alias matching ModuleCategoryPanel#isChildVisible uses.
        for (String alias : module.getAliases()) {
            if (TextUtils.containsIgnoreCase(alias, normalizedQuery)) {
                return true;
            }
        }
        return false;
    }
}
