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

## Version 1.10.62 Auto Mining Candidate Refresh Coalescing Update

- Iteration 1: stopped normal ore-scan marker revisions from rebuilding the nearest-96 candidate heap every client tick, restoring the intended four-tick candidate-cache interval during initial scanning.
- Iteration 2: retained immediate cache invalidation for target labels, quota reservations, cooldown changes and player feet-cell movement, so behavioral state transitions are not delayed by the coalescing interval.
- Iteration 3: detected marker changes that interrupt an empty-path retry and invalidated the candidate cache before selection, allowing a newly scanned ore to wake the miner without entering another 20-tick wait.
- Iteration 4: kept in-progress A* snapshots stable when unrelated markers arrive, avoiding repeated path-search resets while the shared cache is still filling.
- Iteration 5: removed the redundant per-candidate-cache marker revision state and covered retry wake-up separately, making the refresh rules explicit rather than coupling every marker mutation to heap reconstruction.

## Version 1.10.63 Auto Mining Empty Retry Coalescing Update

- Iteration 1: coalesced scan-marker changes that arrive during an empty-path retry while the current four-tick candidate snapshot is still reusable, preventing one heap rebuild per scan batch.
- Iteration 2: retained the originally scheduled marker revision during that short deferral so repeated scan revisions cannot prematurely satisfy a rewritten retry state.
- Iteration 3: invalidated the candidate snapshot as soon as its tick bucket expires, allowing newly scanned ore to wake path selection at the intended cache boundary.
- Iteration 4: kept player feet-cell changes as an immediate refresh condition, so movement never waits for the old snapshot's remaining ticks.
- Iteration 5: preserved planning-state motion damping during the deferred retry without consuming the longer empty-search delay or starting a duplicate path search.

## Version 1.10.64 Auto Mining Nearest Candidate Query Update

- Iteration 1: cached ore-type enablement and quota eligibility once per nearest-candidate query instead of re-evaluating them for every cached marker.
- Iteration 2: added an exact horizontal lower bound based on possible ore block centers, skipping chunks that lie outside the configured three-dimensional search radius.
- Iteration 3: skipped a full chunk once its nearest possible block center is strictly farther than the filled nearest-candidate heap, while retaining equal-distance chunks for stable coordinate tie-breaking.
- Iteration 4: rejected individual markers that cannot improve a filled heap before consulting the target-cooldown predicate, reducing hot-map lookups in dense caches.
- Iteration 5: constructed `CachedOre` objects only for candidates that actually enter the nearest heap, preserving the existing distance and coordinate ordering with less allocation pressure.

## Version 1.10.65 Auto Mining Confirmation Label Ownership Update

- Iteration 1: retained a mined ore's invisible vein label while its client-side disappearance is still inside the three-tick server confirmation window.
- Iteration 2: matched that temporary ownership by world, block position and ore type, preventing an unrelated historical completion from preserving the wrong label.
- Iteration 3: preserved labels while their chunks are temporarily unavailable instead of interpreting an unloaded position as a confirmed non-ore block.
- Iteration 4: removed the label through the existing confirmation path only after stable destruction, while a rollback restores the marker and keeps its original ordering.
- Iteration 5: consulted the bounded pending-completion queue only for loaded labels whose live ore type disagrees, keeping ordinary large-vein pruning on the fast path.

## Version 1.10.66 Auto Mining Retry Position Ownership Update

- Iteration 1: treated a changed player feet cell as an interruption of an empty-path retry even when the shared ore marker revision is unchanged.
- Iteration 2: invalidated the old nearest-candidate snapshot before selecting from the new feet cell, allowing nearby ore to be reconsidered immediately.
- Iteration 3: cancelled the old retry delay on manual takeover instead of freezing its remaining ticks throughout the configured manual-pause window.
- Iteration 4: applied the same retry and snapshot reset while a screen pauses automation, so closing the panel resumes from current world state.
- Iteration 5: retained four-tick scan-marker coalescing when the player stays in the same feet cell, preserving the previous heap-rebuild optimization.

## Version 1.10.67 Auto Mining Runtime Obstacle Ownership Update

- Iteration 1: assigned failed runtime clearing to the specific obstacle cell and replanned from the player's current position instead of cooling the target ore.
- Iteration 2: applied the same obstacle ownership to final mining-face blockers, descent supports, ascent headroom and newly detected swept-player collisions.
- Iteration 3: stopped horizontal route motion before clearing a newly non-standable path node, preventing the action delay from carrying the player into the obstacle.
- Iteration 4: restarted routes after player displacement or transient path-structure changes without hiding a nearby target for the 100-tick failed-route window.
- Iteration 5: retained target cooldown only when a completed mining stand cannot produce a valid seven-sample hit or movement records 30 consecutive ticks without meaningful progress.

## Version 1.10.68 Auto Mining Cooldown Expiry Wake-Up Update

- Iteration 1: made blocked-target cooldown expiry cancel any remaining empty-path retry delay instead of waiting up to another 20 ticks.
- Iteration 2: applied the same wake-up to rejected mining targets and runtime obstacle cells, covering every automatic retry source.
- Iteration 3: discarded the in-progress path-candidate snapshot when a cooldown expires so a newly eligible target is not omitted until the old batch completes.
- Iteration 4: discarded cached A* traversal and clearance costs from the cooldown window, allowing a newly clearable obstacle to participate in a fresh search immediately.
- Iteration 5: invalidated the nearest-candidate snapshot atomically with path state while leaving ticks with no expiry completely untouched.

## Version 1.10.69 Auto Mining Scaffold Failure Ownership Update

- Iteration 1: separated auxiliary scaffold failures from failed mining routes so a rejected placement no longer hides the ore target for 100 ticks.
- Iteration 2: added a dedicated 20-tick scaffold-strategy cooldown, preventing immediate jump/place retry loops while leaving direct mining and A* routing available.
- Iteration 3: applied the dedicated cooldown to placement exhaustion, missing inventory support, column drift, timeout and post-placement visibility failure.
- Iteration 4: pruned scaffold cooldowns with the other retry sources and woke path selection when the strategy becomes eligible again.
- Iteration 5: covered the ownership boundary directly: scaffold rejection blocks only scaffold assistance, not the mining target.

## Version 1.10.70 Auto Mining Vein Queue Ownership Update

- Iteration 1: stopped an unrelated visible ore from replacing the current mining target while a labeled vein is still locked.
- Iteration 2: retained opportunistic direct mining for newly scanned or newly exposed ore that is physically connected to the active labeled vein.
- Iteration 3: required the extension ore to match the locked ore type, preventing adjacent mixed ores from silently joining the wrong queue.
- Iteration 4: appended a connected extension to the invisible label set before mining it and reordered the remaining labels by current player distance.
- Iteration 5: preserved diagonal vein connectivity for ore-cluster ownership while continuing to forbid diagonal player mining positions.

## Version 1.10.71 Auto Mining Route Stall Recovery Update

- Iteration 1: changed the first 30-tick no-progress event from a target-wide failure into a fresh route plan from the player's current feet cell.
- Iteration 2: retained one recovery attempt per unchanged ore target, preventing an unreachable route from entering an endless replan loop.
- Iteration 3: treated reaching a route node or making measurable movement as genuine recovery and reset the prior stall ownership.
- Iteration 4: kept the retry ownership through the initial infinite-distance sample of a rebuilt route, so merely starting the same path cannot erase its retry history.
- Iteration 5: preserved the existing 100-tick target cooldown only after the rebuilt route to the same ore also makes no progress.

## Version 1.10.72 Auto Mining Stall Location Ownership Update

- Iteration 1: bound a route-stall recovery attempt to both the ore target and the player's feet cell where the stall occurred.
- Iteration 2: prevented tiny within-cell movement from resetting the retry budget and creating repeated route rebuilds at the same obstruction.
- Iteration 3: granted a fresh bounded recovery after the player genuinely reaches a different navigation cell.
- Iteration 4: retained target changes as a separate ownership boundary, so a stall history from one ore never penalizes another ore.
- Iteration 5: kept route-node completion as the explicit stable-progress reset while ordinary distance updates only reset the 30-tick timer.

## Version 1.10.73 Auto Mining Vein Extension Lookup Update

- Iteration 1: replaced full-label-map scans for every unlabeled candidate with a fixed neighborhood lookup around that candidate.
- Iteration 2: bounded each connectivity decision to the 26 adjacent ore positions regardless of the active vein's total size.
- Iteration 3: preserved face, edge and corner connectivity exactly, including negative-coordinate diagonal neighbors, using one reusable mutable coordinate cursor.
- Iteration 4: retained the existing same-type and already-labeled guards before any neighborhood work.
- Iteration 5: removed dense-vein quadratic comparisons from the per-tick direct-mining fallback without changing queue ownership.

## Version 1.10.74 Auto Mining Final Exposure Update

- Iteration 1: traced the seven ore sight lines again after reaching a valid mining stand instead of cooling the target when the predefined face block is already clear.
- Iteration 2: allowed the actual breakable blocker adjacent to the ore to enter the existing tracked destruction and three-tick confirmation flow.
- Iteration 3: reused the same hardness-based attempt budget and exact ray hit for corridor and final-exposure obstacles.
- Iteration 4: explicitly protected the player's feet, head and supporting block, and kept the completed route target queued while any necessary ore blocker is mined.
- Iteration 5: bounded accepted blockers to the ore's immediate 3x3x3 neighborhood so the recovery cannot tunnel through unrelated distant terrain.

## Version 1.10.75 Auto Mining Scaffold Material Update

- Iteration 1: required auxiliary scaffold items to provide a full-cube default state instead of accepting every solid material.
- Iteration 2: excluded slabs, stairs and other partial-height blocks that can place successfully without raising the player by the required full block.
- Iteration 3: retained the existing exclusion for gravity-affected sand and gravel.
- Iteration 4: retained normal block placement validation at the exact player column after the material-shape check.
- Iteration 5: covered solid, falling, partial-height and non-solid combinations independently.

## Version 1.10.76 Auto Mining Boundary Scan Fairness Update

- Iteration 1: replaced chunk-grid ordering with the exact horizontal lower-bound distance from the player to each chunk's block centers.
- Iteration 2: allowed queued chunks physically within two blocks of a resumed scan task to receive their current-height section before that task monopolizes all vertical sections.
- Iteration 3: covered cardinal and diagonal boundary cases through the same physical-distance ordering instead of special-casing chunk coordinates.
- Iteration 4: retained the existing 4096-block auto-mining budget, so the latency improvement does not increase worst-case per-tick block reads.
- Iteration 5: continued ordering truly farther chunks behind the resumed nearer task once the two-block fairness window is exceeded.

## Version 1.10.77 Auto Mining Scan Merge Allocation Update

- Iteration 1: replaced three full marker maps per scan slice with one position-to-index map over the stored chunk markers.
- Iteration 2: appended newly discovered ores directly and updated the per-type index only for those new entries.
- Iteration 3: replaced changed ore types in place instead of unregistering and rebuilding every marker in the chunk.
- Iteration 4: retained a rare full compaction path when historical duplicate positions are detected, preserving rollback-marker deduplication.
- Iteration 5: skipped marker revision and validation work when a scan slice only rediscovers identical cached positions and types.

## Version 1.10.78 Auto Mining Scan Distance Window Update

- Iteration 1: corrected the resumed-task fairness window from an additive squared-distance approximation to a true two-block physical distance.
- Iteration 2: converted the resumed lower-bound distance back to linear space before applying the two-block allowance.
- Iteration 3: squared the resulting threshold once for comparison, retaining the existing squared-distance queue representation.
- Iteration 4: kept all tasks already nearer than the resumed task ahead without performing an unnecessary square root.
- Iteration 5: covered both the origin boundary and a resumed task ten blocks away, where the old approximation was most visibly too strict.

## Version 1.10.79 Auto Mining Vertical Scan Reprioritization Update

- Iteration 1: added the player's current vertical section to the ore-cache seed state so same-chunk vertical movement can refresh scan priority.
- Iteration 2: reordered only the unscanned tail of each queued chunk task around the new height.
- Iteration 3: kept a partially scanned section fixed at the front, preventing duplicate block reads or lost cursor progress.
- Iteration 4: used the original nearest-section ordering rule, preferring the lower section first when two heights are equally distant.
- Iteration 5: retained fully scanned chunks and horizontal queue state while making nearby ores at the new height appear sooner.

## Version 1.10.80 Auto Mining Unloaded Validation Guard Update

- Iteration 1: checked chunk availability before reading cached ore positions during background validation.
- Iteration 2: deferred unavailable chunk tasks instead of interpreting placeholder air as confirmed ore removal.
- Iteration 3: kept the validation cursor unchanged while deferred, so the same marker resumes when its chunk is available again.
- Iteration 4: retained fair rotation by moving a deferred task to the end of the insertion-ordered validation queue.
- Iteration 5: preserved the existing marker-read budget for loaded chunks because deferred tasks consume no block checks.

## Version 1.10.81 Auto Mining Vertical Seed Fast Path Update

- Iteration 1: separated horizontal ore-cache seed identity from the player's current vertical section.
- Iteration 2: added a same-chunk vertical fast path that only reprioritizes unscanned section tails.
- Iteration 3: skipped cache pruning and the full loaded-chunk radius enumeration when only height changed.
- Iteration 4: skipped whole-queue horizontal distance sorting and validation-delay resets on the vertical-only path.
- Iteration 5: retained the full seed refresh whenever the world, range, radius or horizontal chunk changes.

## Version 1.10.82 Auto Mining Endpoint Exposure Route Update

- Iteration 1: changed a final visibility failure into route replanning instead of immediately cooling down the ore target.
- Iteration 2: recorded the failed endpoint under its exact ore target and feet cell, then excluded only that stand from the next A* search.
- Iteration 3: retained all other mining stands so the route can immediately try another side or height around the same ore.
- Iteration 4: cooled only the specific unbreakable, unreachable or already rejected exposure blocker, without adding hypothetical per-stand ray traces during path search.
- Iteration 5: expired stand rejections after 100 ticks and retained target cooldown only after bounded path search confirms that no valid stand remains.

## Version 1.10.83 Auto Mining Hidden Vein Extension Update

- Iteration 1: expanded an active vein label set when later scan slices discover connected ore that is not visible yet.
- Iteration 2: traversed the fixed 26-neighbor topology transitively, allowing a newly discovered chain of adjacent ore to join in one refresh.
- Iteration 3: seeded expansion from every existing label so a temporarily absent candidate entry cannot split the owned vein.
- Iteration 4: excluded unrelated positions and mismatched ore types before they can affect target ownership.
- Iteration 5: reordered the enlarged vein by the player's current distance before direct-mining and path selection continue.

## Version 1.10.84 Auto Mining Vein Extension Coalescing Update

- Iteration 1: bound hidden-vein expansion to the identity of the bounded current-candidate snapshot.
- Iteration 2: skipped repeated connectivity traversal while the same four-tick snapshot is reused.
- Iteration 3: ran expansion immediately after a snapshot rebuild caused by scanning, movement or behavioral cache invalidation.
- Iteration 4: kept label acquisition unchanged because its initial flood fill already includes every connected ore in that snapshot.
- Iteration 5: reset snapshot ownership on reload and world unload without coupling it to unrelated path-search state.

## Version 1.10.85 Auto Mining Scaffold Posture Update

- Iteration 1: evaluated scaffold assistance against both the current feet cell and the feet cell after the one-block raise.
- Iteration 2: started assistance when the current posture is invalid but the raised posture satisfies the same stable-mining rule used after placement.
- Iteration 3: removed the contradictory requirement that the ore must be outside raw vanilla reach before the raise.
- Iteration 4: retained the raised-eye vanilla reach check and exact raised-position ray trace before any jump or placement.
- Iteration 5: rejected targets that are already stable, occupy the raised player space or remain too high after one block.

## Version 1.10.86 Auto Mining Endpoint Identity Update

- Iteration 1: bound failed endpoint stands to both the ore position and its current ore type.
- Iteration 2: discarded a stored stand rejection immediately when the same position contains a different ore type.
- Iteration 3: removed all endpoint rejections for a position after server-confirmed mining completes there.
- Iteration 4: retained independent expiry for each stand while removing the empty typed container atomically.
- Iteration 5: prevented a replacement ore from inheriting the previous block's 100-tick endpoint history.

## Version 1.10.87 Auto Mining Vein Search Ownership Update

- Iteration 1: separated the eight-failure snapshot refresh threshold from whole-vein label release.
- Iteration 2: retained the active vein while later labeled candidates in the bounded snapshot remain unsearched.
- Iteration 3: kept independent retry cooldowns for the exact candidates already proven unreachable.
- Iteration 4: retained vein ownership when a selected route becomes stale immediately before activation and requests a fresh snapshot.
- Iteration 5: released the vein only after the complete candidate snapshot is exhausted without an available route.

## Version 1.10.88 Auto Mining Scan Queue Update

- Iteration 1: replaced resumed scan-task queue rotation with direct positional insertion.
- Iteration 2: traversed only the nearby queue prefix required by the existing two-block fairness threshold.
- Iteration 3: calculated the resumed-distance threshold once per insertion instead of once per queued task.
- Iteration 4: retained the existing closest-chunk ordering and bounded per-tick block scan budget.
- Iteration 5: covered zero, positive and clamped negative distance thresholds with focused tests.

## Version 1.10.89 Auto Mining Stable Candidate Cache Update

- Iteration 1: retained the nearest-96 candidate heap beyond its four-tick bucket while the player remains exactly still.
- Iteration 2: bound extended reuse to the ore-marker revision so newly scanned, removed or changed ore still refreshes selection.
- Iteration 3: retained the existing same-bucket marker coalescing behavior during active scanning.
- Iteration 4: preserved immediate invalidation for labels, cooldowns, quota reservations, manual takeover and control-panel pauses.
- Iteration 5: covered movement, marker changes, feet-cell changes and same-bucket coalescing independently.

## Version 1.10.90 Auto Mining Labeled Visibility Cache Update

- Iteration 1: cached the filtered and label-sorted visible candidate list for the current nearest-96 snapshot.
- Iteration 2: reused that ordered list across direct-mining ticks instead of filtering and sorting the same vein repeatedly.
- Iteration 3: retained the fixed nearest-eight plus rotating-tail inspection schedule unchanged.
- Iteration 4: invalidated the ordered list whenever labels change or a new candidate snapshot is produced.
- Iteration 5: covered identity reuse, replacement snapshots, missing labels and uninitialized state independently.

## Version 1.10.91 Auto Mining Selection Revision Update

- Iteration 1: added a monotonic revision for settings that change the auto-mining candidate set.
- Iteration 2: advanced it for path range, per-ore target counts, ore switches, and full config synchronization.
- Iteration 3: bound nearest-candidate cache reuse to the same selection revision, including same-bucket marker coalescing.
- Iteration 4: interrupted an empty-route retry immediately when its scheduled selection revision becomes stale.
- Iteration 5: covered relevant changes, no-op saves, unrelated settings, reloads, and retry/cache invalidation independently.

## Version 1.10.92 Auto Mining Active Selection Refresh Update

- Iteration 1: observed the current auto-mining selection revision at module startup and every enabled tick.
- Iteration 2: released an active route when path range or candidate eligibility changes outside the control panel.
- Iteration 3: stopped current block breaking and scaffold assistance before rebuilding selection state.
- Iteration 4: cleared owned vein labels, candidate snapshots, route motion, and stale action delay together.
- Iteration 5: retained the existing config-reload listener behavior and covered changed versus unchanged revisions independently.

## Version 1.10.93 Auto Mining Scan Slice Index Update

- Iteration 1: reused the existing per-ore encoded-position sets while merging partial chunk scan results.
- Iteration 2: replaced a new whole-chunk temporary index allocation on every non-empty scan slice.
- Iteration 3: skipped unchanged same-type markers with bounded ore-type hash lookups.
- Iteration 4: retained the existing rare replacement path when a scanned position changes ore type.
- Iteration 5: preserved the 4096-block auto-mining scan budget and covered present, absent, and empty index lookups.

## Version 1.10.94 Auto Mining Ore Type Lookup Update

- Iteration 1: resolved each encountered Block instance once, then retained its ore type in an identity map.
- Iteration 2: added an identity-based negative cache so common non-ore blocks also avoid repeated registry-name hashing.
- Iteration 3: retained the existing registry-name table for configuration matching and compatibility.
- Iteration 4: covered all vanilla ore blocks, lit and unlit redstone, non-ore stone, and null input.
- Iteration 5: kept lookup initialization lazy so early enum loading cannot capture an uninitialized Minecraft registry.

## Version 1.10.95 Auto Mining Disabled Ore Idle Update

- Iteration 1: added a shared check for whether any auto-mining ore type is currently enabled.
- Iteration 2: stopped auto-mining target, visibility, and path work while every ore switch is off.
- Iteration 3: excluded an inactive all-disabled auto miner from the shared ore-cache range calculation.
- Iteration 4: let ore visualization continue independently with its own larger scan budget when enabled.
- Iteration 5: covered default, all-disabled, re-enabled, module-disabled, and cache-budget decisions.

## Version 1.10.96 Auto Mining Path Candidate Origin Update

- Iteration 1: recorded the player feet cell that produced each bounded path-candidate snapshot.
- Iteration 2: reused an unfinished candidate comparison only while the player remains in that cell.
- Iteration 3: discarded the pending A* search, best route, failed targets, and comparison timer after a cell change.
- Iteration 4: rebuilt candidate distance ordering from the new player position in the same selection cycle.
- Iteration 5: retained active-route control and manual-takeover behavior while covering stable, moved, and empty snapshots.

## Version 1.10.97 Auto Mining Empty Route Handoff Update

- Iteration 1: reproduced the fixed three-tick idle when the selected mining stand was already the player's current cell.
- Iteration 2: handed a zero-node route directly to the existing endpoint mining state instead of creating a separate wait.
- Iteration 3: retained the endpoint checks for stable footing, head room, support, mining face, visibility, and exposure clearing.
- Iteration 4: retained immediate replanning when the current cell is no longer a valid mining stand.
- Iteration 5: covered zero-length, completed, active, and targetless route states with the focused auto-miner test suite.

## Version 1.10.98 Auto Mining Endpoint Posture Update

- Iteration 1: reproduced endpoint replanning that could immediately select the same unusable mining stand again.
- Iteration 2: separated target validity from the player's final mining posture checks.
- Iteration 3: rejected only the current ore-and-stand pair when relative position, foot room, head room, or support is invalid.
- Iteration 4: retained alternate stand searches for the same ore instead of cooling down the entire target.
- Iteration 5: covered valid posture, invalid relative position, and invalid work-area outcomes in the focused auto-miner tests.

## Version 1.10.99 Auto Mining Blocker Ownership Update

- Iteration 1: reproduced a route blocker that retained its route binding only on the first mining tick.
- Iteration 2: stored blocker ownership alongside the active mining position and ore type.
- Iteration 3: inherited blocker ownership only while continuing the exact same mining target.
- Iteration 4: cleared ownership when mining state is released or a different target begins.
- Iteration 5: covered inherited, replaced, newly requested, and ordinary mining ownership transitions.

## Version 1.10.100 Auto Mining Scan Startup Update

- Iteration 1: reproduced queued chunk scans reaching player-coordinate code before the client player existed.
- Iteration 2: required both the client world and player before consuming shared ore-cache scan tasks.
- Iteration 3: retained chunk-load tasks queued during early world initialization instead of discarding them.
- Iteration 4: resumed the same queue automatically once the player snapshot became available.
- Iteration 5: covered ready, missing-player, and missing-world scan contexts alongside both ore-cache consumers.

## Version 1.10.101 Auto Mining Final Node Handoff Update

- Iteration 1: reproduced the extra idle tick after a non-empty route reached its final stand node.
- Iteration 2: stopped horizontal route motion before handing the target to endpoint mining.
- Iteration 3: entered the existing posture, face, visibility, and exposure checks in the same tick.
- Iteration 4: retained landing confirmation for descents and exact node-center checks for turns and vertical steps.
- Iteration 5: covered one-node and longer completed routes alongside active and zero-node route states.

## Version 1.10.102 Auto Mining Blocker Chain Update

- Iteration 1: reproduced a blocker ray changing to a nearer ore during continued route clearing.
- Iteration 2: propagated explicit route-blocker ownership through the continued mining entry point.
- Iteration 3: retained the original queued route target while the blocker chain changes ore positions.
- Iteration 4: kept ordinary direct mining on the unowned path so unrelated targets cannot inherit the route.
- Iteration 5: covered same-target inheritance, explicit cross-target propagation, replacement, and ordinary mining states.

## Version 1.10.103 Auto Mining Target Switch Reservation Update

- Iteration 1: reproduced an old mining target retaining a finite-quota reservation after the active ray switched blocks.
- Iteration 2: detected target changes by both block position and ore type.
- Iteration 3: released only reservations that had not yet observed server-side block absence.
- Iteration 4: retained later absence confirmation so a genuinely mined old target is still counted.
- Iteration 5: covered no-current-target, same-target, changed-position, and changed-type transitions.

## Version 1.10.104 Auto Mining Hazardous Cell Update

- Iteration 1: reproduced replaceable fire and lava materials being accepted as route cells.
- Iteration 2: separated replaceability from route safety in the shared passability decision.
- Iteration 3: excluded fire and lava from A* traversal, endpoint posture, and route execution checks.
- Iteration 4: retained water and ordinary replaceable blocks so underwater mining remains available.
- Iteration 5: covered safe replaceable, hazardous replaceable, and solid non-replaceable cells.

## Version 1.10.105 Auto Mining Hazardous Support Update

- Iteration 1: reproduced solid magma and cactus blocks passing the route support test.
- Iteration 2: separated physical support from whether that support is safe to stand on.
- Iteration 3: excluded magma blocks and cactus before full-cube and collision-shape acceptance.
- Iteration 4: retained ordinary full blocks and safe low-collision support surfaces.
- Iteration 5: covered safe support, hazardous support, and non-support materials.

## Version 1.10.106 Auto Mining Runtime Hazard Update

- Iteration 1: reproduced fire or lava appearing after route validation but before node entry.
- Iteration 2: rechecked the actual destination foot and head cells during route execution.
- Iteration 3: stopped movement and reused normal replanning when either runtime cell is hazardous.
- Iteration 4: retained collision-box handling for ordinary non-hazardous passable shapes.
- Iteration 5: covered safe, hazardous-foot, and hazardous-head runtime stands.

## Version 1.10.107 Auto Mining Endpoint Motion Update

- Iteration 1: reproduced a zero-node route retaining planning-stage horizontal drift when mining began immediately.
- Iteration 2: moved the stop into the shared endpoint-mining entry point instead of patching one caller.
- Iteration 3: made zero-node and ordinary final-node handoffs follow the same stop-before-mining order.
- Iteration 4: retained the existing posture, face-neighbor, visibility, and exposure recovery checks.
- Iteration 5: covered positive, negative, and already-zero residual horizontal motion.

## Version 1.10.108 Auto Mining Scaffold Safety Update

- Iteration 1: reproduced auxiliary scaffolding accepting a full solid magma block.
- Iteration 2: added the existing hazardous-support classification to scaffold selection.
- Iteration 3: rejected damaging support blocks before placement or inventory swapping.
- Iteration 4: retained ordinary full blocks while continuing to reject falling and partial blocks.
- Iteration 5: covered safe, hazardous, falling, partial, and non-solid scaffold candidates.

## Version 1.10.109 Auto Mining Replan Motion Update

- Iteration 1: reproduced automatic replanning paths that cleared route state without stopping old horizontal motion.
- Iteration 2: centralized route-motion stopping in the automatic restart entry point.
- Iteration 3: covered target invalidation, endpoint rejection, server displacement, and route-transition recovery callers.
- Iteration 4: retained manual takeover through the existing motion-preserving cancellation path.
- Iteration 5: covered positive and negative route motion for both automatic and manual reset modes.

## Version 1.10.110 Auto Mining Break Progress Update

- Iteration 1: reproduced lost-visibility and target-switch paths retaining vanilla block-breaking progress.
- Iteration 2: reset the player controller whenever active mining state is released.
- Iteration 3: reset old breaking progress before switching to a different ore or route blocker.
- Iteration 4: retained uninterrupted progress while continuing the exact same block and ore type.
- Iteration 5: covered complete, changed, released, partial, and empty mining states.

## Version 1.10.111 Auto Mining Clearing Progress Update

- Iteration 1: reproduced ordinary route obstacles retaining vanilla breaking progress after release.
- Iteration 2: reset old progress before switching the active clearing position.
- Iteration 3: reset progress when clearing is cancelled or confirmed complete.
- Iteration 4: retained uninterrupted progress while continuing the exact same obstacle.
- Iteration 5: covered same, changed, released, newly requested, and empty clearing states.

## Version 1.10.112 Auto Mining Damage Acceptance Update

- Iteration 1: confirmed Forge 1.12.2 returns false when the client controller rejects block damage.
- Iteration 2: required accepted damage before advancing ore destruction attempts.
- Iteration 3: prevented rejected ore requests from swinging or creating completion confirmation.
- Iteration 4: applied the same acceptance gate to ordinary route obstacles.
- Iteration 5: covered accepted and rejected attempt progression from zero and active counts.

## Version 1.10.113 Auto Mining Tool Change Update

- Iteration 1: reproduced vanilla block-breaking progress restarting after a materially different tool is selected.
- Iteration 2: captured the accepted mining tool for ore and ordinary obstacle destruction.
- Iteration 3: used Forge 1.12.2 item identity, NBT, and damageability rules to detect progress-resetting changes.
- Iteration 4: rebuilt the matching attempt budget and deadline whenever vanilla progress restarts.
- Iteration 5: covered new targets, unchanged tools, durability-only changes, different items, changed NBT, and empty hands.

## Version 1.10.114 Auto Mining Hit Delay Update

- Iteration 1: reproduced Forge reporting an accepted damage call during vanilla's post-break hit delay without starting the next block.
- Iteration 2: separated an accepted controller call from actual destruction progress.
- Iteration 3: kept the next ore or obstacle locked while the vanilla delay counts down.
- Iteration 4: deferred swings, attempt consumption, and completion registration until breaking starts or the block is instantly removed.
- Iteration 5: covered delayed, active, instant, and rejected destruction requests.

## Version 1.10.115 Auto Mining Hit Delay Pacing Update

- Iteration 1: reproduced configured mining delay stretching vanilla's five-call post-break wait for route obstacles.
- Iteration 2: moved retry pacing into the shared destruction entry points.
- Iteration 3: retried accepted-but-not-started destruction on the next client tick.
- Iteration 4: retained the configured delay after actual ore or obstacle progress.
- Iteration 5: removed path, endpoint, and collision callers that overwrote the destruction outcome's delay.

## Version 1.10.116 Auto Mining Continuous Break Update

- Iteration 1: reproduced vanilla cancelling hard-block progress when no physical attack key remained held between client ticks.
- Iteration 2: temporarily owned the attack binding during the vanilla input phase only for the exact active ore or obstacle.
- Iteration 3: preserved the player's physical attack state and released automatic ownership on pause, target change, disable, and world unload.
- Iteration 4: made mining delay a confirmed block-to-block delay instead of interrupting repeated damage to the same block.
- Iteration 5: aligned destruction deadlines with continuous ticks and covered exact crosshair ownership, completion, delay, and target-priority rules.

## Version 1.10.117 Auto Mining Dynamic Hardness Update

- Iteration 1: reproduced a block retaining its original fast destruction budget after mining conditions became slower.
- Iteration 2: sampled current relative hardness while continuing the same ore or route obstacle.
- Iteration 3: expanded the attempt budget without resetting vanilla's accumulated breaking progress.
- Iteration 4: extended the matching deadline only by the newly required attempts and kept faster changes from shortening it.
- Iteration 5: covered slower, faster, zero-hardness, unchanged, and integer-overflow boundary cases.

## Version 1.10.118 Auto Mining Tool Ownership Update

- Iteration 1: reproduced combat auto-weapon selection leaving a sword active during vanilla's next continuous mining tick.
- Iteration 2: prepared the best mining tool during the client START phase before vanilla processes held attack input.
- Iteration 3: limited early tool preparation to a validated active ore or route obstacle.
- Iteration 4: retained combat's later weapon selection while restoring the pickaxe before every owned mining tick.
- Iteration 5: covered ore, obstacle, and inactive-target preparation decisions alongside the existing tool-reset tests.

## Version 1.10.119 Auto Mining Tool Reach Gate Update

- Iteration 1: reproduced a stale active target switching back to a pickaxe before reach and visibility were revalidated.
- Iteration 2: reused the exact START-phase reach check before changing the selected hotbar slot.
- Iteration 3: required an exact ray trace to the owned ore or route obstacle before tool preparation.
- Iteration 4: left unreachable, newly obstructed, and inactive targets without automatic hotbar changes.
- Iteration 5: covered valid, out-of-reach, occluded, and inactive preparation decisions.

## Version 1.10.120 Auto Mining Focus Pause Update

- Iteration 1: reproduced loss of game focus releasing vanilla attack input while END-phase mining still consumed attempts.
- Iteration 2: unified open-screen and unfocused-window handling under one automatic-input availability gate.
- Iteration 3: stopped active mining, clearing, route motion, and synthetic attack ownership as soon as input became unavailable.
- Iteration 4: resumed through normal fresh target selection after focus returned instead of retaining stale destruction state.
- Iteration 5: covered focused gameplay, open-screen pause, and unfocused-window pause decisions.

## Version 1.10.121 Auto Mining Direct Endpoint Update

- Iteration 1: reproduced a reached endpoint rejecting its current stand before checking an already visible ore.
- Iteration 2: sampled the real reach, ray trace, and stable mining relationship before applying work-area fallback.
- Iteration 3: allowed a directly mineable ore to proceed when a low ceiling makes only the endpoint work-area check fail.
- Iteration 4: retained alternate-stand replanning for unstable geometry and for targets that still cannot be hit.
- Iteration 5: covered ready, unstable, obstructed, and directly mineable endpoint decisions in the focused tests.

## Version 1.10.122 Auto Mining Vein Handoff Update

- Iteration 1: reproduced a completed labeled route blocker leaving the remaining vein in its old distance order.
- Iteration 2: tracked whether completion actually removed an invisible vein label instead of inferring it from route ownership.
- Iteration 3: reordered every remaining labeled block from the player's current position after any labeled block is confirmed mined.
- Iteration 4: retained route ownership, completion confirmation, quota reservations, and unrelated unlabeled completions unchanged.
- Iteration 5: covered removed and retained label outcomes in the focused auto-miner tests.

## Version 1.10.123 Auto Mining Exposure Sample Update

- Iteration 1: reproduced endpoint exposure recovery accepting the first geometric ray hit before checking whether that obstacle was usable.
- Iteration 2: applied the existing cooldown, reach, stable-position, and breakability rules to each sampled hit.
- Iteration 3: skipped unusable early hits and continued through the remaining visibility samples.
- Iteration 4: retained the same mining reach, protected player cells, ore eligibility, and ordinary-block harvest requirements.
- Iteration 5: covered allowed, disallowed, breakable, and unusable sampled obstacles in the focused tests.

## Version 1.10.124 Auto Mining Corner Visibility Update

- Iteration 1: reproduced a locked ore exposing only a corner that none of the center or six face-center rays could reach.
- Iteration 2: retained the seven-sample fast path for ordinary candidate discovery.
- Iteration 3: appended all eight inset corner samples only for the current mining target, route endpoint, active obstacle, and scaffold target.
- Iteration 4: preserved the existing reach, target type, stable-position, and labeled-blocker checks for every added ray.
- Iteration 5: covered the unchanged seven-point candidate set and the ordered fifteen-point locked-target set.

## Version 1.10.125 Auto Mining Exposed Aim Update

- Iteration 1: reproduced a corner-visible ore being detected and then immediately aimed back at its occluded block center.
- Iteration 2: retained the actual ray-trace hit point alongside the ore position, type, and hit side.
- Iteration 3: aimed every non-vanilla-owned destruction request at that exposed point.
- Iteration 4: kept block-center fallback behavior for compatibility paths without a hit vector.
- Iteration 5: covered exposed-point preference and center fallback in the focused auto-miner tests.

## Version 1.10.126 Auto Mining Blocker Order Update

- Iteration 1: reproduced multiple labeled vein blockers being selected by visibility-sample traversal order.
- Iteration 2: preserved an exact hit on the requested ore as the highest-priority result.
- Iteration 3: ranked fallback vein blockers by their existing invisible sequence label.
- Iteration 4: used eye-to-hit distance only when candidate blockers share the same label.
- Iteration 5: covered lower-label and equal-label blocker comparisons in the focused tests.

## Version 1.10.127 Auto Mining Crosshair Ownership Update

- Iteration 1: decompiled Forge 1.12.2 client tick input and confirmed that a non-block crosshair resets vanilla breaking progress.
- Iteration 2: reproduced the missing ownership condition with a focused test requiring the final vanilla crosshair to match the scheduled block.
- Iteration 3: pre-refreshed the vanilla crosshair only after a valid target, reach check, and exact block ray were established.
- Iteration 4: withheld automatic attack-key ownership when an edge blocker or entity replaced the requested block under the vanilla crosshair.
- Iteration 5: retained the existing direct mining, tool selection, physical attack-key, and END-tick ownership behavior.

## Version 1.10.128 Auto Mining Candidate Frontier Update

- Iteration 1: reproduced a same-feet path snapshot continuing to search an older distant ore after a nearer candidate entered the refreshed cache.
- Iteration 2: compared refreshed candidates against the current unprocessed snapshot frontier instead of reusing solely by feet cell.
- Iteration 3: rebuilt the batch when its next candidate disappeared or a newly discovered candidate moved ahead of it.
- Iteration 4: preserved completed comparisons when a lower-priority candidate was merely appended behind the old batch.
- Iteration 5: limited content comparison to actual candidate-cache object changes so stable ticks keep constant-time snapshot checks.

## Version 1.10.129 Auto Mining Crosshair Sample Fallback Update

- Iteration 1: ruled out premature vein-label release during the existing three-tick completion pause.
- Iteration 2: reproduced the first custom-visible point failing vanilla crosshair ownership while a later point remained usable.
- Iteration 3: retained the single-refresh fast path when the first exposed point matches the vanilla crosshair.
- Iteration 4: added a bounded fallback across the remaining face-center and inset-corner samples after a mismatch.
- Iteration 5: stopped at the first vanilla-exact sample and covered accepted, exhausted, and empty sample scans.

## Version 1.10.130 Auto Mining Descending Clearance Update

- Iteration 1: reproduced a horizontal descent being accepted without checking the player's swept head cell above the lower landing.
- Iteration 2: required the transition clearance to include the landing cell two blocks above the feet during a sideways descent.
- Iteration 3: made completed-path validation recheck that clearance with the existing bounded validation passes.
- Iteration 4: reused the same transition rule in corridor generation so pre-clearing sees the obstacle before movement begins.
- Iteration 5: preserved straight-down support removal, ascending jump clearance, level movement, and existing route ordering in focused tests.

## Version 1.10.131 Auto Mining Route Failure Origin Update

- Iteration 1: reproduced a failed A* target remaining hidden for the full retry window after the player moved to a different feet cell.
- Iteration 2: recorded the navigation origin alongside target cooldowns produced only by path-search failures.
- Iteration 3: released those stale failures atomically when the current feet cell no longer matched their origin.
- Iteration 4: refreshed candidate and path-search snapshots immediately after releasing origin-bound failures.
- Iteration 5: preserved same-origin retry pacing and kept destruction, obstacle, scaffold, and mining-stand cooldowns independent.

## Version 1.10.132 Auto Mining Corridor Sample Update

- Iteration 1: reproduced the first corridor ray hit ending the scan before its cooldown, reach, stability, or breakability was checked.
- Iteration 2: applied the existing corridor membership and exposure-usability rules to every sampled hit.
- Iteration 3: continued through the bounded visibility samples after an unusable early corridor hit.
- Iteration 4: retained the first-sample fast path and the existing desired, permitted-lower, and planned-corridor boundary.
- Iteration 5: covered cooled, disallowed, unreachable, unstable-ore, and usable ordinary obstacles in focused tests.

## Version 1.10.133 Auto Mining Stale Path Classification Update

- Iteration 1: reproduced a restarted A* search bypassing failure snapshot validation when its queue or node budget was exhausted.
- Iteration 2: reproduced a second stale success or failure snapshot being reported as a completed unreachable result.
- Iteration 3: routed every exhausted search through the existing bounded two-pass failure validation, including restarted searches.
- Iteration 4: deferred restart-exhausted stale snapshots without adding their ore to the 100-tick failed-route cooldown.
- Iteration 5: added focused state-machine regressions while preserving node budgets, one in-attempt restart, and retry pacing.

## Version 1.10.134 Auto Mining Active Hand Pause Update

- Iteration 1: reproduced START declining attack-key ownership while END still selected a pickaxe and damaged a block during active item use.
- Iteration 2: extended the shared input predicate to cover either active hand and used it in both tick phases.
- Iteration 3: paused route motion and all new mining, clearing, scaffold, rotation, and tool-selection work without discarding locked state.
- Iteration 4: preserved server-side completion confirmation before the pause gate so already-finished work can settle.
- Iteration 5: froze destruction and scaffold deadlines across paused ticks and covered the input and deadline rules with focused assertions.

## Version 1.10.135 Flight Landing Control Update

- Iteration 1: reproduced Jump and Sneak resolving to neutral vertical input before the landing guard forced the same tick downward.
- Iteration 2: restricted landing activation to Sneak without Jump so the established neutral-input contract is preserved.
- Iteration 3: raised controlled descent from a fixed 0.35 cap to at most 1.0 block per tick and bounded the final step by the measured collision distance.
- Iteration 4: removed the manual ground-position snap and extra grounded packet, relying on real player collision for landing state.
- Iteration 5: retained landing control after a high-altitude disable until a real grounded or server-recognized fall-safe state, and documented that client fallDistance resets cannot erase server-side survival fall accumulation.

## Version 1.10.136 Flight Hypixel Ground State Update

- Iteration 1: reproduced Hypixel mode forcing `onGround=true` while the player was physically airborne.
- Iteration 2: traced the Forge 1.12.2 END player tick into the same tick's ordinary movement packet and the server fall-settlement path.
- Iteration 3: added a regression requiring Hypixel mode to preserve both airborne false and real-ground true states.
- Iteration 4: removed the synthetic midair grounded state while retaining the established Hypixel offset and local motion behavior.
- Iteration 5: clarified that this prevents an early client-triggered settlement but cannot erase fall distance already accumulated by a Survival server.

## Version 1.10.137 Blink Reachability Context Update

- Iteration 1: reproduced a ground-origin failed plan suppressing a valid Flight-height route until the 12-tick cache expired.
- Iteration 2: reproduced the inverse stale green status after the player left the context that produced a successful plan.
- Iteration 3: bound reachability evidence to the player's block feet cell and current Flight state.
- Iteration 4: cleared both positive and negative evidence before delay handling whenever that context changed.
- Iteration 5: preserved the existing planning budget, target priority, renderer fallback, route geometry, and packet execution behavior.

## Version 1.10.138 Blink Server Attack Envelope Update

- Iteration 1: reproduced a high-difference candidate passing hitbox and configured attack-point checks while vanilla rejected its entity-position distance.
- Iteration 2: locked vanilla's strict visible `<36.0` and obstructed `<9.0` squared-distance boundaries in focused tests.
- Iteration 3: evaluated entity-eye visibility from each candidate using the same 1.12.2 block-ray options as the server.
- Iteration 4: applied the server-equivalent envelope to direct plans, remote candidate selection, and live plan refresh.
- Iteration 5: retained candidate ordering and continued searching other heights and angles instead of sending a knowingly rejected attack.
