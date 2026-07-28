# Voris Hub

Voris Hub is a Forge 1.12.2 client-side utility mod with configurable combat, automation, visualization, and Creative-mode tools.

Maintainer handoff notes are in [`HANDOFF.md`](HANDOFF.md).

## Requirements

- Minecraft 1.12.2
- Forge 14.23.5.2860
- Java 8

The included wrapper pins Gradle 7.6.1 and ForgeGradle 5.1.77.

## Modules

- `autoGg`: detects local-player kills from chat death messages and sends a delayed configurable response.
- `autoReply`: responds to a selected player's common chat format with rate limiting.
- `autoMine`: mines selected ore presets from the shared ore cache, locks each connected vein into a nearest-first stable order, directly mines any ray-visible ore inside vanilla reach, optionally places one stable assist block for overhead ore, pauses when you move manually, and supports per-ore target counts.
- `autoBridge`: places a solid block under or ahead of the next walking position, including jump/fall gaps, with configurable lookahead, down-scan, delay, and anti-foot-collision behavior.
- `oreVisualizer`: incrementally scans client-loaded chunks and draws configurable colored outlines for each vanilla ore type, merging adjacent ore blocks into outer boundary wireframes.
- `creativeTools`: enables commands that create normal items and NBT custom potions in Creative mode.
- `meleeAura`: targets visible players, hostile mobs, animals or peaceful entities with normal 1.9+ attack cooldown handling and sword/axe selection.
- `blinkStrike`: experimental extended-range attack that sends a collision-checked sequence of temporary position packets, attacks near the target, then returns along the same path.
- `flight`: WWE-style static, vanilla, and Hypixel movement modes with configurable speed and guarded sneak-to-land behavior.
- `criticals`: sends a short grounded critical movement sequence before sword/axe attacks.
- `targetVisualizer`: independently draws model-aware target skeletons, boxes and camera rays within a configurable range, using green for visible targets and red for obstructed targets.

`meleeAura` measures its range from the player's eyes to the nearest point of a target's hitbox. Its default is `3.0` blocks and its configurable maximum is `6.0`. `blinkStrike` measures between entity positions and has a separate `3.0`-`200.0` acquisition range (default `12.0`); it is mutually exclusive with `meleeAura`. Targets inside normal interaction reach use Minecraft's regular controller attack without position packets. Remote attacks advance the server-side position in configurable collision-checked steps, attack from `2.5` blocks away by default, and retrace the accepted path without moving the local camera. Multi-target scanning still considers up to 50 entities, but only one remote packet excursion is executed per attack cycle to avoid correction storms and screen flashing. Controlled flight is accepted as an airborne origin, and high or low targets are first approached at the player's current flight height. Both combat modules expose a selectable attack body point (`head`, `chest`, `legs`, or `feet`) for rotation and aiming behavior. Longer settings, modified servers, and anti-cheat plugins may behave differently, so extended-range hits remain experimental and are not guaranteed.

Target and ore visualization both default to `150` blocks and can be configured up to `500`. Ore visualization can only inspect chunks currently loaded by the client. Each vanilla ore has an independent switch and RGB outline color. Adjacent ore blocks are rendered as a shared outer wireframe so internal nine-grid style lines are skipped. Both visualizers can show compact nearby-count HUD items, sharing the same selectable screen corner. Ore scanning is chunk-cached, distance-culled, and reused by auto mining to avoid repeated large cube scans.

Auto mining uses `pathRange` for both target acquisition and route planning. Each connected vein receives nearest-first invisible sequence labels when acquired, then keeps that order so adjacent ore cannot steal the active target. Before planning or continuing a route, any ore inside vanilla reach and actually hit by a center or exposed-face ray sample is mined directly, including partially exposed and overhead ore. Only destinations that were actually searched and found unreachable receive a retry cooldown; stale vein locks are released after the player leaves their candidate range. Manual movement immediately cancels the old route and block-breaking action while preserving the player's own motion. The optional `辅助垫方块` setting is off by default; when enabled, it stays bound to the original player column, clicks the supporting face center, waits for server placement confirmation, then revalidates and mines the overhead ore. Each ore type has its own target count slider, where `0` means unlimited.

For a guarded landing, keep `flight` enabled and hold Sneak until the player touches the ground, then disable flight. Descent is capped without repeatedly sending fake grounded packets, horizontal direction input remains active, fall accumulation is cleared locally, and one final grounded position is sent only when the detected collision surface is reached. This reduces fall damage but still depends on the server accepting the final movement packet.

All modules and their detailed settings are stored in `config/qazrlegacy.cfg`. The legacy file name, internal mod ID, and `/qazr` command are retained so existing installations keep their settings and key bindings after the Voris Hub rename.

## Controls And Commands

- `·` (grave key below Esc): open the module control screen.
- Module toggle key bindings are unassigned by default and can be set in Minecraft's Controls screen.
- Left-click a module to toggle it; right-click it to expand persistent sliders, switches, color editors, and choices below the module.
- Hover the small question-mark icon beside any parameter for its purpose and usage.
- The Tools panel has a clickable `游戏模式` row for switching between Survival and Creative; multiplayer servers still require the corresponding command permission.
- `创造工具` unlocks `/qazr give` and `/qazr potion` for creating items and custom potions while in Creative mode; its question-mark tooltip repeats this requirement in the panel.
- Combat modules have independent player, hostile, animal, peaceful and mod-entity filters, camera rotation, attack body point, and multi-target limits up to 50. The separate target visualizer has skeleton, box, ray and nearby-count switches.
- Auto mine exposes per-ore switches and target counts, path range, mining delay, manual-movement pause, route visualization, and an optional overhead-ore assist-block switch. Auto bridge exposes lookahead, down-scan, place delay, and anti-foot-collision settings.
- Auto GG and auto reply expose five editable random message slots; blank slots are skipped and `{player}` inserts the matched player name.
- `/qazr status`
- `/qazr toggle <module>`
- `/qazr reload`
- `/qazr range <meleeAura|blinkStrike> [blocks]`
- `/qazr give <item-id> [count] [metadata]`
- `/qazr potion <effect-id> <level> <seconds> [splash]`

Key bindings can be changed in Minecraft's Controls screen. Item and potion commands require Creative mode and the `creativeTools` module.

## Build And Verification

On Windows:

```powershell
.\gradlew.bat clean verifyRelease
```

On Linux or macOS:

```bash
./gradlew clean verifyRelease
```

The release artifact is `build/libs/voris-hub-1.10.52.jar`. `verifyRelease` runs unit tests and checks final JAR metadata, required classes, and Java 8 bytecode.

For a development-client smoke test, run `./gradlew runClient` (or `gradlew.bat runClient` on Windows). The build automatically corrects ForgeGradle's known legacydev `Side.BUKKIT` mapping defect and keeps build-time ASM libraries off the Minecraft 1.12 runtime classpath. This only affects the generated development cache, never the release JAR.

## Scope

This is a client utility mod, not a Meteor addon; Meteor does not support Minecraft 1.12.2. Cross-version packet and item-component code was rewritten against Forge 1.12.2 APIs. Use automation only where server rules allow it.
