package snd.devrepl;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jdk.jshell.Diag;
import jdk.jshell.JShell;
import jdk.jshell.SnippetEvent;
import jdk.jshell.SourceCodeAnalysis;
import snd.core.dev.Evaluator;

/**
 * The /eval engine: JShell with the in-process "local" execution provider, so
 * snippets run inside the game's JVM against live objects, on whatever thread
 * calls eval (the dev server marshals onto the render thread). Session state
 * (imports, variables) persists across calls. Snippets resolve classes through
 * the app classloader, so game types and snd.core (including the /wait Bridge)
 * are the same classes the mod uses; module-loader types are NOT visible —
 * that's what the ModModule.devCommand seam is for.
 */
public final class JShellEvaluator implements Evaluator {
    private JShell shell;

    private synchronized JShell shell() {
        if (shell == null) {
            shell = JShell.builder().executionEngine("local").build();
            String cp = System.getProperty("java.class.path", "");
            for (String entry : cp.split(File.pathSeparator)) {
                if (!entry.trim().isEmpty()) {
                    shell.addToClasspath(entry.trim());
                }
            }
            // The -javaagent jar (snd.core, snd.host) is appended to the system
            // classLOADER but not to java.class.path — add it explicitly so
            // snippets compile against snd.core (Bridge, Dispatcher, ...).
            try {
                java.net.URL loc = snd.core.dev.Bridge.class.getProtectionDomain()
                        .getCodeSource().getLocation();
                shell.addToClasspath(new File(loc.toURI()).getAbsolutePath());
            } catch (Exception e) {
                System.err.println("[snd-access] devrepl: could not add the agent jar to the eval classpath: " + e);
            }
            // Convenience imports; failures are harmless.
            for (String imp : new String[]{
                    "import java.util.*;",
                    "import java.util.function.*;",
                    "import com.badlogic.gdx.*;",
                    "import com.tann.dice.*;"}) {
                shell.eval(imp);
            }
        }
        return shell;
    }

    @Override
    public String eval(String source) {
        JShell js = shell();
        StringBuilder out = new StringBuilder();
        PrintStream oldOut = System.out;
        PrintStream oldErr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(new Tee(captured, oldOut), true));
        System.setErr(new PrintStream(new Tee(captured, oldErr), true));
        try {
            String remaining = source;
            int guard = 0;
            while (remaining != null && !remaining.trim().isEmpty() && guard++ < 200) {
                SourceCodeAnalysis.CompletionInfo info = js.sourceCodeAnalysis().analyzeCompletion(remaining);
                String snippet = info.source();
                if (snippet == null || snippet.isEmpty()) {
                    out.append("[incomplete] ").append(remaining.trim()).append('\n');
                    break;
                }
                List<SnippetEvent> events = js.eval(snippet);
                for (SnippetEvent e : events) {
                    if (e.causeSnippet() != null) {
                        continue; // dependency updates, not this eval
                    }
                    switch (e.status()) {
                        case VALID:
                        case OVERWRITTEN:
                            if (e.exception() != null) {
                                out.append("[exception] ").append(describe(e.exception())).append('\n');
                            } else if (e.value() != null && !e.value().isEmpty()) {
                                out.append("=> ").append(e.value()).append('\n');
                            }
                            break;
                        case REJECTED:
                            js.diagnostics(e.snippet()).forEach(new java.util.function.Consumer<Diag>() {
                                @Override
                                public void accept(Diag d) {
                                    out.append("[compile] ").append(d.getMessage(null)).append('\n');
                                }
                            });
                            break;
                        default:
                            out.append("[").append(e.status()).append("] ").append(snippet.trim()).append('\n');
                    }
                }
                remaining = info.remaining();
            }
        } finally {
            System.setOut(oldOut);
            System.setErr(oldErr);
        }
        String printed = new String(captured.toByteArray(), StandardCharsets.UTF_8);
        if (!printed.isEmpty()) {
            out.insert(0, printed.endsWith("\n") ? printed : printed + "\n");
        }
        if (out.length() == 0) {
            out.append("(ok)");
        }
        return out.toString();
    }

    private static String describe(Throwable t) {
        StringBuilder sb = new StringBuilder();
        sb.append(t.getClass().getSimpleName());
        if (t.getMessage() != null) {
            sb.append(": ").append(t.getMessage());
        }
        Throwable cause = t.getCause();
        if (cause != null) {
            sb.append(" (caused by ").append(cause).append(')');
        }
        return sb.toString();
    }

    @Override
    public synchronized void reset() {
        if (shell != null) {
            shell.close();
            shell = null;
        }
    }

    /** Writes to both the capture buffer and the real console. */
    private static final class Tee extends OutputStream {
        private final OutputStream a;
        private final OutputStream b;

        Tee(OutputStream a, OutputStream b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public void write(int c) throws java.io.IOException {
            a.write(c);
            b.write(c);
        }

        @Override
        public void write(byte[] buf, int off, int len) throws java.io.IOException {
            a.write(buf, off, len);
            b.write(buf, off, len);
        }
    }
}
