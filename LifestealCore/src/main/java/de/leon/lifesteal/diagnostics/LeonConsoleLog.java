package de.leon.lifesteal.diagnostics;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** Captures Bukkit/JUL warning and error records without suppressing normal console output. */
public final class LeonConsoleLog extends Handler {
    public enum Kind { ERROR, WARNING }

    public record Entry(long id, Instant time, Kind kind, String source, String message, String stacktrace) { }

    private static final int MAX_ENTRIES = 500;
    private static final AtomicLong IDS = new AtomicLong();
    private static final List<Entry> ENTRIES = Collections.synchronizedList(new ArrayList<>());
    private final Logger root;
    private final LeonLog4jAppender log4jAppender;

    public LeonConsoleLog() {
        root = Logger.getLogger("");
        setLevel(Level.WARNING);
        root.addHandler(this);
        log4jAppender = LeonLog4jAppender.attach();
    }

    @Override
    public void publish(LogRecord record) {
        if (record == null || !isLoggable(record)) return;
        Kind kind = record.getLevel().intValue() >= Level.SEVERE.intValue() ? Kind.ERROR : Kind.WARNING;
        String message = record.getMessage() == null ? "(no message)" : record.getMessage();
        try {
            message = record.getResourceBundle() == null ? message : record.getResourceBundle().getString(message);
        } catch (Exception ignored) { }
        String trace = "";
        if (record.getThrown() != null) {
            StringWriter writer = new StringWriter();
            record.getThrown().printStackTrace(new PrintWriter(writer));
            trace = writer.toString();
        }
        Entry entry = new Entry(IDS.incrementAndGet(), record.getInstant(), kind,
                record.getLoggerName() == null ? "SERVER" : record.getLoggerName(), message, trace);
        synchronized (ENTRIES) {
            ENTRIES.add(entry);
            while (ENTRIES.size() > MAX_ENTRIES) ENTRIES.remove(0);
        }
    }

    public static List<Entry> snapshot() {
        synchronized (ENTRIES) { return new ArrayList<>(ENTRIES); }
    }

    public static Entry find(long id) {
        synchronized (ENTRIES) {
            for (int i = ENTRIES.size() - 1; i >= 0; i--) if (ENTRIES.get(i).id() == id) return ENTRIES.get(i);
        }
        return null;
    }

    public static void clear() { ENTRIES.clear(); }

    @Override public void flush() { }
    @Override public void close() {
        if (root != null) root.removeHandler(this);
        if (log4jAppender != null) log4jAppender.detach();
    }

    static void addLog4jEntry(Kind kind, String source, String message, Throwable thrown, Instant time) {
        String trace = "";
        if (thrown != null) {
            StringWriter writer = new StringWriter();
            thrown.printStackTrace(new PrintWriter(writer));
            trace = writer.toString();
        }
        synchronized (ENTRIES) {
            // JUL and Log4j can forward the same event; avoid duplicate rows.
            if (!ENTRIES.isEmpty()) {
                Entry last = ENTRIES.get(ENTRIES.size() - 1);
                if (last.kind() == kind && last.source().equals(source) && last.message().equals(message)
                        && Math.abs(time.toEpochMilli() - last.time().toEpochMilli()) < 1500) return;
            }
            ENTRIES.add(new Entry(IDS.incrementAndGet(), time, kind, source, message, trace));
            while (ENTRIES.size() > MAX_ENTRIES) ENTRIES.remove(0);
        }
    }
}
