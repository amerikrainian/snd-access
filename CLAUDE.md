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
- `third_party/` is gitignored (vineflower.jar, prism.dll+h). If missing, prism.dll can be copied
  from `../NonVisualCalculus/third_party/prism/` or `../wotr-access/vendor/`.

## Build & deploy

```
gradle build          # all four modules + core unit tests
gradle :module:build  # just the reloadable module (the hot-reload inner loop)
```

Four Gradle modules; artifact names are fixed (no version suffixes):

| Module    | Artifact           | Target  | Contents                                                                                         |
| --------- | ------------------ | ------- | ------------------------------------------------------------------------------------------------ |
| `core`    | `core.jar`         | Java 8  | contracts, Dispatcher, SpeechPipeline, TextFilter, LineLog, Bridge — pure, unit-tested           |
| `host`    | `snd-host-all.jar` | Java 8  | FAT agent jar (core + Byte Buddy + JNA merged): premain, hooks, Prism, dev server, module loader |
| `module`  | `snd-module.jar`   | Java 8  | module classes ONLY — feature code, reloadable                                                   |
| `devrepl` | `snd-devrepl.jar`  | Java 21 | JShell evaluator; dev classpath only, never shipped                                              |

- **Dev launch:** `scripts/run-dev.ps1` (builds, then runs the game under the system JDK with
  `-javaagent:snd-host-all.jar -Dsnd.dev=1 -Dsnd.module=<module jar> -Djna.library.path=<prism>`
  and `-cp "dice.jar;snd-devrepl.jar"`, cwd = game dir). Never modifies the game install.
- **Player deploy:** `scripts/deploy.ps1` stages jars + prism.dll into `<game>\mods\snd-access\`
  and patches `SliceAndDice.json` vmArgs (backup kept). **Not yet exercised on the bundled OpenJ9
  8 JRE — smoke-test before any release.**
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
  **JShell cannot see module-loader types** (game + core + host only); module internals are
  reached via `ModModule.devCommand` (the `/gui` path) or by adding a devCommand verb.
- `POST /reload` — rebuild-and-swap the module from its freshly built jar, no restart. Responds
  with the reload status plus the full `/module` readout.
- `GET /module` — module class, **load generation**, module jar mtime/age, last reload status,
  **old-loader leak canary** ("collected" = the previous classloader was GC'd; "STILL REACHABLE"
  = a leak — find the reference before adding more state).
- `GET /speech?since=N[&wait=MS]` — everything spoken, as `index: [queue|interrupt] [Source] text`
  with a `cursor:` header; long-polls with `wait`. The tap is upstream of Prism, so it works with
  speech muted (`SND_NO_SPEECH=1` for headless runs — backend skipped, tap still fires).
- `GET /log?since=N[&grep=S]` — the mod's log in-band (same cursor protocol).
- `POST /input` — body is a verb (`up down left right enter escape space tab backspace z r i 1-9`
  or `key:<code>`), driven through the game's own key path (`Screen.mainKeyPress`) — the same
  route the stage listener uses.
- `POST /wait?timeout=MS` — body is a boolean Java expression, compiled once in the eval session
  and evaluated **every frame** on the render thread; returns `true` or `timeout`. Use instead of
  curl sleep-loops.
- `GET /screenshot` — PNG of the current frame; returns the file path to `Read` (it renders
  correctly — use it to _see_ what a sighted player sees).
- `GET /gui` — the module's dev-driver view of the UI (currently: screen + phase; the graph dump
  lands with phase 3/4). `GET /typeinfo?name=<fqcn>` — reflection over app + module loaders.
  `GET /health` — liveness.

Iteration loop for feature code, no game restart: edit `module/`, `gradle :module:build`, then
`curl -X POST localhost:8771/reload`. **Host, core, or devrepl changes need a full restart**
(kill game, `gradle build`, relaunch) — same boundary and same reason as the reference mods: those
load permanently in the app classloader.

Bring-up: `scripts/run-dev.ps1`, then poll
`curl -s --retry 60 --retry-connrefused --retry-delay 1 http://127.0.0.1:8771/health`.

**If `/log` shows "dev server failed to bind"**, a previous game instance still owns the port —
your curls are talking to the OLD process. Kill all java processes and relaunch.

## Architecture

Permanent/reloadable split (verified end-to-end):

- **`core`** (permanent, app loader) — engine-agnostic: the `ModModule`/`HostServices` contracts,
  the **`Dispatcher`** (static fan-out the instrumented game methods call; owns the job queue,
  frame waits, and the module reference), `SpeechPipeline` + `TextFilter`, `LineLog`, the /wait
  `Bridge`. If code decides what words the user hears, it belongs here, unit-tested.
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
  `module.tick()` → host frame hook). Transient-text hooks (banners, popups) come with phase 6.
- The UI model going forward (phases 3+) is the **wotr-access immediate-mode graph** — screens
  declare nodes fresh from live game state each render, focus survives by ControlId identity.
  Port its engine tests as the spec; do not invent a retained-tree/signature design, and do not
  inherit its known frictions: use typed composite keys instead of string-hash ControlIds, and
  one announcement registry for UI and world rather than two parallel ones.

## Conventions & invariants

- **All speech goes through `SpeechPipeline`** (`snd.core.speech`); never call the Prism backend
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
