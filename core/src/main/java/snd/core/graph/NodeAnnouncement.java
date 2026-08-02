package snd.core.graph;

import java.util.function.Supplier;

/**
 * One part of a control's spoken focus readout ("End turn" / "button" / "on"),
 * resolved live at speak time. A LIVE part is additionally watched while its
 * node is focused: when its resolved text changes (a value the game flips),
 * the navigator speaks just that part immediately — state feedback without
 * re-reading the whole control, and without per-element watcher machinery.
 */
public final class NodeAnnouncement {
    /** The part's text, resolved live. Null/empty at speak time = silent. */
    public Supplier<String> text;

    /** Watch this part while the node is focused; speak it when it changes. */
    public boolean live;

    /**
     * The part's kind ({@link AnnouncementKinds}), or null for a custom
     * one-off part. Kinds drive the control type's speak order, let a node's
     * part override the type's common part of the same kind, and key the
     * user's per-kind announcement settings.
     */
    public String kind;

    public NodeAnnouncement(Supplier<String> text) {
        this(text, false, null);
    }

    public NodeAnnouncement(Supplier<String> text, boolean live, String kind) {
        this.text = text;
        this.live = live;
        this.kind = kind;
    }

    public static NodeAnnouncement of(final String text) {
        return new NodeAnnouncement(new Supplier<String>() {
            @Override
            public String get() {
                return text;
            }
        });
    }

    public static NodeAnnouncement kinded(Supplier<String> text, String kind) {
        return new NodeAnnouncement(text, false, kind);
    }
}
