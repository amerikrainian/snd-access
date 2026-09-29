# SnDAccess — Claude Code Instructions

SnDAccess (repo `snd-access`) makes **Slice & Dice** (by tann) playable by blind users. Speech is
the sole interface, so if something fails silently, speaks stale data, or omits information, the
player has no way to know. A logged failure is actionable; a silent one is invisible.

The game is a dice-battler roguelike: 2 real screens (title, dungeon) plus a deep modal stack
(book/almanac, choice phases, dialogs). The full UI inventory is
`docs/snd_accessibility_audit.md` — read the relevant section before touching a screen.

## Game & environment

- Game: **Slice & Dice v3.2.13**, plain unobfuscated Java (libGDX + LWJGL3, scene2d UI, Bullet
  physics for the 3D dice). All game code is in `dice.jar` under `com.tann.dice.*`; **we compile
  against it directly** (`compileOnly files("${gameDir}/dice.jar")`) — no proxies, no reflection
  layer. Game updates surface as build errors, which is the best possible failure mode.
- Install: `C:\Program Files (x86)\Steam\steamapps\common\Slice_n_Dice`. Override with the
  `gameDir` Gradle property (default in `gradle.properties`).
- Launcher: `SliceAndDice.exe` is a packr-style shim that reads **`SliceAndDice.json`**
  (`jrePath`, `classPath`, `mainClass`, `vmArgs`) and boots the JVM via JNI. Main class:
  `com.tann.dice.desktop.DicetopLauncher` (15 lines). The bundled JRE is **OpenJ9 Java
  1.8.0_282** — shipped mod bytecode targets `--release 8`.
- The game runs fine under **Temurin JDK 21** (verified) — the dev loop uses the system JDK, which
  is what makes JShell eval possible. Steam init soft-fails outside Steam ("Could not initialise
  Steam") and the game continues.
- The game defaults to **non-continuous rendering** (`OptionLib.RENDER_MODE`): frames tick only on
  input or `requestRendering()`. The dev server forces continuous rendering while up (re-asserted
  every frame — the game re-applies its own option during load).
- **Save/settings live in the game dir, disguised as `xsrvc.dll`** (see
  `Control.getMainFileString()`; `Gdx.app.getPreferences("xsrvc.dll")` resolves there). Dev
  launches share the player's REAL save data — be careful with destructive actions while testing.
- Speech backend is **Prism** (https://github.com/ethindp/prism), bound via JNA against
  `prism.dll`, vendored in `third_party/prism/` (with `prism.h` — the authoritative API for OUR
  dll version) and loaded via `-Djna.library.path`.

## Decompiled reference (gitignored, regenerable)

- `game/src/` — full Vineflower decompile of `com.tann.dice.*` (1,268 files). Look up any game
  type/method here before guessing; unlike a Cpp2IL dump, method bodies are real.
- `game/assets/` — `lang/en.json` (every UI string, flat key→value), `guide/`, `misc/`.
- Regenerate after a game update: extract `dice.jar` with `jar -xf`, then
  `java -jar third_party/vineflower.jar --only=com/tann --folder <classes> game/src`.
- `third_party/prism/` (prism.dll+h, LICENSES) is COMMITTED — players must not need to fetch it.
  The rest of `third_party/` (vineflower.jar) stays gitignored.

## Build & deploy

```
gradle build          # all five modules + the contracts, core and module unit tests
gradle :module:build  # the reloadable unit: core + module (the hot-reload inner loop)
```

Five Gradle modules; artifact names are fixed (no version suffixes):

| Module    | Artifact           | Target  | Contents                                                                                         |
| --------- | ------------------ | ------- | ------------------------------------------------------------------------------------------------ |
| `contracts` | (merged into host) | Java 8  | PERMANENT, package `snd.contracts`: ModModule/HostServices, Dispatcher, SpeechPipeline, TextFilter, SndLog/LineLog, Bridge — pure, unit-tested |
| `core`    | (merged into module) | Java 8  | RELOADABLE, package `snd.core`: graph, nav, buffers, input, loc, search — pure, unit-tested             |
| `host`    | `snd-host-all.jar` | Java 8  | FAT agent jar (contracts + Byte Buddy + JNA merged): premain, hooks, Prism, dev server, module loader |
| `module`  | `snd-module.jar`   | Java 8  | module classes + core's — feature code, reloadable; NEVER contracts classes                     |
| `devrepl` | `snd-devrepl.jar`  | Java 21 | JShell evaluator; dev classpath only, never shipped                                              |

- **Dev launch:** `run-dev.ps1` (builds, then runs the game under the system JDK with
  `-javaagent:snd-host-all.jar -Dsnd.dev=1 -Dsnd.module=<module jar> -Djna.library.path=<prism>`
  and `-cp "dice.jar;snd-devrepl.jar"`, cwd = game dir). Never modifies the game install.
- **Player deploy:** `deploy.ps1` stages jars + prism.dll into `<game>\mods\snd-access\`
  and patches `SliceAndDice.json` vmArgs (backup kept; restore = copy the .bak back). Verified on
  the bundled OpenJ9 8 JRE. Two shim facts learned the hard way: the shim's JSON parser rejects a
  UTF-8 BOM and **exits silently** (the script writes BOM-free), and the shim loads dice.jar in
  **its own classloader**, not the system loader — so the host never links against game classes
  (game-typed code lives in the module, whose loader bridges through `Dispatcher.gameLoader()`),
  and `/eval` (JShell) cannot see game classes in a deployed dev launch.
- **Version:** `modVersion` in `gradle.properties` names the release zip and is stamped into the
  module jar's manifest (`Implementation-Version`, read by `ModVersion`). The greeting speaks it,
  and the launch update check (`UpdateChecker`, generation 1 only) compares it against GitHub's
  latest release. `SND_UPDATE_URL` overrides that feed — a `file:` URL to a
  `{"tag_name":"v9.9.9"}` payload exercises the announcement without publishing anything.
- From the Bash tool, launch java with `MSYS2_ARG_CONV_EXCL="*"` or the `-javaagent:`/`-cp` args
  get path-mangled. Kill the game with PowerShell `Get-Process java | Stop-Process -Force`
  (note: this also kills the Gradle daemon — it restarts itself).

## Dev driver (loopback HTTP, dev only)

Enabled by `-Dsnd.dev=1` (run-dev sets it), binding **127.0.0.1:8771** (`-Dsnd.dev.port`).
Everything game-touching marshals onto the render thread via `Dispatcher.post`; jobs also fire
`requestRendering()` so an idle loop wakes. Drive with `curl`:

- `POST /eval` — body is Java source, compiled by **JShell** (in-process "local" engine) and run
  on the render thread against the live game. Session state persists across calls. Returns
  printed output, `[compile]`/`[exception]` diagnostics, `=> value`, then a `speech:` section
  with whatever the mod spoke as a consequence (waits for a quiet window; `?speech=0` skips,
  `?settle=MS` tunes, default 250) — act-then-listen in one request.
  **JShell cannot see module-loader types** — that is the module AND core (`snd.core.graph`,
  `nav`, `input`, `loc`, `search`); it sees the game, the contracts and the host. Those internals are
  reached via `ModModule.devCommand` (the `/gui` path) or by adding a devCommand verb.
  **The GL context is NOT current in eval bodies**: JShell's local engine runs each snippet on a
  per-invocation worker thread (the render thread waits on it — game state is safe to touch, but
  any code that triggers GL work hard-aborts the JVM; `OptionLib.LANGUAGE.setValue` →
  `Main.setupScale` is a known case). Wrap such calls in `snd.contracts.Dispatcher.post(...)` so they
  run on a real render frame, or drive them via `/input`.
- `POST /reload` — rebuild-and-swap the module from its freshly built jar, no restart. Responds
  with the reload status plus the full `/module` readout.
- `GET /module` — module class, **load generation**, module jar mtime/age, last reload status,
  **old-loader leak canary** ("collected" = the previous classloader was GC'd; "STILL REACHABLE"
  = a leak — find the reference before adding more state).
- `GET /speech?since=N[&wait=MS]` — everything spoken, as `index: [queue|interrupt] [Source] text`
  with a `cursor:` header; long-polls with `wait`. The tap is upstream of Prism, so it works with
  speech muted (`SND_NO_SPEECH=1` for headless runs — backend skipped, tap still fires).
- `GET /log?since=N[&grep=S]` — the mod's log in-band (same cursor protocol).
- `POST /input` — body is a verb (`up down left right enter escape space tab backspace z r i f1
  1-9` or `key:<code>`; an action id of the key table — `glance.vitals`, `nav.UP` — presses
  whichever chord is bound to it, the way to drive a modified key; a `ctrl+`/`shift+`/`alt+`
  prefix instead resolves the chord with those modifiers held, to test the binding itself — the mod's
  path only, since the game reads its modifiers from the live keyboard), driven through the game's own key path (`Screen.mainKeyPress`) — the same
  route the stage listener uses.
- `POST /wait?timeout=MS` — body is a boolean Java expression, compiled once in the eval session
  and evaluated **every frame** on the render thread; returns `true` or `timeout`. Use instead of
  curl sleep-loops.
- `GET /screenshot` — PNG of the current frame; returns the file path to `Read` (it renders
  correctly — use it to _see_ what a sighted player sees).
- `GET /gui` — the module's dev-driver view of the UI (currently: screen + phase; the graph dump
  lands with phase 3/4). `GET /typeinfo?name=<fqcn>` — reflection over app + module loaders.
  `GET /health` — liveness.
- Any other path is the module's to answer: `ModModule.devCommand(<path>, <body>)`, so a new
  module dev verb needs no host change and no restart. `GET /buffers` — every review buffer with
  its live lines, the review cursor marked.

Iteration loop for feature code, no game restart: edit `module/` or `core/`, `gradle :module:build`
(it rebuilds core and merges it into the module jar), then `curl -X POST localhost:8771/reload`.
**Host, contracts, or devrepl changes need a full restart** (kill game, `gradle build`, relaunch) —
same boundary and same reason as the reference mods: those load permanently in the app
classloader.

Bring-up: `run-dev.ps1`, then poll
`curl -s --retry 60 --retry-connrefused --retry-delay 1 http://127.0.0.1:8771/health`.

**If `/log` shows "dev server failed to bind"**, a previous game instance still owns the port —
your curls are talking to the OLD process. Kill all java processes and relaunch.

## Architecture

Permanent/reloadable split (verified end-to-end):

- **`contracts`** (permanent, app loader) — what the host links against and what must keep one
  identity across reloads: the `ModModule`/`HostServices` contracts, the **`Dispatcher`** (static
  fan-out the instrumented game methods call; owns the job queue, frame waits, and the module
  reference), `SpeechPipeline` + `TextFilter`, `SndLog`/`LineLog`, the /wait `Bridge`. Package
  `snd.contracts` (`.speech`, `.util`, `.dev`): the package name IS the boundary — an import of
  `snd.contracts.*` is a dependency that costs a restart to change, and permanent code importing
  `snd.core.*` is wrong on sight (and a compile error: the host depends on `:contracts` alone).
  Keep it small.
- **`core`** (reloadable, module loader) — engine-agnostic mod logic: the graph engine, the
  navigator and screen stack, the key table (`snd.core.input`), **`Loc`** (the mod's own strings,
  resolved from flat JSON tables), type-ahead. If code decides what words the user hears, it
  belongs here, unit-tested. Its classes ship inside `snd-module.jar` and load with the module, a
  fresh copy per generation — so its statics (`Loc` tables, the announcer's wording hooks) are
  per-generation and the module installs them in `load`. Nothing permanent may link against it:
  the host depends on `:contracts` alone, and `SndModule.load` refuses to start if core resolved
  from any loader but its own (a permanent copy would shadow every rebuilt one).
- **`host`** (permanent) — only what can never reload: `SndAgent` premain + Byte Buddy hook
  install, `PrismBackend` (native handle), `ModuleLoader`, `DevServer`, `GameDriver` (the host's
  few direct game touches). Keep it minimal; every line here costs a restart to change.
- **`module`** (reloadable) — **day-to-day feature work goes here.** Entry class is fixed:
  `snd.module.SndModule`. Loaded from a temp COPY of the jar (Windows file locks) into a fresh
  child `URLClassLoader`; reload is load-new → swap → dispose-old → close+delete, so a broken
  build leaves the running module untouched. A module must own no native handles and register
  nothing permanent, or the leak canary will catch it.
- **The no-leak invariant:** Byte Buddy `Advice` inlined into game methods references ONLY the
  `Dispatcher` (see `FrameAdvice`). Never reference a module type from advice code — that creates
  an edge from a permanent class into the collectible loader and pins every old module in memory.
  Hooks are installed once by the host; a reload swaps one volatile reference.
- Current hooks: `Main.render` → `Dispatcher.frame()` (the pump: drain jobs → poll frame-waits →
  `module.tick()` → host frame hook); and the game's transient text, all into
  `Dispatcher.transientText()` (a bounded queue the module's BannerWatcher drains and speaks):
  `AbilityHolder.showInfo` + `TargetingManager.showError` (half-second banners, red error
  flashes), `EntPanelCombat.addMessage` + `addSpeechBubble` (the floating words over a panel —
  "dodged", "immune", "petrified" — and hero chatter, prefixed with the panel's entity), and
  `AbilityHolder.addWisp` (mana gains, discards). Those are the game's own `TextEvent` /
  `ChatStateEvent` / `SnapshotEvent` emit points, so modded text events come through too.
- The UI model going forward (phases 3+) is the **wotr-access immediate-mode graph** — screens
  declare nodes fresh from live game state each render, focus survives by ControlId identity.
  Port its engine tests as the spec; do not invent a retained-tree/signature design, and do not
  inherit its known frictions: use typed composite keys instead of string-hash ControlIds, and
  one announcement registry for UI and world rather than two parallel ones.

## Conventions & invariants

- **All speech goes through `SpeechPipeline`** (`snd.contracts.speech`); never call the Prism backend
  or JNA directly. All logging goes through `SndLog`; no bare `System.out`.
- **No silent failures.** The pump, hooks, and reloads fail invisibly unless logged: every catch
  logs what failed and where; no empty catches; no catch-and-return-default without logging.
  `Dispatcher` throttles repeated tick failures (first 3, then quiet) — keep that pattern.
- **Never cache game state.** Re-query the game when a value is needed; the only acceptable
  "cache" is a reference to a live game object read at speech time. Combat truth is the
  `FightLog` snapshot system (`Temporality.Present` vs `.Future` — the damage preview); read
  state from the model, not from scene2d actors, which the game rebuilds constantly.
- Never interrupt existing speech unless an action supersedes it (navigation). Default to queued.
- Text through `TextFilter` (it strips the game's `[green]`/`[cu]`/`[n]` markup); game strings
  come from the game (all localizable text is in `lang/en.json` keys via `Main.t()`) — reuse
  them, don't hardcode English copies of game text.
- **Two localization channels, don't cross them.** The mod's OWN strings (role words, glue
  phrases) resolve through `Loc` ("ui" table, `module/src/main/resources/locale/<lang>/`;
  English is the always-loaded fallback, and the module follows the game's live language by
  per-frame poll — `Locales.tick`). GAME text read from the model (`Mode.getName()`, item and
  side descriptions) is English source the game translates at display time — route it through
  `GameText.t` (= the game's `Main.t`) before speaking. Actor text (`TextWriter.text`) is
  already translated at set time; never bridge it twice.
- **Every key of the mod's own is bound in one table** (`SndKeys`, over `snd.core.input`):
  `SndInput` resolves presses through it (most specific chord wins; held modifiers no chord asks
  for are ignored), the key help reads labels and spoken chords from it, and the dev driver drives
  it. Never add a keycode check anywhere else. A handler key states where it applies with
  `.when(...)` — that one predicate gates the press and the help row. A handler key that does not
  apply does nothing, silently, and is still consumed: fallen through, Ctrl+1 is the game's 1.
  The Ctrl tiers of the digits are the glances' (Ctrl+1 = the hp display of the unit the focused
  control concerns); the unassigned ones are bound to a silent placeholder for the same reason.
  Ctrl+arrows review the buffers; the section jump is Alt+Up/Down.
- **The game's hotkeys are never in the table** — they are the game's, reached by fall-through. A
  screen offers the ones live in its state from `AccessScreen.keys()`, gated on what the game's
  own `keyPress` checks and labelled with the game's words (`GameKeys`).
- **Review buffers** (`snd.core.buffers`, roster in `module/Buffers`; ported from guildrun): what a
  control carries beyond its focus line is stepped line by line, not heard in one burst.
  Ctrl+Left/Right switch buffers (speaking "name: current line"), Ctrl+Up steps THROUGH a buffer and
  Ctrl+Down back (an edge re-reads): review lands on the buffer's home line — a source's first
  line, or the last for a `followLatest` log — and "next" is away from it. That orientation is
  `Buffer`'s business alone; a source lists its lines in natural order, head first or oldest
  first, and never reverses them. In cycling order: **control** (the focused node's head line — never the role word or
  the position — then its `TOOLTIP` parts and `NodeVtable.details`, repeats of the head folded),
  **hero** / **monster** (the whole unit the control concerns: `UnitLines`), **items** (that hero's,
  or the focused bag item), **party** and **enemies** (one hp line per unit), **log** (what
  happened; follows its latest line). Empty buffers are skipped; every source is re-read on every
  keypress (never cache lines); a focus change re-homes review to the control buffer. Conventions:
  a tooltip is ONE line, never several joined — helpers return `List<String>`. There is no
  read-the-tooltip key: whatever a control carries beyond its focus line goes in its `details`
  (or the unit's `UnitLines`), never behind a key that speaks it as a burst; a node says what it concerns with `NodeVtable.subject` (an
  `Ent`, an `Item`) and the subject-fed buffers and the glances follow it from any screen.
- **What happened in a fight is read from the FightLog, never from an effect's name.**
  `CommandWatcher` speaks every command the log resolves — the player's as applied, a monster's
  as its animation lands (`Command.getImpacted`), the turn's Start/EndTurn ticks — and what one
  did is `CombatChanges`: the diff of the log's own before/after snapshots across EVERY
  combatant (hp, the blocked-damage and poison counters, shields, max hp, statuses by
  `Personal.treatAsIncoming`, death/flight/return, who joined). Adding a case for a particular
  effect or keyword there is the wrong fix: a mechanic that matters moves something in that
  diff, and one that doesn't shows as one of the game's text events (the transient-text hooks).
  The log archives a turn's commands (`pastCommands` → `commandHistory`) in the tick the last
  one finishes — read the archive's tail or end-of-turn deaths are never seen. `PhaseWatcher`
  opens the player's first phase of a turn with `CombatScreen.enemyIntents()`.
- **Definitions are the game's or they are absent.** `Terms` gives a keyword's rules and its
  almanac extra rules, for the keywords a side/ability DISPLAYS (`Eff.getKeywordsForDisplay`)
  and the ones a status, trait or item REFERENCES (`getReferencedKeywords` — what the game's own
  info panels draw keyword boxes from), plus the almanac glossary's entries where a line uses
  the term. Never write a definition of our own: the game defines "overkill" nowhere, so
  neither do we. Which statuses a unit lists is the game's sheet rule
  (`UnitLines.sheetPersonals`: `showInDiePanel()`, a trait only while `visible`), not a test of
  ours.
- **An event is spoken through the `EventLog`** (`events.say`), not the pipeline directly: a
  banner, a roll's results, a die's outcome, a phase turning over, a notification are heard once
  and gone, and the log buffer is the only way back to them. Echoes of the player's own
  navigation (focus readouts, "selected", typed characters) are not events.
- **Selection follows focus on tabs.** A `ControlTypes.TAB` node is opened (its `onActivate`) by the
  navigator when a move the player made lands on it — arrows, Home/End, Tab, type-ahead — never
  by focus merely landing (a screen opening). So a tab does not announce "selected": it declares
  `NodeVtable.selected` instead, the silent form of a SELECTED part, which is what makes entering
  the group land on the open tab (`KeyGraph.isSelected`). Controls where selecting is a decision
  (the title's mode buttons) are buttons, not tabs, and keep Enter and their spoken "selected".
- **The generic walk knows the game, never a place.** `ActorNodes` holds two kinds of rule: what
  is true of the game's UI everywhere (a `StandardButton` is a button, the innermost clickable
  actor wins, `DipPanel.makeTopPanelGroup`'s title-over-body shape is a titled section), and
  typed adapters keyed on a game class, enum or static constant (`ItemLedgerView` → its item, a
  party-layout card → the `PartyLayoutType` its click listener holds, `Images.cog`). Anything
  that infers meaning from how ONE place is drawn — a child count or order, a position, a
  colour, a sibling comparison, a literal caption — does not go in the walk, where it runs on
  every screen and fails without knowing it failed. Read the game's builder for that place
  (`game/src`), then either give the place its own builder at the seam that knows where it is
  (`BookScreen.buildContent` by tab identifier, `GameModalScreen` by modal class or the game's
  own marker, `DialogPhaseScreen` by phase) — `LedgerNodes` is the model: WHICH tiles exist and
  in what order is the game's actors, WHAT one is comes from the domain object it holds, which
  also keys the node (`ControlId.referenced(type, CompositeKey…)`, never `actorId`), and it
  returns false for content it does not recognise so the walk still reads it
  (`BookPage.showThing` swaps a tab's content without changing the tab) — or, when the place is
  anonymous `Pixl` output with nothing typed inside, have the seam hand the walk an
  `ActorNodes.Place`: that place's shorthand captions (`glyphs`: "fs" is "fullscreen" in the
  cog menu and nowhere else) and how it marks a chosen button. A builder that expects something
  the game adds unconditionally logs once when it is missing (builds run every frame).
- **A chosen button among plain buttons is told by colour, where the place says so.** The game
  builds choose-one rows out of `StandardButton`s and keeps which one is chosen in no field —
  only in the arguments the page was built with — so it is read off the colour:
  `ChosenMark.LIGHT_BORDER` on the almanac's Modifier tab and in the leaderboard picker (the
  modal the game names `leaderboard_modal`), `LIGHT_CAPTION` among `[grey]` ones on the TextMod
  tab. No place, no "selected": the custom-mode magnifier is built with a light border too.
- **Focus whose node vanished stays in its Tab-stop.** Generic actor nodes are keyed per actor
  instance, and the game answers many buttons by rebuilding the page they sit on (a filter, a
  section switch) — every id in the stop changes at once. `KeyGraph.reconcile` then lands on the
  stop's node at the place focus held (`GraphState.lastStopKey`/`lastStopIndex`), and only leaves
  the stop when the stop itself is gone.
- **A pushed panel with nothing to operate is not a dialog.** The game answers many gestures by
  pushing a small bordered panel of text (`Screen.pushAndCenter`: "UI scaling factor", an
  achievement's description). `GameUi.activate`/`info` notice a text-only panel arriving on the
  modal stack, speak it where the player stands, pop it, and keep it for that control's buffer
  (`GameUi.infoLines`). A panel with anything to click stays a modal, read by `GameModalScreen`.
- **Escape closes one panel, not all of them.** The game's Escape is `Screen.popAllMedium` —
  everything at once, which from a details panel over the settings lands on the dungeon. A
  screen reading a stacked panel answers `onCancel` with `GameUi.popTopModalOnly()` (what a click
  outside the panel does); with one level up, or a panel Escape doesn't close, the game's Escape
  applies. A screen whose panel can be covered (`BookScreen`) stays active while covered
  (`GameUi.modalOpen`), one layer under the modal reader, so closing the cover lands back on the
  entry that opened it.
- **The key help (F1) declares nothing of its own.** It lists the screen's `keys()`, the handler
  keys that are available, and the navigator keys the dry run (`GraphNavigator.wouldHandle`)
  answers, at the moment it opens. `wouldHandle` mirrors `onAction` decision for decision —
  change them together.
- Only commit when asked. Gitignored: `game/`, `third_party/`, Gradle build dirs.

## Gotchas

- **The prism.h in `third_party/` is the truth for error codes.** This dll's `PrismError` enum
  differs from the C# reference mods' (e.g. `ALREADY_INITIALIZED` = 15 here, 1 there);
  `prism_registry_create_best` returns an already-initialized backend. JNA note: C `bool`
  parameters are bound as `byte`.
- **The agent jar is not on `java.class.path`.** `-javaagent` appends it to the system classloader
  only; anything that builds a classpath from the property (JShell did) must add the agent jar
  explicitly (see `JShellEvaluator.shell()`).
- **`Main.getCurrentScreen()` is null until the game's frame-2 load**; the pump runs from frame 1.
  Module code guards for a not-yet-loaded game (see `SndModule.tick`).
- **The game rebuilds aggressively**: the whole title screen on option/custom-mode changes, the
  input multiplexer + stage in `Main.setupScale` (window resize), the top button row every turn.
  Never key anything on actor identity; key on domain objects (that's what the graph's ControlId
  is for).
- **"Can this be clicked" is `GameUi.isClickable`, not `hasTannListener`.** Nearly everything
  clickable carries the game's `TannListener` — except the almanac's monster and item tiles that
  are NOT locked, which get a plain libGDX `ClickListener` (`LedgerUtils`, the only two in the
  game). Tested by `TannListener` alone, the generic reader showed the locked tiles and silently
  skipped every monster and item the player had found: a portrait with no text and, to that test,
  nothing to press. `GameUi.activate` reaches a `ClickListener` with its synthesized click.
- The in-game combat hotkeys (1–9, QWERTY, R, Z, Space/Enter, Tab-hold) are real and must keep
  working when our navigator arrives — see the audit §Phase 12 for the complete existing map.
- `xsrvc.dll` in the game dir is the save file, not a library. Don't delete it; remember dev runs
  mutate the player's real progress.
- Byte Buddy retransform needs `Can-Retransform-Classes: true` in the agent manifest and the fat
  jar needs `Multi-Release: true` (both set in `host/build.gradle` — keep them when touching the
  jar task).

## Common LLM Antipatterns

### Comments and docs: state what is, not what isn't

Comments and documentation describe the current state and why — not the change history, the
absence of something, or a path not taken. Consider whether a comment is needed at all.

**WRONG**: `// Removed the old polling loop. Now x does y.`
**WRONG**: `// We don't use Gdx.app.postRunnable here` (documents a non-thing)
**CORRECT**: `// Runs on the render thread via Dispatcher.post`

Prescriptive rules and API contracts ("never call the backend directly") and a what-happens fact
that justifies an instruction ("the game re-applies its render option during load; re-assert every
frame") state what to do and are fine.

### Defensive null handling

Excessive validation hides bugs. Only null-check where null is a legitimate, expected state (game
not loaded yet, `FirstOrDefault`-style lookups, public API boundaries). Let code crash otherwise —
a crash is visible and logged; a silently swallowed null is not. Trust private callers.

### No throwaway dev hacks

Never hack a temporary bypass into the tree to dodge a proper reload or restart. The hot-reload
(`gradle :module:build` + `POST /reload`) and rebuild+relaunch loops are cheap and meant to be
used normally. To toggle a gated dev feature, set its real flag (`-Dsnd.dev`, `SND_NO_SPEECH`)
and reload or relaunch. Keep gates honest.
