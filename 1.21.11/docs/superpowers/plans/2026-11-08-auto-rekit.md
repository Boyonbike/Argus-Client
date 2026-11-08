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
- [ ] Add tests for count targets, compatible merging, incompatible larger-stack swaps, durability, wrong items and safe split replacement.
- [ ] Run tests and observe missing implementation failures.
- [ ] Implement planner and atomic pickup click plans; run planner tests.

### Task 2: Module, storage interaction and persistence
- [ ] Implement settings and preset capture/serialization using Minecraft's registry-aware ItemStack codec.
- [ ] Register before config loading; tick via existing Fabric client lifecycle and add Manual container button.
- [ ] Use InventoryUtils and InventoryStateManager. Coordinate with Chest Stealer while rekitting.
- [ ] Verify compilation and persistent-data behaviour.

### Task 3: GUI, build and installation
- [ ] Add preset management from the module settings page using UiScreen, UiButton and UiInput.
- [ ] Run full tests, build, release checks and a fresh code review; address important findings.
- [ ] Copy release into the identified Prism 1.21.11 instance, preserving a backup; verify hashes.

## Execution ledger
- Baseline: ./gradlew test --offline passed. Existing Gradle cache requires elevated filesystem access.
