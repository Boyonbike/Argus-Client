# Fast Use: Meteor Speed and Comet Container Safety

**Date:** 2026-11-10
**Status:** Proposed
**Scope:** `1.21.11/` only

## Intent

Argus already speeds up right-click use inside `FastPlaceModule`, but it is
conservative by default (`Delay = 2`) and it has no protection against opening
a container the player just placed. Meteor Client's `FastUse` is the simple
reference: a 0-tick, all-items cooldown applied per hand. Comet's
`SafeFastUse` keeps that speed and adds one safety behaviour: after placing a
container during a held Use, repeated Use must not open it until the key is
released.

This work adds those two behaviours to Argus as a dedicated Player module.

## Goals

- Add a Player module named `FastUse`.
- Reproduce Meteor's per-hand cooldown assignment at
  `Minecraft.startUseItem`, including `All` / `Some` selection, an arbitrary
  item list, and the optional all-blocks toggle.
- Default to `All` and `Cooldown = 0`, matching Meteor's speed out of the box.
- Add Comet's just-placed container guard, default on, covering chest-like
  blocks and any block that exposes a menu provider (shulker boxes, barrels,
  furnaces, hoppers, and so on).
- Preserve sneak-placement against a just-placed container when the player is
  sneaking and holding an item.
- Leave `FastPlaceModule` behaviour and defaults unchanged.
- Unit-test the selection rules, the guard state machine, and the packaged
  mixin/module registration.

## Non-goals

- No packet-rate or timer spoofing (LiquidBounce-style bypasses).
- No changes to `FastPlaceModule`'s `AutoPlace`, `RandomBlock`, or ender-chest
  redirect/auto-replace behaviour.
- No new GUI controls; the module uses the existing setting components.
- No port to the 26.x trees; this design targets `1.21.11`.
- No change to sign editing; the module follows Meteor and does not special-case
  signs.

## Decisions

1. **New module, not a FastPlace change.** `FastPlaceModule` is a block
   placement toolbox with automation that is unrelated to item-use speed.
   Changing its defaults would alter existing configs and would make the guard
   inseparable from that automation. A new module mirrors Meteor/Comet, can be
   toggled independently, and leaves existing FastPlace users untouched.

2. **Port Meteor's hook exactly.** Inject into `Minecraft.startUseItem` at the
   `ItemStack.isItemEnabled` call and set the shadowed `rightClickDelay` from
   the per-hand `ItemStack`. This is the same injection point Meteor uses and
   gives per-hand accuracy (main hand and offhand). The injection declares
   `require = 1` because `argusclient.mixins.json` sets `defaultRequire: 0`; a
   future mapping change must fail loudly instead of silently disabling the
   module.

3. **Port Comet's guard semantics.** A small pure `ContainerUseGuard` remembers
   container positions placed during the current hold. `MultiPlayerGameModeUseItemOnEvent`
   is cancelled when the clicked position is a remembered container, unless the
   player is sneaking with an item in either hand (vanilla's place-against path).
   The guard state clears whenever Use is not held, on module disable, and on
   disconnect.

4. **Detect placement through `BlockStateChangeEvent`.** Argus has no
   Meteor-style `PlaceBlockEvent`. `BlockStateChangeEvent` fires at the tail of
   `LevelChunk.setBlockState` for client-side changes, including local
   predictions. Record a position only when Use is held, the new state is a
   container, and the position is within 8 blocks of the player. The distance
   filter keeps remote or distant container changes from poisoning the guard.

5. **Only selected items override the cooldown.** `FastUse` writes
   `rightClickDelay` only when `appliesTo(stack)` is true. Unselected items keep
   whatever vanilla or another module (notably `FastPlace`) left in place. This
   avoids FastUse slowing down a FastPlace setup when its `Some` list does not
   include the held item.

6. **Release handling is tick-based.** `LocalPlayerPreTickEvent` clears the
   guard when `keyUse` is not down. This covers the normal release-and-reclick
   flow; the only uncovered case is a release and re-press inside one tick,
   which is not physically reachable by normal clicking.

## Behaviour

### Settings

| Config name | Type | Default | Visible when | Meaning |
|---|---|---|---|---|
| `Mode` | enum `All` / `Some` | `All` | always | Apply to every item, or only the `Items` list plus optional blocks. |
| `Items` | item registry set | empty | `Mode = Some` | Arbitrary items that receive the fast cooldown. |
| `Blocks` | boolean | `false` | `Mode = Some` | Also speed up all block items. |
| `Cooldown` | integer 0–4 | `0` | always | Ticks between uses; vanilla is 4. |
| `PreventContainerOpen` | boolean | `true` | always | Guard just-placed containers until Use is released. |

Module name: `FastUse`. Category: `PLAYER`. Default description:
"Uses items at high speed without opening containers you just placed."

### Runtime flow

1. Player holds Use. Vanilla calls `Minecraft.startUseItem`.
2. Vanilla sets `rightClickDelay = 4`; the FastUse mixin runs at the
   `isItemEnabled` call for each hand.
3. If the module is active and `appliesTo(heldItem)`, the mixin writes the
   configured cooldown. Otherwise it leaves the field alone.
4. A placement changes a client chunk with Use held. `FastUseModule` records
   the new container position in `ContainerUseGuard`.
5. On the next `useItemOn` against that position, the module cancels the
   interaction, so the placed shulker/chest does not open.
6. The player releases Use. The next pre-tick clears the guard; the next click
   opens the container normally.

### Container classification

A state is a container when either:

- its block is an `AbstractChestBlock<?>` (covers chests, trapped chests, and
  ender chests, which open without exposing a menu provider), or
- `state.getMenuProvider(level, pos) != null` (covers shulker boxes, barrels,
  furnaces, hoppers, and other menu blocks).

## Risks

- **Mixin target drift.** `startUseItem` and the `isItemEnabled` call are
  mapping-sensitive. `require = 1` turns drift into a loud mixin failure.
- **Menu-provider classification** is evaluated on the client level; a provider
  that requires a live block entity could differ from the server. Comet uses
  the same test on the same version, so this is accepted.
- **Remote placements.** The 8-block filter reduces false positives from other
  players' containers. It does not eliminate them; a container placed within
  eight blocks while the player holds Use is also protected until release.
- **Offhand.** The mixin runs per hand, so offhand use follows the same
  selection rules as the main hand.

## Success criteria

- With defaults, holding Use places items at every-tick speed, matching Meteor.
- Placing a shulker or ender chest while holding Use does not open it; releasing
  and clicking opens it.
- Sneak-right-clicking a just-placed container still places against it.
- `FastPlaceModule` still behaves exactly as before.
- `./gradlew test verifyReleaseConfig` passes from `1.21.11/`.
