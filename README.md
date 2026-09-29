# snd-access

Accessibility mod for [Slice & Dice](https://store.steampowered.com/app/1775490/Slice__Dice/) (v3.2.13, Steam/desktop).
Adds full screen-reader support: every screen — title, combat, rewards, inventory, almanac,
options, leaderboards — is navigable by keyboard and spoken through
[Prism](https://github.com/ethindp/prism) (NVDA, SAPI, etc.). Windows only.

## Keys

The mod adds a virtual cursor over the game's own UI. The game's native hotkeys
(digits 1–9, R reroll, Z undo, I inventory, Escape) keep working.

| Key                                  | Action                                                                               |
| ------------------------------------ | ------------------------------------------------------------------------------------ |
| Arrow keys                           | Move between controls (rows/columns, table cells)                                    |
| Tab / Shift+Tab                      | Next / previous group (heroes, enemies, buttons, content…); goes round at either end |
| Alt+Up / Alt+Down                    | Previous / next section within a group                                               |
| Home / End                           | First / last control                                                                 |
| Enter                                | Activate (left-click equivalent)                                                     |
| Backspace                            | Details (right-click equivalent: character sheets, item text, delete/detail actions) |
| Ctrl+Up / Ctrl+Down                  | Review: further through the current buffer / back toward where it starts (see below) |
| Ctrl+Left / Ctrl+Right               | Review: previous / next buffer                                                       |
| Ctrl+1                               | On anything that concerns a hero or monster: its hp, shields and incoming damage     |
| F1                                   | Keys here: every key that would do something right now; Enter on a row runs it      |
| Left / Right on sliders and choosers | Adjust the value                                                                     |
| Any letters                          | Type-ahead search within the current group                                           |
| Escape                               | Game's own back/close (cancels type-ahead first if one is active)                    |

In text inputs (rename, scenario names, modifier search) the keyboard goes to the field:
typing echoes, Backspace deletes, Enter submits, Escape cancels.

### Review buffers

Moving onto a control speaks one line. Everything else it carries — keyword rules, status
rules, item text, a difficulty's rules — waits in a buffer you step through a line at a time
with Ctrl+Up, and back with Ctrl+Down, so nothing has to be heard in one burst or caught the
first time. Every buffer reads the same way up: review starts on its main line — the control's
own readout, the unit's name and hp, the log's latest event — and Ctrl+Up goes on from there,
to the tooltips, the die sides, the earlier events.
Ctrl+Left/Right switch buffers, speaking the buffer's name and its current line; buffers
with nothing to say are skipped. Moving to another control returns review to its own buffer.

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
   It finds the Steam install, downloads the newest mod version, stages it into
   `<game>\mods\snd-access\`, and patches `SliceAndDice.json` so the game's own launcher
   loads the mod (the original is backed up and restored on uninstall). The same window
   handles updates, repair, and uninstall; run it with `--cli` for a console interface.
3. Launch the game normally.

Installing from source instead: run `deploy.ps1`. It builds, stages the mod
(including the vendored [Prism](https://github.com/ethindp/prism) speech library), and
patches `SliceAndDice.json` (original kept as `SliceAndDice.json.bak`; copy it back to
uninstall). If the game is not in the default Steam location, pass `-GameDir <path>`.

## Building / developing

Requirements: JDK 21, Gradle, the game installed (`dice.jar` is compiled against directly —
no reflection layer). Prism is vendored in `third_party/prism/` (MPL-2.0; licenses included).

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

## Releasing

The installer (`installer/`, Rust + wxWidgets; needs cargo, libclang, and ninja) is
adapted from Rashad Naqeeb's Non-Visual Calculus installer — see
`installer/ATTRIBUTION.md`. To cut a release:

1. Bump `modVersion` in `gradle.properties` and add the matching `## VX.Y.Z` section
   to `CHANGELOG.md` (it becomes the release notes the installer shows players).
2. `build_release.ps1` — builds and stages `releases/SnDAccess-vX.Y.Z.zip`.
3. `build-installer.ps1` — builds `releases/SnDAccessInstaller.exe`
   (`test-installer.ps1` runs its unit tests).
4. Commit, tag `vX.Y.Z`, push the tag, then `create-release.ps1 vX.Y.Z` to
   publish the GitHub release with both assets.

## Notes

- Dev launches share the real save data; the mod never writes save state itself.
- Prism is redistributed under its licenses (MPL-2.0 and dependencies — see
  `third_party/prism/LICENSES/`). Nothing from the game itself is redistributed.
