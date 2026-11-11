package dev.argus.client.gui;

import dev.argus.client.modules.client.DiscordModule;
import dev.argus.client.modules.client.DiscordSyncModule;
import dev.argus.client.modules.misc.ChatToolsModule;

import dev.argus.client.modules.Keybind;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.client.ColorsModule;
import dev.argus.client.modules.client.FontsModule;
import dev.argus.client.modules.client.HUDModule;
import dev.argus.client.modules.client.UIModule;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * The Settings page (opened by the gear circle): Appearance (theme, top bar, window placement,
 * and the UI, Colours and Fonts modules), Presets, Social (the Chat Tools and BetterChat settings), and every other
 * Client module. Discord shows Rich Presence and Discord Sync as two cards, each with its own switch.
 */
final class SettingsScreen extends UiScreen {
    static final String APPEARANCE = "Appearance", PRESETS = "Presets", SOCIAL = "Social";
    private static final int SIDE = 128, ITEM = 20, FIXED = 3; // Appearance, Presets, Social; client modules follow
    private static final double MIN_SCALE = .65, MAX_SCALE = 1.4;
    /** Last section shown, so Settings reopens where you left it. */
    private static String remembered = APPEARANCE;
    private static float indicatorY = Float.NaN;

    private final String focusSetting;
    private String section;
    /** Module asked for when opening (e.g. from search), so Appearance can scroll to its card. */
    private String focusModule;
    private List<Module> modules = List.of();
    /** The selected client module, shown with the same page layout as every other module. */
    private ModulePage page;
    /** The modules folded into Appearance (or, on the Discord page, Rich Presence and Discord Sync), shown as cards. */
    private final List<SettingsList> appearanceLists = new ArrayList<>();
    /** Whether {@link #appearanceLists} holds the Discord page's two cards rather than Appearance's. */
    private boolean discordCards;
    private PresetsPanel presets;
    private int left, top, panelWidth, panelHeight;
    private long sectionChanged;
    /** 1 when the last section switch moved down the sidebar, -1 when it moved up; the content slides in from that side. */
    private int sectionDir = 1;

    /** @param section a section name or client module name; null reopens the last section. */
    SettingsScreen(Screen parent, String section, String focusSetting) {
        super("Settings", parent, "Settings");
        DescriptionCatalog.ensureApplied();
        String requested = section == null ? remembered : sectionFor(section);
        if (isAppearanceModule(requested)) { focusModule = requested; requested = APPEARANCE; }
        this.section = requested;
        this.focusSetting = focusSetting;
    }

    // ---- sections ---------------------------------------------------------------------------

    /** The UI, Colours and Fonts modules shape how things look, so they live on the Appearance page. */
    private static boolean appearanceModule(Module module) { return module instanceof UIModule || module instanceof ColorsModule || module instanceof FontsModule; }
    private static boolean isAppearanceModule(String name) { return appearanceModules().stream().anyMatch(m -> m.getName().equals(name)); }
    private static List<Module> appearanceModules() {
        List<Module> result = new ArrayList<>();
        for (Class<?> type : List.of(UIModule.class, ColorsModule.class, FontsModule.class))
            for (Module module : GuiState.modules()) if (type.isInstance(module)) result.add(module);
        return result;
    }

    /** Client modules with their own section. */
    static List<Module> clientModules() {
        List<Module> result = new ArrayList<>();
        for (Module module : GuiState.modules()) if (module.getCategory() == ModuleCategory.CLIENT && !appearanceModule(module)) result.add(module);
        return result;
    }

    static boolean isSection(String name) {
        return APPEARANCE.equals(name) || PRESETS.equals(name) || SOCIAL.equals(name) || clientModules().stream().anyMatch(m -> m.getName().equals(name));
    }
    /** The section that shows a module: Chat Tools is the Social page, and Discord Sync is a card on the Discord page. */
    static String sectionFor(String name) {
        if ("ChatTools".equals(name)) return SOCIAL;
        if ("DiscordSync".equals(name) && discordModule() != null) return discordModule().getName();
        return name;
    }
    private static Module discordModule() {
        for (Module module : GuiState.modules()) if (module instanceof DiscordModule) return module;
        return null;
    }
    /** The Settings section holding the HUD module (its name, normally "HUD"). */
    static String hudSection() {
        for (Module module : GuiState.modules()) if (module instanceof HUDModule) return module.getName();
        return "HUD";
    }
    static void toggleHudTab() {
        var p = GuiState.preferences();
        p.hudTab = p.hudTab == GuiPreferences.HudTab.EDITOR ? GuiPreferences.HudTab.SETTINGS : GuiPreferences.HudTab.EDITOR;
        GuiState.save();
    }
    /** Flips the HUD tab's target and re-lays the top bar so the tab's highlight follows at once. */
    private void flipHudTab() { toggleHudTab(); layoutTabs(); }
    /** What the module tools need from this window: where opened pages return to, and the top-bar tabs. */
    private final ModuleTools.Hooks hooks = new ModuleTools.Hooks() {
        @Override public Screen back() { return rootParent(); }
        @Override public boolean topBar() { return true; }
        @Override public boolean socialOpensMenu() { return GuiState.preferences().socialTab == GuiPreferences.SocialTab.MENU; }
        @Override public void flipSocialTab() { SettingsScreen.this.flipSocialTab(); }
        @Override public boolean hudOpensEditor() { return GuiState.preferences().hudTab == GuiPreferences.HudTab.EDITOR; }
        @Override public void flipHudTab() { SettingsScreen.this.flipHudTab(); }
        @Override public boolean inHudEditor() { return rootParent() instanceof ArgusHudEditorScreen; }
        @Override public void openHudEditor() { SettingsScreen.this.openHudEditor(); }
    };
    private void flipSocialTab() {
        var p = GuiState.preferences();
        p.socialTab = p.socialTab == GuiPreferences.SocialTab.MENU ? GuiPreferences.SocialTab.SETTINGS : GuiPreferences.SocialTab.MENU;
        GuiState.save(); layoutTabs();
    }
    static String sectionLabel(String name) { return APPEARANCE.equals(name) || PRESETS.equals(name) || SOCIAL.equals(name) ? name : GuiState.title(name); }

    /** One icon per section, used in the sidebar, the top-bar chips and icon-style tabs. */
    static String icon(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "appearance" -> "palette";
            case "presets" -> "star";
            case "social" -> "people";
            case "anticheat" -> "shield";
            case "baritone" -> "compass";
            case "colors" -> "drop";
            case "discord", "discordsync" -> "chat";
            case "fonts" -> "font";
            case "hud" -> "render";
            case "notifications" -> "bell";
            case "settings", "ui" -> "sliders";
            default -> "client";
        };
    }

    @Override protected String activeSection() { return section; }
    @Override protected void openSection(String name) {
        name = sectionFor(name);
        if (isAppearanceModule(name)) { focusModule = name; name = APPEARANCE; }
        if (!name.equals(section)) select(name, null, true);
    }

    @Override protected void buildContent() {
        panelWidth = Math.min(640, width - 24); left = (width - panelWidth) / 2; top = 44;
        panelHeight = height - top - 24;
        modules = clientModules();
        if (!isSection(section)) section = APPEARANCE;
        if (page == null && appearanceLists.isEmpty() && presets == null) select(section, focusSetting, false);
        else layoutSection();
    }

    private void select(String name, String focus, boolean animate) {
        if (page != null) page.stopEditing();
        for (SettingsList card : appearanceLists) card.stopEditing();
        if (presets != null) presets.detach();
        if (section != null) sectionDir = sectionIndex(name) >= sectionIndex(section) ? 1 : -1;
        section = name; remembered = name;
        page = null; appearanceLists.clear(); discordCards = false;
        Module selected = modules.stream().filter(m -> m.getName().equals(name)).findFirst().orElse(null);
        if (name.equals(APPEARANCE)) {
            for (Module module : appearanceModules())
                appearanceLists.add(new SettingsList(this, module, module.getName().equals(focusModule) ? focus : null));
            pageScroll = 0; shownPageScroll = 0;
        } else if (name.equals(SOCIAL)) {
            ChatToolsModule tools = ChatToolsModule.get();
            if (tools != null) page = new ModulePage(this, tools, focus, false).icon(icon(SOCIAL));
        } else if (selected instanceof DiscordModule) {
            // Rich Presence and Discord Sync share one page: a card each, each with its own switch.
            discordCards = true;
            appearanceLists.add(new SettingsList(this, selected, focus));
            DiscordSyncModule sync = DiscordSyncModule.get();
            if (sync != null) appearanceLists.add(new SettingsList(this, sync, focus));
            pageScroll = 0; shownPageScroll = 0;
        } else {
            Module module = selected;
            if (module != null) page = new ModulePage(this, module, focus, false).icon(icon(module.getName()));
        }
        if (page != null) ModuleTools.attach(page, this, page.module(), hooks);
        if (name.equals(PRESETS) && presets == null) presets = new PresetsPanel(this);
        layoutSection();
        if (animate) sectionChanged = System.nanoTime();
        sliding = null;
    }

    private void layoutSection() {
        int x = Math.round(contentX()), w = Math.round(contentWidth() - 8);
        if (page != null) page.layout(contentX(), top + 12, contentWidth(), panelHeight - 12, left, left + panelWidth);
        for (SettingsList card : appearanceLists) card.panelEdges(left, left + panelWidth);
        if (section.equals(PRESETS)) presets.layout(x, top, w, panelHeight);
    }

    private float contentX() { return left + SIDE + 24; }
    private float contentWidth() { return panelWidth - SIDE - 34; }
    private float itemY(int index) { return top + 32 + index * ITEM; }
    private int sections() { return modules.size() + FIXED; }
    private int sectionIndex(String name) { for (int i = 0; i < sections(); i++) if (sectionName(i).equals(name)) return i; return 0; }
    private String sectionName(int index) { return index == 0 ? APPEARANCE : index == 1 ? PRESETS : index == 2 ? SOCIAL : modules.get(index - FIXED).getName(); }

    @Override protected void renderUi(GuiGraphicsExtractor g, int mx, int my, float delta) {
        UiTheme t = GuiState.theme();
        UiDraw.panel(g, left, top, panelWidth, panelHeight, t);
        // ---- sidebar
        UiDraw.rounded(g, left + 4, top + 4, SIDE + 8, panelHeight - 8, 5, UiDraw.alpha(t.mantle(), .7f));
        UiDraw.icon(g, "client", left + 12, top + 13, 10, t.accent());
        UiDraw.bold(g, "Settings", left + 27, top + 18, UiDraw.TITLE, t.text());
        int selected = 0;
        for (int i = 0; i < sections(); i++) if (sectionName(i).equals(section)) selected = i;
        float targetY = itemY(selected);
        indicatorY = Float.isNaN(indicatorY) ? targetY : UiAnim.approach(indicatorY, targetY, 18);
        UiDraw.rounded(g, left + 8, indicatorY + 1, SIDE, ITEM - 2, 6, UiDraw.alpha(t.accent(), .2f));
        UiDraw.rounded(g, left + 8, indicatorY + 5, 2, ITEM - 10, 1, t.accent());
        for (int i = 0; i < sections(); i++) {
            float iy = itemY(i);
            boolean active = i == selected, hover = UiDraw.over(mx, my, left + 8, iy, SIDE, ITEM);
            float hv = UiAnim.smooth("side." + sectionName(i), hover && !active ? 1 : 0, 20);
            if (hv > 0) UiDraw.rounded(g, left + 8, iy + 1, SIDE, ITEM - 2, 6, UiDraw.alpha(t.surface(), hv * .8f));
            int colour = active ? t.accent() : UiDraw.mix(t.muted(), t.text(), hv);
            UiDraw.icon(g, icon(sectionName(i)), left + 16, iy + ITEM / 2f - 4, 8, colour);
            UiDraw.text(g, UiDraw.fit(sectionLabel(sectionName(i)), SIDE - 36), left + 29, iy + ITEM / 2f, colour);
            if (i >= FIXED && sectionOn(modules.get(i - FIXED)))
                UiDraw.circle(g, left + SIDE - 4, iy + ITEM / 2f, 2, UiDraw.alpha(t.accent(), active ? 1 : .7f));
        }
        // ---- content, which fades and slides when the section changes
        float change = UiAnim.eased(sectionChanged, .22f);
        float before = UiRender.opacity;
        UiRender.opacity = before * change;
        g.pose().pushMatrix();
        g.pose().translate(0, sectionDir * (1 - change) * 8);
        try {
            switch (section) {
                case PRESETS -> presets.render(g, mx, my, delta, t);
                case APPEARANCE -> appearance(g, mx, my, delta, t);
                default -> { if (discordCards) discord(g, mx, my, delta, t); else module(g, mx, my, delta, t); }
            }
        } finally { g.pose().popMatrix(); UiRender.opacity = before; }
    }

    // ---- client module section --------------------------------------------------------------

    private void module(GuiGraphicsExtractor g, int mx, int my, float delta, UiTheme t) {
        if (page != null) page.render(g, mx, my, delta, t);
    }

    /** The sidebar dot: the Discord section is on while either of its cards is. */
    private static boolean sectionOn(Module module) {
        if (module instanceof DiscordModule) { DiscordSyncModule sync = DiscordSyncModule.get(); return module.isToggled() || sync != null && sync.isToggled(); }
        return module.isToggled();
    }

    // ---- Discord page: Rich Presence and Discord Sync as two cards ---------------------------

    private void discord(GuiGraphicsExtractor g, int mx, int my, float delta, UiTheme t) {
        hits.clear();
        float x = contentX(), w = contentWidth(), ix = x + PAD, iw = w - PAD * 2;
        UiDraw.icon(g, icon("discord"), x, top + 13.5f, 9, t.accent());
        UiDraw.bold(g, "Discord", x + 13, top + 18, UiDraw.TITLE, t.text());
        UiDraw.text(g, "Rich Presence and the clan's IGN sync, each with its own switch", x, top + 31, UiDraw.SMALL, t.muted());
        UiDraw.rect(g, x, top + 40, w, .6f, UiDraw.alpha(t.overlay(), .6f));
        List<Float> heights = new ArrayList<>();
        float total = 0;
        for (SettingsList card : appearanceLists) { float h = CARD_HEAD + card.contentHeight() + 6f; heights.add(h); total += h + CARD_GAP; }
        float view = pageBottom() - pageTop();
        pageScroll = Math.clamp(pageScroll, 0, Math.max(0, total - view));
        shownPageScroll = UiAnim.approach(shownPageScroll, pageScroll, 18);
        g.enableScissor(Math.round(x - 4), Math.round(pageTop()), Math.round(x + w + 4), Math.round(pageBottom()));
        float y = pageTop() - shownPageScroll;
        for (int i = 0; i < appearanceLists.size(); i++) {
            SettingsList card = appearanceLists.get(i);
            float h = heights.get(i);
            if (card.module() instanceof DiscordSyncModule sync) {
                card(g, x, y, w, h, "people", "IGN Sync", syncStatus(sync), sync, 70 + syncButtonWidth(sync) + 6, mx, my, t);
                syncButton(g, sync, x + w - PAD - 74, y + 6, mx, my, t);
            } else card(g, x, y, w, h, icon("discord"), "Rich Presence", "Show Argus Client as your Discord activity", card.module(), mx, my, t);
            card.embed(ix - 4, y + CARD_HEAD, iw + 8, pageTop(), pageBottom());
            card.render(g, mx, my, delta, t);
            y += h + CARD_GAP;
        }
        g.disableScissor();
        UiMotion.faded(UiMotion.edge(shownPageScroll), () -> UiDraw.gradient(g, x - 4, pageTop(), w + 8, 10, 0, t.base(), UiDraw.alpha(t.base(), 0)));
        UiMotion.faded(UiMotion.edge((Math.max(0, total - view)) - shownPageScroll), () -> UiDraw.gradient(g, x - 4, pageBottom() - 10, w + 8, 10, 0, UiDraw.alpha(t.base(), 0), t.base()));
        for (SettingsList card : appearanceLists) card.popup(g, t, width, height);
        String message = appearanceLists.stream().map(SettingsList::message).filter(m -> !m.isEmpty()).findFirst().orElse("");
        if (!message.isEmpty()) UiDraw.text(g, UiDraw.fit(message, w), x, top + panelHeight - 11, UiDraw.SMALL, t.red());
        else UiDraw.text(g, "Changes apply instantly and are saved automatically", x, top + panelHeight - 11, UiDraw.SMALL, UiDraw.alpha(t.muted(), .8f));
    }

    private static String syncStatus(DiscordSyncModule sync) {
        if (!sync.isToggled()) return "Keeps the clan list and Auto TPY whitelist in step with your Discord's #ign channel";
        return sync.syncBusy() ? "Syncing…" : sync.syncFailed() ? "Last sync failed · details are in chat"
                : sync.lastSuccessfulSync() > 0 ? sync.syncedAccounts() + " linked accounts" : "Not synced yet";
    }
    private static String syncLabel(DiscordSyncModule sync) { return sync.syncBusy() ? "Syncing…" : sync.syncFailed() ? "Try again" : "Sync now"; }
    private static float syncButtonWidth(DiscordSyncModule sync) { return UiDraw.width(syncLabel(sync), UiDraw.SMALL) + 26; }

    /** Discord Sync's "Sync now" button, right-aligned at {@code right} in its card's header. */
    private void syncButton(GuiGraphicsExtractor g, DiscordSyncModule sync, float right, float y, int mx, int my, UiTheme t) {
        String label = syncLabel(sync);
        float bw = syncButtonWidth(sync), bx = right - bw, bh = 18;
        boolean hover = UiDraw.over(mx, my, bx, y, bw, bh);
        float hv = UiAnim.smooth("discord.sync.h", hover ? 1 : 0, 18);
        UiDraw.rounded(g, bx, y, bw, bh, 8, UiDraw.alpha(t.accent(), .14f + hv * .1f));
        UiDraw.outline(g, bx, y, bw, bh, 8, .6f, UiDraw.alpha(t.accent(), .35f + hv * .3f));
        UiDraw.icon(g, "reset", bx + 7, y + 5, 8, t.accent());
        UiDraw.text(g, label, bx + 19, y + bh / 2, UiDraw.SMALL, t.accent());
        if (hover) tooltip("discord.sync", "Sync now", "Read Discord now and update the clan list and Auto TPY whitelist. Details appear in chat.");
        hits.add(new Hit(bx, y, bw, bh, sync::syncNow));
    }

    // ---- appearance page: a scrolling stack of cards ------------------------------------------

    private static final float CARD_GAP = 8, CARD_HEAD = 30, PAD = 10, CHIP_H = 16;

    /** A clickable region recorded while drawing, so clicks always match what is on screen. */
    private record Hit(float x, float y, float w, float h, Runnable action) {}
    private final List<Hit> hits = new ArrayList<>();
    /** Slider being dragged ("scale" or "gap") and the track it uses. */
    private String sliding;
    /** True while the Menu card's open-key chip waits for a key press. */
    private boolean bindingMenuKey;
    private float sliderX, sliderW;
    /** Pointer x when a recorded hit runs, so a slider jumps to where it was clicked. */
    private double pointerX;
    private double pendingScale = Double.NaN;
    private float pageScroll, shownPageScroll;

    private float pageTop() { return top + 46; }
    private float pageBottom() { return top + panelHeight - 24; }

    /** Every tab that can sit in the bar, in a fixed order: the two pages, then Settings sections. */
    static List<String> barCandidates() {
        List<String> all = new ArrayList<>();
        for (String page : UiScreen.PAGE_TABS) all.add(UiScreen.PAGE_PREFIX + page);
        all.add(APPEARANCE); all.add(PRESETS); all.add(SOCIAL);
        for (Module module : clientModules()) all.add(module.getName());
        return all;
    }

    static String chipLabel(String entry) {
        if (entry.startsWith(UiScreen.PAGE_PREFIX)) { String page = entry.substring(UiScreen.PAGE_PREFIX.length()); return page; }
        String label = sectionLabel(entry);
        return label.equalsIgnoreCase("HUD") || SOCIAL.equals(entry) ? label + " settings" : label;
    }
    static String tabIcon(String entry) {
        if (entry.startsWith(UiScreen.PAGE_PREFIX)) return entry.endsWith("HUD") ? "hud" : entry.endsWith(SOCIAL) ? icon(SOCIAL) : "modules";
        return icon(entry);
    }

    private int accentColour(GuiPreferences.Accent accent, boolean light) {
        var preview = new GuiPreferences(); preview.accent = accent; preview.mode = light ? GuiPreferences.Mode.LIGHT : GuiPreferences.Mode.DARK;
        return UiTheme.of(preview).accent();
    }

    /** Height of the chip area for a given inner width (chips wrap onto new lines). */
    private float chipsHeight(float width) {
        float x = 0, rows = 1;
        for (String entry : barCandidates()) {
            float w = UiDraw.width(chipLabel(entry), UiDraw.SMALL) + 26;
            if (x + w > width && x > 0) { x = 0; rows++; }
            x += w + 4;
        }
        return rows * (CHIP_H + 4) - 4;
    }

    /** Card frame with an icon badge, title, subtitle and an optional enable switch on the right. */
    private void card(GuiGraphicsExtractor g, float x, float y, float w, float h, String icon, String title, String subtitle, Module toggle, int mx, int my, UiTheme t) {
        card(g, x, y, w, h, icon, title, subtitle, toggle, toggle != null ? 70 : 0, mx, my, t);
    }

    /** As above, keeping {@code reserve} pixels of the header's right side clear of the subtitle. */
    private void card(GuiGraphicsExtractor g, float x, float y, float w, float h, String icon, String title, String subtitle, Module toggle, float reserve, int mx, int my, UiTheme t) {
        UiDraw.rounded(g, x, y, w, h, 8, UiDraw.alpha(t.mantle(), .6f));
        UiDraw.outline(g, x, y, w, h, 8, .6f, UiDraw.alpha(t.overlay(), .4f));
        UiDraw.circle(g, x + PAD + 7, y + 15, 8, UiDraw.alpha(t.accent(), .16f));
        UiDraw.icon(g, icon, x + PAD + 3, y + 11, 8, t.accent());
        UiDraw.bold(g, title, x + PAD + 20, y + 11, UiDraw.TEXT, t.text());
        UiDraw.text(g, UiDraw.fit(subtitle, w - PAD * 2 - 20 - reserve), x + PAD + 20, y + 21, 6f, t.muted());
        if (toggle != null) {
            boolean hover = UiDraw.over(mx, my, x + w - PAD - 70, y + 6, 70, 18);
            float on = UiAnim.smooth("card.on." + toggle.getName(), toggle.isToggled() ? 1 : 0, 16);
            UiDraw.right(g, toggle.isToggled() ? "On" : "Off", x + w - PAD - 24, y + 15, UiDraw.SMALL, UiDraw.mix(t.muted(), t.accent(), on));
            UiDraw.toggle(g, x + w - PAD - 18, y + 10, on, hover, t);
            hits.add(new Hit(x + w - PAD - 70, y + 6, 70, 18, toggle::toggle));
        }
    }

    /** The key that opens this menu: click, then press a key. Backspace restores Right Shift, Escape cancels. */
    private void menuKeyChip(GuiGraphicsExtractor g, float right, float y, int mx, int my, UiTheme t) {
        UIModule ui = UIModule.INSTANCE;
        if (ui == null) return;
        Keybind bind = ui.getKeybind();
        String label = bindingMenuKey ? "Press a key…" : "Open key: " + (bind.isUnbound() ? "None" : bind.getDisplayName());
        float cw = UiDraw.width(label, UiDraw.SMALL) + 26, cx = right - cw, ch = 18;
        boolean hover = UiDraw.over(mx, my, cx, y, cw, ch);
        float hv = UiAnim.smooth("app.menukey.h", hover || bindingMenuKey ? 1 : 0, 18);
        float pulse = bindingMenuKey ? UiAnim.pulse(1.5e8) : 0;
        UiDraw.rounded(g, cx, y, cw, ch, 8, bindingMenuKey ? UiDraw.alpha(t.accent(), .16f + pulse * .12f) : UiDraw.mix(t.surface(), t.overlay(), hv * .7f));
        UiDraw.outline(g, cx, y, cw, ch, 8, .6f, bindingMenuKey ? UiDraw.alpha(t.accent(), .5f + pulse * .5f) : UiDraw.alpha(t.overlay(), .5f + hv * .3f));
        UiDraw.icon(g, "keyboard", cx + 7, y + 5, 8, bindingMenuKey ? t.accent() : UiDraw.mix(t.muted(), t.text(), hv));
        UiDraw.text(g, label, cx + 19, y + ch / 2, UiDraw.SMALL, bindingMenuKey ? t.accent() : t.text());
        if (hover && !bindingMenuKey) tooltip("app.menukey", "Open key", "Click, then press a key or mouse button (middle/side) · Backspace resets to Right Shift");
        hits.add(new Hit(cx, y, cw, ch, () -> { for (SettingsList card : appearanceLists) card.stopEditing(); bindingMenuKey = !bindingMenuKey; }));
    }

    @Override protected boolean capturingMenuKey() { return bindingMenuKey; }

    /** A two-option segmented control with a sliding thumb. */
    private void segmented(GuiGraphicsExtractor g, String key, String[] labels, int chosen, float x, float y, float w, int mx, int my, UiTheme t, java.util.function.IntConsumer pick) {
        float seg = w / 2;
        UiDraw.rounded(g, x, y, w, 18, 7, t.crust());
        float thumb = UiAnim.smooth(key, chosen, 16);
        UiDraw.rounded(g, x + 2 + thumb * seg, y + 2, seg - 4, 14, 5.5f, UiDraw.alpha(t.accent(), .22f));
        for (int i = 0; i < 2; i++) {
            boolean hover = UiDraw.over(mx, my, x + i * seg, y, seg, 18);
            int colour = i == chosen ? t.accent() : UiDraw.mix(t.muted(), t.text(), UiAnim.smooth(key + ".h" + i, hover ? 1 : 0, 18));
            UiDraw.center(g, labels[i], x + i * seg, y + 9, seg, UiDraw.SMALL, colour);
            int index = i;
            hits.add(new Hit(x + i * seg, y, seg, 18, () -> pick.accept(index)));
        }
    }

    /** "1x", "1.25x", "0.5x": the animation speed without trailing zeros. */
    static String speedLabel(double speed) { return java.math.BigDecimal.valueOf(speed).stripTrailingZeros().toPlainString() + "x"; }

    /** A label with a switch at the end of a row. */
    private void toggleRow(GuiGraphicsExtractor g, String label, String hint, boolean on, String key, float x, float y, float w, int mx, int my, UiTheme t, Runnable flip) {
        boolean hover = UiDraw.over(mx, my, x - 4, y, w + 8, 18);
        float h = UiAnim.smooth(key + ".h", hover ? 1 : 0, 18);
        if (h > 0) UiDraw.rounded(g, x - 4, y, w + 8, 18, 6, UiDraw.alpha(t.surface(), h * .8f));
        UiDraw.text(g, label, x, y + 9, UiDraw.SMALL, t.text());
        UiDraw.toggle(g, x + w - 18, y + 4, UiAnim.smooth(key, on ? 1 : 0, 16), hover, t);
        hits.add(new Hit(x - 4, y, w + 8, 18, flip));
        if (hover) tooltip(key, label, hint);
    }

    private void appearance(GuiGraphicsExtractor g, int mx, int my, float delta, UiTheme t) {
        hits.clear();
        var p = GuiState.preferences();
        boolean light = p.mode == GuiPreferences.Mode.LIGHT;
        float x = contentX(), w = contentWidth(), ix = x + PAD, iw = w - PAD * 2, half = (iw - 16) / 2;
        UiDraw.icon(g, "palette", x, top + 13.5f, 9, t.accent());
        UiDraw.bold(g, APPEARANCE, x + 13, top + 18, UiDraw.TITLE, t.text());
        UiDraw.text(g, "Theme, top bar, window placement, and the menu, colour and font options", x, top + 31, UiDraw.SMALL, t.muted());
        UiDraw.rect(g, x, top + 40, w, .6f, UiDraw.alpha(t.overlay(), .6f));

        // Card heights, so the page knows how far it can scroll.
        float themeH = CARD_HEAD + 74, barH = CARD_HEAD + 48 + chipsHeight(iw) + PAD, windowsH = CARD_HEAD + 68;
        List<Float> listHeights = new ArrayList<>();
        for (SettingsList card : appearanceLists) listHeights.add(CARD_HEAD + card.contentHeight() + 6f);
        float total = themeH + barH + windowsH + CARD_GAP * 3;
        for (float h : listHeights) total += h + CARD_GAP;
        float view = pageBottom() - pageTop();
        if (focusModule != null) {
            // Opened for one of the folded-in modules: scroll its card into view.
            float offset = themeH + barH + windowsH + CARD_GAP * 3;
            for (int i = 0; i < appearanceLists.size(); i++) {
                if (appearanceLists.get(i).module().getName().equals(focusModule)) { pageScroll = offset; break; }
                offset += listHeights.get(i) + CARD_GAP;
            }
            focusModule = null;
        }
        pageScroll = Math.clamp(pageScroll, 0, Math.max(0, total - view));
        shownPageScroll = UiAnim.approach(shownPageScroll, pageScroll, 18);

        g.enableScissor(Math.round(x - 4), Math.round(pageTop()), Math.round(x + w + 4), Math.round(pageBottom()));
        float y = pageTop() - shownPageScroll;

        // Theme: mode, scale and accent.
        card(g, x, y, w, themeH, "palette", "Theme", "Dark or light, accent colour, how large the menu is drawn and its motion", null, mx, my, t);
        float row = y + CARD_HEAD + 4;
        UiDraw.text(g, "Mode", ix, row + 9, UiDraw.SMALL, t.muted());
        segmented(g, "app.mode", new String[]{"Dark", "Light"}, light ? 1 : 0, ix + 34, row, half - 34, mx, my, t,
                i -> { p.mode = i == 1 ? GuiPreferences.Mode.LIGHT : GuiPreferences.Mode.DARK; GuiState.save(); });
        double scale = Double.isNaN(pendingScale) ? p.uiScale : pendingScale;
        float sx = ix + half + 16, scaleTrackX = sx + 34, scaleTrackW = ix + iw - 32 - scaleTrackX;
        UiDraw.text(g, "Scale", sx, row + 9, UiDraw.SMALL, t.muted());
        float scaleValue = (float) ((scale - MIN_SCALE) / (MAX_SCALE - MIN_SCALE));
        boolean overScale = UiDraw.over(mx, my, scaleTrackX - 4, row + 1, scaleTrackW + 8, 16);
        UiDraw.slider(g, scaleTrackX, row + 9, scaleTrackW, "scale".equals(sliding) ? scaleValue : UiAnim.smooth("app.scale", scaleValue, 18),
                UiAnim.smooth("app.scale.h", overScale || "scale".equals(sliding) ? 1 : 0, 18), t);
        UiDraw.right(g, Math.round(scale * 100) + "%", ix + iw, row + 9, UiDraw.SMALL, "scale".equals(sliding) ? t.accent() : t.text());
        hits.add(new Hit(scaleTrackX - 4, row + 1, scaleTrackW + 8, 16, () -> startSlider("scale", scaleTrackX, scaleTrackW)));
        row += 26;
        UiDraw.text(g, "Accent", ix, row + 9, UiDraw.SMALL, t.muted());
        var accents = GuiPreferences.Accent.values();
        float cell = (iw - 34) / accents.length;
        for (int i = 0; i < accents.length; i++) {
            var accent = accents[i];
            float cx = ix + 34 + i * cell + cell / 2, cy = row + 9;
            boolean chosen = p.accent == accent, hover = UiDraw.over(mx, my, ix + 34 + i * cell, row - 2, cell, 22);
            float hv = UiAnim.smooth("app.acc.h" + i, hover ? 1 : 0, 18), ring = UiAnim.smooth("app.acc." + i, chosen ? 1 : 0, 14);
            int colour = accentColour(accent, light);
            if (ring > 0) UiDraw.outline(g, cx - 9.5f, cy - 9.5f, 19, 19, 9.5f, 1.3f, UiDraw.alpha(colour, ring));
            UiDraw.circle(g, cx, cy, 6.5f + hv * .8f, colour);
            if (ring > .02f) UiDraw.icon(g, "check", cx - 4, cy - 4, 8, UiDraw.alpha(t.crust(), ring));
            if (hover) tooltip("app.acc." + i, GuiState.title(accent.name().toLowerCase(Locale.ROOT)));
            hits.add(new Hit(ix + 34 + i * cell, row - 2, cell, 22, () -> { p.accent = accent; GuiState.save(); }));
        }
        row += 22;
        toggleRow(g, "Animations", "Page fades, window entrances, hover easing and the top bar's reveal. Off makes every change instant",
                p.animations, "app.anim", ix, row, half, mx, my, t, () -> { p.animations = !p.animations; GuiState.save(); });
        // Speed sits beside the switch, dimmed while animations are off.
        float speedX = ix + half + 16, speedTrackX = speedX + 34, speedTrackW = ix + iw - 32 - speedTrackX;
        float speedValue = (float) ((p.animationSpeed - GuiPreferences.MIN_ANIMATION_SPEED) / (GuiPreferences.MAX_ANIMATION_SPEED - GuiPreferences.MIN_ANIMATION_SPEED));
        boolean overSpeed = p.animations && UiDraw.over(mx, my, speedTrackX - 4, row + 1, speedTrackW + 8, 16);
        float speedBefore = UiRender.opacity;
        UiRender.opacity = speedBefore * UiAnim.smooth("app.speed.on", p.animations ? 1 : .4f, 16);
        UiDraw.text(g, "Speed", speedX, row + 9, UiDraw.SMALL, t.muted());
        UiDraw.slider(g, speedTrackX, row + 9, speedTrackW, "speed".equals(sliding) ? speedValue : UiAnim.smooth("app.speed", speedValue, 18),
                UiAnim.smooth("app.speed.h", overSpeed || "speed".equals(sliding) ? 1 : 0, 18), t);
        UiDraw.right(g, speedLabel(p.animationSpeed), ix + iw, row + 9, UiDraw.SMALL, "speed".equals(sliding) ? t.accent() : t.text());
        UiRender.opacity = speedBefore;
        if (overSpeed) tooltip("app.speed", "Animation speed", "How fast menu motion plays · 1x is normal, higher is snappier");
        if (p.animations) hits.add(new Hit(speedTrackX - 4, row + 1, speedTrackW + 8, 16, () -> startSlider("speed", speedTrackX, speedTrackW)));
        y += themeH + CARD_GAP;

        // Top bar: which tabs sit between the face and the settings circle, and their style.
        card(g, x, y, w, barH, "modules", "Top bar", "Click a tab to add or remove it · drag tabs in the bar to reorder · right-click to remove", null, mx, my, t);
        row = y + CARD_HEAD + 4;
        UiDraw.text(g, "Tab style", ix, row + 9, UiDraw.SMALL, t.muted());
        segmented(g, "app.tabstyle", new String[]{"Names", "Icons"}, p.tabStyle == GuiPreferences.TabStyle.ICONS ? 1 : 0, ix + 50, row, 110, mx, my, t,
                i -> { p.tabStyle = i == 1 ? GuiPreferences.TabStyle.ICONS : GuiPreferences.TabStyle.NAMES; GuiState.save(); layoutTabs(); });
        // What the HUD tab opens, next to the tab style since it changes what a tab does.
        float hudSegW = 110, hudSegX = ix + iw - hudSegW, hudLabelX = hudSegX - UiDraw.width("HUD tab opens", UiDraw.SMALL) - 8;
        UiDraw.text(g, UiDraw.fit("Names switch to icons when they don't fit", Math.max(0, hudLabelX - ix - 176), 6f), ix + 168, row + 9, 6f, UiDraw.alpha(t.muted(), .7f));
        UiDraw.text(g, "HUD tab opens", hudLabelX, row + 9, UiDraw.SMALL, t.muted());
        segmented(g, "app.hudtab", new String[]{"Editor", "Settings"}, p.hudTab == GuiPreferences.HudTab.SETTINGS ? 1 : 0, hudSegX, row, hudSegW, mx, my, t,
                i -> { p.hudTab = i == 1 ? GuiPreferences.HudTab.SETTINGS : GuiPreferences.HudTab.EDITOR; GuiState.save(); layoutTabs(); });
        if (UiDraw.over(mx, my, hudLabelX, row, ix + iw - hudLabelX, 18))
            tooltip("app.hudtab.tip", "HUD tab", "Choose whether the HUD tab in the top bar opens the on-screen editor or the HUD settings page");
        // What the Social tab opens, under the HUD choice.
        row += 22;
        float socialLabelX = hudSegX - UiDraw.width("Social tab opens", UiDraw.SMALL) - 8;
        UiDraw.text(g, "Social tab opens", socialLabelX, row + 9, UiDraw.SMALL, t.muted());
        segmented(g, "app.socialtab", new String[]{"Menu", "Settings"}, p.socialTab == GuiPreferences.SocialTab.SETTINGS ? 1 : 0, hudSegX, row, hudSegW, mx, my, t,
                i -> { p.socialTab = i == 1 ? GuiPreferences.SocialTab.SETTINGS : GuiPreferences.SocialTab.MENU; GuiState.save(); layoutTabs(); });
        if (UiDraw.over(mx, my, socialLabelX, row, ix + iw - socialLabelX, 18))
            tooltip("app.socialtab.tip", "Social tab", "Choose whether the Social tab in the top bar opens the Social menu or the Social settings page");
        float cx = ix, cy = row + 26;
        for (String entry : barCandidates()) {
            float cw = UiDraw.width(chipLabel(entry), UiDraw.SMALL) + 26;
            if (cx + cw > ix + iw && cx > ix) { cx = ix; cy += CHIP_H + 4; }
            boolean shown = p.barTabs.contains(entry), hover = UiDraw.over(mx, my, cx, cy, cw, CHIP_H);
            float on = UiAnim.smooth("chip.on." + entry, shown ? 1 : 0, 16), hv = UiAnim.smooth("chip.h." + entry, hover ? 1 : 0, 20);
            UiDraw.rounded(g, cx, cy, cw, CHIP_H, CHIP_H / 2, UiDraw.mix(UiDraw.mix(t.crust(), t.surface(), hv), UiDraw.alpha(t.accent(), .22f + hv * .08f), on));
            if (on < 1) UiDraw.outline(g, cx, cy, cw, CHIP_H, CHIP_H / 2, .6f, UiDraw.alpha(t.overlay(), (1 - on) * (.6f + hv * .3f)));
            int colour = UiDraw.mix(UiDraw.mix(t.muted(), t.text(), hv), t.accent(), on);
            UiDraw.icon(g, tabIcon(entry), cx + 7, cy + CHIP_H / 2 - 3.5f, 7, colour);
            UiDraw.text(g, chipLabel(entry), cx + 18, cy + CHIP_H / 2, UiDraw.SMALL, colour);
            if (hover) tooltip("chip." + entry, shown ? "Remove from the top bar" : "Add to the top bar");
            hits.add(new Hit(cx, cy, cw, CHIP_H, () -> {
                if (!p.barTabs.remove(entry)) { p.barTabs.add(entry); showBar(); }
                GuiState.save(); layoutTabs();
            }));
            cx += cw + 4;
        }
        y += barH + CARD_GAP;

        // Windows: snapping, grid, auto gap and reset.
        card(g, x, y, w, windowsH, "misc", "Windows", "How module windows and HUD elements snap and space out while you drag them", null, mx, my, t);
        row = y + CARD_HEAD + 2;
        toggleRow(g, "Snap to grid", "Windows land on a fine grid when you let go", p.snap, "app.snap", ix, row, half, mx, my, t,
                () -> { p.snap = !p.snap; GuiState.save(); });
        toggleRow(g, "Show grid while moving", "Draws the grid behind windows while you drag", p.showGrid, "app.grid", ix + half + 16, row, half, mx, my, t,
                () -> { p.showGrid = !p.showGrid; GuiState.save(); });
        row += 22;
        UiDraw.text(g, "Auto gap", ix, row + 9, UiDraw.SMALL, t.text());
        float gapTrackX = ix + 52, gapTrackW = half - 52 - 26;
        float gapValue = p.gap / (float) GuiPreferences.MAX_GAP;
        boolean overGap = UiDraw.over(mx, my, gapTrackX - 4, row + 1, gapTrackW + 8, 16);
        UiDraw.slider(g, gapTrackX, row + 9, gapTrackW, "gap".equals(sliding) ? gapValue : UiAnim.smooth("app.gap", gapValue, 18),
                UiAnim.smooth("app.gap.h", overGap || "gap".equals(sliding) ? 1 : 0, 18), t);
        UiDraw.right(g, p.gap == 0 ? "Off" : Integer.toString(p.gap), ix + half, row + 9, UiDraw.SMALL, p.gap == 0 ? t.muted() : "gap".equals(sliding) ? t.accent() : t.text());
        if (overGap) tooltip("app.gap", "Auto gap", "Keeps dragged windows at least this far from each other");
        hits.add(new Hit(gapTrackX - 4, row + 1, gapTrackW + 8, 16, () -> startSlider("gap", gapTrackX, gapTrackW)));
        float hudRow = row + 22;
        UiDraw.text(g, "HUD gap", ix, hudRow + 9, UiDraw.SMALL, t.text());
        float hudValue = p.hudGap / (float) GuiPreferences.MAX_GAP;
        boolean overHud = UiDraw.over(mx, my, gapTrackX - 4, hudRow + 1, gapTrackW + 8, 16);
        UiDraw.slider(g, gapTrackX, hudRow + 9, gapTrackW, "hudGap".equals(sliding) ? hudValue : UiAnim.smooth("app.hudgap", hudValue, 18),
                UiAnim.smooth("app.hudgap.h", overHud || "hudGap".equals(sliding) ? 1 : 0, 18), t);
        UiDraw.right(g, p.hudGap == 0 ? "Off" : Integer.toString(p.hudGap), ix + half, hudRow + 9, UiDraw.SMALL, p.hudGap == 0 ? t.muted() : "hudGap".equals(sliding) ? t.accent() : t.text());
        if (overHud) tooltip("app.hudgap", "HUD gap", "Keeps HUD elements at least this far from each other while you drag them in the HUD editor");
        hits.add(new Hit(gapTrackX - 4, hudRow + 1, gapTrackW + 8, 16, () -> startSlider("hudGap", gapTrackX, gapTrackW)));
        toggleRow(g, "Fit HUD to grid", "HUD element sizes and spacing round to whole grid steps", p.hudFitGrid, "app.hudgrid", ix + half + 16, hudRow, half, mx, my, t,
                () -> { p.hudFitGrid = !p.hudFitGrid; GuiState.save(); });
        String resetLabel = "Reset window positions";
        float rw = UiDraw.width(resetLabel, UiDraw.SMALL) + 24, rx = ix + iw - rw;
        boolean resetHover = UiDraw.over(mx, my, rx, row + 1, rw, 16);
        float rh = UiAnim.smooth("app.resetpos", resetHover ? 1 : 0, 18);
        UiDraw.rounded(g, rx, row + 1, rw, 16, 8, UiDraw.mix(t.surface(), t.overlay(), rh * .8f));
        UiDraw.icon(g, "reset", rx + 7, row + 5, 8, UiDraw.mix(t.muted(), t.accent(), rh));
        UiDraw.text(g, resetLabel, rx + 18, row + 9, UiDraw.SMALL, t.text());
        hits.add(new Hit(rx, row + 1, rw, 16, () -> { p.windows.clear(); GuiState.save(); minecraft.gui.setScreen(new ModulesScreen(rootParent())); }));
        y += windowsH + CARD_GAP;

        // The folded-in modules, each as a card holding its own settings.
        for (int i = 0; i < appearanceLists.size(); i++) {
            SettingsList card = appearanceLists.get(i);
            Module module = card.module();
            boolean ui = module instanceof UIModule;
            String title = ui ? "Menu" : module instanceof ColorsModule ? "Colours" : "Fonts";
            String subtitle = ui ? "Options for this menu and the classic click GUI"
                    : module instanceof ColorsModule ? "The client's colour scheme for HUD, ESP and other visuals"
                    : "The client's custom font renderer";
            float h = listHeights.get(i);
            card(g, x, y, w, h, icon(module.getName()), title, subtitle, ui ? null : module, ui ? 130 : 70, mx, my, t);
            if (ui) menuKeyChip(g, x + w - PAD, y + 6, mx, my, t);
            card.embed(ix - 4, y + CARD_HEAD, iw + 8, pageTop(), pageBottom());
            card.render(g, mx, my, delta, t);
            y += h + CARD_GAP;
        }
        g.disableScissor();
        // Edge fades and a slim scrollbar hint that the page scrolls.
        UiMotion.faded(UiMotion.edge(shownPageScroll), () -> UiDraw.gradient(g, x - 4, pageTop(), w + 8, 10, 0, t.base(), UiDraw.alpha(t.base(), 0)));
        UiMotion.faded(UiMotion.edge((Math.max(0, total - view)) - shownPageScroll), () -> UiDraw.gradient(g, x - 4, pageBottom() - 10, w + 8, 10, 0, UiDraw.alpha(t.base(), 0), t.base()));
        if (total > view) {
            float track = Math.max(16, view * view / total), offset = (view - track) * shownPageScroll / Math.max(1, total - view);
            UiDraw.rounded(g, x + w + 3, pageTop() + offset, 2, track, 1, UiDraw.alpha(t.overlay(), .8f));
        }
        for (SettingsList card : appearanceLists) card.popup(g, t, width, height);
        String message = appearanceLists.stream().map(SettingsList::message).filter(m -> !m.isEmpty()).findFirst().orElse("");
        if (!message.isEmpty()) UiDraw.text(g, UiDraw.fit(message, w), x, top + panelHeight - 11, UiDraw.SMALL, t.red());
        else UiDraw.text(g, "Changes apply instantly and are saved automatically · scroll for more", x, top + panelHeight - 11, UiDraw.SMALL, UiDraw.alpha(t.muted(), .8f));
    }

    /** Appearance and Discord: a scrolling stack of module cards with clickable regions recorded while drawing. */
    private boolean cardPage() { return section.equals(APPEARANCE) || discordCards; }

    private void startSlider(String which, float trackX, float trackW) {
        sliding = which; sliderX = trackX; sliderW = trackW;
        slideTo(pointerX);
    }

    private void slideTo(double mx) {
        double f = Math.clamp((mx - sliderX) / sliderW, 0, 1);
        if ("scale".equals(sliding)) pendingScale = Math.round((MIN_SCALE + (MAX_SCALE - MIN_SCALE) * f) * 20) / 20.0;
        else if ("speed".equals(sliding)) GuiState.preferences().animationSpeed = Math.round((GuiPreferences.MIN_ANIMATION_SPEED + (GuiPreferences.MAX_ANIMATION_SPEED - GuiPreferences.MIN_ANIMATION_SPEED) * f) * 4) / 4.0;
        else if ("gap".equals(sliding)) GuiState.preferences().gap = (int) Math.round(f * GuiPreferences.MAX_GAP / 2) * 2;
        else if ("hudGap".equals(sliding)) GuiState.preferences().hudGap = (int) Math.round(f * GuiPreferences.MAX_GAP / GuiPreferences.HUD_GAP_STEP) * GuiPreferences.HUD_GAP_STEP;
    }

    // ---- input ------------------------------------------------------------------------------

    @Override protected boolean mouseClickedUi(MouseButtonEvent event, boolean doubled) {
        double mx = event.x(), my = event.y();
        if (bindingMenuKey) {
            bindingMenuKey = false;
            // Middle and side buttons can open the menu; left/right click just cancel so the menu stays usable.
            UIModule ui = UIModule.INSTANCE;
            if (ui != null && event.button() >= 2) {
                ui.setKeybind(new Keybind(event.button(), ui.getKeybind().isUnbound() ? Keybind.Mode.TOGGLE : ui.getKeybind().mode(), true));
                return true;
            }
            if (!section.equals(APPEARANCE)) return true;
        }
        if (event.button() == 0) for (int i = 0; i < sections(); i++)
            if (UiDraw.over(mx, my, left + 8, itemY(i), SIDE, ITEM)) { if (!sectionName(i).equals(section)) select(sectionName(i), null, true); return true; }
        if (section.equals(PRESETS)) { if (presets.mouseClicked(event, doubled)) return true; }
        else if (cardPage()) {
            if (my >= pageTop() && my < pageBottom()) {
                if (event.button() == 0) for (int i = hits.size() - 1; i >= 0; i--) {
                    Hit hit = hits.get(i);
                    if (UiDraw.over(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) { pointerX = mx; hit.action().run(); return true; }
                }
                for (SettingsList card : appearanceLists) if (card.mouseClicked(event, doubled)) return true;
            } else for (SettingsList card : appearanceLists) card.stopEditing();
        } else if (page != null && page.mouseClicked(event, doubled)) return true;
        return super.mouseClickedUi(event, doubled);
    }

    @Override protected boolean mouseDraggedUi(MouseButtonEvent event, double dx, double dy) {
        if (sliding != null) { slideTo(event.x()); return true; }
        if (page != null && page.mouseDragged(event)) return true;
        for (SettingsList card : appearanceLists) if (card.mouseDragged(event)) return true;
        return super.mouseDraggedUi(event, dx, dy);
    }

    @Override protected boolean mouseReleasedUi(MouseButtonEvent event) {
        if (sliding != null) {
            String was = sliding; sliding = null;
            var p = GuiState.preferences();
            if ("scale".equals(was) && !Double.isNaN(pendingScale) && pendingScale != p.uiScale) {
                // Apply the new density on release so the page doesn't resize under the pointer.
                p.uiScale = pendingScale; GuiState.save(); rebuildWidgets();
            } else GuiState.save();
            pendingScale = Double.NaN;
            return true;
        }
        if (page != null && page.mouseReleased()) return true;
        for (SettingsList card : appearanceLists) if (card.mouseReleased()) return true;
        return super.mouseReleasedUi(event);
    }

    @Override protected boolean mouseScrolledUi(double mx, double my, double horizontal, double vertical) {
        if (section.equals(PRESETS)) return presets.mouseScrolled(mx, my, vertical);
        if (cardPage()) {
            if (!UiDraw.over(mx, my, contentX() - 4, pageTop(), contentWidth() + 8, pageBottom() - pageTop())) return false;
            pageScroll = Math.max(0, pageScroll - (float) vertical * 40); return true;
        }
        return page != null && page.mouseScrolled(mx, my, vertical);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (bindingMenuKey) {
            bindingMenuKey = false;
            UIModule ui = UIModule.INSTANCE;
            if (ui != null && event.key() != 256) {
                // Never leave the menu without a key: Backspace/Delete go back to Right Shift.
                int code = event.key() == 259 || event.key() == 261 ? 344 : event.key();
                ui.setKeybind(new Keybind(code, ui.getKeybind().isUnbound() ? Keybind.Mode.TOGGLE : ui.getKeybind().mode(), false));
            }
            return true;
        }
        if (section.equals(PRESETS) && presets.keyPressed(event)) return true;
        if (page != null && page.keyPressed(event)) return true;
        for (SettingsList card : appearanceLists) if (card.keyPressed(event)) return true;
        return super.keyPressed(event);
    }
}
