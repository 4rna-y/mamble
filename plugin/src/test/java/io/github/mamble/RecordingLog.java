package io.github.mamble;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.helpers.AbstractLogger;
import org.slf4j.event.Level;
import org.slf4j.Marker;

/** テスト用の Logger。出力を貯めるだけ。 */
final class RecordingLog extends AbstractLogger {

    final List<String> lines = new ArrayList<>();

    static Logger create() {
        return new RecordingLog();
    }

    @Override
    protected String getFullyQualifiedCallerName() {
        return null;
    }

    @Override
    protected void handleNormalizedLoggingCall(Level level, Marker marker, String pattern, Object[] args,
            Throwable throwable) {
        lines.add(level + " " + org.slf4j.helpers.MessageFormatter.arrayFormat(pattern, args).getMessage());
    }

    @Override
    public boolean isTraceEnabled() { return true; }
    @Override
    public boolean isTraceEnabled(Marker marker) { return true; }
    @Override
    public boolean isDebugEnabled() { return true; }
    @Override
    public boolean isDebugEnabled(Marker marker) { return true; }
    @Override
    public boolean isInfoEnabled() { return true; }
    @Override
    public boolean isInfoEnabled(Marker marker) { return true; }
    @Override
    public boolean isWarnEnabled() { return true; }
    @Override
    public boolean isWarnEnabled(Marker marker) { return true; }
    @Override
    public boolean isErrorEnabled() { return true; }
    @Override
    public boolean isErrorEnabled(Marker marker) { return true; }
}
