# Voris Hub Current Status

Last updated: 2026-08-02

## Current State

- Version: `1.10.145`
- Branch: `main`
- Remote state after the acceptance commit: local branch is 1 commit ahead of `origin/main`
- Push policy: do not push until the user explicitly changes the instruction
- Working tree after the acceptance commit: existing child audit reports remain untracked under `reports/`
- Latest accepted change: `fix: stabilize flight blink and ore discovery`
- Current Codex goal: `019fc144-e3cb-7371-8aea-519a8f355577`
- Recovered stalled session: `019fc0f7-46b1-7b02-869b-b1406acc1e02`
- Current round: complete after the local acceptance commit; no push was performed

## Last Verified Release

- Command: `.\gradlew.bat clean verifyRelease --rerun-tasks --no-daemon --console=plain --stacktrace`
- Result: successful
- Test classes: 17
- Tests: 194
- Failures/errors/skipped: 0/0/0
- Artifact: `build/libs/voris-hub-1.10.145.jar`
- SHA-256: `2A4F35A5D34EDD653BB72FA6E927542F4575E0C2D0F0D3E7792B797E6A03C04C`
- Release checks: Forge 1.12.2 metadata, required classes, and Java 8 bytecode passed

## Accepted Changes

- Flight now exposes only Static and Vanilla modes. Legacy `flight.mode=hypixel` values migrate to and are written back as `static`.
- Flight has an independent descent speed with default `0.35` and range `0.0`-`1.0`; Static uses it separately from horizontal/ascent speed.
- Vanilla Flight enables its capability during `InputUpdateEvent` and compensates the later vanilla Sneak subtraction before travel, giving current-tick controlled descent and surface convergence.
- Blink Strike retains expensive planning across ticks with global limits of 12 target plans, 16 candidates, and 96 collision samples per tick.
- Incomplete Blink searches remain pending instead of being cached as unreachable; feet-cell and Flight context changes still invalidate planning evidence.
- AutoMiner's shared ore cache uses the higher discovery budget until an enabled ore marker exists inside `pathRange`, and restores that warm-up after the final in-range marker is removed.
- Unfinished ore scan tasks advance by scan wave so unstarted chunks are not starved behind a repeatedly resumed nearby chunk.
- Ore outlines have a shared RGB brightness multiplier with default `1.0` and range `0.0`-`1.0`.

## Audited Boundaries

- Forge 1.12.2 bytecode confirms `InputUpdateEvent` runs before vanilla flight's `motionY -= flySpeed * 3` and before parent travel, validating the Vanilla descent compensation.
- AutoMiner's three-tick final completion pause remains an intentional rollback-confirmation window; no separate permanent quota, label-order, route-ownership, or completion defect was proven.
- Blink collision samples are intentionally sliced across ticks. The destination is revalidated before a plan completes; world changes in an already checked route segment remain a residual integration risk, and restoring synchronous whole-route validation would reintroduce the reported client-thread stall.

## Acceptance Rule

Future source changes should include a focused regression, run the affected test classes, update version and handoff documents, pass full `clean verifyRelease`, and create a local commit. Keep `reports/` untracked unless the user explicitly requests otherwise.
