package cz.muni.fi;

/**
 * Main class providing structured logging, parametrized by interface extending {@link VariableContext} class
 */
public class StructLogger<T extends VariableContext> {

    private final LoggingCallback callback;

    public StructLogger(final LoggingCallback callback) {
        this.callback = callback;
    }

    // these are used just as placeholders
    // calls to these methods are replaced by annotation processor to calls to correct method which accepts LoggingEvent
    // these methods are just used to give programmer a nice way to work with fluent logging API instead of creating logging events manually
    public T debug(String message) {
        return null;
    }

    public T info(String message) {
        return null;
    }

    public T error(String message) {
        return null;
    }

    public T warn(String message) {
        return null;
    }

    public T trace(String message) {
        return null;
    }

    public T audit(String message) {
        return null;
    }
    /////////////////////////////////////////////////////////////

    /**
     * log event on info level
     */
    public void infoEvent(final LoggingEvent e) {
        callback.info(e);
    }

    /**
     * log event on debug level
     */
    public void debugEvent(final LoggingEvent e) {
        callback.debug(e);
    }

    /**
     * log event on error level
     */
    public void errorEvent(final LoggingEvent e) {
        callback.error(e);
    }

    /**
     * log event on warn level
     */
    public void warnEvent(final LoggingEvent e) {
        callback.warn(e);
    }

    /**
     * log event on trace level
     */
    public void traceEvent(final LoggingEvent e) {
        callback.trace(e);
    }

    /**
     * log audit event
     */
    public void auditEvent(final LoggingEvent e) {
        callback.audit(e);
    }
}