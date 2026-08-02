package snd.module;

import snd.core.HostServices;
import snd.core.ModModule;
import snd.core.SndLog;

/**
 * The reloadable module — where day-to-day feature work goes. Phase 1 scope:
 * greet on boot and announce screen changes by polling the game's current
 * screen each frame. The class name is a fixed contract with the host's
 * ModuleLoader (snd.module.SndModule).
 */
public class SndModule implements ModModule {
    private HostServices host;
    private Object lastScreen;
    private boolean greeted;

    @Override
    public void load(HostServices h) {
        this.host = h;
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
    }

    @Override
    public void dispose() {
        // nothing permanent held yet
    }

    @Override
    public String devCommand(String command, String arg) {
        if ("gui".equals(command)) {
            StringBuilder sb = new StringBuilder();
            try {
                com.tann.dice.screens.Screen screen = com.tann.dice.Main.getCurrentScreen();
                sb.append("screen: ").append(screen == null ? "none" : screen.getClass().getSimpleName()).append('\n');
                if (screen instanceof com.tann.dice.screens.dungeon.DungeonScreen) {
                    sb.append("phase: ")
                            .append(com.tann.dice.gameplay.phase.PhaseManager.get().getPhase().getClass().getSimpleName())
                            .append('\n');
                }
            } catch (Throwable t) {
                sb.append("(error reading game state: ").append(t).append(")\n");
            }
            sb.append("graph: not built yet (phase 3)");
            return sb.toString();
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
