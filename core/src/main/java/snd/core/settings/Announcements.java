package snd.core.settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import snd.contracts.SndLog;
import snd.core.graph.GraphAnnouncer;

/**
 * What the mod speaks, as parts the player can turn off and reorder (the
 * SayTheSpire2 model). A READOUT is one kind of thing spoken — a button's
 * focus line, a combatant's row, the hero buffer, an enemy-intent event —
 * declared with its parts in their default order. Each part has a global
 * "speak" setting ("announcements.hp.enabled") and, on every readout that
 * has it, a setting that follows the global one until the player sets it
 * ("readouts.combatant.hp.enabled"). A readout's order is saved on its page
 * ("readouts.combatant.order").
 *
 * <p>Readouts live in groups, one settings page each: {@link #READOUTS} (the
 * focus lines, read by the navigator through {@link GraphAnnouncer.PartPolicy}),
 * {@link #BUFFERS}, {@link #GLANCES}, {@link #EVENTS}. A readout is named
 * "group.key" ("events.intent"). Labels come from the "ui" locale table:
 * "part.&lt;part&gt;" and "&lt;group&gt;.&lt;key&gt;".</p>
 */
public final class Announcements implements GraphAnnouncer.PartPolicy {
    public static final String READOUTS = "readouts";
    public static final String BUFFERS = "buffers";
    public static final String GLANCES = "glances";
    public static final String EVENTS = "events";

    private static final class Readout {
        CategorySetting page;
        PartOrder order;
        final Map<String, BoolSetting> enabled = new HashMap<String, BoolSetting>();
        final Map<String, String> suffixes = new HashMap<String, String>();
        BoolSetting announce;
        BoolSetting log;
    }

    /** What follows a part's text before the next part, unless the readout gives it another. */
    public static final String DEFAULT_SUFFIX = ",";

    private final CategorySetting root;
    private final CategorySetting global;
    private final Map<String, Readout> readouts = new LinkedHashMap<String, Readout>();
    private final Set<String> warned = new HashSet<String>();

    /** Add the announcement pages to the settings root. */
    public Announcements(CategorySetting root) {
        this.root = root;
        this.global = root.add(new CategorySetting("announcements", Setting.loc("settings.announcements")));
    }

    /** The global page of a part, made on first use. */
    public CategorySetting part(String part) {
        CategorySetting page = global.category(part, Setting.loc("part." + part));
        if (page.child("enabled") == null) {
            page.add(new BoolSetting("enabled", Setting.loc("settings.speak"), true));
        }
        return page;
    }

    /** The settings page of a group of readouts, made on first use. */
    public CategorySetting group(String group) {
        return root.category(group, Setting.loc("settings." + group));
    }

    /**
     * Declare a readout of group with these parts in their default speaking
     * order. Returns its page, where the module may add settings of its own
     * (an event's "announce").
     */
    public CategorySetting readout(String group, String key, List<String> parts) {
        String id = group + "." + key;
        if (readouts.containsKey(id)) {
            throw new IllegalArgumentException("readout " + id + " declared twice");
        }
        Readout r = new Readout();
        r.page = group(group).add(new CategorySetting(key, Setting.loc(id)));
        r.order = PartOrder.attach(r.page, parts);
        for (String p : parts) {
            BoolSetting globalEnabled = (BoolSetting) part(p).child("enabled");
            CategorySetting partPage = r.page.add(new CategorySetting(p, Setting.loc("part." + p)));
            r.enabled.put(p, partPage.add(BoolSetting.inheriting("enabled", Setting.loc("settings.speak"),
                    globalEnabled)));
        }
        readouts.put(id, r);
        return r.page;
    }

    public boolean has(String id) {
        return readouts.containsKey(id);
    }

    /**
     * What follows part's text in readout id wherever it lands: ":" after an
     * intent's name ("Rat 1: 2 damage"), "" for a name the rest runs on from
     * ("Bandit 1 2"). The default is {@link #DEFAULT_SUFFIX}.
     */
    public void suffix(String id, String part, String suffix) {
        Readout r = readouts.get(id);
        if (r == null || !r.enabled.containsKey(part)) {
            throw new IllegalArgumentException(id + " has no part " + part);
        }
        r.suffixes.put(part, suffix);
    }

    /**
     * Declare an event: a readout of {@link #EVENTS} whose page also has
     * whether it is spoken and whether it is kept in the log buffer.
     */
    public CategorySetting event(String key, List<String> parts) {
        CategorySetting page = readout(EVENTS, key, parts);
        Readout r = readouts.get(EVENTS + "." + key);
        r.announce = page.add(new BoolSetting("announce", Setting.loc("settings.announce"), true));
        r.log = page.add(new BoolSetting("log", Setting.loc("settings.log"), true));
        return page;
    }

    /** Whether event key is spoken. */
    public boolean announces(String key) {
        Readout r = readout(EVENTS + "." + key);
        return r == null || r.announce == null || r.announce.get();
    }

    /** Whether event key is kept in the log buffer. */
    public boolean logs(String key) {
        Readout r = readout(EVENTS + "." + key);
        return r == null || r.log == null || r.log.get();
    }

    /** The parts of readout id in speaking order. */
    public List<String> parts(String id) {
        Readout r = readout(id);
        return r != null ? r.order.order() : new ArrayList<String>();
    }

    /** Whether part speaks in readout id. A part the readout does not declare follows its global setting. */
    public boolean speaks(String id, String part) {
        Readout r = readout(id);
        BoolSetting s = r != null ? r.enabled.get(part) : null;
        return s != null ? s.get() : globalSpeaks(part);
    }

    private boolean globalSpeaks(String part) {
        CategorySetting g = (CategorySetting) global.child(part);
        return g == null || ((BoolSetting) g.child("enabled")).get();
    }

    /**
     * The spoken line of readout id from its parts' texts (part key to text,
     * any order): the speaking parts with text, in the readout's order, each
     * followed by its suffix and a space before the next. Null when nothing
     * is left to say.
     */
    public String compose(String id, Map<String, String> texts) {
        Readout r = readout(id);
        StringBuilder sb = new StringBuilder();
        String pending = null;
        for (String p : parts(id)) {
            String t = texts.get(p);
            if (t == null || t.isEmpty() || !speaks(id, p)) {
                continue;
            }
            if (pending != null) {
                sb.append(pending).append(' ');
            }
            sb.append(t);
            String suffix = r != null ? r.suffixes.get(p) : null;
            pending = suffix != null ? suffix : DEFAULT_SUFFIX;
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * The lines of a sectioned readout (a buffer) from each part's lines
     * (part key to lines, any order): the speaking parts' lines, part by part
     * in the readout's order.
     */
    public List<String> lines(String id, Map<String, List<String>> sections) {
        List<String> out = new ArrayList<String>();
        for (String p : parts(id)) {
            List<String> section = sections.get(p);
            if (section != null && speaks(id, p)) {
                out.addAll(section);
            }
        }
        return out;
    }

    private Readout readout(String id) {
        Readout r = readouts.get(id);
        if (r == null && warned.add(id)) {
            SndLog.error("announcements: no readout " + id + " declared", null);
        }
        return r;
    }

    // ---- the navigator's view: a node's readout is a key in READOUTS; an
    // undeclared one is logged once and keeps its control type's order ----

    @Override
    public List<String> order(String readout) {
        Readout r = readout(READOUTS + "." + readout);
        return r != null ? r.order.order() : null;
    }

    @Override
    public boolean enabled(String readout, String part) {
        String id = READOUTS + "." + readout;
        return readout != null && readouts.containsKey(id) ? speaks(id, part) : globalSpeaks(part);
    }
}
