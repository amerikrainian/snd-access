package snd.module;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;

import snd.core.HostServices;
import snd.core.ModModule;
import snd.core.SndLog;
import snd.core.buffers.EventLog;
import snd.core.input.InputRegistry;
import snd.core.loc.Loc;
import snd.core.nav.GraphDefaults;
import snd.core.nav.GraphNavigator;
import snd.core.nav.NavAction;
import snd.core.nav.ScreenManager;
import snd.module.screens.BookScreen;
import snd.module.screens.ChoiceConfirmScreen;
import snd.module.screens.ChoiceScreen;
import snd.module.screens.CombatScreen;
import snd.module.screens.DialogPhaseScreen;
import snd.module.screens.GameModalScreen;
import snd.module.screens.HelpScreen;
import snd.module.screens.InventoryScreen;
import snd.module.screens.LevelEndScreen;
import snd.module.screens.PauseRecoveryScreen;
import snd.module.screens.RunEndStatsScreen;
import snd.module.screens.SheetScreen;
import snd.module.screens.TitleFlowScreen;

/**
 * The reloadable module — where day-to-day feature work goes. Wires the graph
 * navigator, the screen stack, and the input processor over the game. The
 * class name is a fixed contract with the host's ModuleLoader.
 */
public class SndModule implements ModModule {
    private HostServices host;
    private GraphNavigator nav;
    private ScreenManager screens;
    private SndInput input;
    private HelpScreen help;
    private Buffers buffers;
    private EventLog events;
    private PopupWatcher popups;
    private PhaseWatcher phases;
    private DiceWatcher dice;
    private TargetingWatcher targeting;
    private BannerWatcher banners;
    private TextEntryWatcher textEntry;
    private snd.module.screens.TutorialWatcher tutorial;
    private Object lastScreen;
    private boolean greeted;

    @Override
    public void load(HostServices h) {
        this.host = h;
        // Core reloads with the module: a copy resolved from a permanent
        // loader (core merged into the host jar, or put on the classpath)
        // would shadow every rebuilt one, and reloads would change nothing.
        if (GraphNavigator.class.getClassLoader() != SndModule.class.getClassLoader()) {
            throw new IllegalStateException("snd.core resolved from " + GraphNavigator.class.getClassLoader()
                    + ", not the module loader — core must ship in the module jar only");
        }
        GraphDefaults.install();
        Locales.load(); // before anything speaks
        nav = new GraphNavigator(h.speech());
        screens = new ScreenManager(nav, h.speech());
        screens.register(new TitleFlowScreen(h));
        screens.register(new ChoiceScreen(h));
        screens.register(new GameModalScreen());
        screens.register(new ChoiceConfirmScreen(h));
        screens.register(new CombatScreen(h));
        screens.register(new SheetScreen(h));
        screens.register(new LevelEndScreen(h));
        screens.register(new DialogPhaseScreen());
        screens.register(new InventoryScreen(h));
        screens.register(new PauseRecoveryScreen());
        screens.register(new RunEndStatsScreen());
        screens.register(new BookScreen(h));
        help = new HelpScreen(h, nav);
        screens.register(help);
        events = new EventLog(h.speech());
        buffers = new Buffers(nav, events, h.speech());
        InputRegistry keys = SndKeys.build(help, nav, buffers, h.speech());
        help.setKeys(keys);
        input = new SndInput(screens, nav, help, keys);
        popups = new PopupWatcher(events);
        phases = new PhaseWatcher(events);
        dice = new DiceWatcher(h.speech(), events);
        targeting = new TargetingWatcher(h.speech(), events);
        banners = new BannerWatcher(events);
        textEntry = new TextEntryWatcher(h.speech());
        tutorial = new snd.module.screens.TutorialWatcher(events);
        SndLog.info("module generation " + h.generation() + " loaded");
        if (h.generation() > 1) {
            h.speech().speak(Loc.get("ui", "module_reloaded", "generation", h.generation()), true);
        }
    }

    private static final boolean DEV = "1".equals(System.getProperty("snd.dev"))
            || "1".equals(System.getenv("SND_DEV"));

    @Override
    public void tick() {
        if (DEV) {
            // Keep the loop rendering for the dev server (the host can't:
            // it never links against game classes).
            DevDriver.ensureContinuousRendering();
        }
        com.tann.dice.screens.Screen screen;
        try {
            screen = com.tann.dice.Main.getCurrentScreen();
        } catch (Throwable t) {
            return; // the game is still booting
        }
        if (screen == null) {
            return;
        }
        Locales.tick(); // follow the game's live language option
        if (!greeted) {
            greeted = true;
            host.speech().speak(Loc.get("ui", "greeting"), false);
        }
        if (screen != lastScreen) {
            boolean first = lastScreen == null;
            lastScreen = screen;
            if (screen instanceof com.tann.dice.screens.titleScreen.TitleScreen) {
                events.clear(); // a run's log is that run's alone
            }
            host.speech().speak(spokenName(screen), !first);
        }

        reassertInput();
        screens.tick();
        help.tick();
        buffers.tick();
        popups.tick();
        phases.tick();
        dice.tick();
        targeting.tick();
        banners.tick();
        textEntry.tick();
        tutorial.tick();
    }

    // The game rebuilds its InputMultiplexer in Main.setupScale (resize,
    // scale change), so ownership of the head slot is re-checked every frame.
    private void reassertInput() {
        InputProcessor proc = Gdx.input.getInputProcessor();
        if (!(proc instanceof InputMultiplexer)) {
            return;
        }
        InputMultiplexer m = (InputMultiplexer) proc;
        if (m.getProcessors().size == 0 || m.getProcessors().first() != input) {
            m.removeProcessor(input);
            m.addProcessor(0, input);
            SndLog.info("input processor installed at multiplexer head");
        }
    }

    @Override
    public void dispose() {
        // The game's multiplexer must not keep a reference into this module's
        // classloader — that's the reload leak the canary watches for.
        try {
            InputProcessor proc = Gdx.input.getInputProcessor();
            if (proc instanceof InputMultiplexer) {
                ((InputMultiplexer) proc).removeProcessor(input);
            }
        } catch (Throwable t) {
            SndLog.error("failed to remove input processor on dispose", t);
        }
    }

    @Override
    public String devCommand(String command, String arg) {
        if ("input".equals(command) && arg != null) {
            return DevDriver.input(arg, input);
        }
        if ("screenshot".equals(command) && arg != null) {
            return DevDriver.screenshot(arg);
        }
        if ("gui".equals(command)) {
            StringBuilder sb = new StringBuilder();
            try {
                com.tann.dice.screens.Screen screen = com.tann.dice.Main.getCurrentScreen();
                sb.append("game screen: ").append(screen == null ? "none" : screen.getClass().getSimpleName()).append('\n');
                if (screen instanceof com.tann.dice.screens.dungeon.DungeonScreen) {
                    sb.append("phase: ")
                            .append(com.tann.dice.gameplay.phase.PhaseManager.get().getPhase().getClass().getSimpleName())
                            .append('\n');
                }
            } catch (Throwable t) {
                sb.append("(error reading game state: ").append(t).append(")\n");
            }
            sb.append(nav.devDump());
            return sb.toString();
        }
        if ("buffers".equals(command)) {
            return buffers.devDump();
        }
        if ("nav".equals(command) && arg != null) {
            try {
                NavAction action = NavAction.valueOf(arg.trim().toUpperCase());
                boolean consumed = nav.onAction(action);
                return "nav " + action + " consumed=" + consumed;
            } catch (IllegalArgumentException e) {
                return "unknown nav action: " + arg;
            }
        }
        return null;
    }

    /** The screen's locale name when one exists, else "TitleScreen" -> "Title Screen". */
    private static String spokenName(Object screen) {
        String simple = screen.getClass().getSimpleName();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < simple.length(); i++) {
            char c = simple.charAt(i);
            if (i > 0 && Character.isUpperCase(c) && !Character.isUpperCase(simple.charAt(i - 1))) {
                sb.append(' ');
            }
            sb.append(c);
        }
        return Loc.getOrDefault("ui", "screen." + simple, sb.toString());
    }
}
