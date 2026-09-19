package snd.host;

import snd.contracts.Dispatcher;
import snd.contracts.HostServices;
import snd.contracts.SndLog;
import snd.contracts.speech.SpeechPipeline;
import snd.contracts.util.LineLog;
import snd.host.dev.DevServer;

/**
 * The permanent host: owns the speech pipeline (native handle), the module
 * loader, and the dev server. Everything here survives a module reload;
 * changing host code needs a game restart, so keep it minimal.
 */
public final class Host implements HostServices {
    private final SpeechPipeline speech = new SpeechPipeline();
    private final LineLog speechLog = new LineLog(500);
    private final LineLog logLog = new LineLog(1000);
    private final ModuleLoader loader = new ModuleLoader(this);
    private DevServer devServer;
    private boolean speechInitialized;

    public void boot() {
        SndLog.setSink(new SndLog.Sink() {
            @Override
            public void line(String text) {
                logLog.add(text);
            }
        });
        speech.setTap(new SpeechPipeline.Tap() {
            @Override
            public void spoken(String cleanText, boolean interrupt, String source) {
                speechLog.add((interrupt ? "[interrupt] " : "[queue] ") + "[" + source + "] " + cleanText);
            }
        });
        if (System.getenv("SND_NO_SPEECH") != null || Boolean.getBoolean("snd.nospeech")) {
            speech.setMuted(true);
            SndLog.info("speech muted (SND_NO_SPEECH)");
        }
        boolean dev = "1".equals(System.getProperty("snd.dev")) || "1".equals(System.getenv("SND_DEV"));
        if (dev) {
            devServer = new DevServer(loader, speechLog, logLog);
            devServer.start();
        }

        // Everything that touches the game or native speech runs on the render
        // thread, on the first pumped frame.
        Dispatcher.post(new Runnable() {
            @Override
            public void run() {
                initSpeech();
                loader.reload();
            }
        });
        // The module tick keeps the render loop continuous in dev mode; the
        // host itself never links against game classes (the shipped shim
        // loads the game outside the system loader).
        SndLog.info("host booted (dev server " + (dev ? "on" : "off") + ")");
    }

    private void initSpeech() {
        if (speechInitialized) {
            return;
        }
        speechInitialized = true;
        PrismBackend backend = PrismBackend.tryCreate();
        if (backend != null) {
            speech.setBackend(backend);
        } else {
            SndLog.info("no speech backend; spoken lines reach the dev tap only");
        }
    }

    @Override
    public SpeechPipeline speech() {
        return speech;
    }

    @Override
    public int generation() {
        return loader.generation();
    }
}
