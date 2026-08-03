# Slice & Dice: Comprehensive Accessibility Audit

## Every User-Facing Mechanic and Interface Element

*Ordered by Encounter Sequence*

> **Note:** This document catalogues every screen, panel, popup, menu, and interactive element a player encounters in Slice & Dice (v3.2.13, desktop/Steam build). It is organized in the order a player first encounters each element, progressing from launch through a run, between-fight screens, run end, and the meta/reference screens. The goal is to identify every component that must be made accessible for a blind player using a screen reader.

> **Scope:** Based on decompiled source (`dice.jar` → `game/src/com/tann/dice/`, decompiled with Vineflower; not committed). Class names are cited so mod work can anchor to real code. This document describes what exists in the UI, not how to make it accessible.

> **Status key:** DONE = fully accessible via SnDAccess, PARTIAL = accessible with minor gaps, NOT STARTED = no coverage, N/A = not applicable.

---

# Architecture Primer (read first)

Slice & Dice is a libGDX game. Everything on screen is a scene2d `Actor` drawn into a low-res framebuffer that is integer-scaled up; there is **no semantic UI model** — no names, roles, states, focus, or tab order. Key facts that shape the whole audit:

- **Screens** (`screens/Screen.java`, abstract): `TitleScreen`, `DungeonScreen` (all gameplay), `PauseScreen` (mobile lifecycle artifact), plus dev-only `TestScreen`/`RollScreen`. `Main.setScreen()` swaps them. One screen at a time; everything else is pushed panels.
- **Modal stack**: `Screen.push(actor, blocker, blockerPops, selfPops, alpha)` with `popAllLight/popSingleMedium/popAllMedium`. Most detail panels are "light" (click-through dismiss); menus are "medium" (ESC dismisses).
- **The two pointer verbs** (`util/listener/TannListener`): `action()` = left-click/tap; `info()` = **right-click or 221 ms long-press**. Virtually every explanation, tooltip, and detail panel in the game is reachable *only* through `info()`. On desktop, long-press only works if the `SMARTPHONE_CONTROLS` option is on.
- **Keyboard**: a single global hook (`Main.setupScale` → `stage.addListener` → `Screen.mainKeyPress(keycode)`). No focus traversal, no Enter-to-activate on buttons, no arrow-key navigation anywhere. ESC is the only universal key (pop modals, else open the cog menu). A useful set of in-combat hotkeys exists (see Phase 12) but menus are effectively mouse-only.
- **3D dice**: hero/monster dice are physical Bullet-physics objects (`statics/bullet/BulletStuff`) rendered outside scene2d and picked with a 3D ray from raw mouse coordinates. The physics outcome is **predicted before the roll** (`BulletStuff.predictAndReset`), so the result is known to code the moment the roll starts.
- **The semantic goldmine**: combat state lives in `gameplay/fightLog/` snapshots with five temporalities — `Base`, `Visual` (what's drawn), `Present` (after your commands), `Future` (Present + all queued enemy attacks = the damage preview), `StartOfTurn`. `EntState` exposes readable getters for HP, shields, incoming damage, poison, statuses, death prediction, etc. All of it is currently rendered **only as colored pixels**.
- **Text**: every string routes through `Main.t()` / `Translator` with flat key→value maps in `lang/en.json`, rendered by `TextWriter`/`TannFont` with inline color tags (`[green]`, `[n]`, `[cu]`…). Buttons built from text keep their tagged string (`StandardButton.getText()`); icon-only buttons have no text at all.
- **Audio**: a small fixed SFX vocabulary (`statics/sound/Sounds`): `pip` (open/select), `pop` (close), `error` (any rejected action — overloaded, no distinction), `confirm`, `undo`, `lock`/`unlock` (dice), `pickup`/`drop` (items), etc. This is currently the only non-visual channel.

---

# Phase 1: Application Launch and Title Screen

## 1.1 Splash / Loading — N/A

`SplashDraw` draws `splash/loading.png` on frame 1; loading happens on frame 2. Non-interactive, no text.

> **Hazard:** if the window is too small to compute a valid scale, the game draws `splash/resolution.png` **forever** (`Main.invalidScale`) — an image-only dead end with no text and no keyboard escape.

## 1.2 Title Screen Layout — DONE

`screens/titleScreen/TitleScreen.java`. Contents:

- Game logo (image only, `Touchable.disabled`; slides off-screen when the mode card is tall)
- Per-mode-folder background art (decorative, cross-fades)
- Left icon button cluster (see 1.4)
- Center: the selected mode's "card" (see 1.5)
- Right edge: the modes drawer (see 1.3)
- Popup holder (achievement toasts, top-right; see 13.6)
- On entry: unlock notification modals ("New \<type\> mode unlocked: \<name\>", "New hero color unlocked: \<colour\>") — self-popping, click/ESC to dismiss
- Conditional "new version available" icon button → dialog with current/latest version

> **Note:** `TitleScreen.keyPress` contains only leftover dev-debug keys (C, D). There are **no gameplay hotkeys on the title screen**; everything is mouse-only. Clicking anywhere on the screen closes the mode drawer.

> **Coverage:** SnDAccess presents the title screen as three tab-stops — the mode list, the
selected mode's card, and the system buttons — declared fresh each render from the live game
model. The logo and background art are decorative and stay unread.

## 1.3 Modes Drawer (`ModesPanel`) — DONE

A slide-out drawer on the right edge, toggled by a small tab handle (icon only, `InputListener.touchDown`, no keyboard, no label).

- One `StandardButton` per playable mode (`Mode.makeModeSelectButton()`), sorted unlocked-first
- **Locked modes render six spaces as their label** plus a padlock overlay — the name is deliberately blanked; a screen reader has literally nothing to read. Clicking one opens its unlock-requirement panel (`AchLib.showUnlockFor`)
- Clicking an unlocked mode selects it; **selection state is conveyed solely by border color** (light vs grey)
- Folder modes (`cool/`, `creative/`, `cursed/`, `crappy/`…) replace the center card with a grid of contained-mode buttons; navigating back out is only via a back-arrow icon button in the left cluster
- Demo build: the drawer instead shows a purchase pitch

> **Coverage:** every mode reads its **name**, whether it is **selected**, and whether it is
**locked** — the visual drawer blanks locked names entirely, so the mod is strictly more
informative here. Activating a locked mode opens the game's own unlock-requirement panel (for a
locked folder, its first contained mode, matching the game's rule), which 1.6's reader speaks.
The drawer's slide state is irrelevant to navigation: the list is always present in the graph.

## 1.4 Left Icon Button Cluster — DONE

All are unlabeled icon-only squares (`DungeonUtils.makeBasicButton`), mouse-click only:

1. **Cog** → opens the cog/esc menu (see Phase 8) — ESC also works
2. **Almanac (book)** → opens the Book (see Phase 7)
3. **Globe** → language chooser modal: one button per language (default/en/es/pt/fr/it/ru)
4. **Padlock** (conditional) → "unlocks bypassed" re-enable dialog
5. **Magnifying glass** (conditional, `SEARCH_BUTT` option) → name search; right-click → description search
6. **Version** (conditional) → new-version dialog
7. **Back arrow** (conditional) → leave the current mode folder

> **Coverage:** the cluster's icons are named rather than described by their art — Menu,
Almanac, Language, Search, and the bypass-unlocks toggle — with the conditional ones appearing
only when the game shows them. Language is a left/right chooser instead of the icon-then-modal
flow, since the underlying option is a simple cycle. The back-out-of-folder control lives on the
card, where the folder contents are. The new-version button is not surfaced: it reports a remote
version check, not a game action.

## 1.5 Mode Card (center) — DONE

Built by `Mode.makeStartGameDisplay()` for the selected mode:

- Mode name (clicking opens mode info, see 1.6)
- Description box: one line per `getDescriptionLines()` entry, e.g. "full 20-fight dungeon" (Classic); also clickable → mode info
- Mode-specific extra controls to the left of the title (Choose-Party hero selectors, Custom modifier editor — see Phase 6)
- **Start buttons**: one per unlocked difficulty (see 2.1). For most modes the difficulty buttons ARE the start buttons. Locked difficulties are silently removed, not shown greyed
- **Wins wreath** above each start button: wreath sprite + optional count; the textual record ("Wins: n/m (p%)", streak, history, leaderboards) is only in a **right-click panel**
- **Continue button** ("Continue (fight N, Difficulty)") appended when a save exists — this is the only load-game affordance in the game (one autosave slot per mode). Resumes immediately, no confirmation
- Oversized cards get wrapped in a `ScrollPane` — mouse-wheel/drag only

> **Coverage:** the card reads the mode's description lines, every difficulty (including the ones
the game removes from the UI while locked, which read as "locked"), and the **win/loss record and
streak folded onto each start button** — the visual UI hides that behind a right-click on a
wreath sprite. Folder modes list their contained modes in place, with a back control. The
Continue button appears whenever the mode has a save. Scroll panes are irrelevant: navigation is
over the model, not the viewport.

## 1.6 Mode Info Panel — DONE

Opened by clicking the mode name/description (`Mode.showModeInfo()`), suppressed for brand-new players and creative modes:

- Mode name + extra description
- Wins/losses record
- **History** button → scrollable list of past runs (`RunHistory.makeGroup`)
- **Leaderboards** section (see Phase 10)
- **Challenges** section: achievement icon tiles for mode-specific achievements (icons only; details on right-click)

> **Coverage:** opened with the secondary key on any mode and read by the generic modal reader,
so its text lines and buttons become nodes. The achievement tiles inside are icon-only in the
game and read as unlabeled; naming them belongs with the achievements work (Phase 11).

---

# Phase 2: Starting a Run

## 2.1 Difficulty Selection — DONE

`gameplay/battleTest/Difficulty.java` — seven difficulties, each a colored start button on the mode card:

| Difficulty | Color | Start-of-run rule |
|---|---|---|
| Heaven | light | blessings totalling value 20 |
| Easy | green | one tier-5 blessing |
| Normal | yellow | one tweak |
| Hard | orange | one tier-4 curse |
| Unfair | red | curses totalling −10 |
| Brutal | purple | curses totalling −20 |
| Hell | pink | curses totalling −40 |

Brutal/Hell also inject a "Beware!" message phase. Difficulty rules text (`getRules()`) is shown in the Book glossary, not on the buttons.

> **Coverage:** each difficulty is a start button carrying its record, and the **rules text is on
the tooltip key** — so "what does Unfair actually do" is answerable without leaving the card.
Locked difficulties are announced as locked rather than silently omitted.

## 2.2 Party Layout Chooser — PARTIAL

`GameStart.startWithPLTChoice` → "Choose party layout" panel (appears once the feature is unlocked, for modes that don't disable it):

- Three option cards: layout name (Basic, Greens, Force, Magical, Mountain, Defensive, RNG, CornCob…) + **a row of colored 10×10 squares representing the five hero class colors** — the party composition is purely visual, no text
- Optional rarity line if `SHOW_RARITY` is on
- Horizontally scrollable if needed; self-popping modal (click to dismiss)

> **Coverage:** the picker is a pushed modal, so the generic modal reader lists its options, and
each layout name is enriched with its **colour composition** read from `PartyLayoutType` (the
visual card shows only coloured squares). PARTIAL because the picker is gated behind
`Feature.PARTY_LAYOUT_CHOICE` and has not been exercised on an unlocked profile — the
enrichment is code-complete but unverified live.

## 2.3 Overwrite Confirmation — DONE

If starting would clobber an in-progress save: `ChoiceDialog` "Warning, this will overwrite your in-progress game. Are you sure?" — 2-choice dialogs support Enter=accept / Backspace=decline (the only keyboard-capable dialog type).

> **Coverage:** read by the generic modal reader — the warning text and both buttons are nodes,
so the choice is navigable rather than depending on the game's undiscoverable Enter/Backspace
binding.

## 2.4 First-Run Tutorial Override — N/A

On a player's very first run, the difficulty click is silently redirected to a scripted starter fight (Fighter/Lazy/Thief/Defender/Defender vs 2 Wolves) via `TutorialManager.getTutOverride`.

> **Coverage:** N/A — it has no UI of its own; it substitutes the fight a start button launches.
The button is covered by 2.1 and the fight by Phase 3.

## 2.5 Starting Modifier Pick — DONE

The first fight opens with a difficulty-driven `ChoicePhase` (see 4.5): Easy/Normal/Hard = pick 1 of N; Heaven/Unfair/Brutal/Hell = point-buy to a target value. Digits 1–9 toggle options; everything else is mouse.

> **Coverage:** the offer is drawn onto the dungeon screen rather than pushed as a modal, so it
has its own screen. The screen announces the game's own header ("Choose a curse"), and each
option reads its **name, type, tier and generated effect** — `Choosable.describe()` returns only
the type word ("curse"), so identity and effect come from the underlying Modifier or Item.
Choosing runs the phase's own path and the confirmation dialog reads through the modal reader.
The same screen serves the between-fight offers in 4.5; point-buy offers still lack a
running-total readout.

---

# Phase 3: The Combat Screen (Core Gameplay)

`screens/dungeon/DungeonScreen.java` — the single screen hosting all fights. Layout (landscape): hero panel column on the left edge, monster panel column on the right edge, 3D dice tray in the center, ability/mana bar bottom-center, Reroll/Undo buttons bottom-left, Done Rolling/End Turn button bottom-right, icon button row top-center, tutorial box contextual, transient popups top-right. In portrait the columns compress and buttons stack.

## 3.1 Turn Structure — DONE

Phase-driven (`gameplay/phase/PhaseManager`, a phase stack; the current phase gates all input):

1. **`EnemyRollingPhase`** — monsters' dice roll and land on their panels (their locked face = their intent for the turn). No player input. ~0.5 s.
2. **`PlayerRollingPhase`** — player rolls/locks/rerolls hero dice.
3. **`TargetingPhase`** — player assigns rolled sides and casts abilities, then ends turn.
4. **`DamagePhase`** — enemy attacks resolve with animations; no input.
5. Back to 1, until victory (`LevelEndPhase`/`RunEndPhase`) or defeat (`RunEndPhase`).

> **Hook:** the debug option `PHASE_DISPLAY` prints the current phase class name on screen — the phase is always programmatically knowable.

> **Coverage:** `PhaseWatcher` polls `PhaseManager.get().getPhase()` per tick and speaks phase
changes: the gameplay phases (enemies rolling / your roll / targeting / enemy attacks), surrender,
fight won, run over. Fight ("Fight n/m") and turn stamps ride the first in-combat phase after
their value changes — a fight's opening enemy roll can resolve beneath a ChoicePhase and never
surface, so stamps are value-diffed, not phase-bound. Decision phases stay silent here (their own
screens announce); unknown phases stay silent rather than reading class names.

## 3.2 Player Rolling Phase — DONE

- Hero dice tumble physically in the 3D tray. Clicking a die (< 220 ms press) toggles lock (`EntDie.toggleLock`); locked dice slide onto their hero's panel
- **Right-click / long-press a die** → floating panel (`RollPanel`): the die's full net + current-side explanation, plus a highlight on the owner's panel. Mouse-position anchored
- **Double-click empty tray space** → line up / scatter all unlocked dice
- **Reroll button** (bottom-left): "Reroll" + "n/max" counter; label turns **red at 0 rolls** (the only exhaustion cue). Rerolls only unlocked dice
- **Done Rolling button**: states "Done Rolling" (grey) → pulsates when all dice locked. Locking all dice auto-confirms
- Lock-restriction vetoes (from curses etc.) surface as a transient bottom banner + error sound
- Keyboard: **1–9** lock/unlock hero N's die, **R** rolls, **Space/Enter** confirms. There is **no keyboard path to an individual physical die's info**
- Unspent rerolls carry into targeting (can be "un-rolled" back via Undo)

> **Coverage:** `CombatScreen` (heroes stop: name, rolled side's calculated text, locked state;
buttons stop: Reroll with the n/max counter, the confirm button's own state text read
reflectively) + `DiceWatcher` (when the last tumbling die settles, each rolled die reads its
landed side followed by "Reroll n/max"; lock toggles speak "name locked/unlocked" from any input
path). Activating a hero runs `targetingManager.clicked` — the game's digit route — and Backspace
reads the full die net. The game's 1–9/R/Space keys fall through untouched (the navigator now
leaves its details key unconsumed when the focused node has no details). Vetoed lock banners land
with 3.12's transient-text channel; the double-click tray line-up is visual-only.

## 3.3 Targeting Phase — DONE

`screens/dungeon/TargetingManager.java`. The core loop: select a targetable (a hero's rolled side, or an ability), then click a target.

- **Selecting a hero's die** (click their panel's die half, or digit 1–9): pushes an `Explanel` describing the side (calculated, buff-adjusted text + per-keyword rule boxes) and slides the hero panel out 13 px
- **Valid/recommended targets are indicated only by panel border highlights** (light border; pulsating colored borders for conditional keyword bonuses)
- **Clicking a target** applies the side (`FightLog.addCommand`); auto-fires immediately for untargeted effects
- Invalid target → error sound + red text on the explanel ("Not enough mana", "No valid targets", "Cannot target back-row enemies", "Can only affect \<name\>…", etc. — a complete error vocabulary exists as text)
- **Clicking a monster's die face** shows its attack explanation + targeting arrows to its victims
- **Keyboard**: digits select hero dice; with a selection, digits target entity N (Shift+digit targets the opposite side); QWERTYUI select ability slots 0–7; Z = undo; Tab (hold) = show targeting arrows; Space/Enter = end turn
- **End Turn confirmation**: if dice/mana/tactics would be wasted, a `ChoiceDialog` lists the reasons ("N usable dice remaining", "You can only keep N mana", "Usable tactic(s): …"). Mandatory-use dice hard-block with a banner instead

> **Coverage:** the targeting loop mirrors the sighted flow with no virtual popups: select a die
(digit, click, or Enter on the hero — all `targetingManager.clicked`), then arrow/tab to a
creature and Enter to apply. `TargetingWatcher` speaks state changes from any input path —
selection reads the calculated effect ("Shield 2, selected"), every applied command reads
"effect, on target" straight from the FightLog's own command list, deselection says so. The
border-highlight validity reads as a "valid target" state on each creature node; a vetoed apply
speaks the game's own reason (`getInvalidTargetReason`). The Explanel is a blockerless "light"
push and no longer counts as a modal, so it can't steal navigation. End-turn confirmation reads
through the modal reader; monster nodes carry their locked intent as their value. Abilities land
with 3.6, Undo with 3.8, arrow lines with 3.9.

## 3.4 Hero / Monster Panels — DONE

`screens/dungeon/panels/entPanel/EntPanelCombat.java` — the central readout, 84 px wide, one per combatant. Displays:

- **Name** (colored by class; long names become a scrolling marquee unless `DISABLE_MARQUEE`)
- **HP as a pip grid** (`HPHolder`): red = surviving HP, **yellow = incoming blockable damage this turn** (the damage preview), **green = incoming poison** (pink with `COLORBLIND_POISON`), purple = missing; overkill shown as a "+N" glyph. Pip art shrinks as max HP grows
- **Shield badge** (`ShieldHolder`): sprite + number; shakes on partial block, cracks when broken
- **Status/buff icons** (`TriggerPanel`): 5×5 icons only, no text; incoming-not-yet-applied debuffs drawn faded / with a plus / pulsating per option
- **Die face** (once locked): the current side; a dark overlay is the only "already used" cue
- **Death prediction**: red flash/border when the Future snapshot says this entity dies this turn (grey = will flee); optional red cross over the name
- **Targeting arrows**: dotted attacker→target lines, visible only while Tab / the target button is held or after clicking a monster; thin colored edge bars on a monster show which heroes currently target it
- **Portrait**, speech bubbles (flavor), floating text wisps

Interaction: left-click = die-side action (lock / select / apply target); **right-click / long-press = full character sheet** (see 3.5). No hover behavior exists anywhere.

> **Coverage:** each combatant node reads name, die face (locked/used state), "hp of max" +
shield, and the pip-colour damage preview as text — incoming blockable damage, incoming poison,
and the Future snapshot's death/flee prediction (HPHolder's own formulas over
Present-vs-Future). A kill from a player command resolves in the Present and vanishes from the
column, so the applied announcement appends "defeated". Backspace reads the die net plus each
visible status icon's own trigger-panel description. Targeting arrows are 3.9; portraits and
speech bubbles are flavor.

## 3.5 Character Sheet (`EntPanelInventory`) — DONE

The right-click panel for any hero or monster:

- Portrait, level tag, name, **HP as text** ("hp/maxHp" — the only place HP is text)
- Live spinning 3D die (cosmetic; suppressed by `HIDE_SPINNERS`)
- **Die net** (`NetPanel`): all 6 sides laid out as a cross; the currently rolled side gets a 1-px outline; each side's description is **right-click only**
- Equipped items in the net's corner slots (right-click for item text)
- Traits/passives as stacked explanation panels
- For heroes dead last fight: skull icon (right-click explains the half-HP return rule)

> **Coverage:** Backspace on any combatant reads the sheet as one utterance: name, level (heroes),
hp/shield, defeated state with the game's own half-HP-return sentence, the six-side die net,
equipped items (name + description), and each visible status. Dead heroes keep their place in
the heroes stop (the greyed skull panel) with a "defeated" state; dead monsters vanish like
their corpses. Dialogs that embed sheet pieces read too: DieSidePanels speak their side's base
text and ItemHeroPanels their item (or "empty slot") in the modal reader. Per-side right-click
detail beyond the side text and the cosmetic spinner/portrait are visual-only.

## 3.6 Abilities Bar and Mana — DONE

`ability/ui/AbilityHolder` (bottom-center; hidden during enemy rolling, faded during rolling, active during targeting):

- One card per spell/tactic: icon + title (ellipsised to 5 chars when cramped) + cost strip (mana pips, or required die faces for tactics); border light when selected; skull overlay when the caster is dead
- Left-click = select for casting (auto-casts if untargeted); right-click = description
- **Mana**: up to 5 pips, then collapses to "[blue]xN" text; right-click → "N/M mana stored" banner
- Cast errors as transient banners: "Not enough mana", "All costs must be present on unused dice", "Can't use abilities from defeated heroes"
- Keyboard: QWERTYUI select slots 0–7

> **Coverage:** an abilities stop (present in rolling/targeting, like the bar): one node per card
in `getByIndex` slot order — title, cost (spell mana via `getSpellCost`, tactic faces via
`describeCost`), selected state, dead-caster state; activate runs `selectForCast` (the QWERTY
path), Backspace reads the calculated effect. The mana store reads as the game's own "n/m mana
stored" banner text. Cast-error banners speak via 3.12's channel. Verified live: the mana node
with the game's words; ability cards use the same game paths and read whenever a caster exists.

## 3.7 Confirm Button Warnings — DONE

`ConfirmButton` states: Done Rolling / End turn, pulsating when everything is used. When ending the turn would let **N heroes die**, the tick icon is replaced by **skull icons (one per predicted death) + a red border** — no text anywhere.

> **Coverage:** the confirm node's label is the button's own state text (read reflectively from
`ConfirmState`), and the skull row reads as a value — "a hero dies this turn" / "n heroes die
this turn", counted from the Future snapshot the same way the per-hero prediction is (which also
speaks on each endangered hero's own node, verified live with a lethal wolf intent).

## 3.8 Undo — DONE

Undo button (bottom-left, targeting phase): "Undo (n rolls)". Unlimited undo of player commands within the turn (`FightLog.undo`); when nothing is left to undo and rerolls remain, it rewinds to the rolling phase. Z key works. No redo, no undo-history display.

> **Coverage:** an Undo node in the targeting buttons ("Undo" + "n rolls" when it would rewind,
the game's own words), activating `requestUndo` — the Z path. A command revert speaks "Undo"
from the watcher's command-count diff regardless of input path; the rewind-to-rolling case
announces itself through the phase change ("Your roll"). Verified live: the rewind path and the
button readout; Z falls through the navigator untouched.

## 3.9 Top Icon Button Row — DONE

`DungeonUtils.makeButtonsGroup` — all icon-only, all mouse-only, and the row is **rebuilt every turn** (any state would be lost):

- **Cog** → cog/esc menu (ESC works)
- **Hash (#)** → run summary panel: mode+difficulty, "Fight n/20 Turn t", streak, and the full active modifier list (small panels, right-click each for details)
- **Hourglass** (conditional) → future-turn schedule panel ("Current turn: N", "Turn X (start/end): \<message\>") — the only surfacing of delayed effects
- **Target** (conditional) → **press-and-hold** to reveal targeting arrows (Tab is the keyboard equivalent)
- **Search** (optional) → name/description search

> **Coverage:** named nodes in the combat buttons stop driving the underlying actions, never the
rebuilt actors: Menu (`showCogMenu`, ESC also works), Run summary (`showHashContents` — a
blocker push the modal reader walks: mode+difficulty, fight/turn, modifiers with right-click
detail; verified live), Turn schedule (conditional — speaks the hourglass elements as "Turn n:
message" using the game's own pattern strings), and Search (conditional on its option). The
press-and-hold target-arrows button stays visual; the same information reads per-node as
intents and the damage preview.

## 3.10 Reinforcements Panel — DONE

When more monsters wait off-screen: a "Reinforcements: N" box atop the enemy column; click lists upcoming monster names. Flashes on change. Mouse only.

> **Coverage:** a node at the end of the enemies stop whenever `Snapshot.getReinforcements()` is
non-empty — the game's own "Reinforcements: N" pattern string, with the waiting monsters' names
on Backspace.

## 3.11 Surrender Phase — DONE

When monsters try to flee, a **three-choice** `ChoiceDialog` (decline / "?" explainer / accept). Three-choice dialogs have **no keyboard support** (only 2-choice dialogs do). `AUTO_FLEE` option auto-accepts.

> **Coverage:** the phase announces itself ("Monsters offer to surrender") and the three-choice
dialog reads and activates through the generic modal reader — verified live with a fleeing
wolf: the prompt, no / ? / yes buttons, and the ?-explainer panel all spoke and worked by
keyboard.

## 3.12 Transient Feedback Channels — DONE

- `AbilityHolder.showInfo(msg)` — the main error/notice banner: slides up from the bottom, waits ~0.5 s, leaves
- `PopupHolder` (top-right): achievements, stat deltas, level-difficulty notes; 5 s auto-dismiss; right-click for detail
- Speech bubbles and text wisps (flavor)
- Death/damage animations, combat effect animations (`combatEffects/` — ~30 visual-only effect types)

> **Coverage:** `AbilityHolder.showInfo` and `TargetingManager.showError` are instrumented
(advice → `Dispatcher.transientText`, a bounded queue; the module's BannerWatcher drains,
dedupes repeats, and speaks with interrupt — verified live: the previously-silent "Side does
nothing" veto now reads). PopupHolder was already spoken by PopupWatcher. Bubbles, wisps, and
combat-effect animations are flavor; their gameplay substance reads through the snapshot
previews and applied-command announcements.

---

# Phase 4: Between Fights

## 4.1 Level End Hub (`LevelEndPanel`) — DONE

Slides in after each won fight:

- Header: "Fight n/20"
- Up to **3 reward buttons**, one per pending reward phase — **icon-only, 53×20** (levelup icon, loot icon, chest, anvil, question mark…); ">+N more" text if over 3
- **Inventory** button (pulsing glow when new items)
- **Continue** button; refusal reasons appear as text *below* the panel ("You must choose all rewards…", "Some items must be equipped…"); an "unequipped items" confirmation dialog can also appear
- Keyboard: digits start reward phase N, **I** = inventory, **Enter** = continue (Space is *not* bound here)

> **Coverage:** `LevelEndScreen`. The icon-only reward buttons speak real names — a ChoicePhase
speaks the game's own offer header ("Choose an item"), `PhaseGeneratorTransformPhase` is
resolved through the same cached path the game uses for its button icon, fixed event phases get
role words, anything else falls back to its `getLevelEndButton` text. All pending rewards are
listed (no "+N more" cap). Inventory speaks "new items" when the glow is showing; Continue
speaks the refusal reason (read via `getNoContinueReason`) before running the panel's own
click; the under-panel refusal/reminder TextWriters are also navigable nodes. Activation goes
through `clickPhaseStart`/`inventoryClick`/`continueClick`, so the digit/I/Enter game keys and
our nodes share one code path. The unequipped-items confirmation is a pushed modal (generic
reader). Also added here: `PauseRecoveryScreen` for the game's stuck-pause screen (its only
visual affordance is an invisible-for-a-minute "tap a few times to escape") — speaks the state
and offers Resume-run (the game's own `resume()` recovery) and back-to-title.

## 4.2 Minimap — DONE

Bottom-center progress strip: zone backgrounds + node icons (normal/boss × neutral/current/complete). Purely decorative, no text, no interaction. Bosses are every 4th fight (4/8/12/16/20).

> **Coverage:** a "Map" node on the level-end hub speaks what the strip paints: fight progress
(the game's own `getLevelProgressString`), the current zone name from `getLevelTypes`, and the
next boss fight from `ContextConfig.isBoss` ("Fight 5/20, zone Dungeon, next boss at fight
8"). Only present when the mode shows the minimap.

## 4.3 Level-Up Choice — DONE

Even-numbered fights (`PhaseGeneratorLevelup` → `ChoicePhase`): choose 1 of 2 hero upgrades, plus a "random" option and a "skip" button once past the first run. Only lowest-level heroes are offered. Each option is a full character sheet of the upgraded hero; a dotted line connects it to the current hero's panel. Confirmation dialog shows current → new panel.

> **Coverage:** each option speaks the upgraded class name, **which hero it upgrades** (the
dotted line, as words), tier, and the upgraded hero's full sheet — level, max hp, all six
sides, visible traits — read from a hypothetical hero built exactly the way the game builds
its offer panel (`transformLevelup(...).makeEnt()`, blank state). Random and skip options
speak their own descriptions. Hero offers show no visual header, so the screen names itself
"Choose a level-up". The current→new confirmation dialog is a pushed modal (generic reader).

## 4.4 Loot Choice — DONE

Odd-numbered fights (`PhaseGeneratorStandardLoot` → `ChoicePhase`): choose 1 of 2 items + a special random reward on its own row (same-tier random, +1/−1, double/half, triple/third, N junk items…). Item quality scales with fight number.

> **Coverage:** items speak name, type word, tier, and description; the special random row
speaks its `Choosable` description ("a random tier 1 item"); Or/And/Replace composites recurse
into their parts. Verified live on a fight-3 offer end-to-end including the confirmation
dialog and the gained item arriving in the inventory.

## 4.5 The ChoicePhase Interaction Model — DONE

`ChoicePhase` is the universal "choose a reward" screen (items, levelups, curses, blessings, tweaks):

- Header text: "Choose a \<thing\>" / "Choose up to N…" / "Choose \<things\> with a combined value of \<tier\>"
- Styles: pick-exactly-N (auto-confirms with a dialog), up-to-N (Confirm button), **point-buy** (running "current/target" tally + reset + tick buttons), optional (wrapped in accept/decline)
- Selected options highlighted by outline only
- **Right-click detail is suppressed while choosing** — you cannot inspect options via `info()` during a choice
- Digits 1–9 toggle option N; confirmation dialog accepts Enter/Backspace; everything else mouse
- **Anticheese reroll**: on first-fight offers, a tiny unlabeled icon button (top-right of the panel) rerolls the starting party and options, with its own warning dialogs

> **Coverage:** `ChoiceScreen` (rewritten), driving the phase's own `tapForChoiceToggle` /
`choose` / `clearChoices` / `endPhase`. The outline-only selection speaks as a "selected"
state part plus immediate selected/deselected feedback on toggle; the suppressed right-click
detail is replaced by each option's model-read effect text spoken on focus. All four styles
verified live: exact-N (auto-confirm dialog via the modal reader), up-to-N (Confirm node with
"n of max chosen"), point-buy (tally node, Reset, Confirm with a spoken not-yet-valid
refusal), optional (accept/decline nodes running the dialog's own routes). The anticheese
reroll surfaces as a named button (found by its flaff texture, fired through the game's
listener) — code-complete but not yet exercised live, since it only exists on first-fight
offers. The game's digit keys keep working alongside.

## 4.6 Event Phases — DONE

Random between-fight events (gated by unlockable features), all dialog-based:

- **Challenge** (`ChallengePhase`): split accept/decline dialog — left "Challenge:" extra monsters, right "Reward(s):" items; on accept, a "Challenge reward(s):" reveal panel
- **Cursed chest** (`TradePhase`): "Open cursed chest?" accept/decline
- **Anvil** (`ItemCombinePhase`): "Trade your tier 0–3 items?" / "Smash this item?"
- **Class reroll** (`HeroChangePhase`): "Reroll the class of X?" yes/no → reveal
- **Position swap** (`PositionSwapPhase`): "Swap A with B?" yes/no
- **Message / reveal panels** (`MessagePhase`, `RandomRevealPhase`): text + single OK button; Space/Enter (+Backspace for messages) work
- Blessing/tweak picks: standard `ChoicePhase`

> **Coverage:** these dialogs are added straight to the dungeon screen, never pushed on the
modal stack, so the modal reader can't see them; `DialogPhaseScreen` reads each phase's
private dialog field and walks it with the shared actor reader (`ActorNodes`, extracted from
the modal screen). Monster tiles (`MonsterLedgerView`) and item/modifier cards
(`ConcisePanel`) are labeled from their models — name, effect text. All seven dialog phases
verified live (interrupted synthetically over a real level end). Fixing activation here also
fixed a general bug: `GameUi.activate` now fires **every** TannListener on an actor like a
real tap, not just the first — buttons whose handler is added after construction (reveal "ok",
challenge accept/decline) were silently inert before. The challenge's on-accept reveal is a
pushed modal (generic reader); Space/Enter/Backspace game keys keep working underneath.

## 4.7 Inventory / Equip Screen (`PartyManagementPanel`) — DONE

The party equipment screen (from Level End's Inventory button, or **I**):

- Grid of hero character sheets + the item bag (`InventoryPanel`: up to 6×3 item tiles, "no items :(" / "drag to equip" placeholders, glow on new/force-equip items, zoom button → text list of equipped/inventory items)
- **Equipping is drag-and-drop only**, and the destination slot is chosen by *where inside the hero panel you drop* (bottom → slot 1, right → slot 2, center → slot 3, else 0). Raw `Gdx.input` coordinates; no keyboard or click-click alternative exists
- Item details: right-click a slot → `ItemPanel` text
- Hero rename: click the title bar (text input)
- Keyboard: **I/Enter/ESC** = done, **R** = randomize equipment; all other keys are swallowed while open
- Items are visible but **not equippable during combat** (`Phase.canEquip()` false in-fight)

> **Coverage:** `InventoryScreen` (replaces the generic modal reader for this panel).
Drag-and-drop becomes pick-and-place: activate a bag item or an occupied hero slot to pick it
up (a "holding X" banner node appears), activate any hero slot to place it there, or a
put-away button to return an equipped held item to the bag. Placement runs the panel's own
public `equip` — the exact drop code, including swaps and displaced items — with the outcome
spoken ("Big Shield equipped on Defender, slot 1"); a bag-sourced item is first removed from
the bag list exactly as the drag path does at pickup. Hero rows speak name/level with the full
character sheet on activate or Backspace; slots speak their item or "empty"; bag items speak
name, tier, held/new/force-equip glows, and description (the zoom list's content, in place).
Randomize and Done drive the panel's own R/Enter key routes. Verified live: equip from bag,
pick-up from slot, unequip to bag, re-equip, close, focus restore. Each hero row ends in a
**Rename** button that fires the game's own title-bar listener; the game's in-stage text input
(it is NOT a native dialog) is covered generally — while a `TextInput` holds keyboard focus,
`SndInput` passes every key through to the field (typing, caret keys, Enter submit, Escape
cancel) and `TextEntryWatcher` announces the field on focus and echoes edits (typed chars,
pastes, deletions). The same coverage serves every other text input in the game (scenario
names, custom-mode saves).

---

# Phase 5: Run End

## 5.1 Victory / Defeat Panel (`RunEndPanel`) — DONE

Full-width band: center victory/defeat **image**, left column text (end title, "Fight n/20", streak, "Previous best: n (new record!)", leaderboard submit), right column buttons (**Quit**, **Stats**, mode extras like "ANOTHER!" in Instant). Buttons are invisible until a ~2 s slide/fade completes. **No keyboard handling at all.**

> **Coverage:** `DialogPhaseScreen` reads `RunEndPhase.endPanel` like the other screen-floating
dialogs; the screen announces itself with the mode's own end title plus Victory/Defeat (read
from the phase's `victory` flag). Left-column text and right-column buttons emit as nodes once
the slide-in makes them visible (the immediate-mode build picks them up the frame they appear).
Verified live over a demo run: title, fight progress, Quit and Stats buttons all read, Stats
activates. The keyboard absence is moot — the navigator provides activation.

## 5.2 End-of-Run Stats (`GameEndStatsPanel`) — DONE

- Header: "\<mode\> \<difficulty\> — Victory/Defeat", "Fight n/20"
- Modifier list (small panels, right-click for detail)
- Per-hero row: portrait, death count, equipped items
- Two-column stats: Time, Turns, Undos, Rolls, crosses rolled / Kills, Dmg Taken, Blocked, Healed, Abilities
- Party layout swatch (1-px color squares, no text)

> **Coverage:** `RunEndStatsScreen`, model-driven: the panel's stats render as two parallel
columns of separate name/value actors (association purely spatial) and heroes as portrait
tiles, so instead of walking actors it rebuilds every line from the same `DungeonContext` and
stats-map calls the panel uses — modifiers with full descriptions, per-hero death counts and
equipped items, all ten stat lines correctly paired, and the party-layout swatch as the
layout's name and colour list. Verified live. Ledger portrait tiles (`HeroLedgerView` /
`MonsterLedgerView`) are also now named in the generic actor walker for wherever else they
appear.

## 5.3 Fleeing and Restarting — DONE

- **Flee** (abandon run): cog menu → "Flee" → "Flee? (counts as a loss)" dialog
- Restart: only via mode-specific affordances (Cursed family disallows; Nightmare/Paste can't restart)

> **Coverage:** the cog menu and the flee confirmation are pushed modals the generic reader
already covers — verified live: Flee reads with its "(counts as a loss)" warning, cancel and
Escape both back out, and the in-run Quit → title → Continue round trip restores the run.
Mode-specific restart buttons ride the run-end band reading (5.1).

## 5.4 Cursed Loop Reset (`ResetPanel`) — DONE

At each 20-fight loop boundary in the Cursed family: a purple panel ("You feel weaker. All your items disappear. Only curses remain…") with a single "never" button. Click only, no keys.

> **Coverage:** `ResetPhase.resetPanel` is registered with `DialogPhaseScreen` — the purple
text and the "never" button read and activate like every other dialog phase. Code-complete but
not exercised live: it only occurs at a cursed-family loop boundary, and the cursed modes are
locked in the dev demo build.

---

# Phase 6: Game Modes Catalogue

`gameplay/mode/` — 26 modes organized into folders (cool/, creative/, cursed/, crappy/, debug/). Each has a one-save-slot autosave, its own win/streak stats, and a title-card UI. Status refers to any mode-specific UI beyond the standard card.

| Mode | Folder | Structure | Mode-specific UI |
|---|---|---|---|
| Classic | (root) | 20 fights, boss every 4th | — |
| Shortcut | cool | starts at fight 9 | — |
| Choose-Party | cool | classic + chosen party | 5 hero-selector slots + reroll die (see 6.1) |
| Loot | cool | items after every fight, no levelups | — |
| Raid | cool | 10 heroes, double monsters | — |
| Generate | cool | all heroes procedurally generated | — |
| Alternate | cool | level-shifted existing heroes | — |
| Dream | cool | any monster anywhere, no bosses | — |
| Nightmare | cool | fights 21–30 with a past winning party | run-picker dialog (see 6.2) |
| Paste | creative | play from a clipboard string | Paste!/Store buttons, scenario list (see 6.3) |
| Custom | creative | classic + chosen modifiers | full modifier editor (see 6.4) |
| Wish | creative | sandbox; wish for anything between fights | resolver search UIs |
| Cursed / Blursed / Cursed-Hyper / Cursed-Ultra / Blurtra / Blyptra | cursed | infinite 20-fight loops with escalating curse/blessing schedules | highscore display instead of wins |
| Demo / Instant / Pick / Saves / Balance / Fight / Debug / Empty | crappy, debug | misc | Pick: search dialog; Saves: all-modes continue list; Fight: monster-list editor |

> **Note:** there is no daily/weekly challenge and no numeric seed entry anywhere; the closest is Paste mode's clipboard strings.

## 6.1 Choose-Party Hero Selection — DONE

The only party-building UI: five 25×28 portrait slots (no name text). Left-click a slot → picker grid of every unlocked tier-1 hero grouped by color row; right-click a hero → die panel. Reroll-die icon randomizes all five.

> **Coverage:** the title card gains five "hero slot n" nodes reading the selectors' live hero
names, activating each slot's own listener (the picker — a pushed modal whose portrait-only
tiles are named via the ledger-tile labeling; choosing updates the slot and speaks it) with
the die panel on Backspace. A "Reroll party" node runs the reroll-die's listener body and
speaks the new five-hero party. Verified live under bypass-unlocks (the mode is
progression-locked on this profile).

## 6.2 Nightmare Run Picker — DONE

"Choose Party" button → scrollable list of eligible past victories (each row clickable), or an explanatory "No usable runs found…" text. Eligibility criteria are listed as text.

> **Coverage:** Nightmare's card (which replaces the generic start buttons — those would have
mis-started the mode) shows its own "Choose Party" node driving the mode's picker dialog; the
pushed picker reads through the modal reader — run rows as labeled buttons when victories
exist, the full "No usable runs found…" criteria text otherwise (the state verified live; this
profile has no eligible victories). The mode's Continue node appears when a nightmare save
exists.

## 6.3 Paste Mode — DONE

"Paste!" (clipboard → run), "Store" (native text input names a scenario), stored-scenario buttons (left-click play, **right-click delete** with confirm), plus built-in puzzle scenarios. Clipboard errors appear as dialogs. The cog menu gains a "Copy" button in-run (copy state at start-of-level or now).

> **Coverage:** the Paste card reads Paste!/Store (driven by locating the game's own inline
buttons by text), the stored/built-in scenarios as named rows (play on Enter via the mode's
public start entry, the delete-confirm dialog on Backspace), and Continue when a paste save
exists. Verified live: the "Failed to load fight from clipboard" and "invalid scenario paste"
error dialogs read, and "Delete ludii?" opens and cancels. The Store flow's scenario-title
prompt is the covered in-game text input, NOT a native dialog. The in-run cog "Copy" button
and its copy-state dialog were already readable via the modal reader. Fixed here:
`GameUi.info` now fires every gesture listener like a real right-click (the first-listener bug
`activate` had), which the scenario delete path exposed.

## 6.4 Custom Mode Editor — NOT STARTED

The most complex non-combat UI, embedded in the title screen:

- Modifier list: per-row remove button, optional reorder button, small modifier panel (**details right-click only**); scrollable
- Buttons: add (search dialog), +rng, view-all, clear (no confirmation!), copy, paste (left = replace, **right-click = append**), save (text input), load (left-click load, **right-click delete**)
- "options" / "api" / "resources" links under the description
- **Every edit rebuilds the entire title screen** (only scroll position survives)

---

# Phase 7: The Book (Almanac)

`screens/dungeon/panels/book/Book.java` — the in-game encyclopedia/reference, opened from the title screen's book button or via cog-menu nav links. Three pages, each with its own sidebar of sub-tabs. Remembers the last-open page.

> **Critical:** while the Book is open, `Book.keyPress` + `Screen.genericKeyPress` **swallow every key except ESC** — the entire keyboard is inert. All tab switching is clicking; content scrolling is mouse-wheel only; tab focus state is a color swap. Portrait layout even inserts inert dummy filler tabs labelled "x".

> **Coverage (foundation):** `BookScreen` replaces the old placeholder stub. The key-swallowing
is moot — our processor sits ahead of the Book's, so navigation, type-ahead, and Escape all
work. Three Tab-stops: the page tabs, the focused page's sub-tabs (both with selected state,
activating through each TopTab's own listener so sounds, highlights, and last-page memory
behave), and the current tab's content — walked from the page's scroll panel, so mouse-only
scrolling is irrelevant to reading. Fixed along the way: a ScrollPane's built-in gesture
listener no longer makes it an "interactive leaf" that swallowed its children into one node
(`GameUi.hasTannListener` now means actual TannListeners).

## 7.1 Help Page — DONE

8 tabs: **Basics, Dice, Rolling, Combat, Abilities, Tips, Advanced, Glossary.** Content is bulleted text snippets — the most screen-reader-friendly content in the game — but several embed image-only diagrams:

- Dice tab: a die-position diagram (letters overlaid on a die image, spatial legend)
- Combat tab: a graphical HP-bar legend
- Abilities tab: tactic cost icon grid
- Tips tab: the desktop hotkey list (see Phase 12)
- Glossary: term definitions + unlocked difficulty rules
- Basics tab: also holds the **"Reset Tutorial"** button

> **Coverage:** the snippets are TextWriters the content walk reads line by line (verified live:
Basics, Dice, Abilities). The image diagrams all ship their own text equivalents, which read
while the images drop as decoration: the die-position diagram's L/M/T/B/r/R legend, the HP-bar
legend's per-row labels ("8/10 hp", "2 incoming damage", "1 incoming poison"), and the tactic
cost icons' text explanations (tactics are still locked on the dev profile, so that grid is
pattern-verified only). Reset Tutorial and the youtuber/Discord links are ordinary buttons.

## 7.2 Ledger Page — DONE

8 tabs. The collection/reference database:

- **Hero** — "seen/total heroes found" + a grid of ~130 hero tiles grouped by color and tier, sorted by pick rate. **Right-click** a tile → character sheet + pick-rate text; locked heroes show unlock requirements
- **Monster** — "seen/total monsters found" (~75 designed + generated); left-click → kill count, win ratio, character sheet
- **Item** — "seen/total items found", 200+ items grouped by tier (1–20, then 0 to −7); left-click → item text + pick rate
- **Modifier** — filter rows (Curses/Blessings/Both × Designed/Generated/Wild) + modifier panels
- **Keyword** — ~190 keyword buttons; each opens rules text + every side/hero/item using it; locked ones sit under a translucent dim overlay (still clickable)
- **Pin** — pin heroes/items/etc. by name/description search, paste lists, clear; pinned entries get X-buttons
- **Unlock** — achievements: "achieved/all" counts, rows of 18×18 icon tiles (**detail on right-click only; left-click does nothing**), 5 suggested incomplete challenges, secrets section, copy/load-achievements buttons, bypass-unlocks checkbox
- **TextMod** — modding API documentation sub-pages (info/api/api-2/search) with single-letter generator buttons

> **Coverage:** the content walk plus model labelers. Hero/monster tiles speak their names,
locked ones speak "Locked" like the sighted padlock (right-click detail = Backspace); item
tiles are named from their `Item`; achievement tiles speak name, achieved/not-achieved, and
the right-click-only detail text outright ("winner, not achieved, Complete any mode") —
including glyph-icon achievements whose text glyph would otherwise win the label. Checkbox
rows (bypass-unlocks and kin) speak checked/unchecked. Keyword buttons open their full detail
panels (rules + every side using the keyword — verified live on bloodlust); modifier filters
and pin search controls are ordinary buttons. Seen counts read as text. Type-ahead searches
within the content stop, so typing a hero/keyword name jumps straight to it.

## 7.3 Stuff Page — DONE

7 tabs:

- **Options** — the full settings screen (see Phase 8)
- **Credits** — text + URL links
- **Jukebox** — music volume slider, current-track live text with progress bar, skip/next buttons, per-song checkboxes (click name to play, right-click for details), DJ/shuffle and loop radio groups
- **Online** — leaderboards (see Phase 10)
- **Graph** — locked behind 20 challenges: plots side-value curves; series identified only by hash-colors and icons; add/remove sides via popup; no data table
- **Patch** — patch-notes text blobs per version (pure text)
- **Numbers** — lifetime stats rendered as **two parallel columns of separate actors** (name column + value column; association is purely spatial) + a "Reset Stats" button with confirmation

> **Coverage:** Numbers is rebuilt model-side — each stat reads as one "name value" line from
the same merged-stats data the columns render, plus the tab's own Reset Stats button (its
confirmation dialog rides the modal reader). The other tabs read through the content walk,
verified live: Jukebox (volume slider adjustable with percent readout, live current-track
text, transport buttons, per-song checkbox rows with checked state), Credits, Patch notes,
Online's leaderboard mode buttons. Options is 8.2's dedicated section. The Graph tab is locked
behind 20 challenges on this profile — unverified; its hash-colour series would need a data
table if it ever matters.

---

# Phase 8: Cog / Esc Menu and Settings

## 8.1 Cog Menu — DONE

`EscMenuUtils.makeFullEscMenu()` — opened by ESC or the cog button, anywhere:

1. **Display panel** (icon-only title): screen mode radio (windowed/fullscreen/fullscreen2), **UI size** as `-` / value / `+` raw text actors (not buttons), "Save Display" button
2. **Sound panel** (icon-only title): sfx + music **sliders**, current track, jukebox link, song controls
3. **Nav buttons**: back-to-last-almanac-page, Options, Help, Credits
4. **Misc**: bug report, "Save" (a joke button — explains autosave), **Flee** (in-run; the abandon-run action), **Quit** (in-run: save+exit to title) / **Exit** (title: quit-game dialog — the game's only quit affordance)

ESC closes it (`BasicKeyCatch` passes only ESC through).

> **Coverage:** the menu is a pushed modal the generic reader already covered; this pass named
its shorthand: the screen-mode buttons read "windowed / fullscreen / fullscreen 2" with their
radio state (checked/unchecked via the checkbox labeler), the UI-size steppers read
"decrease / increase" around the current adjustment value, and the jukebox transport reads
"skip back / skip forward / next song" (an exact-whole-label glyph map, so ordinary text never
remaps). Sliders adjust with Left/Right and speak percent. Flee/Quit verified live earlier;
nav buttons open the Book's pages. Escape closes throughout.

## 8.2 Options Screen — NOT STARTED

Book → stuff → Options (`OptionsMenu`). Header hint: "(right-click a checkbox to learn what it does)". Two columns: Gameplay+Modding+Music left, UI right. Locked options render as a padlock + "locked" (right-click → unlock requirement).

**Widget types (all pointer-only):**
- `BOption` = checkbox row (click toggles; **description only on right-click**; some have warning dialogs)
- `ChOption` = radio row inside a titled panel
- `FlOption` = **slider — drag-only**, reads raw mouse X every frame, no keyboard, no steps, no numeric readout

**Full option list** (`OptionLib`):

- **Gameplay:** bypass unlocks (warning dialog, rebuilds title screen), 5% generated heroes / items / monsters, 5% wild modifiers, 5%→20%, complex hard/easy, myriad offers, true-random pre-rotation
- **UI:** fast enemy turns, auto-flee, show timer, show clock, show rarity, target button, search button, triple chat, roman numerals, crazy UI, longtap end, hide spinning dice, smartphone controls, **colorblind poison** (pink instead of green), language (default/en/es/pt/fr/it/ru), roll speed (1x/1.5x/2x), dice size (6 steps), gap (stream letterbox), font (TannFont + 4 HD fonts), dying flash (dark/border/old), incoming debuffs (faded/plus/old), landscape lock (iOS)
- **Modding:** textmod +more, custom rearrange, disable marquee, tiny paste, horus levelup, api2 20
- **Music:** music popups, music loop, music selection
- Hidden/debug categories exist (FPS counter, phase display, render modes…)

> **Accessibility-relevant absences:** no text-size option (only whole-UI integer scaling), no narration/TTS, no screen-shake/motion toggle beyond hide-spinners/roll-speed, no colorblind support beyond poison pips, no keyboard remapping.

---

# Phase 9: Tutorial System — NOT STARTED

`screens/dungeon/panels/tutorial/` — an 85-px "Tutorial" box shown during rolling/targeting/level-end phases with up to 2 items:

- **Tips** (info glyph): contextual text ("Monsters show their intentions…", "If there is a skull on the End turn button, a hero is about to die")
- **Quests** (checkbox glyph): tracked tasks ("Reroll your dice", "Right-click a dice to learn what it does", "Shield some incoming damage", "Cast a spell", "Use a hero, then undo"…). Completion auto-detected; feedback is a flash + checkbox glyph change
- The close button responds **only to right-click** and opens Skip-all / Dismiss options
- Progress persists; reset only via Book → Help → Basics → Reset Tutorial

---

# Phase 10: Leaderboards — NOT STARTED

Book → stuff → Online (`gameplay/leaderboard/`, server at tann.fun):

- Grouped board buttons (streak boards per mode×difficulty, speedrun boards per mode, cursed highscore boards, legacy boards) → board display
- Board table: rank / name / score / date / platform as **five independent parallel columns** (rows associated only by vertical position); your row tinted pink; ranks 1–3 color-coded
- Paging buttons; loading/failure states as text
- **Submit Highscore** panel: name text-input (10 chars), submit button, embedded scrollable board; errors as dialogs

---

# Phase 11: Achievements and Unlocks — NOT STARTED

`gameplay/progress/` — achievements gate modes, difficulties, hero colors, options, features (party layout, events, tactics…):

- Completion toasts appear in the top-right popup holder (right-click for detail; 5 s auto-dismiss)
- On the title screen, dedicated unlock modals announce new modes/colors
- The Book's Unlock tab is the management surface (see 7.2)
- "Bypass unlocks" option unlocks everything while still allowing achievement earning
- Locked content is variously: blanked (mode names), removed (difficulties), padlocked (options), or dimmed (keywords)

---

# Phase 12: Existing Keyboard Support (complete map)

The game ships these hotkeys (documented in Book → Help → Tips via `DesktopControl.getTipsSnippets`):

| Key | Context | Effect |
|---|---|---|
| ESC | everywhere | close modals; else open cog menu |
| Space / Enter | rolling, targeting | Done Rolling / End Turn |
| R | rolling | roll dice |
| 1–9 | rolling | lock/unlock hero N's die |
| 1–9 | targeting | select hero N's die, or target entity N when something is selected |
| Shift+1–9 | targeting | target the opposite side (ally↔enemy) |
| Q W E R T Y U I | targeting | select ability slot 0–7 |
| Z | targeting | undo |
| Tab (hold) | rolling, targeting | show targeting arrows |
| I | level end / any equip-capable phase | open inventory |
| 1–9 | level end / choices | start reward N / toggle option N |
| Enter | level end | continue |
| Enter / Backspace | 2-choice dialogs only | accept / decline |
| Space/Enter(/Backspace) | message & reveal panels | OK |
| I / Enter / ESC | inventory screen | done |
| R | inventory screen | randomize equipment |
| Ctrl+V, Home, End, arrows… | text inputs | standard editing |

**Everything else is pointer-only.** The complete list of mouse-only interactions with no keyboard path:

1. Title screen entirely (drawer, mode buttons, start/difficulty/continue buttons, icon cluster)
2. Every `info()` action (right-click/long-press): all tooltips, descriptions, character sheets, achievement details, win records, modifier details, mana readout
3. The Book entirely (tabs, scrolling, all content interactions)
4. The cog menu's buttons, sliders, and UI-size adjusters
5. 3D dice tray per-die interaction (info panels, line-up/scatter)
6. Item drag-and-drop equipping (position-dependent slot choice)
7. Party layout cards, hero selector slots, custom mode editor, paste scenarios
8. Three-choice dialogs (surrender), the Cursed reset panel, the run-end panel
9. Top icon row (hash summary, hourglass schedule, hold-to-show target button)
10. Reinforcements panel, reroll ("anticheese") icon button in choices
11. All scroll panes (mouse wheel/drag only)

---

# Phase 13: Cross-Cutting Patterns (the real accessibility work)

These patterns repeat across every screen and define the mod's core challenges:

## 13.1 No Semantic Layer
Every widget is a raw scene2d Actor drawing sprites; state (checked, selected, focused, locked, used, dead) exists only as color/border/overlay choices. The mod must map actor types → semantics itself (e.g. `StandardButton.getText()`, `Checkbox` state booleans, `EntState` getters).

## 13.2 The `action()` / `info()` Split
Left-click acts; right-click/long-press explains. All explanatory text is *pull*, never hover, and never keyboard-reachable. Any screen-reader mod must synthesize access to `info()` content.

## 13.3 Color-Only Encodings
Mode selection, valid targets, incoming damage (yellow pips), poison (green pips), death prediction (red flash), used dice (dark overlay), reroll exhaustion (red label), locked/achieved states, pick-rate quality, leaderboard ranks, graph series — all color/animation only. The underlying data is programmatically available (`Snapshot`/`EntState`/`FightLog` temporalities).

## 13.4 Spatial-Only Tables
The Numbers stats page and leaderboard tables are parallel independent columns — row relationships exist only in pixel positions. Data must be re-associated at the model level.

## 13.5 Transient, Timed Surfaces
Bottom banners (~0.5 s), top-right popups (5 s), speech bubbles, flashes. No history/log exists anywhere. A screen reader needs interception at the emit points (`AbilityHolder.showInfo`, `PopupHolder.addText/addAchievement`, `Explanel` creation).

> **Coverage (partial):** the top-right popup holder is polled each frame and every new popup is
spoken once, so achievement and stat toasts are not missed. The in-combat bottom banner and the
explanation panels are Phase 3/6 work.

## 13.6 Layout Volatility
The title screen fully rebuilds on option/custom-mode changes; the top button row rebuilds every turn; portrait/landscape/width breakpoints change structure (and insert dummy tabs). Any focus/virtual-cursor model must be rebuilt from the model, not from actor identity.

## 13.7 The 3D Dice Tray
Dice live outside scene2d, are mouse-ray picked, and physically simulate — but rolls are pre-predicted (`setSideOverride`), and locking via keyboard (digits) already exists. Die faces and their calculated effects have full text (`EntSideState.describe()`).

## 13.8 Existing Text Assets
`lang/en.json` (~163 KB) holds every UI string; `EntSideState`/`Eff.describe()`/`Keyword.getRules()` generate calculated descriptions; `Explanel` content is all text. The vocabulary for narration already exists in the game.

---

# Phase 14: Summary of All Interactive Elements

## 14.1 Screens (top-level)
- Title screen (+ mode drawer + mode cards + folder navigation)
- Dungeon screen (all combat and between-fight play)
- Pause screen (mobile lifecycle artifact), dev Test/Roll screens — ignorable

## 14.2 Modal Panels and Dialogs
- Cog/esc menu; the Book (3 pages × 23 sub-tabs); ChoiceDialogs (2- and 3-choice); ChoicePhase offers; party layout picker; character sheets (EntPanelInventory); explanation panels (Explanel + keyword boxes); inventory/equip screen; hash summary; hourglass schedule; reinforcements list; mode info panels; unlock/achievement details; leaderboard modals; submit-highscore panel; language chooser; URL panels; text inputs; run-end stats

## 14.3 Persistent In-Fight HUD
- Hero panel column, monster panel column, 3D dice tray, ability/mana bar, Reroll/Undo buttons, Done Rolling/End Turn button, top icon row, tutorial box, minimap (level end), timers/clock (optional)

## 14.4 Recurring Choice Points (per run)
- Difficulty pick → party layout → starting modifier pick → per-fight: roll/lock/reroll → target/cast → end turn → level end: levelup (even), loot (odd), events (random), equip → boss every 4th → run end panel → stats

## 14.5 Meta Screens
- Options (30+ settings), Help (8 tabs), Ledger/collection (8 tabs, ~130 heroes / ~75+ monsters / 200+ items / ~190 keywords / modifiers), achievements, leaderboards, jukebox, graph, patch notes, stats, custom mode editor, leaderboard submission

---

*End of Document*
