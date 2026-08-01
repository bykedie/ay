# Voris Hub Current Status

Last updated: 2026-08-01

## Current State

- Version: `1.10.134`
- Branch: `main`
- Remote state after the acceptance commit: local branch is 91 commits ahead of `origin/main`
- Push policy: do not push until the user explicitly changes the instruction
- Working tree: child audit reports remain untracked under `reports/`
- Latest accepted change: `fix: pause auto miner during item use`
- Coordination commit: `407fcc3 docs: coordinate auto miner audit threads`
- Previous fix: `08f062c fix: skip unusable auto miner corridor samples`
- Active objective: iterate Flight landing safety and descent, Blink Strike stability, and AutoMiner reliability under the main-thread acceptance workflow.
- Heartbeat automation `voris-hub` remains paused by user request.

## Last Verified Release

- Command: `.\gradlew.bat clean verifyRelease --rerun-tasks --no-daemon --console=plain --stacktrace`
- Result: successful
- Test classes: 17
- Tests: 178
- Failures/errors/skipped: 0/0/0
- Artifact: `build/libs/voris-hub-1.10.134.jar`
- SHA-256: `6FBC7F3687CC11A6EB737E6D2843863E1B546EA5DBCE11B67917782F76F6ADCC`

## Recently Fixed

- Horizontal descent now validates swept head clearance consistently in A*, path validation, and corridor generation.
- Path-search failure cooldowns are bound to the feet-cell origin and released after the player moves to a new cell.
- Corridor ray samples now skip allowed-but-unusable early hits and continue to a usable sample.
- Restarted A* searches now validate exhausted failure snapshots instead of treating them as immediately unreachable.
- A second stale validation result is deferred instead of adding a reachable ore to the 100-tick failed-route cooldown.
- Active item use now pauses both START- and END-phase automatic mining work without discarding the locked target.
- Mining, clearing, and scaffold deadlines are frozen while the player eats, draws a bow, blocks, or uses another item.

## Known Non-Defects Already Audited

- The three-tick mining completion pause is an intentional rollback-confirmation window.
- `pathIndex == 1` corridor lookback retains one completed node as safety context and has no proven missing-transition defect.
- `routeCorridorCache` keys include path identity, index, and route start; no stale-cache defect has been proven.

## Main Thread Acceptance Rule

Child reports are hypotheses until the main thread reproduces them with current code, adds a failing regression, applies a minimal fix, runs `AutoMinerTest`, updates version/docs, runs full `clean verifyRelease`, and creates one local commit.

## Active Parallel Audit Threads

- Main acceptance thread: `019f9f53-3e60-77c2-8cd8-200a735687ba` (the only pinned Voris thread)
- Review heartbeat: automation `voris-hub`, every two minutes, attached to the main thread
- Thread 01: `019fbc95-4783-7cd3-b136-ac1ec3026422`
- Thread 02: `019fbc95-8fad-7ff0-a158-3ada819ea97f`
- Thread 03: `019fbc95-9858-7d73-b1a6-6fff02cfa405`
- Thread 04: `019fbc95-a250-74f1-8e22-e53c19e78c52`
- Thread 05: `019fbc95-aa57-7ef0-90ca-35b799b16f7a`
- Thread 06: `019fbc95-c30d-7e31-8ea4-22bf6a4ae205`
- Thread 07: `019fbc95-d4af-7480-8b82-8609c4afd026`
- Thread 08: `019fbc95-baae-7f80-9263-65604b42a88b`
- Thread 09: `019fbc95-ccfb-7491-aa8a-b0bff2b169fa`
- Thread 10: `019fbc95-b2b9-7271-b21e-7c86ed2900ba`
