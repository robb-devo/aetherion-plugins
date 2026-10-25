package de.aetherion.helm.agent;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * WebSocket to the gateway on loopback. Reconnects on its own with a gentle backoff; while it is down, only
 * events queue up (a bounded number), stats are simply skipped.
 */
public final class Link {
    private final URI uri;
    private final Supplier<JsonObject> hello;
    private final Consumer<JsonObject> onMessage;
    private final Runnable onConnected;
    private final Logger log;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ExecutorService sender = Executors.newSingleThreadExecutor(r -> daemon(r, "helm-send"));
    private final ScheduledExecutorService retry = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "helm-link"));
    private final java.util.ArrayDeque<String> backlog = new java.util.ArrayDeque<>();
    private volatile WebSocket ws;
    private volatile boolean closed;
    private int attempt;
    private boolean warned;

    public Link(String url, Supplier<JsonObject> hello, Consumer<JsonObject> onMessage, Runnable onConnected, Logger log) {
        this.uri = URI.create(url);
        this.hello = hello;
        this.onMessage = onMessage;
        this.onConnected = onConnected;
        this.log = log;
    }

    private static Thread daemon(Runnable r, String name) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        return t;
    }

    public boolean connected() {
        return ws != null;
    }

    public void start() {
        retry.execute(this::connect);
    }

    private void connect() {
        if (closed) return;
        StringBuilder buf = new StringBuilder();
        http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5)).buildAsync(uri, new WebSocket.Listener() {
            @Override
            public void onOpen(WebSocket w) {
                w.request(1);
            }

            @Override
            public CompletionStage<?> onText(WebSocket w, CharSequence data, boolean last) {
                buf.append(data);
                if (last) {
                    String s = buf.toString();
                    buf.setLength(0);
                    try {
                        onMessage.accept(JsonParser.parseString(s).getAsJsonObject());
                    } catch (Exception e) {
                        log.warning("[Helm] bad message from gateway: " + e);
                    }
                }
                w.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onClose(WebSocket w, int code, String reason) {
                lost(code == 4001 ? "token refused by gateway" : code == 4004 ? "server id unknown to gateway" : null);
                return null;
            }

            @Override
            public void onError(WebSocket w, Throwable error) {
                lost(null);
            }
        }).whenComplete((w, err) -> {
            if (err != null) {
                lost(null);
                return;
            }
            ws = w;
            attempt = 0;
            if (warned) log.info("[Helm] connected to the gateway again");
            warned = false;
            sendNow(hello.get().toString());
            String q;
            synchronized (backlog) {
                while ((q = backlog.pollFirst()) != null) sendNow(q);
            }
            onConnected.run();
        });
    }

    private void lost(String why) {
        boolean was = ws != null;
        ws = null;
        if (closed) return;
        if (why != null) log.warning("[Helm] " + why + ". Check plugins/AetherionHelm/config.yml.");
        else if (was || !warned) log.info("[Helm] gateway not reachable, retrying in the background");
        warned = true;
        long delay = Math.min(15, 1L << Math.min(attempt++, 4));
        retry.schedule(this::connect, delay, TimeUnit.SECONDS);
    }

    /** Fire and forget; dropped while disconnected. */
    public void send(JsonObject o) {
        if (ws == null) return;
        sendNow(o.toString());
    }

    /** Kept (up to 300) while disconnected and delivered after the next hello. */
    public void sendReliable(JsonObject o) {
        String s = o.toString();
        if (ws == null) {
            synchronized (backlog) {
                backlog.addLast(s);
                while (backlog.size() > 300) backlog.pollFirst();
            }
            return;
        }
        sendNow(s);
    }

    private void sendNow(String s) {
        sender.execute(() -> {
            WebSocket w = ws;
            if (w == null) return;
            try {
                w.sendText(s, true).get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                // the listener notices the broken socket and reconnects
            }
        });
    }

    public void close() {
        closed = true;
        WebSocket w = ws;
        ws = null;
        if (w != null) {
            try { w.sendClose(WebSocket.NORMAL_CLOSURE, "bye").get(2, TimeUnit.SECONDS); } catch (Exception ignored) {}
        }
        sender.shutdownNow();
        retry.shutdownNow();
    }
}
