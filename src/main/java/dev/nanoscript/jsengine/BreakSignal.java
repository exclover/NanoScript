package dev.nanoscript.jsengine;

public class BreakSignal extends RuntimeException {
    public final String label;
    public BreakSignal(String label) { super(null,null,true,false); this.label = label; }
}
