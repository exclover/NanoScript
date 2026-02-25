package dev.nanoscript.jsengine;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Basit JS objesi — LinkedHashMap tabanlı anahtar-değer deposu.
 * Prototype zinciri sadeleştirilmiştir (tek seviye prototype desteği).
 */
public class JSObject {

    private final Map<String, JSValue> props = new LinkedHashMap<>();
    private JSObject prototype;

    public JSObject() {}

    public JSObject(JSObject prototype) {
        this.prototype = prototype;
    }

    // ── Property operations ───────────────────────────────────────────

    public JSValue get(String key) {
        JSValue val = props.get(key);
        if (val != null) return val;
        if (prototype != null) return prototype.get(key);
        return JSValue.UNDEFINED;
    }

    public void set(String key, JSValue value) {
        props.put(key, value);
    }

    public boolean has(String key) {
        return props.containsKey(key) || (prototype != null && prototype.has(key));
    }

    public boolean hasOwn(String key) {
        return props.containsKey(key);
    }

    public void delete(String key) {
        props.remove(key);
    }

    public Set<String> ownKeys() {
        return props.keySet();
    }

    public Map<String, JSValue> ownProps() {
        return props;
    }

    public JSObject getPrototype() { return prototype; }

    public void setPrototype(JSObject proto) { this.prototype = proto; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, JSValue> e : props.entrySet()) {
            if (!first) sb.append(", ");
            sb.append(e.getKey()).append(": ").append(e.getValue().asString());
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
}
