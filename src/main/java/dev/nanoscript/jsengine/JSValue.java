package dev.nanoscript.jsengine;

import java.util.Objects;

/**
 * JS çalışma zamanı değeri.
 * Bir JSValue şunlardan biri olabilir:
 *   UNDEFINED, NULL, BOOLEAN, NUMBER, STRING, OBJECT, JAVA
 *
 * JSFunction bu sınıfı extend eder.
 */
public class JSValue {

    public enum Type { UNDEFINED, NULL, BOOLEAN, NUMBER, STRING, OBJECT, JAVA }

    // ── Singleton constants ────────────────────────────────────────────
    public static final JSValue UNDEFINED = new JSValue(Type.UNDEFINED, null);
    public static final JSValue NULL      = new JSValue(Type.NULL,      null);
    public static final JSValue TRUE      = new JSValue(Type.BOOLEAN,   Boolean.TRUE);
    public static final JSValue FALSE     = new JSValue(Type.BOOLEAN,   Boolean.FALSE);
    public static final JSValue ZERO      = new JSValue(Type.NUMBER,    0.0);
    public static final JSValue ONE       = new JSValue(Type.NUMBER,    1.0);
    public static final JSValue NAN       = new JSValue(Type.NUMBER,    Double.NaN);

    protected final Type type;
    protected final Object raw;

    protected JSValue(Type type, Object raw) {
        this.type = type;
        this.raw  = raw;
    }

    // ── Factory methods ────────────────────────────────────────────────

    public static JSValue of(double v) {
        if (v == 0.0) return ZERO;
        if (v == 1.0) return ONE;
        return new JSValue(Type.NUMBER, v);
    }

    public static JSValue of(long v)    { return of((double) v); }
    public static JSValue of(int v)     { return of((double) v); }
    public static JSValue of(boolean b) { return b ? TRUE : FALSE; }

    public static JSValue of(String s) {
        if (s == null) return NULL;
        return new JSValue(Type.STRING, s);
    }

    public static JSValue of(JSObject obj) {
        if (obj == null) return NULL;
        return new JSValue(Type.OBJECT, obj);
    }

    /** Wrap an arbitrary Java object (e.g. Bukkit Player, World, etc.) */
    public static JSValue wrap(Object javaObj) {
        if (javaObj == null)             return NULL;
        if (javaObj instanceof JSValue v) return v;
        if (javaObj instanceof Boolean b) return of(b);
        if (javaObj instanceof Number n)  return of(n.doubleValue());
        if (javaObj instanceof String s)  return of(s);
        if (javaObj instanceof Character ch) return of(String.valueOf(ch));
        return new JSValue(Type.JAVA, javaObj);
    }

    // ── Type checks ───────────────────────────────────────────────────

    public boolean isUndefined() { return type == Type.UNDEFINED; }
    public boolean isNull()      { return type == Type.NULL; }
    public boolean isNullish()   { return type == Type.UNDEFINED || type == Type.NULL; }
    public boolean isNumber()    { return type == Type.NUMBER; }
    public boolean isString()    { return type == Type.STRING; }
    public boolean isBoolean()   { return type == Type.BOOLEAN; }
    public boolean isObject()    { return type == Type.OBJECT; }
    public boolean isJava()      { return type == Type.JAVA; }
    public boolean isFunction()  { return this instanceof JSFunction; }
    public boolean isArray()     { return type == Type.OBJECT && raw instanceof JSArray; }

    public Type getType() { return type; }

    // ── Value accessors ───────────────────────────────────────────────

    public double asNumber() {
        return switch (type) {
            case NUMBER    -> (Double) raw;
            case BOOLEAN   -> ((Boolean) raw) ? 1.0 : 0.0;
            case STRING    -> { try { yield Double.parseDouble((String) raw); } catch (Exception e) { yield Double.NaN; } }
            case NULL, UNDEFINED -> 0.0;
            case JAVA      -> { try { yield ((Number) raw).doubleValue(); } catch (Exception e) { yield Double.NaN; } }
            default        -> Double.NaN;
        };
    }

    public int asInt()  { return (int) asNumber(); }
    public long asLong(){ return (long) asNumber(); }

    public boolean asBoolean() {
        return switch (type) {
            case BOOLEAN   -> (Boolean) raw;
            case NUMBER    -> { double d = (Double)raw; yield d != 0 && !Double.isNaN(d); }
            case STRING    -> !((String) raw).isEmpty();
            case NULL, UNDEFINED -> false;
            default        -> true; // objects, java, functions are truthy
        };
    }

    public String asString() {
        return switch (type) {
            case STRING    -> (String) raw;
            case NUMBER    -> {
                double d = (Double) raw;
                if (Double.isNaN(d))      yield "NaN";
                if (Double.isInfinite(d)) yield d > 0 ? "Infinity" : "-Infinity";
                // Remove trailing .0 for whole numbers
                if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15)
                    yield String.valueOf((long) d);
                yield String.valueOf(d);
            }
            case BOOLEAN   -> String.valueOf((Boolean) raw);
            case NULL      -> "null";
            case UNDEFINED -> "undefined";
            case OBJECT    -> raw instanceof JSArray a ? a.toJsString() : "[object Object]";
            case JAVA      -> raw != null ? raw.toString() : "null";
            default        -> this instanceof JSFunction ? "[function]" : "[object]";
        };
    }

    public JSObject asObject() {
        if (type == Type.OBJECT) return (JSObject) raw;
        return null;
    }

    public JSArray asArray() {
        if (isArray()) return (JSArray) raw;
        return null;
    }

    /** Get the underlying Java object (for JAVA type values) */
    public Object javaRaw() { return raw; }

    // ── Property access ───────────────────────────────────────────────

    /**
     * Get a property from this value.
     * Handles: JSObject properties, JSArray [index] and .length,
     * String .length and index access, JAVA object field/getter access.
     */
    public JSValue getProp(String name) {
        return switch (type) {
            case OBJECT -> raw instanceof JSArray arr ? arr.getProp(name) : ((JSObject) raw).get(name);
            case STRING -> {
                String s = (String) raw;
                if ("length".equals(name)) yield of(s.length());
                try { int i = Integer.parseInt(name); yield i >= 0 && i < s.length() ? of(String.valueOf(s.charAt(i))) : UNDEFINED; }
                catch (NumberFormatException e) { yield JSBuiltins.getStringMethod(name); }
            }
            case JAVA   -> JSValue.wrap(JavaInterop.getProperty(raw, name));
            default     -> UNDEFINED;
        };
    }

    public void setProp(String name, JSValue value) {
        if (type == Type.OBJECT) {
            if (raw instanceof JSArray arr) arr.setProp(name, value);
            else ((JSObject) raw).set(name, value);
        }
    }

    // ── Equality ──────────────────────────────────────────────────────

    /** JS loose equality (==) */
    public boolean looseEquals(JSValue other) {
        if (this == other) return true;
        if (type == other.type) return strictEquals(other);
        // null == undefined
        if (isNullish() && other.isNullish()) return true;
        // number/string coercion
        if (isNumber() && other.isString()) return asNumber() == other.asNumber();
        if (isString() && other.isNumber()) return asNumber() == other.asNumber();
        if (isBoolean()) return of(asNumber()).looseEquals(other);
        if (other.isBoolean()) return looseEquals(of(other.asNumber()));
        return false;
    }

    /** JS strict equality (===) */
    public boolean strictEquals(JSValue other) {
        if (this == other) return true;
        if (type != other.type) return false;
        return switch (type) {
            case UNDEFINED, NULL -> true;
            case BOOLEAN  -> raw.equals(other.raw);
            case NUMBER   -> Objects.equals(raw, other.raw);
            case STRING   -> raw.equals(other.raw);
            case OBJECT, JAVA -> raw == other.raw; // reference equality
            default -> false;
        };
    }

    // ── toString ──────────────────────────────────────────────────────

    @Override public String toString() {
        return "JSValue[" + type + ": " + asString() + "]";
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof JSValue v)) return false;
        return strictEquals(v);
    }

    @Override public int hashCode() {
        return Objects.hashCode(raw);
    }
}
