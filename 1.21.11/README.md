# Random Stuff Client — Minecraft 1.21.11

[Repository overview](../README.md)

A Fabric client with a Meteor-style floating-window GUI, a pastel theme,
ranked search, module settings and Friends management.

## Install

Download [`random-stuff-client.jar`](random-stuff-client.jar), place it in your
instance's `mods` folder, and replace earlier copies of this client or its
separate GUI add-on.

- Minecraft **1.21.11**
- Fabric Loader **0.19.5 or newer**
- Fabric API for Minecraft 1.21.11 (built against **0.141.3+1.21.11**)
- Java **21 or newer**

## Open and use the GUI

Use the client's existing GUI keybind or type `/randomstuff` in chat.

| Control | Action |
| --- | --- |
| **Modules** tab | Floating category windows and a Favourites window |
| **Search** floating panel | Search modules, aliases and settings inside the right-shift menu |
| **Friends** tab or `/friends` | Add, remove and list friends |
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
This GUI is independently implemented against Random Stuff Client's existing
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

From this `1.21.11` directory:

```sh
./gradlew build
python3 tools/verify_branding.py build/libs/random-stuff-client.jar
python3 tools/verify_merge.py build/libs/random-stuff-client.jar
```

On Windows use `gradlew.bat build`. Install the combined output at
`build/libs/random-stuff-client.jar`. The smaller `random-stuff-client-gui-...jar`
is an intermediate build artifact.

The build compiles the GUI against Mojang mappings, remaps it to Fabric
intermediary names, and merges it into the supplied compiled base. It preserves
the core Mixin metadata and registers the source-built GUI integration.
JUnit tests cover search ranking, inline placement, GUI/input scaling, font and
art resolution, settings dependencies, preferences, layout, namespaces, migration
and shared-field linkage. The build also rejects packaged saved configurations.

## Source and data

- `src/main/java/dev/randomstuff/client/gui/`: menus, search, theme, controls and GUI integration.
- `src/main/java/dev/randomstuff/client/config/`: settings-directory migration.
- `src/test/java/`: regression tests.
- `src/main/resources/`: GUI Mixin configuration and font assets/license.
- `libs/client-base.jar`: the compiled underlying client, including existing features and protections.
- `tools/`: package verification and an optional display-branding utility.

The ZIPs did not include the full underlying client's source. This project
rebuilds the available and newly implemented GUI source and combines it with
that compiled base. The runtime mod/resource ID is `randomstuffclient`, and
packages are under `dev.randomstuff`.

No user config, preset, saved window layout or friend list is bundled in the
repository or release JAR; releases start with factory defaults.

Settings are stored in `randomstuffclient/` inside the instance's game directory;
GUI-only preferences are in `randomstuffclient/gui.properties`. An upgrade
copies a previous settings folder if the new folder does not exist. Existing
Random Stuff Client settings take priority; historical names appear only in
compatibility code.

Build and automated checks are verified. The new GUI still needs visual and
interaction testing in a running Minecraft instance.

To regenerate the GUI font atlas offline (JDK only):

```sh
java -Djava.awt.headless=true tools/BakeGuiFont.java
java -Djava.awt.headless=true tools/BakeGuiArt.java
```
