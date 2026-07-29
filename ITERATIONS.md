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

## Version 1.10.48 Auto Mining Path State Update

- Iteration 1: validated completed incremental routes against current corridor and jump-clearance costs before movement begins.
- Iteration 2: compared the current mining stand set with the search snapshot, detecting newly exposed approaches beside an ore even when they were absent from the old cost cache.
- Iteration 3: split failed-search validation into bounded 64-state slices, preventing a large stale-world check from creating a single-frame spike.
- Iteration 4: required two stable failed-search validation passes, reducing the chance that a block opened between slices is accepted as permanently unreachable.
- Iteration 5: rebuilt the complete A* search at most once after a detected world change, then rejected any still-stale second result so an old tree is never resumed or an endless restart loop created.

## Version 1.10.49 Auto Mining Continuity Update

- Iteration 1: held the current ore through the three-tick server absence confirmation window, preventing the miner from planning a distant target while the final block is still being confirmed or rolled back.
- Iteration 2: treated a player leaving the current route segment as a recoverable position change, immediately replanning from the actual feet cell without applying the 100-tick unreachable-target cooldown.
- Iteration 3: capped failed-search world validation at 128 relevant states and stopped counting as soon as truncation is known, keeping both world checks and cache inspection bounded.
- Iteration 4: forced the one permitted full search restart when a failed-state sample is truncated, so unchecked stale nodes are never accepted as proof that the ore is permanently unreachable.
- Iteration 5: allowed an immediately visible unlabeled ore to yield ahead of a hidden labeled vein target while preserving the original vein labels, avoiding idle time without reintroducing distant target thrashing.

## Version 1.10.50 Auto Mining Movement Update

- Iteration 1: required turn, vertical-transition and final route nodes to reach within 0.1 blocks of center while retaining the wider straight-line threshold, reducing corner clipping without slowing long corridors.
- Iteration 2: reset route-stall progress whenever a real corridor-clearing action is issued, preventing a hard side obstacle from exhausting the 30-tick movement-stall budget while it is still being mined.
- Iteration 3: retried scaffold ascent at a bounded four-tick interval after a delayed server placement confirmation, with the existing 40-tick total timeout still enforcing a finite recovery window.
- Iteration 4: aligned scaffold completion with the navigation feet-cell threshold so the raised mining attempt starts only after the player actually occupies the upper block cell.
- Iteration 5: required the final ray-traced ore block to be in a stable adjacent, overhead or underfoot mining position, preventing vanilla reach from stopping the route four to five blocks away or enabling unstable diagonal mining.

## Version 1.10.51 Auto Mining Descent And Support Update

- Iteration 1: replaced material-only route support checks with collision-shape validation, keeping slabs and stairs usable while excluding open gates, fences and walls that cannot hold the planned feet cell; the temporary collision list is reused during A* expansion.
- Iteration 2: aligned grounded foot-cell normalization with the same collision-aware support test, stopped horizontal momentum whenever a route is abandoned, and rejected stale route steps that cross more than one vertical block or two horizontal axes.
- Iteration 3: kept descending nodes active until the player is grounded or within the final landing tolerance, preventing a falling player from consuming the node and steering toward the following segment too early.
- Iteration 4: modeled same-column descent as explicit support removal, charged and revalidated its excavation cost during incremental A*, then stopped movement and mined the underfoot block before allowing the controlled one-block drop.
- Iteration 5: allowed a labeled ore that physically blocks the queued vein target to remain mineable during continued, routed and scaffold-assisted work, so exposing the final ore no longer releases the active vein and idles.

## Version 1.10.52 Auto Mining Target Ownership And Route Cost Update

- Iteration 1: reordered only the remaining connected-vein labels after the server confirms the currently owned ore is gone, using the player's new position while leaving in-progress planning and movement stable.
- Iteration 2: retained cooldowns for every candidate already proven unreachable even when another candidate in the same search batch has a valid route, and preserved the active vein labels when such a route exists.
- Iteration 3: applied failed-candidate cooldowns before the four-candidate early return, preventing a low-label impossible ore from immediately competing again after the selected route completes.
- Iteration 4: stopped charging and validating the same support block twice for a same-column descent, while retaining the explicit pre-drop excavation step during movement.
- Iteration 5: limited jump waiting to genuine upward route transitions and cached the active route corridor by path, index and start cell, avoiding false jumps on slabs and repeated corridor allocations while clearing one obstacle.

## Version 1.10.53 Auto Mining Large Vein Visibility Update

- Iteration 1: split labeled direct-mining checks into eight fixed high-priority slots and eight rotating slots, so a visible block beyond the first sixteen entries of a large vein cannot starve indefinitely.
- Iteration 2: explicitly sorted the reusable labeled visibility buffer by stable vein label before inspection, preserving nearest-first ownership even when the shared ore cache was built under an older label order.
- Iteration 3: reset the visibility cursor and current candidate cache whenever labels are created, pruned, cleared, removed or reordered, making confirmed mining transitions visible in the same tick.
- Iteration 4: generated the center and six face ray samples by index inside the hot visibility loop, preserving the seven exact points without allocating a temporary list for every inspected ore.
- Iteration 5: invalidated the current candidate cache as soon as a route or destruction target cooldown expires, allowing the target to re-enter selection immediately instead of waiting for the next four-tick cache bucket.

## Version 1.10.54 Auto Mining Successful Route Validation Update

- Iteration 1: converted successful-route world-state validation from one unbounded loop into a persistent cursor limited to 64 traversal or clearance checks per client tick.
- Iteration 2: validated route nodes and only their distinct transition clearances in movement order, retaining ascent headroom checks without rechecking same-column descent support twice.
- Iteration 3: required two stable successful-route validation passes and checked the current mining stand set before and after every pass, preventing a world change during sliced validation from activating an old route.
- Iteration 4: rebuilt completed A* routes by collecting predecessor nodes once and reversing the list, replacing quadratic front insertion on long paths.
- Iteration 5: released the completed search queue, predecessor map and total-cost map before sliced validation, and applied the same bounded validation-budget helper to both successful and failed searches.

## Version 1.10.55 Auto Mining Completion Ownership Update

- Iteration 1: bound pending completion ownership to both block position and ore type, preventing a delayed confirmation at a reused position from cancelling work on a replacement ore.
- Iteration 2: cleared completed blocker-only mining state immediately while retaining the queued vein target, allowing the newly exposed ore to be reconsidered later in the same client tick.
- Iteration 3: reconciled the marker at a confirmed position with its current ore type instead of always deleting it, preserving a server-side ore replacement for immediate future selection.
- Iteration 4: treated a rolled-back blocker as a recoverable route change and replanned from the player's current feet cell without assigning the real queued target an unreachable cooldown.
- Iteration 5: replaced unconditional oldest-first completion eviction with priority eviction that removes expired, foreign-world, unreserved and non-current entries before protected current quota work.

## Version 1.10.56 Auto Mining Blocker Confirmation Update

- Iteration 1: checked route-bound pending confirmations before continued mining or corridor clearing, holding movement and aim through the first two client-predicted missing ticks.
- Iteration 2: retained the real queued vein target when direct ray tracing selects a labeled ore blocker after the route reaches its final stand position.
- Iteration 3: recorded the queued route position and ore type on every pending completion, so a blocker can wait for confirmation without replacing the target it was exposing.
- Iteration 4: released or replanned only when the pending completion is still bound to the current route, preventing a delayed old confirmation from freezing or cancelling a later target.
- Iteration 5: included bound-route ownership in completion-queue eviction priority, keeping the blocker confirmation that currently gates movement ahead of unrelated historical entries.

## Version 1.10.57 Auto Mining Route Start Latency Update

- Iteration 1: started a four-tick comparison budget when the first valid path target is found, preventing later hard candidates from delaying an already usable route indefinitely.
- Iteration 2: discarded a still-pending comparison search at the budget boundary without classifying that unproven candidate as unreachable or adding a retry cooldown.
- Iteration 3: consolidated batch-size, snapshot-end and comparison-timeout exits into one atomic finalizer that applies only completed failures, labels the selected vein and resets every batch timer.
- Iteration 4: revalidated the selected ore type and target cooldown immediately before route activation, preventing a route found several ticks earlier from starting against stale world state.
- Iteration 5: requested a next-tick candidate snapshot refresh when the selected route target becomes unavailable during comparison, avoiding the normal 20-tick empty-search delay.

## Version 1.10.58 Auto Mining Corridor Confirmation Update

- Iteration 1: moved ordinary corridor-block confirmation ahead of the mining-delay gate, counting disappearance on every client tick instead of stretching a three-tick check across action delays.
- Iteration 2: required three consecutive loaded-world passable observations before releasing a cleared ordinary obstacle, matching ore confirmation and rejecting one-tick client prediction flicker.
- Iteration 3: stopped route motion while an obstacle is predicted absent or its chunk is unavailable, preventing the player from entering a block before the server accepts its removal.
- Iteration 4: reset the missing counter and resumed the same destruction budget when an obstacle reappears before confirmation, avoiding premature route abandonment after a rollback.
- Iteration 5: replaced center-only corridor damage rays with a bounded center-plus-six-face search for both initial obstacle discovery and continued breaking of partially exposed blocks.

## Version 1.10.59 Auto Mining Quota Reservation Update

- Iteration 1: released an unconfirmed mining target's quota reservation whenever manual control, an open screen or route cancellation clears the active breaking state, preventing a cancelled ore from hiding its entire type for the remaining confirmation timeout.
- Iteration 2: retained the reservation after the client has observed the block missing, so cancellation cannot start another limited-count ore while server destruction confirmation is still in progress.
- Iteration 3: matched pending-completion release and rejection by world, block position and ore type, preventing a delayed entry at a reused position from changing the replacement ore's quota ownership.
- Iteration 4: routed visibility-loss cleanup through the shared mining-target reset, covering every cancellation path without duplicating partial state resets.
- Iteration 5: invalidated the four-tick candidate cache only when a reservation is added, released, retried or removed, making quota changes visible immediately without restoring per-tick ore-cache rebuilds.

## Version 1.10.60 Auto Mining Completion Evidence And Cooldown Update

- Iteration 1: separated consecutive missing ticks from persistent missing evidence, allowing unloaded chunks to reset confirmation continuity without erasing the fact that client-side destruction had already been observed.
- Iteration 2: retained a route-bound completion wait across a temporary chunk-availability gap, preventing manual cancellation or route planning from releasing protected work before confirmation or rollback.
- Iteration 3: restored the ore marker and restarted owned work when a previously missing ore reappears after a chunk gap, instead of treating the entry as an ordinary untouched timeout.
- Iteration 4: centralized target cooldown writes so every new or extended mining, scaffold and failed-route cooldown immediately invalidates the bounded candidate cache.
- Iteration 5: made cooldown updates monotonic and batched cache invalidation for failed candidate sets, preventing a shorter retry window from reviving a target early without adding repeated cache rebuilds.

## Version 1.10.61 Auto Mining Ore Scan Budget Update

- Iteration 1: replaced section-count-only throttling with a hard per-tick block-read budget for the shared ore cache, making scan work measurable even when section density varies.
- Iteration 2: reduced the auto-mining scan ceiling from two complete sections to 4096 block states per tick, halving its previous worst-case scan work while preserving one full-section throughput.
- Iteration 3: retained a separate 16384-block visualization budget, keeping four-section loading throughput when auto mining is disabled instead of applying the stricter mining budget globally.
- Iteration 4: added an in-section block cursor so a future or reduced budget can resume at the exact next block without rescanning an earlier portion of the section.
- Iteration 5: kept the existing task-visit bound alongside the block budget, so empty sections cannot turn zero block reads into an unbounded scan-queue traversal.
