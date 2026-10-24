package com.gnjake.ardaclient;

/**
 * Developer builds ({@code ./gradlew build -Pdev}) include the {@code structure.dev} package, which records new
 * structures for the bundled list. Release builds leave it out, so its features are only reached through here.
 */
public final class ArdaBuild {
    private static final String RECORDER = "com.gnjake.ardaclient.structure.dev.StructureRecorder";

    public static final boolean DEV = ArdaBuild.class.getClassLoader().getResource(RECORDER.replace('.', '/') + ".class") != null;

    private ArdaBuild() {
    }

    static void registerDevFeatures() {
        try {
            Class.forName(RECORDER).getMethod("register").invoke(null);
            ArdaClient.LOG.info("Developer build: structure recording is on");
        } catch (ReflectiveOperationException e) {
            ArdaClient.LOG.warn("Could not start the structure recorder", e);
        }
    }
}
