package snd.core.update;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The version logic behind the launch update announcement: what version a
 * GitHub latest-release payload names, and whether it outranks the running
 * build. The fetch itself is the module's {@code UpdateChecker}; this stays
 * pure so the comparison rules are unit-tested. Ported from Guildrun Access
 * by way of Echopunks.
 */
public final class UpdateCheck {
    private static final Pattern TAG = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");

    private UpdateCheck() {
    }

    /**
     * The version the release payload names: its tag_name value with any
     * leading v stripped, or null when the payload carries none.
     */
    public static String latestVersion(String releaseJson) {
        if (releaseJson == null) {
            return null;
        }
        Matcher m = TAG.matcher(releaseJson);
        if (!m.find()) {
            return null;
        }
        String tag = m.group(1).trim();
        if (!tag.isEmpty() && (tag.charAt(0) == 'v' || tag.charAt(0) == 'V')) {
            tag = tag.substring(1);
        }
        return tag.isEmpty() ? null : tag;
    }

    /**
     * Strict greater-than on dot-separated numeric components. Missing
     * components count as zero ("1.0" equals "1") and non-numeric ones parse
     * as zero, so a malformed tag compares safe instead of throwing.
     */
    public static boolean isNewer(String remote, String local) {
        String[] r = remote.split("\\.");
        String[] l = local.split("\\.");
        int length = Math.max(r.length, l.length);
        for (int i = 0; i < length; i++) {
            int remotePart = component(r, i);
            int localPart = component(l, i);
            if (remotePart != localPart) {
                return remotePart > localPart;
            }
        }
        return false;
    }

    private static int component(String[] parts, int index) {
        if (index >= parts.length) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[index].trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
