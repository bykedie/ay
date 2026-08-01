# Voris Hub Parallel Audit Plan

All child threads share the repository but have independent chat context. Each child owns exactly one report file and must not edit source.

| Thread | Area | Report |
| --- | --- | --- |
| 01 | A* search, goal generation, and success/failure validation | `reports/thread-01-path-search.md` |
| 02 | Route movement, collision, vertical transitions, and stall recovery | `reports/thread-02-route-runtime.md` |
| 03 | Mining destruction controller, START/END tick ownership, and tool changes | `reports/thread-03-destruction.md` |
| 04 | Corridor and exposure obstacle selection, ray samples, and clearing state | `reports/thread-04-clearing.md` |
| 05 | Target labels, vein ordering, completion handoff, and quota reservations | `reports/thread-05-target-handoff.md` |
| 06 | Scaffold assist reachability, placement, ascent, and failure recovery | `reports/thread-06-scaffold.md` |
| 07 | Candidate caches, ore visualizer integration, scan latency, and FPS risks | `reports/thread-07-performance.md` |
| 08 | Mining stands, target reach, exposure geometry, and overhead/underfoot cases | `reports/thread-08-reachability.md` |
| 09 | AutoMiner test coverage audit and missing high-value regressions | `reports/thread-09-tests.md` |
| 10 | End-to-end state-machine review and prioritized cross-component risks | `reports/thread-10-state-machine.md` |

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
