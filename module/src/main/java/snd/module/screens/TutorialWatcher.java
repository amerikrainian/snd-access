package snd.module.screens;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.tann.dice.screens.dungeon.DungeonScreen;
import com.tann.dice.screens.dungeon.panels.tutorial.TutorialHolder;
import com.tann.dice.screens.dungeon.panels.tutorial.TutorialItem;
import com.tann.dice.screens.dungeon.panels.tutorial.TutorialQuest;

import snd.core.loc.Loc;
import snd.core.speech.SpeechPipeline;

/**
 * Speaks tutorial quest completion: the game's only feedback is a flash and
 * a checkbox-glyph swap. Polled from the module tick; items are tracked by
 * identity, so a completion flip speaks exactly once.
 */
public final class TutorialWatcher {
    private final SpeechPipeline speech;
    private Map<TutorialItem, Boolean> known = new IdentityHashMap<TutorialItem, Boolean>();
    private Map<TutorialItem, Boolean> next = new IdentityHashMap<TutorialItem, Boolean>();

    public TutorialWatcher(SpeechPipeline speech) {
        this.speech = speech;
    }

    public void tick() {
        DungeonScreen ds = DungeonScreen.get();
        if (ds == null) {
            known.clear();
            return;
        }
        TutorialHolder holder;
        try {
            holder = ds.getTutorialManager().tutorialHolder;
        } catch (Throwable t) {
            return; // the screen is mid-construction
        }
        if (holder == null) {
            known.clear();
            return;
        }
        List<TutorialItem> items = TutorialNodes.items(holder);
        next.clear();
        for (TutorialItem item : items) {
            boolean complete = item.isComplete();
            Boolean previous = known.get(item);
            if (previous != null && !previous && complete && item instanceof TutorialQuest) {
                speech.speak(Loc.get("ui", "tutorial.complete",
                        "quest", item.getSortText()), false);
            }
            next.put(item, complete);
        }
        Map<TutorialItem, Boolean> swap = known;
        known = next;
        next = swap;
    }
}
