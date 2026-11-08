# Random Stuff Client

Vortex Client with a Search panel in the click GUI. Type in the existing search
box to show matching modules together in the Search panel, after the category
panels. Results match module names and aliases, exclude HUD widgets, and are
sorted alphabetically with a maximum of 14 results.

## Install

Download [`vortexclient-search.jar`](vortexclient-search.jar) and put it in your
Minecraft instance's `mods` folder, replacing the previous Vortex/Egg Client JAR.
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
coordinate. All other original JAR entries are preserved byte-for-byte.

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

The included JAR retains the supplied client's existing Vortex branding, config
loading changes, auto-focused search box, and drawn search bar.

## Validation

- SearchPanel compiled targeting Java 21.
- ASM bytecode verification passed for SearchPanel and the patched ClickGuiScreen.
- JAR integrity and single-panel constructor/position wiring checks passed.
- Every original JAR entry except ClickGuiScreen.class is unchanged; only
  SearchPanel.class was added.
- In-game behavior and appearance have not been tested.
