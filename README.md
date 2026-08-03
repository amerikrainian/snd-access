# snd-access

Accessibility mod for [Slice & Dice](https://store.steampowered.com/app/1775490/Slice__Dice/) (v3.2.13, Steam/desktop).
Adds full screen-reader support: every screen — title, combat, rewards, inventory, almanac,
options, leaderboards — is navigable by keyboard and spoken through
[Prism](https://github.com/ethindp/prism) (NVDA, SAPI, etc.). Windows only.

## Keys

The mod adds a virtual cursor over the game's own UI. The game's native hotkeys
(digits 1–9, R reroll, Z undo, I inventory, Escape) keep working.

| Key | Action |
|---|---|
| Arrow keys | Move between controls (rows/columns, table cells) |
| Tab / Shift+Tab | Next / previous group (heroes, enemies, buttons, content…) |
| Ctrl+Up / Ctrl+Down | Previous / next region within a group |
| Home / End | First / last control |
| Enter | Activate (left-click equivalent) |
| Backspace | Details (right-click equivalent: character sheets, item text, delete/detail actions) |
| Space | Extra info (keyword rules in combat, difficulty rules) |
| Left / Right on sliders and choosers | Adjust the value |
| Any letters | Type-ahead search within the current group |
| Escape | Game's own back/close (cancels type-ahead first if one is active) |

In text inputs (rename, scenario names, modifier search) the keyboard goes to the field:
typing echoes, Backspace deletes, Enter submits, Escape cancels.

## Installing (players)

1. Install the game via Steam.
2. Get `prism.dll` (and `prism.h`) from the [Prism releases](https://github.com/ethindp/prism)
   and place them in `third_party/prism/`.
3. Run `scripts/deploy.ps1`. It builds, stages the mod into
   `<game>\mods\snd-access\`, and patches `SliceAndDice.json` so the game's own launcher
   loads it (original kept as `SliceAndDice.json.bak`).
4. Launch the game normally. To uninstall: copy the `.bak` back over `SliceAndDice.json`.

If the game is not in the default Steam location, pass `-GameDir <path>` to the script.

## Building / developing

Requirements: JDK 21, Gradle, the game installed (`dice.jar` is compiled against directly —
no reflection layer), `third_party/prism/prism.dll`.

```
gradle build            # all modules + core unit tests
scripts/run-dev.ps1     # build, then launch the game with the mod + dev server attached
```

Iteration loop (no game restart):

```
gradle :module:build
curl -X POST localhost:8771/reload
```

The dev launch starts a loopback HTTP driver on `127.0.0.1:8771`:
`/gui` (UI graph dump), `/input` (send keys), `/speech` and `/log` (tail with cursors),
`/eval` (JShell against the live game), `/screenshot`, `/reload`, `/health`.
Never enabled in a player deploy.

Modules: `core` (engine-agnostic navigation/speech, unit-tested), `host` (the `-javaagent`:
hooks, Prism, module loader — game-blind), `module` (all feature code, hot-reloadable),
`devrepl` (JShell, dev only). See `CLAUDE.md` for architecture rules and
`docs/snd_accessibility_audit.md` for the per-screen coverage inventory.

## Notes

- Dev launches share the real save data; the mod never writes save state itself.
