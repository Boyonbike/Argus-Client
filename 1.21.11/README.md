# Random Stuff Client — Minecraft 1.21.11

One client JAR combining the original click GUI's Search panel with the tabbed
module menu and Friends window from the supplied GUI project. Display branding
uses **Random Stuff Client** throughout.

[← Repository overview](../README.md)

## Install

Download [`random-stuff-client.jar`](random-stuff-client.jar) and put it in your
Minecraft instance's `mods` folder. Replace previous copies of this client or
its separate GUI add-on; the combined JAR includes both.

Requirements:

- Minecraft **1.21.11**
- Fabric Loader **0.19.5 or newer**
- Fabric API for Minecraft 1.21.11 (built against **0.141.3+1.21.11**)
- Java **21 or newer**

## Open the menus

- `/randomstuff`: tabbed module menu with search and draggable category windows.
- `/friends`: Friends window with add, remove, and list controls.
- **Friends** tab: open Friends from the module menu.
- **Classic** tab: open the client's original click GUI, including the Search panel.

In the module menu, left-click a module to toggle it. Drag a category header to
move it, right-click a header or click its minus sign to collapse it, and use
the mouse wheel to scroll. The search box filters modules by name. Categories wrap into columns that fit
the scaled screen; vertical scrolling reaches the remaining windows. Draft
friend names survive scrolling.

The Classic Search panel gathers module name/alias matches, sorts them
alphabetically, excludes HUD widgets, and shows up to 14 results. The client
retains auto-focused classic search and its search bar, plus protections against
presets overwriting HUD, UI, Colors, and Fonts. The Classic GUI also includes
the lavender UI colour defaults from the second client build; existing saved
settings can override defaults.

## Build

Run these commands from this `1.21.11` directory with JDK 21 or newer.
A Gradle wrapper and Fabric Loom build are included:

```sh
./gradlew build
```

On Windows use `gradlew.bat build`. The combined installable client is:

```text
build/libs/random-stuff-client.jar
```

The smaller `random-stuff-client-gui-...jar` is an intermediate GUI artifact;
install the combined JAR above. The build compiles the GUI against Mojang
mappings, remaps it to Fabric intermediary names, and merges it into the base
client while preserving its Mixin runtime metadata.

### What source is available

- [`ModulesScreen.java`](src/main/java/dev/randomstuff/client/gui/ModulesScreen.java): tabbed module menu.
- [`FriendsScreen.java`](src/main/java/dev/randomstuff/client/gui/FriendsScreen.java): friends management using the client's existing social registry.
- [`FriendsMod.java`](src/main/java/dev/randomstuff/client/gui/FriendsMod.java): command registration and deferred screen opening.
- [`SearchPanel.java`](src/main/java/dev/eggclient/client/gui/panels/SearchPanel.java): original GUI's Search panel.
- [`libs/client-base.jar`](libs/client-base.jar): previously supplied compiled client, including the search hook, config protections, branding, and imported lavender defaults.

The ZIPs did not include the full underlying client's Java source. This project
rebuilds the available GUI sources and combines them with that compiled base;
it is not a reconstruction of all client source. Internal `eggclient` mod IDs,
Java packages, resource paths, and migration keys remain for compatibility.
The merged GUI uses the `dev.randomstuff.client.gui` package and does not require
a separate `shuclient` mod.

## Verification

```sh
python3 tools/verify_branding.py build/libs/random-stuff-client.jar
python3 tools/verify_merge.py build/libs/random-stuff-client.jar
```

The JUnit viewport regression test, compilation/remapping, package integrity,
entrypoint registration, display
branding, and bytecode checks passed. In-game appearance and controls have not
been tested in a running Minecraft instance.

The display rename can also be applied to a compatible existing build:

```sh
python3 tools/rebrand.py input.jar output.jar
```

Historical branding strings used to recognize old settings remain in migration
code. `tools/rebrand.py` changes display literals without relocating internal
classes or Minecraft resources.
