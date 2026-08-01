# Voris Hub Multi-Thread Rules

## Repository

- Repository: `C:\Users\Administrator\Documents\客户端\ay`
- Branch: `main`
- Runtime: Minecraft Forge 1.12.2, Java 8, MCP stable 39
- Main acceptance thread: `019f9f53-3e60-77c2-8cd8-200a735687ba`

## Shared Objective

Continuously improve AutoMiner from evidence until the user stops the work. Do not push.

## Child Thread Contract

1. Read this file, `STATUS.md`, and `PLAN.md` before inspecting code.
2. Work only on the assigned audit area. Do not broaden scope.
3. Do not edit production code, tests, build files, version files, or shared status files.
4. Write findings only to the report file assigned in the thread prompt under `reports/`.
5. Do not commit, amend, rebase, reset, clean, checkout, pull, or push.
6. Treat the shared worktree as live. Re-read relevant files before finalizing findings.
7. Findings must include exact methods/lines, a concrete trigger sequence, old behavior, expected behavior, and the smallest regression test.
8. If evidence is insufficient, explicitly report `NO_PROVEN_DEFECT`; do not invent a fix.
9. Do not create or fork more threads.
10. The main thread owns acceptance, implementation, version updates, validation, commits, and any future push.

## Safety And Compatibility

- Preserve the panel key `·` and default-unbound module keys.
- Do not touch `ClientControls.java`, `MeleeCombat.java`, `BlinkStrike.java`, or `FlightController.java`.
- Keep changes compatible with Java 8 and Forge 1.12.2.
- Do not implement server-check bypasses, forged-position attacks, or packet flight.
- Manual edits use `apply_patch`; validation uses the Gradle wrapper.

## Evidence Standard

A useful defect report must be reproducible from current source. Prefer a pure helper regression that fails before the fix. Passing tests alone do not prove the runtime behavior. Distinguish deliberate confirmation delays from permanent stalls.
