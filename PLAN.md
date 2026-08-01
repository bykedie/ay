# Voris Hub Flight, Blink, And AutoMiner Audit Plan

All child threads share the repository but have independent chat context. Each child owns exactly one report file and must not edit source.

| Thread | Area | Report |
| --- | --- | --- |
| 01 | Flight landing safety, fall-distance lifecycle, disable/world transitions | `reports/phase2-thread-01-flight-landing.md` |
| 02 | Flight descent speed, vertical controls, landing responsiveness | `reports/phase2-thread-02-flight-descent.md` |
| 03 | WWE 1.12.2 Flight reference comparison and compatible behavior gaps | `reports/phase2-thread-03-flight-reference.md` |
| 04 | Blink path execution, correction handling, and origin recovery | `reports/phase2-thread-04-blink-recovery.md` |
| 05 | Blink target selection, reachability, and Flight interaction | `reports/phase2-thread-05-blink-targeting.md` |
| 06 | Blink underwater, vertical, entity-hit timing, and attack completion | `reports/phase2-thread-06-blink-hit.md` |
| 07 | AutoMiner final failure-validation atomicity | `reports/phase2-thread-07-miner-validation.md` |
| 08 | AutoMiner replaceable collision and snow-layer route loops | `reports/phase2-thread-08-miner-collision.md` |
| 09 | AutoMiner clearing, scaffold, destruction ownership, and recovery | `reports/phase2-thread-09-miner-work.md` |
| 10 | AutoMiner end-to-end target handoff, caching, FPS, and missing regressions | `reports/phase2-thread-10-miner-system.md` |

## Report Template

Each report must contain:

- `STATUS: PROVEN_DEFECT` or `STATUS: NO_PROVEN_DEFECT`
- Current commit inspected
- Exact source references
- Concrete tick-by-tick or method-by-method trigger
- Why existing tests miss it
- Minimal failing regression
- Minimal production fix
- Risk and compatibility notes
- Commands the main thread can use to reproduce or verify

## Acceptance Queue

The main thread reviews reports every two minutes, ranks proven defects by user-visible impact and evidence strength, then accepts one isolated fix per version. No child report is merged automatically.
