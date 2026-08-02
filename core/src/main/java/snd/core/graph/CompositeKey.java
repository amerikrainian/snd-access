package snd.core.graph;

import java.util.Arrays;

/**
 * A typed structural key built from value parts — the house alternative to the
 * reference codebase's string-concatenated hashCode keys, whose collisions
 * silently rebadged one control as another (and crashed on the builder's
 * duplicate-id guard). Parts compare by equals, so screens key controls as
 * {@code CompositeKey.of("inv", hero, slot)} without ever formatting a string.
 */
public final class CompositeKey {
    private final Object[] parts;

    private CompositeKey(Object[] parts) {
        this.parts = parts;
    }

    public static CompositeKey of(Object... parts) {
        if (parts == null || parts.length == 0) {
            throw new IllegalArgumentException("a CompositeKey needs at least one part");
        }
        return new CompositeKey(parts.clone());
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof CompositeKey && Arrays.equals(parts, ((CompositeKey) obj).parts);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(parts);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append(':');
            }
            sb.append(parts[i]);
        }
        return sb.toString();
    }
}
