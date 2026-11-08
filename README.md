# Random Stuff Client

Random Stuff Client with a Search panel in the click GUI. Type in the existing search
box to show matching modules together in the Search panel, after the category
panels. Results match module names and aliases, exclude HUD widgets, and are
sorted alphabetically with a maximum of 14 results.

## Install

Download [`random-stuff-client.jar`](random-stuff-client.jar) and put it in your
Minecraft instance's `mods` folder, replacing the previous client JAR.
Keep only one copy of the client installed.

Requirements declared by the included client:

- Minecraft **1.21.11**
- Fabric Loader **0.19.5 or newer**
- Fabric API for Minecraft 1.21.11
- Java **21 or newer**

The mod ID and Java packages remain `eggclient`.

## Source and build status

[`SearchPanel.java`](src/main/java/dev/eggclient/client/gui/panels/SearchPanel.java)
is the panel source used for this JAR, using Fabric intermediary Minecraft names.
This repository does not contain the full client's source or a standalone build
project. The supplied compiled client was patched to construct one SearchPanel
immediately after the category panels and position it using their running X
coordinate. Display branding was also updated in the packaged classes, language files, and mod metadata. Internal mod IDs, Java packages, resource paths, and legacy migration keys remain compatible.

To integrate the panel into a full matching client source project, add the source
and import `dev.eggclient.client.gui.panels.SearchPanel` in `ClickGuiScreen`.
Immediately after the constructor's category-panel loop, add:

```java
SearchPanel searchPanel = new SearchPanel();
searchPanel.setX(x);
this.panels.add(searchPanel);
```

For a mapped development project, remap `net.minecraft.class_4587` and
`searchBox.method_1882()` to the equivalent names in your mappings.

The included JAR retains the supplied client's existing Random Stuff Client branding, config
loading changes, auto-focused search box, and drawn search bar.

## Validation

- SearchPanel compiled targeting Java 21.
- ASM bytecode verification passed for SearchPanel and the patched ClickGuiScreen.
- JAR integrity and single-panel constructor/position wiring checks passed.
- Display names use Random Stuff Client; legacy identifiers remain for compatibility.
- Branding, JAR integrity, entrypoints, and Search panel checks passed.
- In-game behavior and appearance have not been tested.

## Branding tools

To apply the same display rename to a compatible existing build:

```sh
python3 tools/rebrand.py input.jar random-stuff-client.jar
python3 tools/verify_branding.py random-stuff-client.jar
```

The rename covers the window title, HUD watermark and welcome text, chat messages,
Discord description, language text, and Fabric display metadata. Historical
branding strings used to recognize legacy settings remain in the migration code.
