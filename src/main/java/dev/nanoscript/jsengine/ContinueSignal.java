package dev.nanoscript.jsengine;

public class ContinueSignal extends RuntimeException {
    public final String label;
    public ContinueSignal(String label) { super(null,null,true,false); this.label = label; }
}
