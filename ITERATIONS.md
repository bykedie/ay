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

- Added a localized in-game module control screen, opened with the grave key (`·`) by default.
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

## Version 1.6 Voris Hub Update

- Renamed the user-facing project and panel to `Voris Hub` while keeping internal `qazrlegacy` compatibility keys.
- Added per-ore visualization controls, configurable colors, 150-block defaults and 500-block maximum visualization ranges.
- Added parameter help icons and right-click expandable module settings throughout the control panel.

## Version 1.7 Automation And Targeting Update

- Added `autoBridge` / `自动搭路`, which places a block below the next walking position and can temporarily swap blocks from inventory without changing the visible held slot for long.
- Upgraded `autoMine` with per-ore presets, configurable walking range, optional target count, nearest reachable ore selection and simple loaded-world path following.
- Added combat attack body-point choices for `meleeAura` and `blinkStrike`.
- Optimized `oreVisualizer` to merge adjacent ore blocks into outer wireframes and skip internal cluster lines such as the center lines in a 3x3 ore group.
- Added tests for auto-bridge movement, ore-boundary rendering, attack-point persistence and auto-mining preset configuration.

## Version 1.8 Flight And HUD Update

- Added `flight` / `飞行` to the Combat panel with mutually exclusive elytra-packet and boat-packet modes plus configurable horizontal and vertical speed.
- Hardened `blinkStrike` for Survival use by keeping packet excursions on the origin Y level, restoring the original grounded flag, resetting fall accumulation and briefly suspending flight packets around strikes.
- Added compact selectable-corner count HUD items for target and ore visualization.
- Improved quadruped and horse skeleton heading so body bones follow body yaw while neck/head bones follow head yaw.
- Added tests for flight movement math, HUD/config persistence and wrapped head-yaw interpolation.

## Version 1.9 Automation Performance Update

- Reworked `autoMine` to reuse the shared ore cache instead of scanning a large cube every tick.
- Replaced the old global auto-mine target count with per-ore target count sliders and preserved the legacy count as the initial default for new per-ore values.
- Added auto-mine route visualization, current-target highlighting and a manual-movement pause setting.
- Added configurable `autoBridge` lookahead, down-scan, placement delay and anti-foot-collision settings for jump/fall bridging.
- Reduced ore visualizer overhead with chunk-level distance culling and throttled cache validation.
