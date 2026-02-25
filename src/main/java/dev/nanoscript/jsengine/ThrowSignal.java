package dev.nanoscript.jsengine;

public class ThrowSignal extends RuntimeException {
    public final JSValue value;
    public ThrowSignal(JSValue value) {
        super(value.asString(), null, true, false);
        this.value = value;
    }
}
