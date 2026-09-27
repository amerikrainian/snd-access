package snd.module;

/**
 * The running mod's version: {@code modVersion} from gradle.properties, which
 * the module jar's manifest carries as Implementation-Version (the release zip
 * is named from the same property). The module loader defines the package from
 * that manifest.
 */
final class ModVersion {
    private ModVersion() {
    }

    static String current() {
        String v = SndModule.class.getPackage().getImplementationVersion();
        if (v == null || v.trim().isEmpty()) {
            throw new IllegalStateException("module jar manifest carries no Implementation-Version");
        }
        return v.trim();
    }
}
