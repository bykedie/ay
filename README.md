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
- `autoMine`: mines configured, visible ore blocks within normal reach and selects the fastest hotbar pickaxe.
- `oreVisualizer`: incrementally scans client-loaded chunks and draws configurable colored outlines for each vanilla ore type.
- `creativeTools`: enables commands that create normal items and NBT custom potions in Creative mode.
- `meleeAura`: targets visible players, hostile mobs or animals with normal 1.9+ attack cooldown handling and sword/axe selection.
- `blinkStrike`: experimental extended-range attack that sends a collision-checked sequence of temporary position packets, attacks near the target, then returns along the same path.
- `criticals`: sends a short grounded critical movement sequence before sword/axe attacks.
- `targetVisualizer`: independently draws model-aware target skeletons, boxes and camera rays within a configurable range, using green for visible targets and red for obstructed targets.

`meleeAura` measures its range from the player's eyes to the nearest point of a target's hitbox. Its default is `3.0` blocks and its configurable maximum is `6.0`. `blinkStrike` measures between entity positions and has a separate `3.0`-`200.0` acquisition range (default `12.0`); it is mutually exclusive with `meleeAura`. It advances the server-side position in configurable steps, sends the attack from `2.5` blocks away by default, and retraces the path without moving the local camera. The default geometry moves the server-side position at most `9.5` blocks for a 12-block target, matching the approximate vanilla movement threshold; vanilla also accepts visible attacks below six blocks. Longer settings, modified servers, and anti-cheat plugins may behave differently, so extended-range hits remain experimental and are not guaranteed.

Target and ore visualization both default to `150` blocks and can be configured up to `500`. Ore visualization can only inspect chunks currently loaded by the client. Each vanilla ore has an independent switch and RGB outline color.

All modules and their detailed settings are stored in `config/qazrlegacy.cfg`. The legacy file name, internal mod ID, and `/qazr` command are retained so existing installations keep their settings and key bindings after the Voris Hub rename.

## Controls And Commands

- `Right Shift`: open the module control screen.
- Module toggle key bindings are unassigned by default and can be set in Minecraft's Controls screen.
- Left-click a module to toggle it; right-click it to expand persistent sliders, switches, color editors, and choices below the module.
- Hover the small question-mark icon beside any parameter for its purpose and usage.
- Combat modules have independent player, hostile, animal, peaceful and mod-entity filters, camera rotation, and multi-target limits up to 50. The separate target visualizer has skeleton, box and ray switches.
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

The release artifact is `build/libs/voris-hub-1.6.0.jar`. `verifyRelease` runs unit tests and checks final JAR metadata, required classes, and Java 8 bytecode.

For a development-client smoke test, run `./gradlew runClient` (or `gradlew.bat runClient` on Windows). The build automatically corrects ForgeGradle's known legacydev `Side.BUKKIT` mapping defect and keeps build-time ASM libraries off the Minecraft 1.12 runtime classpath. This only affects the generated development cache, never the release JAR.

## Scope

This is a client utility mod, not a Meteor addon; Meteor does not support Minecraft 1.12.2. Cross-version packet and item-component code was rewritten against Forge 1.12.2 APIs. Use automation only where server rules allow it.
