package dev.argus.client.gui;

import dev.argus.client.network.*;
import java.util.*;
import java.nio.file.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

public final class ProxiesScreen extends ProfileManagerScreen {
    private static final long CONFIRM_NANOS = 3_000_000_000L;
    private final UiInput name = new UiInput(0, 0, 100, 12, "Optional"), host = new UiInput(0, 0, 100, 12, "Host or IP"), port = new UiInput(0, 0, 80, 12, "1080");
    private final UiInput user = new UiInput(0, 0, 100, 12, "Optional"), password = new UiInput(0, 0, 100, 12, "Optional");
    private final Map<String, ProxyChecker.Result> results = ProxyHealth.results();
    private static final int CARD_Y = TOOLBAR_Y + 28, CARD_H = 30;
    private ProxyEntry.Type type = ProxyEntry.Type.SOCKS5;
    private boolean editing;
    private String editId, deleting;
    private long deleteAt;

    public ProxiesScreen(Screen parent) { super("Proxies", parent); password.secret(true); }

    // ---- header, footer, toolbar ---------------------------------------------------------------

    @Override protected String status() {
        var a = profiles == null ? null : profiles.activeProxy();
        if (a == null) return "Direct connection";
        return (ProxyHealth.activeDead() ? "Dead: " : "Via ") + (a.name().isBlank() ? a.address() : a.name());
    }
    @Override protected boolean statusGood() { return profiles != null && profiles.activeProxy() != null && !ProxyHealth.activeDead(); }
    @Override protected String hint() { return "Switch a proxy on to route connections through it · Reconnect to apply"; }
    @Override protected String[] empty() { return new String[]{"shield", "No proxies yet", "Add a SOCKS proxy, import a text list, or paste one from the clipboard."}; }

    @Override protected void buildToolbar(int x, int y, int w) {
        var buttons = toolbar(x, y, w, new String[]{"New", "Find free", "Import file", "Paste", "Check all", "Clean up"}, new String[]{"plus", null, null, null, "reset", null},
                new Runnable[]{() -> edit(null), this::findFree, this::importFile, () -> importText(minecraft.keyboardHandler.getClipboard()), this::checkAll, this::cleanup});
        // Clean up arms on the first click and shows it on the button itself, not only in the footer.
        buttons[5].confirm(() -> "cleanup".equals(deleting) ? deleteAt : 0, CONFIRM_NANOS * 4 / 3, "Confirm?");
    }

    /** Downloads ranked free SOCKS proxies, saves them, then checks the list. Free proxies are never switched on automatically. */
    private void findFree() {
        background("Finding free proxies…", () -> FreeProxySource.fetch(FreeProxySource.LIMIT, FreeProxySource.MIN_UPTIME, FreeProxySource.MAX_LATENCY, ""), found -> {
            var entries = new ArrayList<ProxyEntry>();
            for (var candidate : found) { var entry = candidate.toEntry(); if (entry != null) entries.add(entry); }
            try {
                boolean hadActive = profiles.activeProxy() != null;
                int added = profiles.importProxies(entries);
                if (!hadActive && profiles.activeProxy() != null) profiles.selectProxy(null);
                if (added == 0) { info("No new free proxies found."); return; }
                rebuildWidgets();
                checkAll();
                message = "Added " + added + " free proxies, checking them now. Free proxies are untrusted; they can see your traffic.";
            } catch (java.io.IOException e) { error("Couldn't save the free proxies. No entries were changed."); }
        });
    }

    // ---- keep-on-launch card ------------------------------------------------------------------------

    @Override protected int listY() { return LIST_Y + CARD_H + 8; }
    @Override protected void renderAboveList(GuiGraphicsExtractor g, int mx, int my) {
        var t = GuiState.theme();
        float x = left + 12, y = top + CARD_Y, w = panelWidth - 24;
        boolean keep = profiles.keepProxy(), dead = ProxyHealth.activeDead();
        String sub = dead ? ProxyHealth.warning()
                : ProxyHealth.checking() ? "Checking saved proxies…"
                : keep ? "Reconnects through the same proxy after a restart · all proxies are re-checked on launch"
                : "Every launch starts on a direct connection";
        UiDraw.card(g, x, y, w, CARD_H, dead ? "shield_off" : "shield", "Keep proxy on launch", dead ? "" : sub, 60, t);
        if (dead) UiDraw.text(g, UiDraw.fit(sub, w - UiDraw.CARD_PAD * 2 - 80, 6f), x + UiDraw.CARD_PAD + 20, y + 21, 6f, t.red());
        boolean hover = !busy && UiDraw.over(mx, my, x + w - 70, y + 6, 70, 18);
        float on = UiAnim.smooth("px.keep", keep ? 1 : 0, 16);
        UiDraw.right(g, keep ? "On" : "Off", x + w - UiDraw.CARD_PAD - 24, y + 15, UiDraw.SMALL, UiDraw.mix(t.muted(), t.accent(), on));
        UiDraw.toggle(g, x + w - UiDraw.CARD_PAD - 18, y + 10, on, hover, t);
        if (!busy) hit(x + w - 70, y + 6, 70, 18, () -> change(() -> profiles.setKeepProxy(!keep),
                keep ? "The proxy will be switched off when the game restarts." : "The switched-on proxy will be kept and re-checked on launch."));
    }

    // ---- edit form ----------------------------------------------------------------------------

    @Override protected boolean formOpen() { return editing; }
    @Override protected void closeForm() { editing = false; clearSecrets(); }

    /** Form rows: name + type, host + port, credentials. Field text sits 10 units under its caption. */
    private static final int ROW1 = 38, ROW2 = 72, ROW3 = 106, FORM_H = 146;
    private int typeWidth() { return 96; }

    @Override protected void buildForm(int x, int y, int w) {
        int inner = w - 20, half = (inner - 8) / 2;
        field(name, x + 10, y + ROW1 + 12, inner - typeWidth() - 8);
        field(host, x + 10, y + ROW2 + 12, inner - 88);
        field(port, x + w - 10 - 80, y + ROW2 + 12, 80);
        field(user, x + 10, y + ROW3 + 12, half);
        field(password, x + 18 + half, y + ROW3 + 12, half);
    }

    @Override protected void renderForm(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
        var t = GuiState.theme();
        UiDraw.card(g, x, y, w, FORM_H, "shield", editId == null ? "New proxy" : "Edit proxy", "SOCKS4 or SOCKS5 · reconnect to apply changes", 100, t);
        float right = x + w - 10;
        right -= pill(g, "Cancel", right, y + 15, mx, my, t.muted(), false, true, () -> { closeForm(); rebuildWidgets(); }) + 4;
        pill(g, "Save", right, y + 15, mx, my, t.accent(), true, true, this::submitForm);
        int inner = w - 20, half = (inner - 8) / 2;
        label(g, "Name", x + 10, y + ROW1);
        label(g, "Host", x + 10, y + ROW2);
        label(g, "Port", x + w - 10 - 80, y + ROW2);
        label(g, "Username", x + 10, y + ROW3);
        label(g, "Password", x + 18 + half, y + ROW3);
        // SOCKS version as a two-way segmented control.
        float sx = x + w - 10 - typeWidth(), sy = y + ROW1 + 10, sw = typeWidth() / 2f;
        label(g, "Type", sx, y + ROW1);
        UiDraw.rounded(g, sx, sy - 2, typeWidth(), 20, 7, t.crust());
        UiDraw.outline(g, sx, sy - 2, typeWidth(), 20, 7, .7f, UiDraw.alpha(t.overlay(), .7f));
        float knob = UiAnim.smooth("px.type", type == ProxyEntry.Type.SOCKS5 ? 0 : 1, 18);
        UiDraw.rounded(g, sx + 2 + knob * sw, sy, sw - 4, 16, 6, UiDraw.alpha(t.accent(), .22f));
        ProxyEntry.Type[] types = {ProxyEntry.Type.SOCKS5, ProxyEntry.Type.SOCKS4};
        for (int i = 0; i < 2; i++) {
            ProxyEntry.Type option = types[i];
            float ox = sx + i * sw;
            boolean on = option == type, hover = UiDraw.over(mx, my, ox, sy - 2, sw, 20);
            UiDraw.center(g, option.toString(), ox, sy + 8, sw, UiDraw.SMALL, on ? t.accent() : hover ? t.text() : t.muted());
            hit(ox, sy - 2, sw, 20, () -> type = option);
        }
    }

    @Override protected void submitForm() {
        change(() -> {
            int p;
            try { p = Integer.parseInt(port.getValue().trim()); } catch (NumberFormatException e) { throw new IllegalArgumentException("Enter a port between 1 and 65535."); }
            var entry = new ProxyEntry(editId == null ? UUID.randomUUID().toString() : editId, name.getValue().trim(), type, host.getValue().trim(), p, user.getValue(), password.getValue());
            profiles.putProxy(entry); results.remove(entry.id()); editing = false; clearSecrets();
        }, "Proxy saved. Reconnect to apply it.");
    }

    private void clearSecrets() { user.setValue(""); password.setValue(""); }
    private void edit(ProxyEntry p) {
        editing = true; editId = p == null ? null : p.id(); type = p == null ? ProxyEntry.Type.SOCKS5 : p.type();
        name.setValue(p == null ? "" : p.name()); host.setValue(p == null ? "" : p.host()); port.setValue(p == null ? "1080" : String.valueOf(p.port()));
        user.setValue(p == null ? "" : p.username()); password.setValue(p == null ? "" : p.password());
        message = ""; rebuildWidgets(); setFocused(host);
    }

    // ---- list ---------------------------------------------------------------------------------

    @Override protected int rowCount() { return profiles.proxies().size(); }
    @Override protected Object rowId(int index) { return profiles.proxies().get(index).id(); }

    @Override protected void renderRow(GuiGraphicsExtractor g, int index, float x, float y, float w, int mx, int my, boolean hover) {
        var t = GuiState.theme();
        var p = profiles.proxies().get(index);
        var active = profiles.activeProxy();
        boolean enabled = active != null && p.id().equals(active.id());
        String key = "px." + p.id();
        rowFrame(g, key, x, y, w, hover, enabled);
        float cy = y + (ROW - 3) / 2f;
        // On/off switch: one proxy at a time.
        boolean toggleHover = UiDraw.over(mx, my, x + 6, cy - 7, 24, 14);
        UiDraw.toggle(g, x + 8, cy - 5, UiAnim.smooth(key + ".use", enabled ? 1 : 0, 16), toggleHover, t);
        if (!busy) hit(x + 6, cy - 7, 24, 14, () -> change(() -> profiles.selectProxy(enabled ? null : p.id()), enabled ? "Proxy switched off. Reconnect to apply." : "Proxy switched on. Reconnect to apply."));
        if (toggleHover) tooltip(key + ".tip", enabled ? "Stop using this proxy" : "Use this proxy");
        // Actions, right to left.
        float right = x + w - 8;
        boolean confirm = p.id().equals(deleting) && System.nanoTime() - deleteAt < CONFIRM_NANOS;
        right -= confirmPill(g, key + ".del", "Delete", "Really delete?", confirm, deleteAt, CONFIRM_NANOS, right, cy, mx, my, t.red(), !busy, () -> {
            if (p.id().equals(deleting) && System.nanoTime() - deleteAt < CONFIRM_NANOS) { deleting = null; change(() -> profiles.removeProxy(p.id()), "Proxy removed."); }
            else { deleting = p.id(); deleteAt = System.nanoTime(); }
        }) + 4;
        right -= pill(g, "Edit", right, cy, mx, my, t.accent(), false, !busy, () -> edit(p)) + 4;
        right -= pill(g, "Check", right, cy, mx, my, t.blue(), false, !busy,
                () -> background("Checking " + p.address() + "…", () -> ProxyChecker.check(p, 5000), r -> {
                    results.put(p.id(), r);
                    if (r.alive()) ok(r.message() + " · " + r.latency() + " ms"); else error(r.message());
                })) + 4;
        float textX = x + 36, room = right - textX - 6;
        UiDraw.bold(g, UiDraw.fit(p.name().isBlank() ? p.address() : p.name(), room), textX, cy - 5, UiDraw.TEXT, enabled ? t.accent() : t.text());
        float cx = textX + chip(g, p.type().toString(), textX, cy + 6.5f, t.blue()) + 4;
        var r = results.get(p.id());
        boolean tcpOnly = p.type() == ProxyEntry.Type.SOCKS4;
        String state = r == null ? (ProxyHealth.checking() ? "Checking…" : "Unchecked") : !r.alive() ? (enabled ? "Dead" : "Unreachable") : tcpOnly ? "TCP reachable" : r.latency() + " ms";
        int tint = r == null ? t.muted() : shouldCleanUp(p, r) ? t.red() : r.latency() >= 500 ? t.yellow() : t.green();
        float sw = chip(g, state, cx, cy + 6.5f, tint);
        if (r != null && !r.alive() && UiDraw.over(mx, my, cx, cy + 2, sw, 9)) tooltip(key + ".err", "Unreachable", r.message());
        cx += sw + 5;
        if (!p.name().isBlank()) UiDraw.text(g, UiDraw.fit(p.address(), right - cx - 6, 5.5f), cx, cy + 6.5f, 5.5f, UiDraw.alpha(t.muted(), .7f));
    }

    // ---- import, checks and clean-up --------------------------------------------------------------

    private void importFile() {
        background("Choose a proxy list…", () -> {
            String file = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog("Import proxies", "", (org.lwjgl.PointerBuffer) null, "Text files", false);
            if (file == null) return "";
            Path path = Path.of(file); if (Files.size(path) > 2_000_000) throw new IllegalArgumentException("Proxy list is too large (maximum 2 MB).");
            return Files.readString(path);
        }, this::importText);
    }
    private void importText(String text) {
        if (text.isBlank()) { info("No proxy list selected."); return; }
        var parsed = new ArrayList<ProxyEntry>(); int skipped = 0;
        if (text.length() > 2_000_000) { error("Proxy list is too large (maximum 2 MB)."); return; }
        var lines = text.lines().filter(line -> !line.isBlank() && !line.stripLeading().startsWith("#")).limit(10001).toList();
        if (lines.size() > 10000) { error("Import at most 10,000 proxies at once."); return; }
        for (String line : lines) {
            try { parsed.add(ProxyEntry.parse(line)); } catch (IllegalArgumentException e) { skipped++; }
        }
        try {
            int added = profiles.importProxies(parsed), rejected = skipped + parsed.size() - added;
            if (added == 0) error("No new proxies found" + (rejected > 0 ? "; skipped " + rejected + " invalid or duplicate lines." : "."));
            else ok("Imported " + added + " proxies" + (rejected > 0 ? "; skipped " + rejected + " invalid or duplicate lines." : "."));
        } catch (java.io.IOException e) { error("Couldn't save imported proxies. No entries were changed."); }
        rebuildWidgets();
    }

    private void checkAll() {
        var list = profiles.proxies();
        if (list.isEmpty()) { info("Add a proxy first."); return; }
        background("Checking " + list.size() + " proxies…", () -> {
            var checked = new LinkedHashMap<String, ProxyChecker.Result>();
            // Separate work-stealing pool prevents blocking the shared authentication executor.
            try (var pool = java.util.concurrent.Executors.newFixedThreadPool(8)) {
                var tasks = new ArrayList<java.util.concurrent.Future<ProxyChecker.Result>>(); for (var p : list) tasks.add(pool.submit(() -> ProxyChecker.check(p, 5000)));
                try { for (int i = 0; i < list.size(); i++) checked.put(list.get(i).id(), tasks.get(i).get()); }
                catch (InterruptedException e) { pool.shutdownNow(); Thread.currentThread().interrupt(); throw e; }
            }
            return checked;
        }, checked -> {
            results.putAll(checked);
            long alive = checked.values().stream().filter(ProxyChecker.Result::alive).count();
            ok("Checked " + checked.size() + " · " + alive + " responding. SOCKS4 checks TCP reachability.");
        });
    }
    private void cleanup() {
        if (!"cleanup".equals(deleting) || System.nanoTime() - deleteAt >= CONFIRM_NANOS * 4 / 3) {
            deleting = "cleanup"; deleteAt = System.nanoTime();
            error("Press Clean up again to remove unreachable proxies and SOCKS5 checks of 2000 ms or more.");
            return;
        }
        deleting = null;
        change(() -> {
            for (var p : profiles.proxies()) {
                if (shouldCleanUp(p, results.get(p.id()))) profiles.removeProxy(p.id());
            }
        }, "Removed unreachable and slow proxies.");
    }

    private static boolean shouldCleanUp(ProxyEntry proxy, ProxyChecker.Result result) {
        return result != null && (!result.alive() || proxy.type() == ProxyEntry.Type.SOCKS5 && result.latency() >= 2000);
    }
}
