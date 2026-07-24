# Iteration Record

1. Bootstrapped a pinned Forge 1.12.2 and Java 8 project with persistent module states, a client command, unit tests, and an obfuscated build.
2. Ported delayed chat automation, Creative-mode item and custom-potion commands, and reachable ore mining. Added chat parser tests.
3. Replaced unavailable mace mechanics with visible-target sword/axe melee and guarded critical packets. Added rotation and priority tests.
4. Added configuration reload, persisted key toggles, world-unload cleanup, pickaxe selection, and module permission checks.
5. Prevented delayed-message replacement, required visible mining targets, bounded legacy potion levels, added final artifact verification, and made Forge 1.12 development-client startup reproducible.

## Version 1.1 Combat Update

- Refined `meleeAura` range to use the nearest point of each target hitbox and restored a conservative three-block default.
- Added mutually exclusive `blinkStrike`, a collision-checked packet excursion that attacks near a visible target and retraces its path.
- Added in-game range commands plus path, diagonal-step, hitbox-distance, and release-contents verification.
