package snd.host.dev;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import snd.core.Dispatcher;
import snd.core.ModModule;
import snd.core.SndLog;
import snd.core.dev.Bridge;
import snd.core.dev.Evaluator;
import snd.core.util.LineLog;
import snd.host.GameDriver;
import snd.host.ModuleLoader;

/**
 * Loopback-only dev driver: introspect and drive the live game over HTTP.
 * Enabled by -Dsnd.dev=1 (the run-dev script sets it); never on in a player
 * launch. Everything that touches game state marshals onto the render thread
 * via Dispatcher.post; /speech and /log read thread-safe ring buffers directly.
 */
public final class DevServer {
    private final ModuleLoader loader;
    private final LineLog speechLog;
    private final LineLog logLog;
    private final AtomicInteger waitIds = new AtomicInteger();
    private Evaluator evaluator;
    private boolean evalUnavailable;
    private HttpServer server;

    public DevServer(ModuleLoader loader, LineLog speechLog, LineLog logLog) {
        this.loader = loader;
        this.speechLog = speechLog;
        this.logLog = logLog;
    }

    public void start() {
        int port = Integer.getInteger("snd.dev.port", 8771);
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        } catch (Exception e) {
            SndLog.error("dev server failed to bind 127.0.0.1:" + port, e);
            return;
        }
        server.setExecutor(Executors.newCachedThreadPool(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "snd-dev-http");
                t.setDaemon(true);
                return t;
            }
        }));
        server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) {
                try {
                    route(exchange);
                } catch (Throwable t) {
                    SndLog.error("dev request failed: " + exchange.getRequestURI(), t);
                    respond(exchange, 500, "[error] " + stackTrace(t));
                }
            }
        });
        server.start();
        SndLog.info("dev server listening on http://127.0.0.1:" + port);
    }

    private void route(HttpExchange ex) throws Exception {
        String path = ex.getRequestURI().getPath();
        Map<String, String> q = query(ex);
        String body = readBody(ex);
        if (path.equals("/health")) {
            respond(ex, 200, "ok");
        } else if (path.equals("/speech")) {
            respond(ex, 200, tail(speechLog, q));
        } else if (path.equals("/log")) {
            String rendered = tail(logLog, q);
            String grep = q.get("grep");
            respond(ex, 200, grep == null ? rendered : grepLines(rendered, grep));
        } else if (path.equals("/module")) {
            respond(ex, 200, loader.describe());
        } else if (path.equals("/reload")) {
            String result = onMainThread(new Callable<String>() {
                @Override
                public String call() {
                    return loader.reload();
                }
            }, 30000);
            Evaluator ev = evaluator;
            if (ev != null) {
                ev.reset();
            }
            respond(ex, 200, result + "\n\n" + loader.describe());
        } else if (path.equals("/eval")) {
            respond(ex, 200, eval(body, q));
        } else if (path.equals("/input")) {
            final String verb = body.trim();
            respond(ex, 200, onMainThread(new Callable<String>() {
                @Override
                public String call() {
                    return GameDriver.input(verb);
                }
            }, 15000));
        } else if (path.equals("/wait")) {
            respond(ex, 200, waitFor(body.trim(), parseLong(q.get("timeout"), 10000)));
        } else if (path.equals("/screenshot")) {
            final String file = System.getProperty("java.io.tmpdir") + "\\snd_shot_" + System.currentTimeMillis() + ".png";
            respond(ex, 200, onMainThread(new Callable<String>() {
                @Override
                public String call() {
                    return GameDriver.screenshot(file);
                }
            }, 15000));
        } else if (path.equals("/gui")) {
            respond(ex, 200, onMainThread(new Callable<String>() {
                @Override
                public String call() {
                    ModModule m = Dispatcher.current();
                    String fromModule = m != null ? m.devCommand("gui", null) : null;
                    return fromModule != null ? fromModule : GameDriver.describeScreen();
                }
            }, 15000));
        } else if (path.equals("/typeinfo")) {
            respond(ex, 200, typeInfo(q.get("name")));
        } else {
            respond(ex, 404, "unknown endpoint " + path
                    + "\nendpoints: /health /eval /reload /module /speech /log /input /wait /screenshot /gui /typeinfo");
        }
    }

    // ---- /eval -------------------------------------------------------------

    private synchronized Evaluator evaluator() {
        if (evaluator == null && !evalUnavailable) {
            try {
                evaluator = (Evaluator) Class.forName("snd.devrepl.JShellEvaluator")
                        .getDeclaredConstructor().newInstance();
            } catch (Throwable t) {
                evalUnavailable = true;
                SndLog.error("eval unavailable (snd-devrepl.jar on the classpath? JDK 9+?)", t);
            }
        }
        return evaluator;
    }

    private String eval(final String source, Map<String, String> q) {
        final Evaluator ev = evaluator();
        if (ev == null) {
            return "[unavailable] eval needs snd-devrepl.jar on the classpath and a JDK 9+ runtime";
        }
        boolean captureSpeech = !"0".equals(q.get("speech"));
        long settle = parseLong(q.get("settle"), 250);
        long cursor = speechLog.end();
        String result = onMainThread(new Callable<String>() {
            @Override
            public String call() {
                return ev.eval(source);
            }
        }, 30000);
        if (captureSpeech) {
            // Extend while lines keep landing (multi-part announcements), cap at 5s.
            long deadline = System.currentTimeMillis() + 5000;
            long seen = cursor;
            while (System.currentTimeMillis() < deadline && speechLog.waitForNew(seen, settle)) {
                seen = speechLog.end();
            }
            if (speechLog.end() > cursor) {
                result += "\nspeech:\n" + speechLog.render(cursor);
            }
        }
        return result;
    }

    // ---- /wait -------------------------------------------------------------

    private String waitFor(String expr, long timeoutMs) throws InterruptedException {
        final Evaluator ev = evaluator();
        if (ev == null) {
            return "[unavailable] /wait needs the eval engine";
        }
        if (expr.isEmpty()) {
            return "[error] body must be a boolean Java expression";
        }
        final String id = "wait-" + waitIds.incrementAndGet();
        final String code = "snd.core.dev.Bridge.put(\"" + id + "\", (java.util.function.BooleanSupplier)(() -> ("
                + expr + ")));";
        String evalOut = onMainThread(new Callable<String>() {
            @Override
            public String call() {
                return ev.eval(code);
            }
        }, 30000);
        Object supplier = Bridge.remove(id);
        if (!(supplier instanceof BooleanSupplier)) {
            return "[error] condition did not compile:\n" + evalOut;
        }
        final BooleanSupplier cond = (BooleanSupplier) supplier;
        final CountDownLatch hit = new CountDownLatch(1);
        Dispatcher.addWait(id, new Dispatcher.FrameWait() {
            @Override
            public boolean poll() {
                return cond.getAsBoolean();
            }

            @Override
            public void satisfied() {
                hit.countDown();
            }
        });
        GameDriver.requestRender();
        boolean ok = hit.await(timeoutMs, TimeUnit.MILLISECONDS);
        if (!ok) {
            Dispatcher.removeWait(id);
        }
        return ok ? "true" : "timeout after " + timeoutMs + "ms";
    }

    // ---- /typeinfo ---------------------------------------------------------

    private String typeInfo(String name) {
        if (name == null || name.isEmpty()) {
            return "[error] pass ?name=<binary class name>";
        }
        Class<?> cls = null;
        StringBuilder sb = new StringBuilder();
        try {
            cls = Class.forName(name, false, DevServer.class.getClassLoader());
        } catch (Throwable ignored) {
            ModModule m = Dispatcher.current();
            if (m != null) {
                try {
                    cls = Class.forName(name, false, m.getClass().getClassLoader());
                    sb.append("(resolved from the module loader)\n");
                } catch (Throwable alsoIgnored) {
                    // fall through to the not-found message
                }
            }
        }
        if (cls == null) {
            return "not found: " + name + " (use the fully qualified binary name, e.g. com.tann.dice.Main)";
        }
        sb.append(cls).append('\n');
        sb.append("loader: ").append(cls.getClassLoader()).append('\n');
        sb.append("extends: ").append(cls.getSuperclass()).append('\n');
        if (cls.isEnum()) {
            sb.append("enum constants:\n");
            for (Object c : cls.getEnumConstants()) {
                sb.append("  ").append(c).append('\n');
            }
        }
        sb.append("declared fields:\n");
        for (Field f : cls.getDeclaredFields()) {
            sb.append("  ").append(f).append('\n');
        }
        sb.append("declared methods:\n");
        for (Method m : cls.getDeclaredMethods()) {
            sb.append("  ").append(m).append('\n');
        }
        return sb.toString();
    }

    // ---- plumbing ----------------------------------------------------------

    private String onMainThread(final Callable<String> work, long timeoutMs) {
        final String[] result = new String[1];
        final CountDownLatch done = new CountDownLatch(1);
        Dispatcher.post(new Runnable() {
            @Override
            public void run() {
                try {
                    result[0] = work.call();
                } catch (Throwable t) {
                    result[0] = "[error] " + stackTrace(t);
                } finally {
                    done.countDown();
                }
            }
        });
        GameDriver.requestRender();
        try {
            if (!done.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                return "[timeout] render thread did not run the job within " + timeoutMs
                        + "ms (game frozen or not pumping?)";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "[interrupted]";
        }
        return result[0];
    }

    private String tail(LineLog log, Map<String, String> q) {
        long since = parseLong(q.get("since"), 0);
        long wait = parseLong(q.get("wait"), 0);
        if (wait > 0) {
            log.waitForNew(since, wait);
        }
        return log.render(since);
    }

    private static String grepLines(String rendered, String needle) {
        StringBuilder sb = new StringBuilder();
        for (String line : rendered.split("\n")) {
            if (line.startsWith("cursor: ") || line.contains(needle)) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private static long parseLong(String s, long fallback) {
        if (s == null) {
            return fallback;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static Map<String, String> query(HttpExchange ex) {
        Map<String, String> map = new HashMap<String, String>();
        String raw = ex.getRequestURI().getRawQuery();
        if (raw == null) {
            return map;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            try {
                if (eq < 0) {
                    map.put(URLDecoder.decode(pair, "UTF-8"), "");
                } else {
                    map.put(URLDecoder.decode(pair.substring(0, eq), "UTF-8"),
                            URLDecoder.decode(pair.substring(eq + 1), "UTF-8"));
                }
            } catch (Exception e) {
                SndLog.error("bad query pair: " + pair, e);
            }
        }
        return map;
    }

    private static String readBody(HttpExchange ex) throws Exception {
        InputStream in = ex.getRequestBody();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void respond(HttpExchange ex, int code, String text) {
        try {
            byte[] bytes = (text.endsWith("\n") ? text : text + "\n").getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            ex.sendResponseHeaders(code, bytes.length);
            OutputStream os = ex.getResponseBody();
            os.write(bytes);
            os.close();
        } catch (Exception e) {
            SndLog.error("dev response failed", e);
        }
    }

    private static String stackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
