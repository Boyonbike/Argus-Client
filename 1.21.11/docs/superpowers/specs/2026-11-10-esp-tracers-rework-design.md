# ESP and Tracers rework (with Search merged into ESP)

## Why

ESP and Tracers both miss target types players want, and ESP gets slow around big stashes. Today:

- **ESP** handles a fixed set of entity groups (hostiles, players, animals, pearls, XP, items) and only 10 hard-coded
  block-entity types. It redraws every container model every frame through the outline shader, so the cost grows
  with every chest loaded.
- **Tracers** handles players only.
- **Search** finds any block from the full list, with natural-structure filters, but it's a separate module with its
  own look.

## Goals

1. Both modules act on the same four targets: **Players**, **Mobs**, **Items** and **Blocks**.
   - **Players:** all players, with separate show/hide and colour for friends, enemies and everyone else.
   - **Mobs:** all mobs: hostile, passive and neutral, plus an optional entity-type list.
   - **Items:** dropped items with type filtering, following Trouser Streak's Advanced Item ESP: a valuable-items
     default list, enchant requirements, a chat alert with coordinates, and a near-to-far colour.
   - **Blocks:** any block from the full block list, as Search does today.
2. **Search merges into ESP.** ESP's Blocks target replaces Search, and the Search module is removed.
3. **Big stashes stay fast.** Block rendering uses Comet Fast Storage ESP's approach: 8×8×8 cells, a few
   reconciliations a second, rebuilds only for cells that changed (within a per-frame time budget), and geometry
   kept on the GPU.
4. **Same structure, separate settings.** Each module has its own target list and its own colours, and both lay
   out their target cards the same way.
5. **Pairing between the modules,** per target:
   - **Toggle in other:** switches the target on or off in the other module. Switching it on copies this module's
     current settings for that target once. After that, the two can be changed separately.
   - **Mirror in other:** links the target in both modules. A change on either side is copied to the other until
     **Break link** is pressed.

## Non-goals

- The visual style stays per-module and is never mirrored: ESP box/2D, outline width and glow; Tracers lines/markers
  and hitbox point.
- ESP's extras (mob owner, light levels, chorus teleports, pearls, XP bottles) stay as they are, in ESP's "Others"
  card.
- Other ESP-style modules (Hole ESP, Void ESP, Phase ESP, Waypoints) are untouched.

## Anti-goals (failure even if it works)

- Frame time still grows with the number of containers on screen while nothing is changing.
- The two module pages look or behave differently for the same target.
- A mirrored setting silently drifts out of sync, or a change ping-pongs between the two modules.
- Old configs lose a player's ESP storage choices or Search block list.

## Constraints

- Minecraft 1.21.11 (Fabric, Mojang mappings). The client already draws with raw GL in places
  (`CompiledShaderProgram`, `PostProcessShaderPass`), and the ESP outline is a post pass over `POST_TARGET`.
- Settings are flat lists. A setting marked `markAsGroup()` starts a card in the settings GUI, and `dependsOn`
  controls visibility. Module tools are `InlineCard`s registered in `ModuleTools`.
- `Setting.setValue` fires its change callback only when the value object changes identity. Set settings are
  replaced, not edited in place, but mirroring must not rely on callbacks alone.

## Design

### Shared target settings

A `TargetSettings` builder adds the same settings to whichever module it's called on, with identical names and
config keys. ESP and Tracers therefore have identical target cards, and pairing can match settings by config name.
Every target card starts with **Enabled** and ends with **Range** (the maximum shows as "Unlimited").

- **Players:** Enabled, Friends (Show/Hide), Enemies (Show/Hide), Others (Show/Hide), Ignore naked, Colour,
  Friend colour, Enemy colour (the friend and enemy colours start from the Colors module's friend and enemy colours),
  Range.
- **Mobs:** Enabled, Hostile + colour, Passive + colour, Neutral + colour, Filter (Any/Whitelist/Blacklist) with an
  entity-type list (this replaces Search's entity list), Ignore vehicles, Sound alert, Range.
- **Items:** Enabled, Filter (Any/Whitelist/Blacklist) with an item list. The list defaults to Trouser Streak's
  valuables, and the filter defaults to Whitelist. Then:
  - Enchants: Ignore / Any enchant / Specific. Specific uses separate lists for tools, swords, armour, mace and
    trident.
  - Colour, Far colour + Far distance, Chat alert + Coordinates, Range.
- **Blocks:** Enabled, Blocks (full block list; the defaults are chest, trapped chest, barrel, ender chest and every
  shulker box), Colour mode (Auto from the block colour registry and map colour, or Custom) + Custom colour. Then:
  - Opened containers: Mark opened + opened colour, Hide opened.
  - Ignore natural + structure toggles (dungeon, fortress, bastion, ancient city, trial chambers), Height
    (1.18/1.12), Only ominous vaults.
  - Sound alert, Range.

### Module-specific style

- **ESP General:** Box/2D, the outline/fill/tint/line-width settings, and the 2D Info sub-group (unchanged). Block
  style adds Outline width, Fill opacity, Glow and Fade distance. Item labels keep their Text/Box modes, Group and
  Scale.
- **Tracers General:** Lines/Marker, Hitbox point, Line width, Stem, Min/Max distance, Distance colour, Marker
  distance/shadow and Ignore visible (unchanged). Tracers draw to an entity's hitbox point and to a block's centre.

### Pairing card

Each page gets a **"Pair with Tracers"** (or "Pair with ESP") card at the top, built from `InlineCard.Items`. There
is one row per target, and the row's detail shows its state: "Separate", "Separate · on in Tracers" or
"Mirrored". Each row has two actions:

- **Toggle in Tracers**, highlighted while the target is on in the other module.
- **Mirror**, which becomes **Break link** while the target is mirrored.

Mirror state is saved per target as a hidden setting in both modules (for example `PlayersMirror`), so it survives
restarts. A `TargetLinks` service:

- copies settings by config name through their JSON form, so any setting type works;
- keeps the last-synced JSON for each pair, and syncs on every client tick and straight after any GUI change;
- copies whichever side differs from the last sync onto the other side. If both changed in the same tick, ESP wins
  (my call).

Mirroring includes Enabled. It never includes the module's own on/off switch.

### Block engine (shared)

A single `BlockTargetIndex` service scans for the union of both modules' block lists, so a block is scanned once
even when both modules use it. It reuses Search's `ChunkScanner`: async chunk scans, block-update packets and chunk
unloads. Matches go into 8×8×8 cells, each with a revision counter and a dirty queue (as in Comet's
`StorageEspIndex`). Block entities are reconciled per loaded chunk 5 times a second, which catches lid and shulker
animation poses, colour changes and opened state.

ESP renders blocks through retained per-cell GPU meshes:

- **Block entities** (chests, shulkers and so on) use their real models, baked once per cell rebuild through the
  existing `ColorOutlineBufferSource` path into a position+colour vertex buffer.
- **Other blocks** use their shape bounds as boxes.

Rebuilds stay within a per-frame budget (default 2 ms, at most 128 models per frame). Each frame, ESP tests the
visible cells against the view frustum and sorts them front to back. It then draws them with a small raw-GL program
into the outline pass's `POST_TARGET`, which has its own depth, so terrain never hides ESP, followed by the existing
outline composite. The nearby fade happens in the shader. Off-screen cells cost one frustum test. If the program
fails to compile, the Blocks target turns off with a chat error.

Tracers reads the same index and draws one line per in-range block centre, using the existing line batcher.

Opened containers (from ESP's `LootedStorageTracker` and Comet) are remembered for the current world and dimension,
up to 65,536, with the oldest forgotten first. That memory is shared by both modules.

### Search removal and migration

- `SearchModule` is deleted, along with its registration, its bindings and the Colors-scheme callback. The callback
  now asks the block index to recolour.
- ESP's `fromJson` maps the old storage toggles (Chests, Barrels, EChests, Shulkers, Beds, Signs, Dispensers,
  Hoppers, Furnaces, Pots) into the Blocks list. Old Hostiles/Players/Animals/Items settings map onto the new
  targets.
- If a saved Search config exists, its selected blocks, structure filters, height, ominous and sound settings are
  merged into ESP's Blocks target once.
- Tracers' old player settings map onto its Players target.

## Left to the builder

- Exact default colours, and the order of settings inside each card.
- Whether 2D mode draws block targets (my call: no, blocks always use the outline).
- How the outline composite shares `POST_TARGET` with the existing entity outline. One pass per frame is fine.
- Test coverage: the cell index, pairing/mirror sync (both directions, no ping-pong, break link) and the item filter
  rules are plain-Java units and must be tested. GPU code is verified in game.

## Calls made during the build

These differ from, or add to, the design above:

- **Cell meshes are drawn with Minecraft's own render-pass API.** They use the vanilla `position_color` shader
  through a custom pipeline (`BlockMeshCache.PIPELINE`) into `POST_TARGET`'s colour and depth, not a raw-GL
  program. There is no nearby fade.
- **Block entities beyond the game's block-entity draw distance are baked as boxes.** Their cell isn't rebuilt
  when you come closer, so a far chest can stay a box until something in its cell changes.
- **"Toggle in other" turns the other module on** if it was off; otherwise switching the card on would have no
  visible effect.
- **If only one side of a mirror is set** (for example after resetting one page), the link counts as broken
  rather than restored.
- **Item chat alerts are shared,** so an item is announced once even when ESP and Tracers both show items.
  Sound alerts are limited to 10 a second across all cards.
- **The Mobs whitelist shows exactly the listed entity types,** including non-mobs such as minecarts, whatever
  the category toggles say. Listed non-mobs use the passive colour.
- **Enchant lists** offer the server's enchantments when connected, and anything can be typed in.
- **Old ESP configs** that used "Any" items get Enchants set to Ignore, so previously shown items still show.
- **Migration:** if Search was on and ESP was off, ESP is switched on with only the cards Search covered.
- **Not done:** the Search block-colour scheme callback is gone. A Colors scheme change now rebuilds meshes
  through the look key on the next tick. Changes made with `.color` are only picked up when a cell rebuilds.

## Target groups (2026-11-11, replaces the four cards)

ESP and Tracers now show one **Targets** card at the top of the page. The Players/Mobs/Items/Blocks cards are hidden
(they stay in the config only so old choices can move across once).

- **Search row:** search field, a filter button that cycles All / Blocks / Players / Mobs / Entities (right-click goes
  back), then Add.
- **Multi-select:** matches show with a tick box, icon, name and kind. Clicking ticks or unticks; ticked things stay at
  the top while you search on. Enter or Add adds everything ticked as **one group**. With nothing ticked, the top match
  is added. A thing can be in only one group, and things already added show as "already added".
- **Categories:** a group's category comes from its things: Blocks, Players, Mobs (living entity types), Entities
  (other entity types and dropped items) or All (mixed). The category picks the settings:
  - All: Color, Range.
  - Players: Color, Ignore naked, Range.
  - Mobs: Color, Ignore vehicles, Sound, Range.
  - Entities: Color; for dropped items, Enchants (gear only) + Required enchants, Distance color + Far color + Far
    distance, Chat alert + Coords; for other entities, Ignore vehicles and Sound; Range.
  - Blocks: Color mode + Color; Mark opened, Opened color, Hide opened and Ignore natural + structures (containers
    only); Height; Only ominous (vaults only); Sound; Range.
- **Rows:** the category tag on the left (tinted with the group's colour), the first thing's icon, the names, and
  "On · range · linked/also on/this page only". Left click turns the group on or off. Right click drops its settings
  down under the row. On the right: a pill naming the other module (Toggle in other), a round link button
  (Mirror/Break link) and a round X (click twice to remove).
- **Players:** All players, Friends and Enemies are searchable groups ("player", "friend", "enemy"). A named
  player's group wins over Friends/Enemies, which win over All players. Online players come from the tab list;
  `player:Name` adds anyone.
- **Pairing:** groups pair across modules by id. Toggle in other copies the group across once (things already in
  another group there move to it). Link keeps both copies identical, ESP winning ties, and a link with one side
  missing is broken.
- **Migration:** once per module, an enabled Players card becomes an All players group; an enabled Blocks card
  becomes a group of its blocks; whitelisted Items and Mobs lists become groups. Colours and ranges carry over. Mobs
  chosen only by hostile/neutral/passive have no single thing to carry over. The cards are then switched off.
- **Drawing:** unchanged from before. Blocks in groups are scanned by the block index and drawn in ESP's retained
  meshes (one cache per range in use). Tracers draws them in its per-tick block pass.

### ESP's labels and extras moved into the groups (2026-11-11)

- **Pearls and XP bottles** are ordinary entity types now (search "ender pearl", "bottle o' enchanting"); their
  special toggles and fill/outline colours are gone. Tracers gets them too.
- **Item labels** are per group, on ESP's page only: Item display (Box / Label / Both), Stack labels, Label text,
  Label background and Label scale. They appear in Entities groups that hold dropped items.
- **Show owner** (owner names above tamed mobs and thrown pearls) is a Mobs/Entities group setting, ESP only.
- **Extras:** Light levels and Chorus teleports are searchable (filter "Extras"; words like "light", "spawn",
  "chorus"). Their group's colour colours the marks/labels, and its range limits them (light levels scan at most 12
  blocks). Tracers draws lines to chorus teleport spots; light levels are ESP only, and its row says so on Tracers' page.
- **Migration:** once, after the card migration, ESP's old Pearls, Exp, Light and Chorus toggles become groups with
  their colours, and the old label mode, stacking, colours, scale and mob owner options are copied onto every
  Mobs/Entities group. The Item labels and Others sections are then hidden.

### Generic search terms and very common blocks (2026-11-11)

- **Generic terms:** words like "storage", "shulker", "ores", "beds", "spawner", "weapons", "armour", "tools",
  "valuables", "hostile", "passive" and "vehicles" show a tick-all row for the whole set above the matches. Enter with
  nothing ticked adds the first set.
- **Very common blocks (netherrack, stone):** the block index still holds at most 300,000 positions, but it now
  scans chunks nearest first. When full, a nearer chunk pushes out the farthest stored ones, and left-out chunks are
  rescanned once a second when they become nearer. Rows of block groups say "too many to show all, nearest shown"
  while that happens.
- **Meshes:** faces shared by two drawn full blocks of the same colour are left out, so solid areas cost little.
  Removing a block rebuilds neighbouring cells across the border. Only real models (chests, shulkers…) count
  against the 128-models-per-frame limit; plain boxes are bounded by the time budget only.
