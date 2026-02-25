package dev.nanoscript.jsengine;

public record Token(TokenType type, String value, int line) {
    @Override public String toString() {
        return type + "(" + value + ")@" + line;
    }
}
