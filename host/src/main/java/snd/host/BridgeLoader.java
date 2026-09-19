package snd.host;

/**
 * Parent for the module's classloader: resolves game classes from the game's
 * own loader (the shipped shim keeps them out of the system loader) and
 * everything else — snd.contracts, JNA, the JDK — from the host's loader.
 * snd.core is in neither: it ships in the module jar and resolves from the
 * module's own loader. Under the dev launch both are the system loader and
 * this is a plain pass-through.
 */
final class BridgeLoader extends ClassLoader {
    private final ClassLoader game;
    private final ClassLoader host;

    BridgeLoader(ClassLoader game, ClassLoader host) {
        super(null);
        this.game = game;
        this.host = host;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (game != null && game != host) {
            try {
                return game.loadClass(name);
            } catch (ClassNotFoundException ignored) {
                // fall through to the host loader
            }
        }
        return host.loadClass(name);
    }

    @Override
    protected java.net.URL findResource(String name) {
        java.net.URL url = game != null ? game.getResource(name) : null;
        return url != null ? url : host.getResource(name);
    }
}
