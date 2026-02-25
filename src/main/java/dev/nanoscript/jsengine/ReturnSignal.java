package dev.nanoscript.jsengine;

/** return ifadesi için sinyal */
public class ReturnSignal extends RuntimeException {
    public final JSValue value;
    public ReturnSignal(JSValue value) {
        super(null, null, true, false); // suppress stack trace (performans için)
        this.value = value;
    }
}
