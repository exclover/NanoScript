package dev.nanoscript.jsengine;

/** Hem parse hem runtime hataları için kullanılan genel hata sınıfı. */
public class JsError extends RuntimeException {
    private final int line;

    public JsError(String message) {
        super(message);
        this.line = -1;
    }

    public JsError(String message, int line) {
        super("Satır " + line + ": " + message);
        this.line = line;
    }

    public int getLine() { return line; }
}
