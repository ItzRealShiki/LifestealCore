package de.leon.lifesteal.diagnostics;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;

import java.time.Instant;

/** Captures Paper/Minecraft Log4j console events at WARN and ERROR level. */
final class LeonLog4jAppender extends AbstractAppender {
    private final LoggerContext context;
    private final LoggerConfig rootLogger;

    private LeonLog4jAppender(LoggerContext context, Configuration configuration) {
        super("LeonConsoleCapture", null, null, true, Property.EMPTY_ARRAY);
        this.context = context;
        this.rootLogger = configuration.getRootLogger();
    }

    static LeonLog4jAppender attach() {
        try {
            Object raw = LogManager.getContext(false);
            if (!(raw instanceof LoggerContext context)) return null;
            Configuration configuration = context.getConfiguration();
            LeonLog4jAppender appender = new LeonLog4jAppender(context, configuration);
            appender.start();
            configuration.addAppender(appender);
            configuration.getRootLogger().addAppender(appender, Level.WARN, null);
            context.updateLoggers();
            return appender;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public void append(LogEvent event) {
        if (event == null || event.getLevel().intLevel() > Level.WARN.intLevel()) return;
        LeonConsoleLog.Kind kind = event.getLevel().isMoreSpecificThan(Level.ERROR)
                ? LeonConsoleLog.Kind.ERROR : LeonConsoleLog.Kind.WARNING;
        String source = event.getLoggerName() == null ? "SERVER" : event.getLoggerName();
        String message = event.getMessage() == null ? "(no message)" : event.getMessage().getFormattedMessage();
        LeonConsoleLog.addLog4jEntry(kind, source, message, event.getThrown(), Instant.ofEpochMilli(event.getTimeMillis()));
    }

    void detach() {
        try {
            rootLogger.removeAppender(getName());
            context.updateLoggers();
            stop();
        } catch (Throwable ignored) { }
    }
}
