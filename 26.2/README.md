# Argus Client — Minecraft 26.2

[Repository overview](../README.md)

A Fabric client with a Meteor-style floating-window GUI, a pastel theme,
ranked search, module settings and a Social menu for chat, friends and your clan.

## Install

Download [`Argus-2.6.0-mc26.2.jar`](Argus-2.6.0-mc26.2.jar), place it in your
instance's `mods` folder, and replace earlier copies of this client or its
separate GUI add-on.

- Minecraft **26.2**
- Fabric Loader **0.20.1 or newer**
- Fabric API for Minecraft 26.2 (built against **0.161.0+26.2**)
- Java **25 or newer**

## Open and use the GUI

Use the client's existing GUI keybind or type `/argus` in chat.

| Control | Action |
| --- | --- |
| **Modules** tab | Floating category windows and a Favourites window |
| **Search** floating panel | Search modules, aliases and settings inside the right-shift menu |
| **Social** tab, `/social` or `/friends` | Open the Social menu (or Social settings, see below) |
| **HUD** tab | Open the existing HUD editor |
| **Theme** tab | Choose Light/Dark, accent colour, grid snapping and reset positions |
| **Classic** tab | Open the original click GUI and its Search panel |

Drag a window header to move it. Right-click its header or click the minus sign
to collapse it. Scroll over a window to scroll its modules; scrolling past a
window's limit scrolls the surrounding canvas. Positions, collapsed windows,
favourites and appearance choices are saved between launches.

Left-click a module to toggle it, right-click it to open settings, or middle-click
it to add/remove it from Favourites. Enabled modules use an animated accent
highlight. Module names and descriptions appear in hover tooltips.

### Search

**Search is a draggable, collapsible dropdown panel beside the category panels.**
Expand it if needed, click its text box and type. It is not a top navigation tab
and does not require a shortcut.

Search ranks module names, aliases and individual settings together. Exact and
substring matches appear before approximate matches, so minor typing mistakes
are tolerated. Each result shows its type and owning category/module.

- Left-click a module result to toggle it.
- Right-click a result to open the module's settings.
- Click a setting result to open its owning module and reveal that setting.
- Drag its header to move it; use the header’s plus/minus control to expand or collapse it.
- Scroll its results independently. Ctrl+F optionally focuses and reveals the same panel.

### Appearance

The user-facing controls are **Light / Dark** and **Accent colour**, with Dark
and Lavender as defaults. Accent choices are Lavender, Blue, Green, Peach,
Pink and Red. The theme applies across modules, search, friends and settings.
The menu uses an independent, compact scale rather than inheriting Minecraft’s
GUI zoom. **Theme → GUI scale** adjusts it, and clicking the percentage resets
it to 100%. The default renders a category window at about 196 screen pixels
wide on ordinary desktop windows.

The font uses a 2048×896 pre-rendered atlas with 128-pixel glyph cells, four
times the previous source resolution in each dimension. Text inputs use the
same font, with matching cursor and selection positioning. Panels have
antialiased, high-density rounded edges and category icons. The atlas avoids
native TrueType loading during Minecraft resource reloads.

The design and search interaction are inspired by
[Catppuccin for Meteor Client](https://github.com/X-C-0/catppuccin-addon).
This GUI is independently implemented against Argus Client's existing
module and settings APIs; it does not require Meteor or that add-on.

### Module settings

Click values to edit them. Booleans toggle; enum values cycle; text, numeric
and colour values use an editor. Colour input accepts `#RRGGBB` or `#AARRGGBB`.
Right-click a value to reset it. Expand sections or `+` labels to access nested
settings; a search result expands the relevant sections automatically.

Collection settings have an entry list with individual selection controls and
an explicit one-entry add/remove field. The keybind control accepts a keyboard
key or mouse button; Delete/Backspace clears it, and Escape cancels capture.

## Build and verify

From this `26.2` directory:

```sh
./gradlew build
python3 tools/verify_branding.py build/libs/Argus-2.6.0-mc26.2.jar
python3 tools/verify_merge.py build/libs/Argus-2.6.0-mc26.2.jar
```

On Windows use `gradlew.bat build`. Install the output at
`build/libs/Argus-2.6.0-mc26.2.jar`.

Fabric Loom compiles the core and GUI together against Mojang mappings and
remaps the result to Fabric intermediary names. Gradle downloads the pinned
optional integration APIs for compilation. Install the corresponding mods
separately if you use those integrations.
JUnit tests cover search ranking, inline placement, GUI/input scaling, font and
art resolution, settings dependencies, preferences, layout, namespaces, migration
and shared-field linkage. The build also rejects packaged saved configurations.

## Source and data

- `src/main/java/` contains the client core, modules, GUI and Mixins.
- `src/main/resources/` contains Fabric metadata, the access widener, Mixin configuration and assets.
- `src/test/java/` contains regression tests.
- `tools/` contains package verification and legacy binary migration utilities.

The core Java source was recovered from the compiled Argus base and repaired
for source compilation. The existing GUI and feature source takes precedence
where the two previously overlapped. The runtime mod and resource ID is
`argusclient`, and the client packages are under `dev.argus`.

No user config, preset, saved window layout or friend list is bundled in the
repository or release JAR; releases start with factory defaults.

Settings are stored in `argusclient/` inside the instance's game directory;
GUI-only preferences are in `argusclient/gui.properties`. An upgrade
copies a previous settings folder if the new folder does not exist. Existing
Argus Client settings take priority; historical names appear only in
compatibility code.

Build and automated checks are verified. The new GUI still needs visual and
interaction testing in a running Minecraft instance.

To regenerate the GUI font atlas offline (JDK only):

```sh
java -Djava.awt.headless=true tools/BakeGuiFont.java
java -Djava.awt.headless=true tools/BakeGuiArt.java
```

## Auto Rekit

Open **Player → AutoRekit → Manage rekit presets**. Arrange your inventory, worn armour and
offhand, enter a preset name, and click **Save inventory**. Saving also selects
that preset; click another saved preset to make it active. **Update** replaces a
preset with your current inventory, and **Delete** asks for a second click.

- **Auto** refills while a chest, barrel, ender chest, shulker box, hopper or
  dispenser/dropper is open. **Manual** waits for the **Rekit** container button.
- **Stack size** chooses the saved amounts or each item's full stack limit.
  Existing excess is left alone.
- **Replace damaged gear** upgrades carried tools, worn armour and offhand gear
  to a better-durability exact match. Worn armour uses the normal equip action;
  displaced gear returns to storage and your original hotbar item is restored.
- Matching ignores custom/item names and damage; enchantments, potion contents,
  and all other components must match. Minecraft's actual stackability still applies.
- **Replace with larger stacks** swaps in a bigger matching stack if names prevent
  merging. Displaced items return to storage. Partial replacements need a spare
  storage slot for the displaced stack; unavailable replacements are skipped.
- Unrecorded slots are untouched. Transfers pause while you carry an item on the
  cursor and respect the client's click limit and the module's transfer delay.
- While AutoRekit is enabled with an active preset, it takes priority over
  ChestStealer in supported storage screens, including after a manual run.

Inventory presets and the active selection persist in the normal module config.

Presets saved before equipment support need **Update** once to record armour and offhand. Curse of Binding prevents replacing worn armour in survival, as in vanilla Minecraft.

### Import Meteor or Argus configs

Open **Settings → Presets → Import config**. Paste a file/folder path or drop a file into the import screen, choose a preset name, and select **Preview import**. Supported inputs are Meteor `modules.nbt`, Meteor profile folders, and Argus `modules.json` or Modules/Everything preset JSON. A folder containing several candidates asks you to choose one. Everything inputs import their module portion only.

The preview lists modules to enable and unsupported enabled modules. Clear module counterparts transfer their on/off state; only explicitly compatible settings transfer. All other gameplay settings use declared **Argus defaults**, rather than your current values or Meteor defaults. Unrepresented gameplay modules are disabled when loaded. HUD, appearance and other client configuration stay as they are. Compatible binds can be imported optionally; the default keeps your current binds. Meteor addon modules without a supported counterpart are reported as skipped.

**Save preset** imports without changing the running config. Choose **Load** afterwards to open the replacement warning. **Save current & load** lets you name and save your current configuration first; **Load without saving** skips that named save, and **Cancel** changes nothing. Save errors stop the load. Imported loads always create a complete gameplay recovery snapshot, including colours, binds and AutoRekit inventory presets. **Restore backup** opens the same confirmation flow, and recovery snapshots also appear under Modules. Existing native presets use the same replacement warning and retain their native category scope.

#### From another instance

In the import screen choose **From an instance**. Argus lists the other launcher instances (Prism/MultiMC `instances` folder) that have a Meteor folder; pick one, or paste/drop any instance, `minecraft` or `meteor-client` folder and press **Scan**. The screen shows everything in that folder with an Argus equivalent — **Modules** (main config or a saved profile), **Friends**, **Enemies**, **Waypoints** and **Macros** — with counts, and ticks the ones that have entries. Files with no Argus equivalent (HUD layout, Meteor settings, GUI/themes, accounts, proxies, addon data) are listed under *Stays in Meteor* and never read.

If the instance ran Comet, a **Chat Tools data** row brings over its Chat Tools and Discord IGN Sync data: homes and favourites, recent players, saved cooldowns, the clan list, the Auto TPY whitelist, blocklist and role filter, DM patterns, command options, the Discord roster and the relay link. It only adds: names already in Argus stay, servers Argus already has cooldowns or recent players for keep theirs, and Argus's own Discord sync tracking, roster and relay are kept. Comet's Chat Tools settings transfer with the Modules preset. The chat data file is backed up to `argusclient/import-backups/` first.

**Import** saves the modules as a normal Modules preset (load it from Presets when ready, with the usual replacement warning) and **adds** friends, enemies, waypoints and macros to what Argus already has — existing entries are never changed or removed. Duplicates, invalid player names, waypoints with no matching dimension and macros without messages are skipped and listed under *Won’t transfer* with the reason; macro key combinations with modifiers import without a bind. Before adding anything Argus writes the current friends, waypoints and macros to `argusclient/import-backups/`, and stops if it can’t. Waypoints keep their server (from the Meteor file name) and dimension; Meteor icons and colours aren’t carried over. The Meteor folder is only read.

Automatic server/world bindings load imported presets through the checked loader with a recovery backup; they do not interrupt connection with a popup. Import does not copy account/proxy data, friends, macros, themes or waypoints.

## Social

**Social** (formerly Chat Tools) gathers private messages, the server's chat, players, your clan, homes, teleport requests, Auto TPY and the server's own settings in one window, in the same style as Settings. It also contains **BetterChat**: BetterChat no longer appears on its own, it switches on and off with Social, and its settings are the Chat style, Sounds, Animation, Prefix & suffix, Highlights and History cards on the Social settings page.

Social isn't in the module list. Open the Social menu from the **Social** top-bar tab, with its **Open window key** (not bound by default), by clicking a player's name in open chat, with `/social` (or `/chattools`; `/friends` opens it on Players), or with **Open Social Menu** on **Settings → Social**. Like the HUD tab, the Social tab can open either the menu or the settings page: choose under Settings → Appearance → Top bar → *Social tab opens*, or with the *Social tab opens* button on the Social settings page.

| Page | What it does |
| --- | --- |
| **Overview** | What's waiting for you (requests, unread messages, a Discord sync problem), running warmups and cooldowns, favourite homes and recent players, with the newest chat in a card at the bottom: click it to open Chat, drag over lines to select them |
| **Chat** | The server's chat as it appears in game, with times (BetterChat's format when its timestamps are on). Click or drag over lines to select them (Shift+click extends), then **Copy** or Ctrl+C; right-click a line to copy just that one |
| **Messages** | Conversations as chat bubbles. Messages queue for the server's cooldown and show as pending until the server confirms them |
| **Players** | Search, recent players, Argus friends, and actions for any player: message, teleport, friend or enemy, clan, `/ignore` and commands found on the server |
| **Clan** | Members grouped by Discord role, add by hand or import a CSV (pick it or drop it on the window) |
| **Homes** | Go, rename, delete and star homes; starred homes appear on Overview |
| **Teleports** | Incoming requests, warmups and cooldowns, and cooldown lengths (learned from the server, or set by you) |
| **Auto TPY** | Accept teleport requests automatically, with a queue, Discord role filter, whitelist and blocklist (CSV import) |
| **Server** | Your hotspot and the server's own settings. Only shown on servers that have those commands |
| **Settings** | All Social and BetterChat settings |

The message box at the bottom sends public chat (queued at the server's pace), `/commands`, or a private message on a conversation. Friends and enemies are Argus friends; BetterChat colours them in chat and ExtraTab in the tab list, and Social adds clan colours in both. The **Chat Waiting** HUD widget shows waiting requests, unread messages and Discord problems. Chat data (homes, contacts, clan, lists) is saved in `argusclient/chat.json`, separately from settings presets.

**Settings → Discord** has two cards, each with its own switch: **Rich Presence** shows Argus Client as your Discord activity, and **IGN Sync** keeps the clan list and Auto TPY whitelist in step with the Minecraft names posted in your Discord's #ign channel, read through your clan's relay link (**Sync now** in its header syncs at once). The sync never empties a list and only removes names a sync added.

## Credits and licensing

The **Volytra Fly** ElytraFly mode is ported and adapted from
[VolytraFly](https://github.com/Volizray/VolytraFly-Addon) by **Volizray**,
released under **CC0 1.0 Universal**. The attribution, third-party notice and
full licence text are included in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
and [licenses/VolytraFly-CC0.txt](licenses/VolytraFly-CC0.txt), and are packaged
inside `Argus-2.6.0-mc26.2.jar`.
