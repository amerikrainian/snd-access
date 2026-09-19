package snd.host;

import java.nio.charset.StandardCharsets;

import com.sun.jna.Pointer;
import snd.contracts.SndLog;
import snd.contracts.speech.SpeechPipeline;

/**
 * The Prism-backed speech sink. Acquire-then-adopt like the reference
 * handlers: context, then the best available backend (NVDA, JAWS, SAPI, ...).
 * Any failure leaves the pipeline backend-less — spoken lines still reach the
 * dev tap, so headless/dev sessions keep working without a screen reader.
 */
final class PrismBackend implements SpeechPipeline.Backend {
    private final PrismLib lib;
    private final Pointer ctx;
    private final Pointer backend;

    private PrismBackend(PrismLib lib, Pointer ctx, Pointer backend) {
        this.lib = lib;
        this.ctx = ctx;
        this.backend = backend;
    }

    /** @return a live backend, or null (already logged) when unavailable. */
    static PrismBackend tryCreate() {
        PrismLib lib;
        try {
            lib = PrismLib.Loader.load();
        } catch (Throwable t) {
            SndLog.error("prism.dll not loadable (jna.library.path? VC++ runtime?)", t);
            return null;
        }
        Pointer ctx = lib.prism_init(null);
        if (ctx == null) {
            SndLog.error("prism_init returned null", null);
            return null;
        }
        Pointer backend = lib.prism_registry_create_best(ctx);
        if (backend == null) {
            SndLog.error("prism: no usable speech backend on this machine", null);
            lib.prism_shutdown(ctx);
            return null;
        }
        int init = lib.prism_backend_initialize(backend);
        if (init != PrismLib.OK && init != PrismLib.ALREADY_INITIALIZED && init != PrismLib.NOT_IMPLEMENTED) {
            SndLog.error("prism backend initialize failed: " + init, null);
            lib.prism_backend_free(backend);
            lib.prism_shutdown(ctx);
            return null;
        }
        String backendName = utf8(lib.prism_backend_name(backend));
        SndLog.info("prism backend acquired: " + (backendName != null ? backendName : "<unknown>")
                + " (features=0x" + Long.toHexString(lib.prism_backend_get_features(backend)) + ")");
        return new PrismBackend(lib, ctx, backend);
    }

    @Override
    public boolean speak(String text, boolean interrupt) {
        try {
            return lib.prism_backend_speak(backend, utf8z(text), (byte) (interrupt ? 1 : 0)) == PrismLib.OK;
        } catch (Throwable t) {
            SndLog.error("prism speak failed", t);
            return false;
        }
    }

    @Override
    public void stop() {
        try {
            lib.prism_backend_stop(backend);
        } catch (Throwable t) {
            SndLog.error("prism stop failed", t);
        }
    }

    private static byte[] utf8z(String s) {
        byte[] raw = s.getBytes(StandardCharsets.UTF_8);
        byte[] z = new byte[raw.length + 1];
        System.arraycopy(raw, 0, z, 0, raw.length);
        return z;
    }

    private static String utf8(Pointer p) {
        if (p == null) {
            return null;
        }
        long len = 0;
        while (p.getByte(len) != 0) {
            len++;
        }
        byte[] bytes = p.getByteArray(0, (int) len);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
