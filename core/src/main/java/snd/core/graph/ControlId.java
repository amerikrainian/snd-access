package snd.core.graph;

/**
 * The identity of a control (graph node) — a two-tier identity so focus can be
 * followed across rebuilds even when the world shifts under us. Ported from
 * wotr-access / Tanglebeep, which upgraded Factorio Access's plain string node
 * key.
 *
 * <p><b>reference</b> (optional) is the game/domain object a node was derived
 * from (an Ent, an Item, a Mode), compared by reference identity.
 * <b>structuralKey</b> (always present) is a value-equatable key — a string,
 * or a {@link CompositeKey}.</p>
 *
 * <p>Two controls are "the same" when their references are identical (tier 1 —
 * follows an object that MOVED, its structural key changing) OR their
 * structural keys are equal (tier 2 — follows a logical control whose backing
 * object was rebuilt).</p>
 *
 * <p>Equality/hashing is defined on structuralKey alone, so it is a stable map
 * key. The reference tier is metadata, applied explicitly during focus
 * reconciliation via {@link #referenceMatches}.</p>
 */
public final class ControlId {
    /** The originating game/domain object, or null. Matched by identity. */
    public final Object reference;

    /** The value-equatable structural identity. Never null. */
    public final Object structuralKey;

    private ControlId(Object reference, Object structuralKey) {
        if (structuralKey == null) {
            throw new IllegalArgumentException("structuralKey must not be null");
        }
        this.reference = reference;
        this.structuralKey = structuralKey;
    }

    /** A control identified only by a structural key (no backing object). */
    public static ControlId structural(Object structuralKey) {
        return new ControlId(null, structuralKey);
    }

    /** A control with both tiers: a backing object and a structural key. */
    public static ControlId referenced(Object reference, Object structuralKey) {
        return new ControlId(reference, structuralKey);
    }

    /**
     * A control identified by a backing object only — the object doubles as
     * the structural key (equality collapses to identity).
     */
    public static ControlId forObject(Object reference) {
        if (reference == null) {
            throw new IllegalArgumentException("reference must not be null");
        }
        return new ControlId(reference, reference);
    }

    /** Tier-1 test: is obj this control's backing object? */
    public boolean referenceMatches(Object obj) {
        return reference != null && reference == obj;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ControlId)) {
            return false;
        }
        return structuralKey.equals(((ControlId) obj).structuralKey);
    }

    @Override
    public int hashCode() {
        return structuralKey.hashCode();
    }

    @Override
    public String toString() {
        return reference == null
                ? "ControlId(" + structuralKey + ")"
                : "ControlId(" + structuralKey + ", ref=" + reference + ")";
    }
}
