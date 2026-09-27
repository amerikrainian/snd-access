package snd.module;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

import snd.contracts.SndLog;
import snd.core.update.UpdateCheck;

/**
 * Fetches the newest GitHub release on a background thread and holds the
 * answer for the module's tick to announce: the request never touches the
 * render thread. Only a release strictly newer than the running build ever
 * surfaces; up to date, ahead of the release (a dev build), offline,
 * rate-limited or no release published at all (404) stay silent with a log
 * line. Started once per game launch (generation 1: a dev reload does not ask
 * again). Ported from Guildrun Access by way of Echopunks.
 */
final class UpdateChecker {
    private static final String API_URL = "https://api.github.com/repos/amerikrainian/snd-access/releases/latest";
    private static final int TIMEOUT_MS = 10000;

    private volatile String newerVersion;

    /**
     * The version to announce, set once the background request found a
     * release strictly newer than the running build; null before that, and
     * forever when none is.
     */
    String newerVersion() {
        return newerVersion;
    }

    void start(final String local) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                check(local);
            }
        }, "snd-update-check");
        t.setDaemon(true);
        t.start();
    }

    // The whole request on the background thread, blocking there. Never throws.
    private void check(String local) {
        try {
            // SND_UPDATE_URL overrides the feed, like the installer's
            // releases-URL override: an end-to-end check can point at any
            // latest-release payload (a file: URL works) without publishing one.
            String url = System.getenv("SND_UPDATE_URL");
            if (url == null || url.trim().isEmpty()) {
                url = API_URL;
            }
            String json = fetch(url);
            String remote = UpdateCheck.latestVersion(json);
            if (remote == null) {
                SndLog.info("[update] release payload named no version");
                return;
            }
            if (UpdateCheck.isNewer(remote, local)) {
                SndLog.info("[update] " + remote + " available (running " + local + ")");
                newerVersion = remote;
            } else {
                SndLog.info("[update] up to date (latest " + remote + ", running " + local + ")");
            }
        } catch (Throwable t) {
            SndLog.info("[update] check failed (" + t + ")");
        }
    }

    private static String fetch(String url) throws Exception {
        URLConnection c = new URL(url).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        if (c instanceof HttpURLConnection) {
            c.setRequestProperty("User-Agent", "snd-access-mod");
            c.setRequestProperty("Accept", "application/vnd.github+json");
            int status = ((HttpURLConnection) c).getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                ((HttpURLConnection) c).disconnect();
                throw new IllegalStateException("HTTP " + status);
            }
        }
        InputStream in = c.getInputStream();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }
}
