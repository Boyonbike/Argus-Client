package dev.randomstuff.client.gui;

import dev.eggclient.client.ClientContext;
import dev.eggclient.client.modules.Module;
import dev.eggclient.client.modules.ModuleCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Meteor-style module GUI:
 *  - a tab bar at the top (Modules / Friends / Classic)
 *  - one draggable window per category, collapsible, with one row per module
 *  - left click a row = toggle module; hover = description tooltip
 *  - mouse wheel scrolls the whole GUI; a search box filters every window
 *
 * Integrated with Random Stuff Client for Minecraft 1.21.11 (Mojang mappings).
 */
public final class ModulesScreen extends Screen {

    // ---- look (matches the retuned Random Stuff Client defaults) ----
    private static final int C_PANEL      = 0xE618181C;
    private static final int C_HEADER     = 0xFF202024;
    private static final int C_ROW_ON     = 0x649664FF; // purple fill, alpha 100
    private static final int C_ROW_HOVER  = 0x30FFFFFF;
    private static final int C_ACCENT     = 0xFFB978FF; // lavender bar + enabled text
    private static final int C_TEXT       = 0xFFFFFFFF;
    private static final int C_DIM        = 0xFF9A9AA6;
    private static final int C_TABBAR     = 0xE6101014;

    private static final int WIN_W = 112;
    private static final int HEAD_H = 16;
    private static final int ROW_H = 13;

    /** window state survives closing/reopening the screen */
    private static final class Win {
        int x, y;
        boolean collapsed;
    }
    private static final Map<ModuleCategory, Win> WINS = new EnumMap<>(ModuleCategory.class);
    private static float scrollY = 0f;
    private static int layoutWidth = -1;

    private final Screen parent;
    private EditBox search;
    private ModuleCategory dragging;
    private int dragDX, dragDY;
    private final List<Tab> tabs = new ArrayList<>();

    private record Tab(String label, Runnable action, boolean active) {}

    public ModulesScreen(Screen parent) {
        super(Component.literal("Random Stuff Client"));
        this.parent = parent;
    }

    // ---------------------------------------------------------------- setup

    @Override
    protected void init() {
        String query = search == null ? "" : search.getValue();
        search = new EditBox(this.font, this.width - 168, 36, 150, 16, Component.literal("Search"));
        search.setHint(Component.literal("Search modules..."));
        search.setMaxLength(32);
        search.setValue(query);
        this.addRenderableWidget(search);
        this.setInitialFocus(search);

        ModuleCategory[] categories = ModuleCategory.values();
        if (layoutWidth != this.width || WINS.size() != categories.length) {
            int[] heights = new int[categories.length];
            for (int i = 0; i < categories.length; i++) {
                int rows = ClientContext.moduleRegistry.getModulesByCategory(categories[i]).size();
                heights[i] = HEAD_H + rows * ROW_H + 2;
            }
            int[][] positions = CategoryLayout.arrange(this.width, heights);
            for (int i = 0; i < categories.length; i++) {
                Win w = WINS.computeIfAbsent(categories[i], c -> new Win());
                w.x = positions[i][0];
                w.y = positions[i][1];
            }
            layoutWidth = this.width;
            scrollY = 0;
        }
    }

    private List<Module> modulesOf(ModuleCategory cat) {
        List<Module> out = new ArrayList<>();
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        for (Object o : ClientContext.moduleRegistry.getModulesByCategory(cat)) {
            Module m = (Module) o;
            if (q.isEmpty() || m.getName().toLowerCase(Locale.ROOT).contains(q)) out.add(m);
        }
        out.sort(Comparator.comparing(m -> m.getName().toLowerCase(Locale.ROOT)));
        return out;
    }

    private int winHeight(Win w, int rows) {
        return HEAD_H + (w.collapsed ? 0 : rows * ROW_H + 2);
    }

    // -------------------------------------------------------------- drawing

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        g.fill(0, 0, this.width, this.height, 0x70000000);

        // clamp scroll to the tallest window
        int tallest = 0;
        for (ModuleCategory cat : ModuleCategory.values()) {
            Win w = WINS.get(cat);
            if (w == null) continue;
            tallest = Math.max(tallest, w.y + winHeight(w, modulesOf(cat).size()));
        }
        float minScroll = Math.min(0f, this.height - tallest - 12);
        scrollY = Math.max(minScroll, Math.min(0f, scrollY));

        Module hovered = null;
        int sy = (int) scrollY;

        for (ModuleCategory cat : ModuleCategory.values()) {
            Win w = WINS.get(cat);
            if (w == null) continue;
            List<Module> mods = modulesOf(cat);
            if (mods.isEmpty() && !search.getValue().isBlank()) continue;

            int x = w.x, y = w.y + sy;
            int h = winHeight(w, mods.size());
            g.fill(x, y, x + WIN_W, y + h, C_PANEL);
            g.fill(x, y, x + WIN_W, y + HEAD_H, C_HEADER);
            g.drawString(this.font, cat.getName(), x + 6, y + 4, C_TEXT, true);
            g.drawString(this.font, w.collapsed ? "+" : "-", x + WIN_W - 12, y + 4, C_DIM, true);

            if (!w.collapsed) {
                int ry = y + HEAD_H + 1;
                for (Module m : mods) {
                    boolean over = mx >= x && mx < x + WIN_W && my >= ry && my < ry + ROW_H;
                    boolean on = m.isToggled();
                    if (on) {
                        g.fill(x + 1, ry, x + WIN_W - 1, ry + ROW_H, C_ROW_ON);
                        g.fill(x + 1, ry, x + 3, ry + ROW_H, C_ACCENT);
                    } else if (over) {
                        g.fill(x + 1, ry, x + WIN_W - 1, ry + ROW_H, C_ROW_HOVER);
                    }
                    String name = m.getName();
                    int tw = this.font.width(name);
                    g.drawString(this.font, name, x + (WIN_W - tw) / 2, ry + 3, on ? C_ACCENT : C_TEXT, true);
                    if (over) hovered = m;
                    ry += ROW_H;
                }
            }
        }

        // tab bar (drawn after windows so it stays on top)
        drawTabs(g, mx, my);

        super.render(g, mx, my, delta); // search box

        if (hovered != null) {
            String d = hovered.getDescription();
            if (d != null && !d.isEmpty()) {
                int tw = this.font.width(d);
                int tx = Math.min(mx + 10, this.width - tw - 8);
                int ty = my + 12;
                g.fill(tx - 3, ty - 3, tx + tw + 3, ty + 11, 0xF0100010);
                g.drawString(this.font, d, tx, ty, C_TEXT, true);
            }
        }
    }

    private void drawTabs(GuiGraphics g, int mx, int my) {
        tabs.clear();
        tabs.add(new Tab("Modules", () -> {}, true));
        tabs.add(new Tab("Friends", () -> this.minecraft.setScreen(new FriendsScreen(this)), false));
        tabs.add(new Tab("Classic", this::openClassic, false));

        int total = 0;
        for (Tab t : tabs) total += this.font.width(t.label()) + 20;
        int x0 = (this.width - total) / 2;
        int y0 = 6;
        g.fill(x0 - 6, y0, x0 + total + 6, y0 + 22, C_TABBAR);

        int x = x0;
        for (Tab t : tabs) {
            int w = this.font.width(t.label()) + 20;
            boolean over = mx >= x && mx < x + w && my >= y0 && my < y0 + 22;
            if (over && !t.active()) g.fill(x, y0 + 2, x + w, y0 + 20, C_ROW_HOVER);
            g.drawString(this.font, t.label(), x + 10, y0 + 7, t.active() ? C_ACCENT : C_TEXT, true);
            if (t.active()) g.fill(x + 6, y0 + 19, x + w - 6, y0 + 21, C_ACCENT);
            x += w;
        }
    }

    // ---------------------------------------------------------------- input

    private void openClassic() {
        ClientContext.getClickGui().open();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
        if (super.mouseClicked(event, dbl)) return true; // search box etc.
        double mx = event.x(), my = event.y();
        int button = event.button();

        // tabs
        int total = 0;
        for (Tab t : tabs) total += this.font.width(t.label()) + 20;
        int x = (this.width - total) / 2;
        for (Tab t : tabs) {
            int w = this.font.width(t.label()) + 20;
            if (mx >= x && mx < x + w && my >= 6 && my < 28) {
                if (!t.active()) t.action().run();
                return true;
            }
            x += w;
        }

        int sy = (int) scrollY;
        for (ModuleCategory cat : ModuleCategory.values()) {
            Win w = WINS.get(cat);
            if (w == null) continue;
            List<Module> mods = modulesOf(cat);
            if (mods.isEmpty() && !search.getValue().isBlank()) continue;
            int wx = w.x, wy = w.y + sy;
            int h = winHeight(w, mods.size());
            if (mx < wx || mx >= wx + WIN_W || my < wy || my >= wy + h) continue;

            if (my < wy + HEAD_H) {                       // header
                if (button == 1 || mx >= wx + WIN_W - 18) {
                    w.collapsed = !w.collapsed;           // right click / arrow
                } else if (button == 0) {
                    dragging = cat;                       // start dragging
                    dragDX = (int) mx - w.x;
                    dragDY = (int) my - sy - w.y;
                }
                return true;
            }
            if (!w.collapsed && button == 0) {            // module row
                int idx = (int) ((my - (wy + HEAD_H + 1)) / ROW_H);
                if (idx >= 0 && idx < mods.size()) mods.get(idx).toggle();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging != null) {
            Win w = WINS.get(dragging);
            w.x = Math.max(10, Math.min(this.width - WIN_W - 10, (int) event.x() - dragDX));
            w.y = Math.max(64, (int) event.y() - (int) scrollY - dragDY);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = null;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        scrollY += (float) (vertical * 24.0);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
