# snd-access

Accessibility mod for [Slice & Dice](https://store.steampowered.com/app/1775490/Slice__Dice/).

## Keys

| Key                                  | Action                                                                               |
| ------------------------------------ | ------------------------------------------------------------------------------------ |
| Arrow keys                           | Move between controls (rows/columns, table cells)                                    |
| Tab / Shift+Tab                      | Next / previous group (heroes, enemies, buttons, content…) |
| Alt+Up / Alt+Down                    | Previous / next section within a group                                               |
| Home / End                           | First / last control                                                                 |
| Enter                                | Activate (left-click equivalent)                                                     |
| Backspace                            | Details (right-click equivalent: character sheets, item text, delete/detail actions) |
| Ctrl+Up / Ctrl+Down                  | Review: further through the current buffer / back toward where it starts |
| Ctrl+Left / Ctrl+Right               | Review: previous / next buffer                                                       |
| Ctrl+1                               | On anything that concerns a hero or monster: its hp, shields and incoming damage     |
| F1                                   | Keys here: every key that would do something right now; Enter on a row runs it      |
| Left / Right on sliders and choosers | Adjust the value                                                                     |
| Any letters                          | Type-ahead search within the current group                                           |
| Escape                               | Game's own back/close (cancels type-ahead first if one is active)                    |

### Review buffers

Buffers are those of STS2:

| Buffer         | Holds                                                                                          |
| -------------- | ---------------------------------------------------------------------------------------------- |
| control        | The focused control's line, then one line per tooltip: keyword rules, statuses in full         |
| hero / monster | The whole unit the control concerns: hp, targets, all six sides, keyword rules, statuses       |
| items          | What that hero carries, or the focused item: name and tier, then its description               |
| party, enemies | Everyone on a side, one line each, with hp, shields and incoming damage                        |
| log            | What happened — banners, rolls, outcomes, phases, notifications; opens on the latest line     |

## Installing (players)

1. Install the game via Steam.
2. Download `SnDAccessInstaller.exe` from the
   [latest release](https://github.com/amerikrainian/snd-access/releases/latest) and run it.
3. Launch the game normally.

## Building / developing

Requirements: JDK 21, Gradle, the game installed (`dice.jar` is compiled against directly —
no reflection layer).

```
gradle build            # all modules + core unit tests
run-dev.ps1             # build, then launch the game with the mod + dev server attached
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
`devrepl` (JShell, dev only).
