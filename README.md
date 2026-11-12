# Argus Client

A Fabric client with a Meteor-style floating-window GUI, pastel Light/Dark
themes, ranked module/settings search, and Friends management. This branch holds the Minecraft 26.2 client; other versions live on their own branches (for example `26.1.2` and `1.21.11`).

## Versions

| Minecraft | Client project | Download |
| --- | --- | --- |
| **26.2** | [Source, build files, and documentation](26.2/) | [Argus-2.6.0-mc26.2.jar](26.2/Argus-2.6.0-mc26.2.jar) |
| **26.1.2** | [Source, build files, and documentation](26.1.2/) | [Argus-2.6.0-mc26.1.2.jar](26.1.2/Argus-2.6.0-mc26.1.2.jar) |

## Install for Minecraft 26.2

1. Set up Minecraft **26.2** with Fabric Loader **0.20.1 or newer** and Java **25 or newer**.
2. Install Fabric API for Minecraft 26.2.
3. Download [`Argus-2.6.0-mc26.2.jar`](26.2/Argus-2.6.0-mc26.2.jar) and place it in your instance's `mods` folder, replacing older copies.

## Menus

- The existing GUI keybind or `/argus` opens the floating module menu.
- Windows can be placed anywhere, including beside the navigation bar. Appearance has a fine snap grid, **Show grid while moving**, and **Auto gap**, which keeps dragged windows at least that far apart for tidy layouts. Everything and Modules presets also save and restore the window layout.
- Drag a box across empty space in the module menu to select several windows (hold Shift to add to the selection), then drag any selected window to move them together. Click empty space or press Esc to clear the selection.
- Use the **Search dropdown panel** inside the right-shift menu to search modules, aliases and settings — or just start typing anywhere in the menu. Arrow keys pick a result and Enter toggles it (Shift+Enter opens its settings).
- The navigation bar starts as a circle showing your skin's face; click it to open the bar. A matching gear circle on the right opens **Settings**. Choose the tabs between them with the **Top bar** chips in Settings → Appearance, switch between names and icons there, drag tabs in the bar to reorder them, and right-click a tab to remove it. Names switch to icons automatically when they don't fit.
- **Settings** holds Appearance, Presets, Friends and the other Client modules (Anti Cheat, Baritone, Discord, HUD, Notifications, Settings). Appearance is one scrolling page of cards: Theme (Light/Dark, scale, accent), Top bar, Windows, and the Menu, Colours and Fonts options.
- Every setting has a reset icon that restores its default value. Settings are grouped into cards (General plus one per settings group) that fold open from their titles, and colour settings open a colour picker with hue, shade, transparency, hex entry, rainbow/theme sync and a saved palette you can add colours to.
- **Settings → Presets** saves your current config (everything, or just modules, binds, HUD, colours, visuals or macros) under a name, loads it back, and binds it to any number of servers or singleplayer worlds so it loads automatically when you join. Presets are the same files the `/preset` and `/autopreset` commands use.
- **Settings → Presets → Import config** previews Meteor NBT and Argus JSON configs before saving them as presets. Module on/off states take priority; settings transfer only through clear mappings, otherwise using Argus defaults. Every manual Load warns before replacing the current config, offers **Save current & load**, and lets you cancel. Imported loads create recovery backups with a **Restore backup** action.
- **Settings → Friends** (or `/friends`) adds, removes and lists friends.

The combined client uses Argus Client branding, with Dark mode and
Lavender accents by default, with compact sizing and high-density font/art assets.
Text, corners and icons are rendered at your screen's real pixel size, and menus animate when they open, collapse, scroll and change. Minecraft's standard loading screen is used. Release JARs contain no saved user configuration. Its mod ID and resource namespace are `argusclient`, with Java packages
under `dev.argus`.

## Credits and licensing

The **Volytra Fly** ElytraFly mode is ported and adapted from
[VolytraFly](https://github.com/Volizray/VolytraFly-Addon) by **Volizray**,
released under **CC0 1.0 Universal**. See the
[third-party notices](26.1.2/THIRD_PARTY_NOTICES.md) for attribution and the
[full CC0 text](26.1.2/licenses/VolytraFly-CC0.txt).

## Repository layout

```text
26.2/
├── README.md                 Version-specific instructions
├── THIRD_PARTY_NOTICES.md    Third-party attribution and licence notices
├── licenses/                 Full third-party licence texts
├── src/main/java/            Editable client source
├── src/test/java/            Regression tests
├── src/main/resources/       Mod metadata, Mixins and assets
├── tools/                    Branding and package verification scripts
├── gradle/                   Gradle wrapper files
├── gradlew / gradlew.bat      Build launchers
├── build.gradle              Fabric Loom build
├── gradle.properties         Version and dependency settings
├── settings.gradle           Project and plugin configuration
└── Argus-2.6.0-mc26.2.jar  Installable client
```

## Build

From the repository root:

```sh
cd 26.2
./gradlew build
```

On Windows use `gradlew.bat build`. The client JAR is generated at
`26.2/build/libs/Argus-2.6.0-mc26.2.jar`.

See the [26.2 README](26.2/README.md) for controls, verification commands,
and details of the source recovery. The core and GUI compile together from Java source.
In-game appearance and controls have been exercised in a development client; the final Prism instance test is the user sign-off.
