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

## Version 1.2 Control Update

- Added a localized in-game module control screen, opened with Right Shift by default.
- Removed the default R, B, C, and M module bindings while keeping each action available for manual binding.
- Added Chinese module names and release verification for the packaged language resource and GUI class.
- Reworked the module screen into an in-world, multi-column ClickGUI with Chinese categories and live states.
- Embedded visible Chinese labels in code so legacy clients cannot expose untranslated resource keys.
- Added right-click module drawers with persistent sliders, toggles, and target-priority controls.
- Split melee and blink targeting, added optional camera rotation, multi-target attacks, target boxes, per-module key binding, and selectable mod entity types.

## Version 1.5 Messaging And Visualization Update

- Added five editable random message slots to automatic GG and reply automation.
- Added peaceful-target filters and raised both combat multi-target limits to 50.
- Added an independent target visualizer with skeleton, box and ray switches plus visible/obstructed colors.
