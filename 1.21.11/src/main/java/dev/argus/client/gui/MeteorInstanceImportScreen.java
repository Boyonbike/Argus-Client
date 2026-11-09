package dev.argus.client.gui;

import dev.argus.client.config.importing.*;
import dev.argus.client.config.importing.ChatToolsDataImport;
import dev.argus.client.config.importing.MeteorDataReader.DataKind;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/**
 * Pick a launcher instance (or any folder), see everything in its Meteor folder that Argus has an
 * equivalent for, tick what to bring over and import. Reading happens off-thread and never writes to
 * the source. Modules become a normal preset (loaded later from Presets); friends, enemies, waypoints
 * and macros are added to what Argus already has after a backup.
 */
final class MeteorInstanceImportScreen extends UiScreen {
    private enum Kind { HEADER, MODULES, SOURCE, DATA, SKIPPED, WARNING, NOTE }
    private record Row(Kind kind, String text, String detail, DataKind data, int source) {
        static Row header(String text) { return new Row(Kind.HEADER, text, "", null, -1); }
    }
    /** Everything read from one Meteor folder; each part fails on its own so one bad file can't hide the rest. */
    private record Scan(MeteorInstanceScanner.Detection detection, MeteorDataReader.DataSource data,
                        Map<DataKind, String> errors, List<ConfigImportModel.SourceConfig> modules, List<String> moduleErrors,
                        ChatToolsDataImport.ChatData chat) {}

    private static final int ROW = 20, LIST_ROW = 22, HEADER_H = 40;
    private final Consumer<String> saved;
    private final ImportPreviewState session = new ImportPreviewState();
    private final List<MeteorInstanceScanner.Instance> instances = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final EnumSet<DataKind> selected = EnumSet.noneOf(DataKind.class);
    private String listError = "", path = "", presetName = "", message = "", sourceLabel = "";
    private int selectedInstance = -1, moduleSource = -1, scroll, instanceScroll;
    private float shownScroll, shownInstanceScroll;
    private boolean scanning, importing, done, failed, modulesOn, previewReady;
    private Scan scan;
    private ArgusModuleCatalog catalog;
    private ConfigImportConverter.ImportPreview preview;
    private ArgusDataPlan.Plan plan;
    private ChatToolsDataImport.Plan chatPlan = ChatToolsDataImport.Plan.EMPTY;
    private UiInput pathInput, nameInput;
    private UiButton importButton, scanButton;
    // Layout.
    private int left, top, panelWidth, panelHeight, paneX, paneY, paneW, paneH, listX, listY, listW, listH;
    private boolean stacked;

    MeteorInstanceImportScreen(Screen parent, Consumer<String> saved) { super("Import from instance", parent, "Settings"); this.saved = saved; }

    @Override protected void buildContent() {
        panelWidth = Math.min(620, width - 24); left = (width - panelWidth) / 2; top = 36; panelHeight = height - top - 16;
        stacked = panelWidth < 470;
        int inner = top + HEADER_H, footer = 54;
        if (stacked) {
            listX = left + 12; listW = panelWidth - 24; listY = inner + 18; listH = Math.min(3, Math.max(1, instances.size())) * LIST_ROW;
            paneX = left + 12; paneW = panelWidth - 24; paneY = listY + listH + 34; paneH = top + panelHeight - footer - paneY;
        } else {
            listX = left + 12; listW = Math.min(190, panelWidth / 3); listY = inner + 18; listH = top + panelHeight - footer - listY - 30;
            paneX = listX + listW + 12; paneW = left + panelWidth - 12 - paneX; paneY = inner + 18; paneH = top + panelHeight - footer - paneY;
        }
        int pathY = stacked ? listY + listH + 6 : listY + listH + 8;
        pathInput = new UiInput(listX, pathY, listW - 44, 12, "Other folder…").withIcon("search");
        pathInput.place(listX, pathY, listW - 44); pathInput.setMaxLength(4096); pathInput.setValue(path);
        pathInput.setResponder(v -> path = v); addRenderableWidget(pathInput);
        scanButton = addRenderableWidget(new UiButton(listX + listW - 40, pathY - 5, 40, 22, "Scan", this::scanPath));
        int bottom = top + panelHeight - 30, bw = 96;
        importButton = addRenderableWidget(new UiButton(left + panelWidth - 12 - bw, bottom, bw, 22, "Import", true, "check", this::importSelected));
        addRenderableWidget(new UiButton(left + panelWidth - 18 - bw - 76, bottom, 76, 22, done ? "Done" : "Cancel", this::onClose));
        int nameW = Math.max(80, panelWidth - 24 - bw - 76 - 18 - 92);
        nameInput = new UiInput(left + 92, bottom + 5, nameW, 12, "Preset name");
        nameInput.place(left + 92, bottom + 5, nameW); nameInput.setMaxLength(32); nameInput.setValue(presetName);
        nameInput.setResponder(v -> { presetName = v; updateButtons(); }); addRenderableWidget(nameInput);
        if (instances.isEmpty() && listError.isEmpty()) loadInstances();
        updateButtons();
    }

    // ---- Instance list ----------------------------------------------------------------------------

    private void loadInstances() {
        try { instances.addAll(MeteorInstanceScanner.siblings(FabricLoader.getInstance().getGameDir())); }
        catch (Exception e) { listError = "Couldn’t list instances: " + e.getMessage(); }
        if (instances.isEmpty() && listError.isEmpty()) listError = "No other instances with Meteor found. Enter a folder below.";
        rebuildWidgets();
    }
    private void choose(int index) {
        if (index < 0 || index >= instances.size() || busy()) return;
        selectedInstance = index; var instance = instances.get(index);
        start(instance.meteorFolder(), instance.name());
    }
    private void scanPath() {
        if (busy()) return;
        Path folder;
        try { if (path.isBlank()) throw new IllegalArgumentException("Enter a folder path."); folder = Path.of(path.trim()); }
        catch (RuntimeException e) { fail(e.getMessage()); return; }
        var meteor = MeteorInstanceScanner.resolve(folder);
        if (meteor.isEmpty()) { fail("No Meteor folder found there. Choose an instance, its minecraft folder or meteor-client."); return; }
        selectedInstance = -1; start(meteor.get(), folder.getFileName() == null ? folder.toString() : folder.getFileName().toString());
    }

    // ---- Scanning ---------------------------------------------------------------------------------

    private void start(Path meteor, String label) {
        try { catalog = ArgusModuleCatalog.capture(); }
        catch (Exception e) { fail("Couldn’t read Argus defaults: " + e.getMessage()); return; }
        long token = session.begin();
        scan = null; preview = null; plan = null; rows.clear(); scroll = 0; done = false; failed = false; scanning = true; sourceLabel = label;
        message = "Reading " + label + "…"; updateButtons();
        CompletableFuture.supplyAsync(() -> {
            try { return read(meteor); } catch (Exception e) { throw new CompletionException(e); }
        }).whenComplete((result, error) -> minecraft.execute(() -> {
            if (!session.accepts(token) || minecraft.screen != this) return;
            scanning = false;
            if (error != null) { fail("Couldn’t read " + label + ": " + cause(error).getMessage()); updateButtons(); return; }
            scan = result; selected.clear();
            for (DataKind kind : result.detection().data().keySet()) if (!result.errors().containsKey(kind) && count(kind) > 0) selected.add(kind);
            if (result.chat().count() > 0) selected.add(DataKind.CHAT_TOOLS);
            moduleSource = -1;
            for (int i = 0; i < result.modules().size(); i++) if (result.modules().get(i) != null) { moduleSource = i; break; }
            modulesOn = moduleSource >= 0;
            presetName = suggestName(label); nameInput.setValue(presetName);
            refresh();
            if (previewReady) message = result.detection().isEmpty() ? "Nothing in this Meteor folder has an Argus equivalent." : "Tick what to bring over, then Import.";
        }));
    }
    /** Background thread: reads only; nothing here touches live Argus state. */
    private static Scan read(Path meteor) throws Exception {
        var detection = MeteorInstanceScanner.detect(meteor);
        Map<DataKind, String> errors = new EnumMap<>(DataKind.class);
        List<String> friends = List.of(), enemies = List.of();
        List<MeteorDataReader.SourceWaypoint> waypoints = List.of(); List<MeteorDataReader.SourceMacro> macros = List.of();
        for (var e : detection.data().entrySet()) {
            try {
                switch (e.getKey()) {
                    case FRIENDS -> friends = MeteorDataReader.readPlayers(e.getValue(), "friends");
                    case ENEMIES -> enemies = MeteorDataReader.readPlayers(e.getValue(), "enemies");
                    case WAYPOINTS -> waypoints = MeteorDataReader.readWaypoints(e.getValue());
                    case MACROS -> macros = MeteorDataReader.readMacros(e.getValue());
                }
            } catch (Exception x) { errors.put(e.getKey(), cause(x).getMessage()); }
        }
        List<ConfigImportModel.SourceConfig> modules = new ArrayList<>(); List<String> moduleErrors = new ArrayList<>();
        for (var source : detection.moduleSources()) {
            try { modules.add(MeteorConfigReader.read(source.path())); moduleErrors.add(null); }
            catch (Exception x) { modules.add(null); moduleErrors.add(cause(x).getMessage()); }
        }
        // Comet's Chat Tools data lives in the main module config; profiles only hold module toggles and settings.
        ChatToolsDataImport.ChatData chat = ChatToolsDataImport.ChatData.EMPTY;
        for (var config : modules) if (config != null) { chat = ChatToolsDataImport.extract(config); break; }
        return new Scan(detection, new MeteorDataReader.DataSource(friends, enemies, waypoints, macros), errors, modules, moduleErrors, chat);
    }

    /** Client thread: recompute the module preview and data plan from current choices, then the rows. */
    private void refresh() {
        rows.clear(); preview = null; plan = null; chatPlan = ChatToolsDataImport.Plan.EMPTY;
        previewReady = false;
        if (scan == null) { updateButtons(); return; }
        previewReady = true;
        if (moduleSource >= 0 && scan.modules().get(moduleSource) != null) {
            try { preview = ConfigImportConverter.convert(scan.modules().get(moduleSource), catalog, false); }
            catch (Exception e) { preview = null; }
        }
        if (modulesOn && preview == null) {
            previewReady = false;
            fail("Couldn’t preview the selected module config.");
        }
        try { plan = ArgusDataPlan.plan(scan.data(), ArgusDataStore.existing(), selected); }
        catch (Exception e) { previewReady = false; fail("Couldn’t compare with Argus: " + e.getMessage()); }
        if (selected.contains(DataKind.CHAT_TOOLS)) {
            try { chatPlan = ChatToolsDataImport.plan(scan.chat(), ChatToolsDataImport.current()); }
            catch (Exception e) { previewReady = false; fail("Couldn’t compare chat data with Argus: " + e.getMessage()); }
        }
        var d = scan.detection();
        rows.add(Row.header("Will import"));
        if (!d.moduleSources().isEmpty()) {
            rows.add(new Row(Kind.MODULES, "Modules", modulesDetail(), null, -1));
            if (modulesOn) for (int i = 0; i < d.moduleSources().size(); i++) {
                String err = scan.moduleErrors().get(i);
                rows.add(new Row(Kind.SOURCE, d.moduleSources().get(i).label(), err == null ? enabledCount(i) : "Couldn’t read · " + err, null, i));
            }
        }
        for (DataKind kind : DataKind.values()) if (d.data().containsKey(kind)) rows.add(new Row(Kind.DATA, label(kind), dataDetail(kind), kind, -1));
        if (scan.chat().count() > 0) rows.add(new Row(Kind.DATA, label(DataKind.CHAT_TOOLS), dataDetail(DataKind.CHAT_TOOLS), DataKind.CHAT_TOOLS, -1));
        if (d.moduleSources().isEmpty() && d.data().isEmpty() && scan.chat().count() == 0) rows.add(new Row(Kind.NOTE, "Nothing here has an Argus equivalent", "", null, -1));
        List<ConfigImportModel.Issue> warnings = new ArrayList<>();
        if (plan != null) plan.issues().stream().filter(i -> i.severity() == ConfigImportModel.Severity.WARNING).forEach(warnings::add);
        chatPlan.issues().stream().filter(i -> i.severity() == ConfigImportModel.Severity.WARNING).forEach(warnings::add);
        if (modulesOn && preview != null) preview.issues().stream().filter(i -> i.severity() == ConfigImportModel.Severity.WARNING).forEach(warnings::add);
        if (!warnings.isEmpty()) {
            rows.add(Row.header("Won’t transfer (" + warnings.size() + ")"));
            for (var w : warnings) rows.add(new Row(Kind.WARNING, w.sourceId(), w.message(), null, -1));
        }
        if (!d.skipped().isEmpty()) {
            rows.add(Row.header("Stays in Meteor · no Argus equivalent"));
            for (String s : d.skipped()) rows.add(new Row(Kind.SKIPPED, s, "", null, -1));
        }
        updateButtons();
    }

    private String modulesDetail() {
        if (!modulesOn) return "Not importing";
        if (preview == null) return moduleSource < 0 ? "Couldn’t read any module config" : "Couldn’t convert this config";
        long unsupported = preview.issues().stream().filter(i -> i.field().equals("module") && i.severity() == ConfigImportModel.Severity.WARNING).count();
        return preview.enabledTargets().size() + " on in Argus" + (unsupported > 0 ? " · " + unsupported + " not in Argus" : "") + " · saved as a preset";
    }
    private String enabledCount(int index) {
        var source = scan.modules().get(index);
        long on = source.modules().stream().filter(ConfigImportModel.SourceModule::enabled).count();
        return on + " enabled in Meteor";
    }
    private int count(DataKind kind) {
        var data = scan.data();
        return switch (kind) { case FRIENDS -> data.friends().size(); case ENEMIES -> data.enemies().size(); case WAYPOINTS -> data.waypoints().size(); case MACROS -> data.macros().size(); case CHAT_TOOLS -> scan.chat().count(); };
    }
    private int planned(DataKind kind) {
        if (plan == null) return 0;
        return switch (kind) { case FRIENDS -> plan.friends().size(); case ENEMIES -> plan.enemies().size(); case WAYPOINTS -> plan.waypoints().size(); case MACROS -> plan.macros().size(); case CHAT_TOOLS -> chatPlan.added(); };
    }
    private String dataDetail(DataKind kind) {
        if (scan.errors().containsKey(kind)) return "Couldn’t read · " + scan.errors().get(kind);
        int found = count(kind);
        if (found == 0) return "None saved";
        if (!selected.contains(kind)) return found + " found · not importing";
        int add = planned(kind), skip = found - add;
        String extra = kind == DataKind.WAYPOINTS ? " across " + scan.data().waypoints().stream().map(MeteorDataReader.SourceWaypoint::server).distinct().count() + " server(s)" : "";
        return (add == 0 ? "Nothing new" : add + " to add" + extra) + (skip > 0 ? " · " + skip + " skipped" : "");
    }
    private static String label(DataKind kind) {
        return switch (kind) { case FRIENDS -> "Friends"; case ENEMIES -> "Enemies"; case WAYPOINTS -> "Waypoints"; case MACROS -> "Macros"; case CHAT_TOOLS -> "Chat Tools data"; };
    }
    private static String icon(DataKind kind) {
        return switch (kind) { case FRIENDS -> "people"; case ENEMIES -> "shield"; case WAYPOINTS -> "compass"; case MACROS -> "keyboard"; case CHAT_TOOLS -> "chat"; };
    }
    private boolean available(DataKind kind) { return scan != null && !scan.errors().containsKey(kind) && count(kind) > 0; }
    private static String suggestName(String label) {
        String n = ("Meteor " + label).replaceAll("[^A-Za-z0-9 _.\\-]", " ").replaceAll(" +", " ").trim();
        if (n.length() > 32) n = n.substring(0, 32).trim();
        String base = n; int i = 2;
        while (exists(n)) { String suffix = " " + i++; n = (base.length() + suffix.length() > 32 ? base.substring(0, 32 - suffix.length()).trim() : base) + suffix; }
        return n;
    }
    private static boolean exists(String name) {
        try { return dev.argus.client.ClientContext.configurationManager.getPresetRegistry(dev.argus.client.settings.options.PresetCategory.MODULES).getPreset(name) != null; }
        catch (Exception e) { return false; }
    }

    // ---- Import -----------------------------------------------------------------------------------

    private boolean busy() { return scanning || importing; }
    private boolean modulesReady() { return modulesOn && preview != null; }
    private boolean anything() { return modulesReady() || (plan != null && plan.total() > 0) || !chatPlan.isEmpty(); }
    private void updateButtons() {
        if (importButton != null) importButton.active = scan != null && previewReady && !busy() && !done && anything() && (!modulesReady() || ImportedPresetStore.validName(presetName.trim()));
        if (scanButton != null) scanButton.active = !busy();
        if (nameInput != null) nameInput.visible = modulesReady() && !done;
    }
    private void importSelected() {
        if (importButton == null || !importButton.active) return;
        var previewedPlan = plan;
        var previewedChatPlan = chatPlan;
        refresh();
        if (!previewReady) return;
        if (!Objects.equals(previewedPlan, plan) || !previewedChatPlan.equals(chatPlan)) {
            fail("Argus data changed since the preview. Review the updated import, then press Import again.");
            return;
        }
        importing = true; updateButtons();
        List<String> done = new ArrayList<>();
        try {
            if (modulesReady()) {
                String name = presetName.trim();
                try { ImportedPresetStore.save(name, preview); done.add("preset “" + name + "”"); saved.accept(name); }
                catch (Exception e) { fail("Couldn’t save preset: " + e.getMessage() + ". Nothing was imported."); return; }
            }
            if (plan != null && plan.total() > 0) {
                var result = ArgusDataStore.apply(plan);
                if (!result.success()) { fail((done.isEmpty() ? "" : "Saved " + done.getFirst() + ". ") + result.message()); return; }
                done.add(ArgusDataStore.summary(plan) + (result.backup() == null ? "" : " (backup: " + result.backup().getFileName() + ")"));
            }
            if (!chatPlan.isEmpty()) {
                var result = ChatToolsDataImport.apply(chatPlan);
                if (!result.success()) { fail((done.isEmpty() ? "" : "Imported " + String.join(" and ", done) + ". ") + result.message()); return; }
                done.add(ChatToolsDataImport.summary(chatPlan) + (result.backup() == null ? "" : " (backup: " + result.backup().getFileName() + ")"));
            }
            this.done = true; failed = false;
            message = "Imported " + String.join(" and ", done) + "." + (modulesReady() ? " Load the preset from Presets when you’re ready." : "");
            rebuildWidgets();
        } finally { importing = false; updateButtons(); }
    }
    private void fail(String text) { message = text; failed = true; }
    private static Throwable cause(Throwable e) { while (e.getCause() != null) e = e.getCause(); return e; }

    // ---- Rendering --------------------------------------------------------------------------------

    private int maxScroll() { return Math.max(0, rows.size() * ROW - paneH + 6); }
    private int maxInstanceScroll() { return Math.max(0, instances.size() * LIST_ROW - listH); }

    @Override protected void renderUi(GuiGraphics g, int mx, int my, float delta) {
        UiTheme t = GuiState.theme();
        UiDraw.panel(g, left, top, panelWidth, panelHeight, t);
        float bh = UiAnim.smooth("mii.back", UiDraw.over(mx, my, left + 10, top + 10, 18, 18) ? 1 : 0, 18);
        UiDraw.circle(g, left + 19, top + 19, 9, UiDraw.mix(t.surface(), t.overlay(), bh));
        UiDraw.icon(g, "chevron_left", left + 15, top + 15, 8, UiDraw.mix(t.muted(), t.text(), bh));
        UiDraw.bold(g, "Import from another instance", left + 36, top + 19, UiDraw.TITLE, t.text());
        ProfileManagerScreen.chip(g, "Meteor", left + panelWidth - 12 - UiDraw.width("Meteor", 5.5f) - 10, top + 19, t.blue());
        UiDraw.text(g, UiDraw.fit("Reads only · your Meteor files are never changed", panelWidth - 120, UiDraw.SMALL), left + 36, top + 31, UiDraw.SMALL, t.muted());
        renderInstances(g, mx, my, t);
        renderPane(g, mx, my, t);
        // Footer.
        float bottom = top + panelHeight - 30;
        UiDraw.rect(g, left + 12, bottom - 26, panelWidth - 24, .6f, UiDraw.alpha(t.overlay(), .6f));
        if (modulesReady() && !done) {
            boolean bad = !ImportedPresetStore.validName(presetName.trim());
            UiDraw.text(g, "Preset name", left + 14, bottom + 11, UiDraw.SMALL, bad ? t.red() : t.muted());
        }
        String status = message.isEmpty() ? (scan == null ? "Choose an instance to see what it has." : summaryLine()) : message;
        if (!done && !failed && scan != null && !busy() && anything()) status = summaryLine();
        String shown = UiDraw.fit(status, panelWidth - 28, UiDraw.SMALL);
        UiDraw.text(g, shown, left + 14, bottom - 14, UiDraw.SMALL, failed ? t.red() : done ? t.green() : t.muted());
        if (!shown.equals(status) && UiDraw.over(mx, my, left + 10, bottom - 22, panelWidth - 24, 16)) tooltip("mii.status", status);
    }
    private String summaryLine() {
        List<String> parts = new ArrayList<>();
        if (modulesReady()) parts.add("saves “" + presetName.trim() + "” as a Modules preset");
        if (plan != null && plan.total() > 0) parts.add("adds " + ArgusDataStore.summary(plan) + " · backs up first");
        if (!chatPlan.isEmpty()) parts.add("adds " + ChatToolsDataImport.summary(chatPlan));
        return parts.isEmpty() ? "Nothing selected to import." : Character.toUpperCase(parts.getFirst().charAt(0)) + String.join(" · ", parts).substring(1);
    }

    private void renderInstances(GuiGraphics g, int mx, int my, UiTheme t) {
        UiDraw.bold(g, "Instances", listX + 2, listY - 9, UiDraw.SMALL, t.muted());
        UiDraw.rounded(g, listX, listY, listW, listH, 7, UiDraw.alpha(t.mantle(), .7f));
        instanceScroll = Math.clamp(instanceScroll, 0, maxInstanceScroll());
        shownInstanceScroll = UiAnim.approach(shownInstanceScroll, instanceScroll, 20);
        g.enableScissor(listX, listY, listX + listW, listY + listH);
        for (int i = 0; i < instances.size(); i++) {
            float y = listY + i * LIST_ROW - shownInstanceScroll; if (y + LIST_ROW < listY || y > listY + listH) continue;
            boolean sel = i == selectedInstance, hover = UiDraw.over(mx, my, listX, y, listW, LIST_ROW) && UiDraw.over(mx, my, listX, listY, listW, listH);
            float hv = UiAnim.smooth("mii.inst." + i, sel ? 1 : hover ? .5f : 0, 18);
            if (hv > .01f) UiDraw.rounded(g, listX + 3, y + 2, listW - 6, LIST_ROW - 4, 5, UiDraw.alpha(sel ? t.accent() : t.surface(), sel ? .22f : hv));
            if (sel) UiDraw.rounded(g, listX + 3, y + 6, 2.5f, LIST_ROW - 12, 1.2f, t.accent());
            String name = UiDraw.fit(instances.get(i).name(), listW - 30, UiDraw.SMALL);
            UiDraw.text(g, name, listX + 11, y + LIST_ROW / 2f, UiDraw.SMALL, sel ? t.text() : UiDraw.mix(UiDraw.mix(t.muted(), t.text(), .55f), t.text(), hv));
            UiDraw.icon(g, "chevron_right", listX + listW - 16, y + LIST_ROW / 2f - 4, 8, UiDraw.alpha(sel ? t.accent() : t.muted(), sel ? 1 : .4f + hv * .6f));
            if (hover && !name.equals(instances.get(i).name())) tooltip("mii.inst." + i, instances.get(i).name());
        }
        if (instances.isEmpty()) {
            UiDraw.center(g, "No instances found", listX, listY + Math.min(listH / 2f, 20), listW, UiDraw.SMALL, t.muted());
            if (!listError.isEmpty() && listH > 44) UiDraw.center(g, UiDraw.fit(listError, listW - 12, UiDraw.SMALL), listX, listY + 34, listW, UiDraw.SMALL, UiDraw.alpha(t.muted(), .7f));
            if (UiDraw.over(mx, my, listX, listY, listW, listH) && !listError.isEmpty()) tooltip("mii.listerr", listError);
        }
        g.disableScissor();
    }

    private void renderPane(GuiGraphics g, int mx, int my, UiTheme t) {
        String title = scan == null ? "What can be imported" : "Found in " + sourceLabel;
        UiDraw.bold(g, UiDraw.fit(title, paneW - 8, UiDraw.SMALL), paneX + 2, paneY - 9, UiDraw.SMALL, t.muted());
        UiDraw.rounded(g, paneX, paneY, paneW, paneH, 7, UiDraw.alpha(t.mantle(), .45f));
        if (scan == null) {
            float cy = paneY + paneH / 2f - 14;
            UiDraw.icon(g, scanning ? "reset" : "search", paneX + paneW / 2f - 7, cy - 8, 14, UiDraw.alpha(failed ? t.red() : t.muted(), .45f));
            UiDraw.center(g, scanning ? "Reading Meteor folder…" : "Pick an instance", paneX, cy + 16, paneW, t.muted());
            UiDraw.center(g, scanning ? "Nothing is changed while reading." : "Modules, friends, enemies, waypoints and macros", paneX, cy + 29, paneW, UiDraw.SMALL, UiDraw.alpha(t.muted(), .7f));
            return;
        }
        scroll = Math.clamp(scroll, 0, maxScroll()); shownScroll = UiAnim.approach(shownScroll, scroll, 20);
        g.enableScissor(paneX, paneY, paneX + paneW, paneY + paneH);
        for (int i = 0; i < rows.size(); i++) {
            float y = paneY + 3 + i * ROW - shownScroll; if (y + ROW < paneY || y > paneY + paneH) continue;
            Row row = rows.get(i); float cy = y + ROW / 2f;
            boolean hover = clickable(row) && UiDraw.over(mx, my, paneX + 4, y, paneW - 8, ROW) && UiDraw.over(mx, my, paneX, paneY, paneW, paneH);
            float hv = UiAnim.smooth("mii.row." + row.kind() + row.text(), hover ? 1 : 0, 20);
            if (hv > .01f) UiDraw.rounded(g, paneX + 4, y + 1, paneW - 8, ROW - 2, 5, UiDraw.alpha(t.surface(), hv * .8f));
            switch (row.kind()) {
                case HEADER -> UiDraw.bold(g, UiDraw.fit(row.text(), paneW - 20, UiDraw.SMALL), paneX + 10, cy + 2, UiDraw.SMALL, t.muted());
                case MODULES -> item(g, t, y, "modules", row.text(), row.detail(), modulesOn, scan.modules().stream().anyMatch(Objects::nonNull), hover, mx, my, i);
                case DATA -> item(g, t, y, icon(row.data()), row.text(), row.detail(), selected.contains(row.data()), available(row.data()), hover, mx, my, i);
                case SOURCE -> {
                    boolean on = row.source() == moduleSource, ok = scan.modules().get(row.source()) != null;
                    float rx = paneX + 34, rcy = cy;
                    UiDraw.outline(g, rx - 4.5f, rcy - 4.5f, 9, 9, 4.5f, .8f, ok ? (on ? t.accent() : t.overlay()) : UiDraw.alpha(t.overlay(), .4f));
                    if (on) UiDraw.circle(g, rx, rcy, 2.4f, t.accent());
                    UiDraw.text(g, UiDraw.fit(row.text(), paneW * .5f, UiDraw.SMALL), rx + 10, cy, UiDraw.SMALL, ok ? (on ? t.text() : UiDraw.mix(t.muted(), t.text(), .55f)) : t.muted());
                    String detail = UiDraw.fit(row.detail(), paneW * .42f, UiDraw.SMALL);
                    UiDraw.right(g, detail, paneX + paneW - 10, cy, UiDraw.SMALL, ok ? t.muted() : t.red());
                    if (hover && !detail.equals(row.detail())) tooltip("mii.src." + i, row.detail());
                }
                case WARNING -> {
                    UiDraw.circle(g, paneX + 16, cy, 4.5f, UiDraw.alpha(t.yellow(), .2f)); UiDraw.center(g, "!", paneX + 11.5f, cy, 9, 6f, t.yellow());
                    String name = UiDraw.fit(row.text(), paneW * .32f, UiDraw.SMALL);
                    UiDraw.text(g, name, paneX + 26, cy, UiDraw.SMALL, t.text());
                    float nx = paneX + 30 + UiDraw.width(name, UiDraw.SMALL);
                    String why = UiDraw.fit(row.detail(), paneX + paneW - 10 - nx, UiDraw.SMALL);
                    UiDraw.text(g, why, nx, cy, UiDraw.SMALL, t.yellow());
                    if (UiDraw.over(mx, my, paneX + 4, y, paneW - 8, ROW) && !why.equals(row.detail())) tooltip("mii.warn." + i, row.text(), row.detail());
                }
                case SKIPPED -> {
                    UiDraw.icon(g, "close", paneX + 12, cy - 3.5f, 7, UiDraw.alpha(t.muted(), .55f));
                    UiDraw.text(g, UiDraw.fit(row.text(), paneW - 40, UiDraw.SMALL), paneX + 26, cy, UiDraw.SMALL, UiDraw.alpha(t.muted(), .85f));
                }
                case NOTE -> UiDraw.text(g, row.text(), paneX + 12, cy, UiDraw.SMALL, t.muted());
            }
        }
        g.disableScissor();
        if (shownScroll > 1) UiDraw.gradient(g, paneX, paneY, paneW, 8, 7, UiDraw.alpha(t.mantle(), .9f), UiDraw.alpha(t.mantle(), 0));
        if (shownScroll < maxScroll() - 1) UiDraw.gradient(g, paneX, paneY + paneH - 8, paneW, 8, 7, UiDraw.alpha(t.mantle(), 0), UiDraw.alpha(t.mantle(), .9f));
    }
    /** Checkbox row: tick, icon, label and a right-aligned detail. Disabled rows say why in the detail. */
    private void item(GuiGraphics g, UiTheme t, float y, String icon, String label, String detail, boolean on, boolean enabled, boolean hover, int mx, int my, int index) {
        float cy = y + ROW / 2f, bx = paneX + 10;
        float k = UiAnim.smooth("mii.tick." + label, on && enabled ? 1 : 0, 18);
        UiDraw.rounded(g, bx, cy - 5, 10, 10, 3, UiDraw.mix(UiDraw.alpha(t.crust(), .9f), t.accent(), k));
        UiDraw.outline(g, bx, cy - 5, 10, 10, 3, .7f, enabled ? UiDraw.mix(t.overlay(), t.accent(), Math.max(k, hover ? .5f : 0)) : UiDraw.alpha(t.overlay(), .35f));
        if (k > .05f) UiDraw.icon(g, "check", bx + 1.5f, cy - 3.5f, 7, UiDraw.alpha(t.crust(), k));
        UiDraw.icon(g, icon, bx + 17, cy - 4.5f, 9, enabled ? UiDraw.mix(t.muted(), t.text(), .55f) : UiDraw.alpha(t.muted(), .5f));
        UiDraw.text(g, label, bx + 31, cy, UiDraw.TEXT * .92f, enabled ? t.text() : t.muted());
        float room = paneX + paneW - 10 - (bx + 36 + UiDraw.width(label, UiDraw.TEXT * .92f));
        String shown = UiDraw.fit(detail, room, UiDraw.SMALL);
        boolean error = detail.startsWith("Couldn’t");
        UiDraw.right(g, shown, paneX + paneW - 10, cy, UiDraw.SMALL, error ? t.red() : on && enabled ? t.accent() : t.muted());
        if (UiDraw.over(mx, my, paneX + 4, y, paneW - 8, ROW) && !shown.equals(detail)) tooltip("mii.item." + index, label, detail);
    }
    private boolean clickable(Row row) {
        if (done || busy() || scan == null) return false;
        return switch (row.kind()) {
            case MODULES -> scan.modules().stream().anyMatch(Objects::nonNull);
            case DATA -> available(row.data());
            case SOURCE -> scan.modules().get(row.source()) != null;
            default -> false;
        };
    }

    // ---- Input ------------------------------------------------------------------------------------

    @Override protected boolean mouseClickedUi(MouseButtonEvent event, boolean doubled) {
        if (super.mouseClickedUi(event, doubled)) return true;
        if (event.button() != 0) return false;
        double mx = event.x(), my = event.y();
        if (UiDraw.over(mx, my, left + 10, top + 10, 18, 18)) { onClose(); return true; }
        if (UiDraw.over(mx, my, listX, listY, listW, listH)) {
            int index = (int) ((my - listY + shownInstanceScroll) / LIST_ROW); choose(index); return true;
        }
        if (scan != null && UiDraw.over(mx, my, paneX, paneY, paneW, paneH)) {
            int index = (int) ((my - paneY - 3 + shownScroll) / ROW);
            if (index < 0 || index >= rows.size()) return true;
            Row row = rows.get(index);
            if (!clickable(row)) return true;
            switch (row.kind()) {
                case MODULES -> modulesOn = !modulesOn;
                case DATA -> { if (!selected.remove(row.data())) selected.add(row.data()); }
                case SOURCE -> moduleSource = row.source();
                default -> {}
            }
            message = ""; failed = false; refresh(); return true;
        }
        return false;
    }
    @Override protected boolean mouseScrolledUi(double x, double y, double h, double v) {
        if (UiDraw.over(x, y, listX, listY, listW, listH)) instanceScroll = Math.clamp(instanceScroll - (int) (v * LIST_ROW * 2), 0, maxInstanceScroll());
        else scroll = Math.clamp(scroll - (int) (v * ROW * 2), 0, maxScroll());
        return true;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if ((event.key() == 257 || event.key() == 335) && pathInput.isFocused()) { scanPath(); return true; }
        if (!pathInput.isFocused() && !nameInput.isFocused() && (event.key() == 265 || event.key() == 264)) { scroll = Math.clamp(scroll + (event.key() == 265 ? -ROW : ROW), 0, maxScroll()); return true; }
        return super.keyPressed(event);
    }
    @Override public void onFilesDrop(List<Path> files) { if (files.size() == 1) { pathInput.setValue(files.getFirst().toString()); scanPath(); } }
    @Override public void removed() { session.close(); super.removed(); }
}
