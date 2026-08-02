package snd.module;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;

import snd.core.HostServices;
import snd.core.ModModule;
import snd.core.SndLog;
import snd.core.nav.GraphNavigator;
import snd.core.nav.NavAction;
import snd.core.nav.ScreenManager;
import snd.module.screens.ChoiceScreen;
import snd.module.screens.GameModalScreen;
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
    private PopupWatcher popups;
    private Object lastScreen;
    private boolean greeted;

    @Override
    public void load(HostServices h) {
        this.host = h;
        nav = new GraphNavigator(h.speech());
        screens = new ScreenManager(nav, h.speech());
        screens.register(new TitleFlowScreen(h));
        screens.register(new ChoiceScreen());
        screens.register(new GameModalScreen());
        input = new SndInput(screens, nav);
        popups = new PopupWatcher(h.speech());
        SndLog.info("module generation " + h.generation() + " loaded");
        if (h.generation() > 1) {
            h.speech().speak("Module reloaded, generation " + h.generation(), true);
        }
    }

    @Override
    public void tick() {
        com.tann.dice.screens.Screen screen;
        try {
            screen = com.tann.dice.Main.getCurrentScreen();
        } catch (Throwable t) {
            return; // the game is still booting
        }
        if (screen == null) {
            return;
        }
        if (!greeted) {
            greeted = true;
            host.speech().speak("Slice and Dice access loaded", false);
        }
        if (screen != lastScreen) {
            boolean first = lastScreen == null;
            lastScreen = screen;
            host.speech().speak(spokenName(screen), !first);
        }

        reassertInput();
        screens.tick();
        popups.tick();
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

    /** "TitleScreen" -> "Title Screen". */
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
        return sb.toString();
    }
}
