package snd.core;

/**
 * The reloadable module's contract. Implemented by exactly one class in the
 * module jar, found by its fixed name {@code snd.module.SndModule}. Loaded into
 * a fresh child classloader per reload; must hold no native handles and install
 * nothing permanent, or it cannot be torn down.
 */
public interface ModModule {
    /** Called once after construction, on the game's render thread. */
    void load(HostServices host);

    /** Called every frame from the pump, on the game's render thread. */
    void tick();

    /** Called on the old module during a reload, after the new one is live. */
    void dispose();

    /**
     * Dev-driver seam: the host's dev server forwards commands it cannot serve
     * itself (e.g. "gui"). Only JDK/core types cross this boundary. Returns
     * null for "not handled".
     */
    default String devCommand(String command, String arg) {
        return null;
    }
}
