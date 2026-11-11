# SpeedMine upgrade: merge Comet's strengths into Argus

Status: plan only, no code changed. Written from a settings/structure comparison, not from a line-by-line packet trace.

## Goal
Make `SpeedMineModule` (Argus, 1.21.11) the fastest and most reliable packet-miner of the two, by taking Comet's rebreak confirmation model, TPS start budget, air-mining claim rules and tested policy classes, while keeping Argus's swap options, render options and the ~33 modules that depend on it.

## Non-goals
- Do not copy Comet's auto-targeting into SpeedMine. Argus already has `AutoMineModule`, `AntiFeetPlaceModule`, `BlockerModule`, `PhaseModule`; duplicating them splits ownership. Instead, check what Comet's targeting (`SpeedmineTargeting`: phase-first then surround, never-mine-own, enemy range) does that `AutoMineModule` lacks, and port only those gaps there.
- Do not rename or remove public API other modules call (`requestBreak`, `isMining`, `getMiningPos`, `rangeSetting`, `grimSetting`, etc.). 33 files reference the module.
- No Meteor dependencies: Comet's code is Meteor-based; port logic, not classes.

## Constraints
- **License:** Comet's files carry a GPL-3.0 / "keep repo public" header (THM Addons). Confirm you own or may reuse this before copying. Safest route is to reimplement the *ideas* (confirmation modes, start-credit budget) rather than paste code.
- Close the game before installing the jar (see memory: install only when game closed).
- Argus targets Minecraft 1.21.11; Comet targets 26.1.2. Mappings and packet APIs differ (sequence numbers, `ServerboundPlayerActionPacket`), so every ported call needs rechecking.

## Phases

### Phase 0: Baseline (before touching code)
Measure current Argus: ticks per rebreak on obsidian with Instant, delay 0, TPS sync off, and again with Fast. Same test on Comet Strong. Record numbers in this doc so later phases can prove they helped. Without this, "faster" is a guess.

### Phase 1: Extract testable core (no behaviour change)
Move pure logic out of the 1446-line module into small classes under `modules/player/speedmine/`, mirroring Comet's `core/`:
- `SpeedMineTiming` (server-tick conversion, start credit, `canStart`)
- `SpeedMinePolicy` (pause rules, bedrock-while-eating, air claim, slot restore)
- `SpeedMineRange` already exists; keep.
Add unit tests for each. Module delegates to them. Gate: build + existing behaviour identical.

### Phase 2: TPS start budget
Replace the plain `TPSSync` check with Comet's credit model: credit accrues at server TPS, capped at 2, a start costs 1, no wait at 20 TPS or with sync off. Fixes client-tick jitter dropping progress. Small, low risk, good first real win.

### Phase 3: Rebreak confirmation model (biggest speed/safety lever)
Add a rebreak mode that coexists with Fast/Instant rather than replacing them:
- **Strict**: wait for server confirmation between rebreaks.
- **Strong**: skip confirmation waits and TPS start delay (fastest).
- **Bypass**: Strict until a rebreak is confirmed, then Strong.
Plus a trigger setting: on server update packet vs. each client tick. Implementation hook: a `setServerVerifiedBlockState` mixin on `ClientLevel` (Comet's `SpeedmineBlockUpdateMixin`) so confirmation is seen the instant the server replaces/airs the block, including the case where break and replacement land in one server tick and no air update arrives ("primed" logic).
Decide in this phase whether `SpeedMineRebreakChoice` (None/Fast/Instant) maps onto the new modes or stays as a second axis. Recommended: keep None/Fast/Instant as the *what*, add Strict/Strong/Bypass as the *how confident*, default Bypass on anticheat-heavy servers.

**Phase 3 as built (2026-11-11):** `Confirm` setting (Strict/Strong/Bypass, default Bypass) under Rebreak = Instant; logic in `SpeedMineConfirmation`, air seen via `ClientLevelMixin` on `setServerVerifiedBlockState`. Argus Instant was already effectively Strong. Gating only begins after the server confirms the first break, because Argus's repeated STOPs before that are first-break retries. Fast rebreak is untouched.
**Deferred (Phase 3b):** Comet's `onPacket` trigger (fire the rebreak inside the block-update hook instead of on the next mining tick). Argus's swap/STOP path lives inside the mining tick, so this needs that path extracted first; it saves up to one tick per rebreak.

### Phase 4: Swap/tool-hold policy
Add Comet's release policies as an option under Silent swap: Keep for next tick, Same tick, End of tick, Tool hold (hold through validation + 3 idle ticks). Keep Argus's `Alternative`/`SwapReset`/`1.21` options; verify each combination doesn't desync the server slot (a held tool is only restored if we still own the server slot).
Also port "validation tool slot" logic so a secondary break doesn't swap away from the primary's tool mid-validation.

### Phase 5: Air mining
Add Comet's air-claim rules: only while airborne, not creative/spectator, optional while gliding, speed cap, claim window; plus the `getDestroySpeed` mixin that cancels the air penalty (x5). Replace the current bare `Air` toggle.

### Phase 6: Extras worth having
- Multitask (don't pause mining while eating/using items) as an explicit setting; Argus has `MultiTaskModule`, so wire to it instead of duplicating.
- Client prediction toggle.
- Bedrock options **only if** `AutoMineModule` doesn't cover them.
- Targeting gaps ported into `AutoMineModule` (see Non-goals).

### Phase 7: Keep Argus's edge
Leave render settings, `ExtraBreak`/`SneakExtend`, `Grim`, `Rotate`+`Limit` as they are. Optionally add Comet's bedrock colour/shape.

### Phase 8: Verify
Re-run the Phase 0 benchmark; compare Instant+Strong against old Instant. Test on a Grim-style server in Bypass mode and on a vanilla server in Strong. Regression-check dependants: AutoMine, AntiFeetPlace, Blocker, Phase, MultiTask, Nuker, FastPlace.

## Order and risk
1 -> 2 -> 3 is the core; 3 carries the most risk and reward. 4 and 5 are independent of each other after 3. Do each phase as its own commit so a bad one can be reverted alone.

## Decisions (answered 2026-11-11)
1. **License:** resolved. The user built Comet, so its logic can be reused directly. The constraint above no longer applies; ported code can be adapted rather than reimplemented.
2. **Modes:** Strict/Strong/Bypass sit **beside** Fast/Instant (a second axis), not replacing them.
3. **Target server:** 6b6t. Default the confirmation mode to **Bypass** (Strict until a rebreak is confirmed, then Strong), since the fastest mode is only safe once confirmation has been seen. Phase 8 testing should happen on 6b6t itself, and the 6b6t mode in `RotationManager`/`AntiCheatModule` should be checked so the new modes respect it.
