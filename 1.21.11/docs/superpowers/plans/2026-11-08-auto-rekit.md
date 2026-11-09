# Auto Rekit Implementation Plan

> **For agentic workers:** Execute inline with superpowers:executing-plans. The user approved implementation, build and instance installation.

**Goal:** An independent Player module that restores a saved hotbar/main inventory from storage, automatically or by button.

**Architecture:** Register through the existing module registry before configuration loading. Use existing settings, configuration serialization, inventory click utilities and click limits. Separate a deterministic transfer planner from Minecraft interaction; add a preset page using the existing GUI widgets.

**Tech Stack:** Java 21, Minecraft 1.21.11, Fabric, existing Argus module API, JUnit 5.

**Spec:** User requirements in this chat: named saved inventory presets and active preset; Auto/Manual; saved counts/full stacks; better durability exact matches; ignore names for matching but obey Minecraft stacking rules; optionally replace incompatible stacks with larger ones; follow Chest Stealer's slot swap behaviour.

## Global Constraints
- Preserve all pre-existing working tree changes. Git metadata is read-only, so worktree creation failed and implementation stays in this checkout.
- No additional runtime dependencies. No dropping items. Leave unsaved slots untouched.
- Match all components except names and damage; durability upgrades must still match the current gear's other components.
- Presets cover hotbar and main inventory, matching Chest Stealer's accessible inventory slots. Equipped armour/offhand are not present in storage menus.
- Exact count means a refill target; already excessive existing stacks are not trimmed.

## Review Focus
- Different names match presets but cannot merge: replacement must strictly increase the useful count.
- Partial replacement must preserve displaced stacks and source remainders; skip if no safe spare slot exists.
- Never transfer with an occupied cursor or exceed the client's click limit.
- Closing/changing containers or disabling Manual cancels its request.
- Configuration reload preserves presets and selection; simultaneous Chest Stealer must not fight an active rekit.

### Task 1: Planner and transfer transactions
- [x] Add tests for count targets, compatible merging, incompatible larger-stack swaps, durability, wrong items and safe split replacement.
- [x] Run tests and observe missing implementation failures.
- [x] Implement planner and atomic pickup click plans; run planner tests.

### Task 2: Module, storage interaction and persistence
- [x] Implement settings and preset capture/serialization using Minecraft's registry-aware ItemStack codec.
- [x] Register before config loading; tick via existing Fabric client lifecycle and add Manual container button.
- [x] Use InventoryUtils and InventoryStateManager. Coordinate with Chest Stealer while rekitting.
- [x] Verify compilation and persistent-data behaviour.

### Task 3: GUI, build and installation
- [x] Add preset management from the module settings page using UiScreen, UiButton and UiInput.
- [x] Run full tests, build, release checks and a fresh code review; address important findings.
- [x] Copy release into the identified Prism 1.21.11 instance, preserving a backup; verify hashes.

## Execution ledger
- Baseline: ./gradlew test --offline passed. Existing Gradle cache requires elevated filesystem access.

- Planner/transfer/persistence tests were observed failing before implementation and then passed.
- Minecraft matching tests exposed reference equality in filtered component maps; keys now use immutable value maps. Names and damage match correctly.
- Core enum codec unit tests need a minimal Fabric game-provider fixture because it reads launch flags. Module JSON roundtrip now passes with that environment supplied.
- Review: fresh read-only reviewer verified registration timing and mixin targets. Fixed infeasible source selection with a RED→GREEN regression; Manual ownership persists for the container; small-screen button placement uses actual container bounds and a side fallback.
- Ruling: existing excess counts are left untouched because this is a refill module; equipped armour/offhand follow the existing ChestStealer scope.
- Ruling: AutoRekit takes priority over ChestStealer whenever enabled with an active preset, so completed Manual runs are not immediately undone.

- Complete: ./gradlew build --offline passed; 64 tests, zero failures/errors/skips. verifyReleaseConfig, verify_merge.py, verify_branding.py and git diff --check passed.
- Installed into Prism instance Keo Optimized Keo Optimized 1.21.11; previous JAR backed up under argus-backups/2026-11-08-auto-rekit/. Release, distribution and installed SHA-256 all equal 8d6eed12053190a3489ea5cae59b6bdc2e600f2762d7f552ee57b889affcfb45.
- No in-game client/server validation performed. No branch integration requested; current checkout changes retained.

## Equipment correction (user-approved continuation)

- User reported worn armour missing and requested offhand. This supersedes the original 36-slot scope: capture/decode slots 0–40, preserving old presets until explicitly updated.
- Use the vanilla equip action through a verified temporary hotbar swap for worn armour, respecting binding, cooldowns, slot compatibility and normal click limits. Restore the original selected slot and return displaced gear to storage. Only single-piece armour sources are eligible, avoiding vanilla's stacked-item drop fallback.
- Offhand uses vanilla SWAP button 40 around the existing counted transfer, restoring the staging hotbar slot and preserving displaced items/source remainders. Staging is checked before further actions.
- Regression tests cover snapshot capture/reload and actual Minecraft equipment-index mapping, old preset compatibility, armour success/rejection/cancelled staging, offhand exact counts and displaced-stack preservation.

- Equipment review found NoSlow Grim Strict queues item-use but sends hotbar selection immediately. Fixed by disallowing alternate selected-slot staging while that mode is active; a regression verifies the selection remains fixed. No other important review findings.

- Equipment correction complete: build/release checks, verify_merge.py, verify_branding.py and git diff --check passed; 73 tests, zero failures/errors/skips. Installed into the same Prism 1.21.11 instance with a separate backup under argus-backups/2026-11-08-equipment-rekit/. Installed/release SHA-256 b8be4da68a40b637113d0960a8a2a5e7e8193fc853e3817893791a405314b010. Existing presets need Update to record newly supported slots. No live in-game verification performed.
