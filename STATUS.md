# Voris Hub Current Status

Last updated: 2026-08-02

## Current State

- Version: `1.10.147`
- Branch: `main`
- Remote state after the acceptance commit: local branch is 3 commits ahead of `origin/main`
- Push policy: do not push until the user explicitly changes the instruction
- Working tree after the acceptance commit: existing audit reports remain untracked under `reports/`
- Latest accepted change: `fix: stabilize flight landing and blink strikes`
- Current Codex goal: `019fc144-e3cb-7371-8aea-519a8f355577`
- Current round: complete after the local acceptance commit; no push was performed

## Last Verified Release

- Command: `.\gradlew.bat clean verifyRelease --rerun-tasks --no-daemon --console=plain --stacktrace`
- Result: successful
- Test classes: 17
- Tests: 201
- Failures/errors/skipped: 0/0/0
- Artifact: `build/libs/voris-hub-1.10.147.jar`
- SHA-256: `90A2EA4A1E594CA5156A0F134C929D15168C1D5DBCEF332FEB4103D239DDC3B8`
- Release checks: Forge 1.12.2 metadata, required classes, and Java 8 bytecode passed

## Accepted Changes

- Vanilla Flight controlled descent and disable landing now send a post-movement `onGround=true` then `false` state pair, resetting fall distance accepted by a stock Forge 1.12.2 server without leaving the player grounded.
- Static Flight movement and packet behavior remain unchanged.
- Vanilla Flight Blink transport carries the controlled ground state through the remote excursion and restores airborne state after returning.
- Blink pending collision work now survives feet-cell movement while Flight-state changes still discard incompatible evidence.
- Absolute path evidence stays tied to its original position; when the player moves within the configured packet step, the path is rebuilt from the current origin and collision-checked again inside the existing 96-sample budget.
- Rebuilt destinations are rechecked against the current configured range and current target geometry before execution.
- Blink movement preflight now uses the tick-start movement origin instead of assuming the normal client movement packet had zero displacement.
- Fully checked direct routes return directly to the current origin, avoiding the stock post-five-packet threshold that rejected otherwise valid ground and airborne attacks. Dogleg routes retain their reverse path.
- A completed collision cursor can now advance to destination/current-origin validation on the next tick instead of remaining permanently pending.
- The global Blink planning limits remain 12 target plans, 16 candidates, and 96 collision samples per tick.

## Audited Boundaries

- Forge 1.12.2 `NetHandlerPlayServer.processPlayer` passes each movement packet's vertical delta and `onGround` flag to `EntityPlayerMP.handleFalling`; `Entity.updateFallState` accumulates negative deltas and resets/settles the value on a grounded packet.
- The Vanilla Flight reset prevents new controlled descent from accumulating fatal fall distance. It cannot erase a dangerous server-side fall distance that already existed before Flight took control without settling that existing fall.
- Blink collision sampling remains intentionally sliced across ticks. A moved origin receives a bounded current-path recheck, but a world change in an already checked route can still invalidate one attempt after planning.
- Extended-range attacks remain subject to modified server movement rules and anti-cheat plugins; the implementation mirrors stock Forge 1.12.2 acceptance rather than promising a universal bypass.

## Acceptance Rule

Future source changes should include a focused regression, run the affected test classes, update version and handoff documents, pass full `clean verifyRelease`, and create a local commit. Keep `reports/` untracked unless the user explicitly requests otherwise.
