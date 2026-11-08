# Random Stuff Client GUI implementation plan

> **For agentic workers:** Execute inline using superpowers:executing-plans, then request one independent whole-change review.

**Goal:** Replace the basic menu with Meteor-style floating category windows and an independently implemented Catppuccin-inspired visual system and global search.

**Architecture:** Keep the compiled core and its module/settings APIs. Source-built screens share a drawing/theme layer and persistent GUI preferences. A narrowly scoped UIModule mixin routes the existing GUI bind to the new menu without binary patches.

**Tech stack:** Java 21, Fabric/Minecraft 1.21.11, Mojang mappings, existing Loom Gradle build, JUnit 5.

**Spec:** User requests Meteor-like GUI with styling/search from X-C-0/catppuccin-addon. User explicitly wants Light/Dark plus accent colour controls (no flavour names), Dark default, and a clickable Search tab that focuses input without requiring a shortcut.

## Constraints
- All runtime packages stay under dev.randomstuff; mod/resource ID randomstuffclient.
- No Meteor runtime dependency and no source copying from the reference add-on.
- Preserve existing module toggles, friends, Classic fallback, HUD editor, settings and migration.
- Search spans module names, aliases and setting names, ranked with fuzzy matching. Left-click toggles modules; right-click opens settings. Setting results open their owning module and focus that setting.
- GUI preferences and windows persist; small scaled screens must keep all categories reachable.
- Built-in GUI bind and /randomstuff open the new menu. Search tab works with the mouse and autofocuses.

## Review focus
- UI module lifecycle must not close the new screen on the next player tick.
- Search setting results cannot accidentally toggle a different module.
- Hidden/conditional settings and invalid values must not corrupt configuration.
- Window clipping, overlapping windows and viewport resize must agree with hit-testing.
- A corrupt preferences file must safely fall back to defaults.

## Tasks
- [x] Add failing search and preferences tests, implement pure search ranking and persisted Light/Dark/accent/window preferences; prove tests green.
- [x] Add shared rounded drawing, palette, bundled readable font, themed controls and top navigation.
- [x] Implement draggable, collapsible, individually scrollable category/favourites windows; a clickable global Search screen; right-click module settings editor; Appearance screen and styled Friends.
- [x] Add source mixin and runtime resources to route the existing GUI bind, preserving Classic access and module lifecycle.
- [x] Run clean build/full tests/package and bytecode checks, review the complete changes, update docs and version, build the deliverable and push.

## Execution decisions
- Kept a source-built GUI mixin rather than patching compiled UI lifecycle methods.
- Added independent expansion controls for both groups and visibility-dependency settings after review.
- Collection settings use entry selection, never serialized-set text editing.
- Search is a mouse-accessible tab; keyboard shortcuts are optional.
