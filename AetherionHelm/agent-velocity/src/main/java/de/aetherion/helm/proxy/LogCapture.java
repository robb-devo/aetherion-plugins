package de.aetherion.helm.proxy;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.regex.Pattern;

/** Mirrors the server console: every log line goes to the app (batched), the last 600 are kept for new viewers. */
public final class LogCapture extends AbstractAppender {
    private static final Pattern ANSI = Pattern.compile("\u001B\\[[;\\d]*[ -/]*[@-~]");
    private final ArrayDeque<JsonObject> recent = new ArrayDeque<>();
    private final ArrayDeque<JsonObject> pending = new ArrayDeque<>();

    public LogCapture() {
        super("AetherionHelm", null, null, true, Property.EMPTY_ARRAY);
    }

    public void attach() {
        start();
        ((Logger) LogManager.getRootLogger()).addAppender(this);
    }

    public void detach() {
        ((Logger) LogManager.getRootLogger()).removeAppender(this);
        stop();
    }

    @Override
    public void append(LogEvent e) {
        String msg = e.getMessage() == null ? "" : e.getMessage().getFormattedMessage();
        if (e.getThrown() != null) {
            StringWriter sw = new StringWriter();
            e.getThrown().printStackTrace(new PrintWriter(sw));
            String[] lines = sw.toString().split("\n");
            StringBuilder sb = new StringBuilder(msg);
            for (int i = 0; i < Math.min(lines.length, 40); i++) sb.append('\n').append(lines[i].replace("\t", "    "));
            if (lines.length > 40) sb.append("\n    … ").append(lines.length - 40).append(" weitere Zeilen");
            msg = sb.toString();
        }
        JsonObject o = new JsonObject();
        o.addProperty("ts", e.getTimeMillis());
        o.addProperty("lvl", e.getLevel().name());
        String src = e.getLoggerName();
        if (src != null && !src.isEmpty()) o.addProperty("src", src.contains(".") ? src.substring(src.lastIndexOf('.') + 1) : src);
        o.addProperty("msg", ANSI.matcher(msg).replaceAll(""));
        synchronized (this) {
            recent.addLast(o);
            if (recent.size() > 600) recent.pollFirst();
            pending.addLast(o);
            if (pending.size() > 2000) pending.pollFirst();
        }
    }

    public synchronized JsonArray drain() {
        JsonArray a = new JsonArray();
        JsonObject o;
        while ((o = pending.pollFirst()) != null) a.add(o);
        return a;
    }

    public synchronized JsonArray backlog() {
        JsonArray a = new JsonArray();
        recent.forEach(a::add);
        pending.clear();
        return a;
    }
}
