# External Config Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. The user subsequently authorized implementation. Installation remains outside this request.

**Goal:** Load Meteor and Argus module configurations into Argus, prioritising which modules are enabled, copying settings only through clear mappings and otherwise using Argus defaults.

**Architecture:** Separate format readers produce a common source model. A pure converter combines that model with a snapshot of Argus module defaults and explicit mappings to produce an ordinary Argus Modules preset plus a compatibility report. Import saves the preset without changing live modules; loading uses the existing preset system with a backup and settings applied before activation.

**Tech Stack:** Java 21, Minecraft 1.21.11, Fabric Loader 0.19.5+, existing Gson/Minecraft NBT APIs and Argus settings/preset APIs, JUnit 5. No Meteor runtime dependency.

**Spec:** User requirements in this chat, 2026-11-08: “modules on is the primary thing, exact module config where there is a clear map then default argus settings otherwise.” Support Meteor and Argus inputs. Follow-up requirement: show a popup warning that loading replaces the existing configuration, help the user save it first, and provide a helpful GUI. The scope and defaults below are implementation recommendations; review before building.

## Behaviour and scope

- First deliverable imports module state/settings from Meteor `modules.nbt`, a Meteor profile folder containing that file, Argus `modules.json`, or an Argus Modules/Everything preset JSON. A `meteor-client` folder with a root `modules.nbt` is also accepted. No automatic search through the user's launcher directories.
- Selecting a folder reads only its `modules.nbt` or `modules.json`. Multiple candidates require an explicit choice. If the chosen Meteor root has only saved profiles, list its immediate `profiles/<name>/modules.nbt` candidates for selection; never silently choose a profile.
- Preserve source on/off state for mapped modules. The imported preset explicitly disables other ordinary gameplay modules so an old active module cannot linger. Client configuration modules, permanent modules and HUD widgets are excluded from this state replacement; preserve their settings/state and report excluded source entries.
- Enabled source modules get priority in the report. Importing an unsupported enabled module must produce a visible warning, not silently claim success. Do not promise identical behaviour between different client implementations.
- A mapped module starts with a complete Argus default settings object. Overlay only validated, explicitly supported source settings. Never fill missing fields from current live settings.
- Meteor settings absent from NBT remain at Argus defaults. Do not reconstruct Meteor defaults: Meteor saves only changed settings, and the user prefers Argus defaults for the remainder.
- For native Argus inputs, exact module/config-setting IDs and compatible types count as clear mappings. Missing or rejected settings retain Argus defaults. Preserve native module-specific JSON extensions only through an explicit codec: custom data such as AutoRekit presets must not be silently discarded or copied unchecked.
- Meteor mappings use explicit source IDs and full group/setting paths. Normalising punctuation/case can suggest a match for development, but cannot authorise a runtime mapping. Similar module names do not establish compatible settings or units.
- If two source modules map to one Argus module, desired enabled state is OR: any enabled source enables the target. Conflicting settings use the Argus default with a warning; never depend on iteration order.
- Import binds is an optional checkbox, off by default. When off, preserve existing Argus binds. When on, translate only representable key/mouse/mode combinations; keep the current bind and report unsupported modifiers or release behaviour. Do not turn a modified chord into an unmodified key.
- Imported Modules presets do not carry Meteor HUD layouts, themes, macros, accounts, proxies, friends, waypoints, GUI layout or server auto-load bindings. For Argus Everything presets, explicitly say only the module portion is imported; do not label this a complete Everything restore.
- Import action creates a new named preset; duplicate names are rejected with a rename prompt. Loading is a separate explicit action. Preview, cancellation and saving must not toggle modules, change settings, save the live configuration, or write to the source instance.
- Before loading an imported preset, save a recoverable native Modules backup. Backup failure aborts loading. On application failure attempt restoration and surface the failure; rollback restores configuration, not world actions already performed by modules.

## Load confirmation popup

- Every manual GUI load of an imported or existing native preset opens a modal before changing live settings. Importing/saving a file alone does not open it. Keep automatic server/world loading separate: it must have been explicitly configured by the user; retain the mandatory backup for imported presets without opening a modal during connection.
- Title: **Replace current config?** Body: **Loading “{preset}” will replace your current {scope}. Save your current config first if you want to keep it.** Scope comes from the actual category (for example, “module settings and enabled modules” for Modules); never imply that an imported Modules preset replaces HUD or appearance.
- For imported presets, add: **Settings without a supported match will use Argus defaults.** Show the selected preset name and a compact summary of modules being enabled/disabled and settings copied/defaulted. Native category presets show their affected scope instead of inaccurate module counts.
- Use a centred card matching the existing theme, rounded corners, a warning icon and clear text hierarchy. Dim the underlying screen and block its pointer, scroll and keyboard actions. Warning meaning must be conveyed in text, not colour alone. Keep content readable at the minimum supported resolution; scroll the body or stack buttons when required.
- Primary accent action: **Save current & load**. Secondary outlined action: **Load without saving**. Quiet action: **Cancel**. The second action skips only a user-named saved preset; imported loads still make the automatic recovery backup. Explain this beneath the actions: **An automatic recovery backup is also created before loading imported configs.** Show that helper only for imported loads.
- Initial keyboard focus is **Cancel**. Tab/Shift+Tab remain within the modal; Enter/Space activate the focused control. Esc and the close button cancel. Clicking outside does nothing. A held key, repeated click or click used to open the modal cannot confirm the load. No remember/never-ask checkbox in this version.
- **Save current & load** opens an inline name field before loading. Suggest a valid collision-free name such as `Before <short preset name>`, truncated to 32 characters with a numeric suffix when needed. Capture the *current* configuration in the same category/scope that will be replaced, using existing preset saving (including native Everything/Modules layout support). Imported configs use a native Modules save. Label the next button **Save & load**.
- Reject invalid/duplicate save names inline, preserve the entered name and keep the modal open. Do not silently overwrite a saved preset. If saving fails, show **Couldn’t save your current config. Nothing has been loaded.** with useful error detail; never continue loading automatically.
- **Load without saving** confirms replacement directly; no second confirmation popup. For imported presets the backup is still mandatory. Disable action buttons while saving/loading, and prevent double submission. Cancellation before commitment changes nothing.
- On success return to Presets and show **Loaded {preset}. Previous config saved as {savedName}.** when explicitly saved; otherwise show **Loaded {preset}.** Include the imported recovery backup name with a **Restore backup** action. Restoration is itself a config replacement and uses the same manual confirmation flow.
- Revalidate the selected preset and save-name availability at commitment. If the selected preset changes or disappears while the modal is open, invalidate confirmation and request a fresh preview. Snapshot the current config at commitment; serialize the save/backup/load sequence on the client thread so it cannot save one state and replace a different one.

## Evidence from this checkout

- Keo instances are labelled 26.1.2. The user said either instance is acceptable. The inspected `Keo Optimized(1)/minecraft/meteor-client/modules.nbt` contains 360 module entries, including addons. The other instance has Normal (359 entries) and Community 2b2t (225 entries) profiles.
- Meteor root: `{modules: List<Compound>}`. Module fields include `name`, `active`, `keybind: {isKey, value, modifiers}`, `toggleOnKeyRelease`, and `settings.groups[].{name, settings:[{name,value}]}`. NBT booleans are bytes; not every byte-valued setting is a boolean.
- Locally cached Meteor 26.1.2 sources confirm Settings/SettingGroup serialize changed settings only. Empty settings are normal.
- Argus root: `client: "argusclient"`, `category`, and module config IDs. Module objects contain `enabled`, `key`, `state`, `mouse`, `drawn`, and `settings`.
- Example mismatch: Meteor AutoEat's blacklist is not Argus AutoEat's Items list; do not copy it. Meteor ElytraFly horizontal speed is not automatically Argus ElytraFly Speed: modes and units need proof.
- Base implementation is compiled in `libs/client-base.jar`; editable GUI delegates to it. `Module.fromJson` skips missing settings and catches setting exceptions; `ModuleRegistry.fromJson` catches module exceptions. Directly passing sparse imported JSON therefore neither establishes defaults nor provides reliable failure reporting.
- `Presets.java` wraps native registries and adds layout data; `PresetsPanel.java` provides the existing settings entry point. Do not fork the native preset store.

## Global constraints

- All paths below are relative to `1.21.11/`; keep changes within that project. Preserve all pre-existing work, especially current AutoRekit changes.
- Inspect applicable AGENTS.md and current git status when implementation starts. Use an isolated checkout if available; do not stage or overwrite unrelated work.
- No downloading configs, executing source content, or copying private account/proxy data. Use synthetic fixtures in tests; local player configurations stay outside the repo and release JAR.
- Use existing Minecraft NBT readers, supporting raw and gzip NBT with explicit accounting. Limits: 8 MiB input file, 32 MiB decompressed/read allocation budget, 64 levels nesting, 4,096 modules and 65,536 settings overall. Reject malformed structures and duplicate source module IDs with useful errors.
- Parse files off the render thread; capture registry/default metadata and perform application on the client thread. Ignore stale async preview results after source changes or closing the screen.
- Implementation and build are now authorized by the user’s follow-up. No instance installation, publishing or deployment is requested.

## Review focus

- Previous Argus custom values must not leak into defaulted imported settings (Task 1/3).
- Sparse Meteor settings and byte/enum/list values must retain their meaning (Task 2/3).
- Duplicate mappings and unsupported enabled addon modules must be visible and deterministic (Task 3).
- Damaged/oversized input or stale preview must never modify live settings or presets (Task 2/5).
- Native loader exception swallowing must not turn partial application into reported success; backup failures must prevent activation (Task 4).

## Files and interfaces

Create package `dev.argus.client.config.importing`:

- `ConfigImportModel.java`: immutable records and enums. `SourceConfig(Format format, List<SourceModule> modules)`, `SourceModule(String id, boolean enabled, Map<String, JsonElement> settings, JsonObject extensions, Optional<SourceBind> bind)`, `SourceBind(boolean mouse, int code, int modifiers, String mode)`, `Issue(String sourceId, String field, Severity severity, String message)`; enum values are defined alongside the records. Meteor paths are `group/name`; native settings use their exact config IDs. Defensively copy mutable JSON.
- `ArgusModuleCatalog.java`: `static ArgusModuleCatalog capture()` runs on the client thread; exposes immutable metadata/default JSON, supported type validators and eligibility for registered modules without mutating them. Include supported module extension codecs.
- `ExternalConfigReader.java`: `static SourceConfig read(Path source) throws IOException` and `static List<Path> candidates(Path directory) throws IOException`. Dispatch by verified structure; callers must resolve ambiguous folders before reading.
- `MeteorConfigReader.java` and `ArgusConfigReader.java`: format-specific `static SourceConfig read(Path file) throws IOException` implementations.
- `MeteorMappings.java`: `static List<ModuleMapping> modules()` with explicit target IDs, source setting paths, transforms and source evidence. Mapping record definitions live here; each setting mapping validates type, range, enum domain and units.
- `ConfigImportConverter.java`: `static ImportPreview convert(SourceConfig source, ArgusModuleCatalog catalog, boolean importBinds)`. Define `ImportPreview(JsonObject preset, List<Issue> issues, Set<String> enabledTargets)` here. Output always includes complete defaults for eligible target settings and explicit enabled states; optional preserved-bind fields are omitted.
- `ImportedPresetStore.java`: `static void save(String name, ImportPreview preview) throws IOException`, `static ImportLoadResult load(String name)` and `static boolean isImported(String name)`. Define `ImportLoadResult(boolean success, String backupName, List<Issue> issues)` here. Own import metadata and backup/application coordination.

Create GUI `gui/ConfigImportScreen.java` extending `UiScreen`, `gui/PresetLoadConfirmationScreen.java` for the modal, and `gui/PresetLoadController.java` for its testable state transitions. The controller exposes `request(PresetCategory category, String name)`, `chooseSaveCurrent()`, `confirmSaveAndLoad(String backupName)`, `confirmLoadWithoutNamedSave()` and `cancel()`; inject save/load operations so failure and duplicate-submit paths can be tested without rendering. Modify `gui/PresetsPanel.java` for the import button and to route every manual Load through the confirmation controller and `gui/Presets.java` to route imported-preset loads through the checked loader. Ensure server/world auto-load uses the same checked path (including the base multiplayer hook); do not enable binding imported presets until this is covered.

Create tests under `src/test/java/dev/argus/client/config/importing/` named after the catalog, readers, converter and store; GUI async state can use a small controller helper if needed for direct testing. Put synthetic fixtures under `src/test/resources/config-import/`. Update version/root README usage when implementation ships.

## Task 1: Capture Argus defaults without changing the running client

**Consumes:** Existing Module/Setting APIs and registry, including current source-added AutoRekit.
**Produces:** Model records and `ArgusModuleCatalog.capture()` from the interfaces above.

- [ ] Write `ArgusModuleCatalogTest`: deliberately change a live setting; capture must return its declared default, leave current value/state/bind unchanged, and run no change callbacks. Cover booleans, numbers, enums, identifiers, colours, collections and excluded settings.
- [ ] Test eligible gameplay modules versus permanent/client/HUD exclusions, and default extension data for AutoRekit. Use existing test environment fixtures for Minecraft-dependent tests.
- [ ] Run `./gradlew test --offline --tests '*ArgusModuleCatalogTest'`; observe failure before implementation.
- [ ] Implement immutable records and capture metadata. Serialize `Setting.getDefaultValue()` using codecs equivalent to the actual setting subclass serializers; do not temporarily reset/mutate registered settings or instantiate modules with constructor side effects. Fail preview explicitly if a target setting has no correct default codec.
- [ ] Run the focused test command; expect PASS. Commit only this task's files if commits are available.

## Task 2: Read Meteor and Argus files

**Consumes:** `SourceConfig`, `SourceModule`, `SourceBind` and `Issue` model definitions.
**Produces:** Reader and candidate-discovery methods listed above.

- [ ] Write `ExternalConfigReaderTest`, `MeteorConfigReaderTest` and `ArgusConfigReaderTest` using synthetic raw/gzip NBT, native JSON, empty Meteor settings, profile selection, and native module extensions.
- [ ] Assert exact active states, group-qualified paths and bind representation; reject malformed roots, duplicate IDs, ambiguous folders and non-module categories. Test all size/depth/count budgets, truncated gzip and invalid JSON without any writes.
- [ ] Run `./gradlew test --offline --tests '*ConfigReaderTest'`; observe failures.
- [ ] Implement bounded readers using Minecraft NBT APIs and Gson. Verify the exact 1.21.11 NbtIo/NbtAccounter signatures locally. File extension alone is not proof of format; unknown JSON is rejected, not treated as Argus.
- [ ] Re-run the focused command; expect PASS. Commit only reader code and synthetic fixtures/tests if available.

## Task 3: Convert enabled modules and clear settings

**Consumes:** `SourceConfig` and `ArgusModuleCatalog`.
**Produces:** `MeteorMappings.modules()` and `ConfigImportConverter.convert(...)`.

- [ ] Write `ConfigImportConverterTest`: source Sprint enabled yields target Sprint enabled with complete Argus defaults; a nondefault current Sprint setting must not appear. Source-disabled and unrepresented gameplay modules are explicitly disabled. Excluded client/HUD settings do not appear.
- [ ] Test missing Meteor settings stay at Argus defaults, native exact settings survive, unsupported enums/ranges/IDs fall back with issues, AutoEat blacklist is not copied into Items, and an unsupported enabled addon is prominently reported.
- [ ] Test collision OR activation, conflicting values falling back to defaults, deterministic output, and binds off/on with unsupported modifier/release cases.
- [ ] Run `./gradlew test --offline --tests '*ConfigImportConverterTest'`; observe failures.
- [ ] Audit every Meteor source module against the registered Argus catalog. Add all clear module counterparts, documenting ambiguous exclusions and many-to-one cases. Inspect module behaviour/defaults locally; use javap for compiled Argus classes. Do not limit support to the examples inspected during planning or assume every similarly named addon is compatible.
- [ ] Implement default-first conversion. Map individual settings only when semantics, target mode and units are proven; otherwise keep the target default and explain the fallback. Preserve module mapping independently of setting support.
- [ ] Re-run the focused command; expect PASS. Audit the user's selected Meteor file read-only and report mapped/unmapped enabled counts; do not promise a coverage number before the mapping audit. Commit only task files if available.

## Task 4: Save native presets and load with backup

**Consumes:** `ImportPreview`, native `PresetData`, `PresetRegistry`, `Presets.load(...)` and configuration save APIs.
**Produces:** `ImportedPresetStore` methods and `ImportLoadResult`.

- [ ] Write `ImportedPresetStoreTest`: import writes a new Modules preset without changing live state; restart/reload retains it; case-insensitive duplicate names and invalid names are rejected without overwriting.
- [ ] Test backup write failure leaves all module states unchanged; successful load applies all settings before any module activation; simulated application failure attempts backup restoration and returns failure. Native loader swallowing a malformed setting must not pass the test.
- [ ] Test imported presets loaded through manual, singleplayer and multiplayer paths use identical default/application semantics. If binding support is deferred, assert binding is blocked with a clear message and existing native bindings still work.
- [ ] Run `./gradlew test --offline --tests '*ImportedPresetStoreTest'`; observe failures.
- [ ] Implement staged file writing and registry registration through existing APIs, adding a small adapter only where needed. Confirm registry side effects before using restorePreset; do not maintain a second store. Mark imports with a versioned metadata key ignored by native module decoding.
- [ ] Implement backup capture and checked loading on the client thread: disable affected active modules, apply and verify settings/extensions/binds, then activate desired modules. Validate before mutation; explicitly surface exceptions rather than relying on base catch-and-continue behaviour. Persist the successful result. Preserve unrelated client/HUD/layout state.
- [ ] Re-run the focused command; expect PASS. Commit only task files if available.

## Task 5: Import preview in Settings → Presets

**Consumes:** Candidate discovery, readers, converter and store interfaces.
**Produces:** `ConfigImportScreen`, Presets panel entry, usage documentation.

- [ ] Add `PresetLoadControllerTest`: opening/cancelling changes nothing; load requires a confirmation action; Save current & load saves before loading; invalid/duplicate names and save failure never load; Load without saving skips the named save but preserves the mandatory imported backup; repeated submissions run once; source replacement invalidates confirmation. Test native category scope and imported Modules scope.
- [ ] Add tests for source-change/close invalidating pending preview work; saving the preview must not load it; selected source/profile and target name must stay visible after a recoverable error.
- [ ] Run the focused GUI/controller tests; observe failure before implementing their state logic.
- [ ] Add Import button opening a screen with path input (supports spaces), profile/file choice when ambiguous, target name, optional Import binds checkbox, Preview, Save preset and Cancel. Use existing UiInput/UiButton widgets; no new native file-picker dependency.
- [ ] Implement the modal and controller exactly as specified in Load confirmation popup; wire native and imported manual loads to it, retaining a separate checked automatic-load path. Verify cancel focus, keyboard containment, button ordering, inline naming/errors and small-screen layout.
- [ ] Preview lists modules to enable first, then unsupported enabled modules and defaulted/skipped settings. Distinguish 'mapped module using Argus defaults' from 'settings copied'. Disable Save on fatal parse/conversion errors. Show backup name after a later Load action.
- [ ] Run `./gradlew test --offline` and `./gradlew build --offline`; expect zero test failures and successful merge/release checks. Run `python tools/verify_merge.py`, `python tools/verify_branding.py` using their documented arguments, then `git diff --check`.
- [ ] In a running client, verify a real Keo Meteor profile and an exported Argus config: correct enabled targets, default fallbacks, bind option, no preview mutation, saved preset reload and backup restoration. Check small screens, long reports, all confirmation actions, keyboard-only cancellation, failed saves and duplicate clicks. If client access is unavailable, record that limitation explicitly; do not claim in-game verification.
- [ ] Update README with supported inputs, default fallback behaviour, skipped categories, backups and import steps. Commit only task files if available. Do not install into a launcher instance without a subsequent implementation/install request.

## Completion criteria

A user can select Meteor or Argus module config, see which enabled modules will transfer, save a normal Argus preset and load it. Every imported eligible module has deterministic Argus-default settings plus only clearly mapped source values; unsupported enabled modules are visible. Existing values never fill unmapped settings, preview never changes the live config, and loading an imported preset creates a usable recovery backup. Every manual GUI load warns about replacement and lets the user save the current config before proceeding; cancellation or save failure leaves it unchanged.


## Execution ledger

- Implemented format readers, explicit module mappings, default-first conversion, native preset registration and a checked loader. GUI includes path/drop import, profile choice, compatibility report, optional binds and separate saving/loading.
- Implemented the shared native/imported manual load modal, inline save naming/errors, Cancel focus, keyboard containment, duplicate-submit protection and recovery restoration.
- Ruling: Git metadata is read-only; branch creation failed, so changes remain in this checkout without commits, preserving unrelated AutoRekit/accounts/proxies work.
- Ruling: Injected backend/controller helpers replace plan-only interface sketches to test actual ordering, failures and async invalidation without rendering Minecraft.
- Ruling: Recovery snapshots and Save current for imported configs use a complete checked Modules snapshot. The compiled native Modules filter omits colours/binds/object settings and is insufficient for this recovery requirement.
- Review: independent reviewer found fractional Float normalization, unordered collection comparison, dynamic colour serialization, filtered backups, malformed NBT fields and on-disk freshness issues. Addressed with regressions, stable stored-colour capture, canonical codecs, full snapshots, strict tag checks and file fingerprints. Fixed naming-screen refresh after failed loads as well.
- Read-only real-file audit: Keo Optimized(1) has 360 source modules, 44 enabled. Current mappings yield 19 enabled Argus targets; 24 enabled source entries are explicitly unsupported (two source entries share Offhand). No source configs were modified or copied into the project.
- In-game interaction/visual verification remains unavailable; do not claim it was performed. Build and final verification results are recorded below when completed.
- Review follow-up: Meteor AutoReconnect `General/delay` maps to two Argus settings (Minutes + Delay seconds). A value such as 119.5 s copied Minutes=1 while Delay=59.5 was rejected, silently changing the delay. Split-unit mappings now validate every target first and copy all-or-nothing (`reconnectDelayFallsBackAsAUnitWhenSecondsCannotBeRepresented` RED→GREEN). Added a Float-at-minimum regression guard (`0.1` with minimum `0.1`); it already passed after the earlier Float normalization fix.
- Final verification (2026-11-08): `./gradlew test --offline --rerun-tasks` → 104 tests, 0 failures, 0 errors, 0 skipped; `./gradlew build --offline` → BUILD SUCCESSFUL; `python3 tools/verify_branding.py build/libs/argus-client.jar` → PASS; `python3 tools/verify_merge.py build/libs/argus-client.jar` → PASS; `git diff --check` → clean (untracked files checked for trailing whitespace too).
- Not performed: in-game interaction/visual verification (no client access in this session). The checked-in `1.21.11/argus-client.jar` was not replaced with the new build output, and nothing was installed into a launcher instance.
- In-game bug (2026-11-08): loading an imported preset failed with “Couldn’t load config: Unsupported value; using Argus default”. Root cause: base AutoNameTag declares `Delay` default 0.0 with minimum 0.1; the native setter clamps, but the catalog stored/validated the raw declared default, so every imported preset failed pre-load validation (no live state changed). Fixed: catalog defaults and `current()` use the native clamped value (`outOfBoundsDeclaredDefaultsUseTheClampedEffectiveValue` RED→GREEN); pre-load validation errors now name module/setting (`invalidStoredSettingNamesTheModuleAndSetting` RED→GREEN). Suite 106/106, build and release checks pass. Presets saved before the fix must be re-imported. Found with a throwaway probe that built 163/177 real modules under a stub Fabric provider; 14 modules (incl. BetterChat, Search, NoRender) could not be constructed headless and were not probed.

## Follow-up: whole-instance Meteor import (2026-11-08)

User request: import more config types that have Argus equivalents, browse another instance's Meteor folder, auto-detect files, good GUI. Accounts and proxies explicitly excluded by the user. User skipped mockups ("do the implementation then if the gui is bad ill fix it after").
- Added `MeteorDataReader` (friends/enemies/waypoints/macros, shared bounded NBT read extracted from `MeteorConfigReader.readNbt`), `MeteorInstanceScanner` (Prism/MultiMC sibling instances, folder-level resolution, detection + named skips), `ArgusDataPlan` (pure additive plan with reasons), `ArgusDataStore` (backup to `argusclient/import-backups/`, add via native registry APIs, verify, undo on failure) and `MeteorInstanceImportScreen`, opened from **From an instance** on the import screen.
- Ruling: friends/enemies/waypoints/macros are added, never replacing — the user's “only import the ones with equivalents” plus existing Argus data the user would not expect lost — cost if wrong: a replace mode would need adding.
- Ruling: HUD layout has no faithful mapping (different widget set and anchoring) so it is listed as staying in Meteor — cost if wrong: HUD import is new work.
- Ruling: Meteor waypoint files named without a dot (singleplayer worlds) map to Argus “singleplayer”; dimension maps overworld/nether/end only, others skipped with a reason; block coordinates kept exactly; icons/colours not carried — cost if wrong: waypoints land in the wrong world list.
- Ruling: modified macro chords import unbound with a warning rather than as plain keys, matching the module-bind rule.
- Tests: `MeteorDataImportTest` (7 tests) written first, failed to compile before implementation, now pass. Suite 113/113, build, verify_branding, verify_merge and `git diff --check` pass. Read-only audit of the three real Keo/Seed instances: all files parse (4 friends each, 0–2 waypoints, no enemies/macros saved); nothing copied into the repo.
- Not verified: the new screen has not been seen in game; screen state logic has no automated test (pure parts do).
