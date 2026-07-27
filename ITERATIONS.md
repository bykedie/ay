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

## Version 1.10.39 Mining, Flight And Blink Stability Update

- Iteration 1: added stable invisible labels for connected ore veins so auto mining owns one ordered group instead of repeatedly selecting neighboring blocks.
- Iteration 2: allowed a labeled same-vein blocker to be mined before the queued block, kept the active mining block exact, and preferred reachable side stands over the foot-level fallback.
- Iteration 3: added per-block failed-route cooldowns and whole-vein release after an exhausted search so one impossible mining position cannot freeze automation.
- Iteration 4: added guarded sneak-to-land descent with speed limiting, periodic fall reset packets, collision-surface alignment, and re-arming after walking off an edge.
- Iteration 5: accepted controlled flight as a real airborne blink origin, stopped marking airborne transport packets as grounded, used collision-backed ground detection for Hypixel mode, and added a 40-tick red/unreachable backoff after a server position correction.
- Re-ran the Forge 1.12.2 development client and confirmed `FMLFileResourcePack:Voris Hub` plus successful loading of all five development mods without a Voris Hub exception.

## Version 1.10.40 Flight, Blink And Tools Update

- Iteration 1: removed periodic fake-ground packets during sneak descent so horizontal input and vertical landing motion remain continuous.
- Iteration 2: restored direct controller attacks for visible targets inside vanilla reach and based that path on the target's current hitbox rather than its predicted remote position.
- Iteration 3: limited remote Blink Strike excursions to one per attack cycle, removed the client-side correction replay loop, and preferred the player's current flight height for uneven targets.
- Iteration 4: added a Tools-panel Survival/Creative control, with direct integrated-server switching in singleplayer, permission-respecting `/gamemode` commands in multiplayer, and an in-panel explanation of Creative Tools.
- Iteration 5: restricted Auto Miner route rendering to remaining stand nodes, so directly mineable ore keeps only its target marker and route lines no longer terminate inside ore or blocker blocks.
- Verified 128 tests, the Forge 1.12.2 release JAR metadata and Java 8 bytecode, then loaded Voris Hub successfully in the development client.

## Version 1.10.41 Auto Mining Reach And Scaffold Update

- Iteration 1: moved direct mining ahead of active route following, so the final ore is reconsidered every tick instead of waiting on a stale stand position.
- Iteration 2: separated direct mining from path-planning posture rules; any valid ore inside vanilla reach now mines when the real world ray trace hits it, including exposed overhead ore.
- Iteration 3: assigned each connected vein a nearest-first label order at acquisition time, retained that order while mining, and used a set-backed traversal for large veins.
- Iteration 4: added the default-off `辅助垫方块` parameter, which can jump and place one stable non-falling block under a grounded player when exactly one block of extra height brings overhead ore into reach.
- Iteration 5: bounded assist placement to five attempts and 40 ticks, restored temporarily swapped inventory blocks, added failed-target cooldowns, and covered reach, ordering, placement, configuration, and GUI-setting counts with tests.

## Version 1.10.42 Auto Mining Cancellation And Confirmation Update

- Iteration 1: moved scaffold exhaustion checks before each retry so the fifth placement request gets its full server-confirmation window instead of failing against the same-tick world state.
- Iteration 2: made module disable, screen opening and quota completion stop route motion, cancel active block breaking and clear partial mining/scaffold state immediately.
- Iteration 3: made manual movement cancel the old route and vein lock while preserving the player's own horizontal motion, then reacquire from the new position after the configured pause.
- Iteration 4: bound scaffold assistance to the original vertical column, used the supporting face center for reach and placement, sampled the ore center plus all six face centers, and revalidated then mined the ore immediately after placement confirmation.
- Iteration 5: tracked only path candidates that were actually searched and found unreachable, cooled down only those blocks, and released stale vein labels after they leave the current candidate range.

## Version 1.10.43 Auto Mining Routing And Server Confirmation Update

- Iteration 1: filtered completed quotas, route cooldowns and server-rejected targets before the nearest 96-candidate cache window, and removed stale ore markers without spending the direct-visibility budget.
- Iteration 2: refreshed stale planning snapshots after eight failed targets, skipped invalid candidates in the same tick, and kept expensive path searches sliced across ticks to avoid frame spikes.
- Iteration 3: retained horizontal motion while ascending, allowed only earlier obstacles from the active route corridor to be cleared, and kept unrelated blocks outside the clearing authorization.
- Iteration 4: bounded ore and corridor destruction with tool-aware hardness budgets plus elapsed-time deadlines, and separated server-rejected destruction cooldowns from route-unreachable cooldowns.
- Iteration 5: replaced the single completion slot with a bounded world-bound queue, required three loaded-world missing ticks before counting a block, reserved pending quota, and prevented delayed confirmations from cancelling a newer route.

## Version 1.10.44 Auto Mining Navigation And Cache Update

- Iteration 1: normalized grounded fractional foot heights only on movement-blocking support, so slab and stair landings match route nodes without treating carpet-like surfaces as a full block.
- Iteration 2: retained vein labels across temporary quota and cooldown states while pruning labels whose configured ore block is actually gone.
- Iteration 3: reused the active path-candidate snapshot and cached per-ore quota availability for each batch, avoiding a full ore-cache sort on every sliced search tick.
- Iteration 4: skipped corridor breakability and tool checks for already-passable feet, head and jump-clearance cells, reducing A* work in open tunnels.
- Iteration 5: cached route obstacles and visible-ore HUD counts per tick, removed stale scaffold candidates before spending the inspection budget, and prevented the generic visibility pass from rechecking the current vein.

## Version 1.10.45 Auto Mining Cache Lifecycle Update

- Iteration 1: evicted scanned chunks and ore markers after the player leaves the active cache range, preventing validation and candidate costs from growing across long mining trips.
- Iteration 2: tracked queued chunks in an O(1) membership set and kept it synchronized through loading, resuming, pruning, unloading and world resets.
- Iteration 3: removed the redundant pre-sort used while seeding loaded chunks, retained the final distance-priority ordering, and rejected newly loaded chunks outside the active range.
- Iteration 4: bounded invalid validation-task visits per pass and restored direct visibility checks for the seventeenth and later blocks of a large connected vein.
- Iteration 5: revalidated ore enablement and quota state across visible, scaffold and path candidates so an in-progress snapshot immediately respects control-panel changes.

## Version 1.10.46 Auto Mining Cache Reconciliation Update

- Iteration 1: replaced the split validation map and stale-task deque with one insertion-ordered validation table, so chunk eviction removes validation work immediately while preserving fair rotation.
- Iteration 2: expressed pending-completion quota changes as explicit retry, visibility-loss and missing-block events, with sequential state-transition coverage.
- Iteration 3: restored an ore marker when the server rolls back a client-predicted block break, preventing the last ore from disappearing permanently after a rejected completion.
- Iteration 4: made marker restoration collapse duplicate entries and refuse to recreate markers for chunks already evicted from the active cache.
- Iteration 5: reconciled cached ore-type changes in direct, vein, labeled, scaffold, path and background-validation flows instead of dropping the replacement ore until a chunk reload.

## Version 1.10.47 Auto Mining Target Freshness Update

- Iteration 1: merged partial chunk scans by block position, preventing restored server-rollback markers from being duplicated when their section is scanned later.
- Iteration 2: used hash-based marker reconciliation for dense chunks and compacted historical duplicates while preserving ore-type replacements.
- Iteration 3: cleared invalid active routes and vein labels immediately when their ore disappears, allowing the normal retry backoff to run instead of searching every tick.
- Iteration 4: separated live direct-mining candidates from stable incremental path snapshots, so newly scanned, restored or replaced ores become eligible without disrupting an in-progress A* search.
- Iteration 5: excluded the block directly above an ore from remote path goals, preventing the miner from deliberately standing on and then removing its own support.
