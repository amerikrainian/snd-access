package snd.host;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

/**
 * JNA binding of the subset of prism.dll's C API the mod uses (mirrors the
 * reference P/Invoke layers in wotr-access / NonVisualCalculus): context
 * lifecycle, registry enumeration, per-backend speak/stop/free. All strings
 * are null-terminated UTF-8; C bool is passed as a byte.
 */
interface PrismLib extends Library {
    // PrismError values (subset we check) — from third_party/prism/prism.h.
    // NOTE: older bindings (the C# reference mods) used a different enum
    // ordering; these match the vendored dll.
    int OK = 0;
    int NOT_IMPLEMENTED = 3;
    int ALREADY_INITIALIZED = 15;

    Pointer prism_init(Pointer config);

    void prism_shutdown(Pointer ctx);

    long prism_registry_count(Pointer ctx);

    long prism_registry_id_at(Pointer ctx, long index);

    Pointer prism_registry_name(Pointer ctx, long id);

    Pointer prism_registry_create(Pointer ctx, long id);

    Pointer prism_registry_create_best(Pointer ctx);

    int prism_backend_initialize(Pointer backend);

    void prism_backend_free(Pointer backend);

    long prism_backend_get_features(Pointer backend);

    Pointer prism_backend_name(Pointer backend);

    int prism_backend_speak(Pointer backend, byte[] textUtf8, byte interrupt);

    int prism_backend_stop(Pointer backend);

    final class Loader {
        private Loader() {
        }

        static PrismLib load() {
            return Native.load("prism", PrismLib.class);
        }
    }
}
