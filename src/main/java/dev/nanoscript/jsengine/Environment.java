package dev.nanoscript.jsengine;

import java.util.HashMap;
import java.util.Map;

/**
 * Değişken kapsamı (scope) — zincirli yapı.
 * Her fonksiyon çağrısı veya blok yeni bir Environment oluşturur.
 * Arama sırasında: kendi map'i → parent → ... → global
 */
public class Environment {

    private final Map<String, JSValue> vars = new HashMap<>();
    private final Environment parent;

    public Environment(Environment parent) {
        this.parent = parent;
    }

    // ── Define (yeni değişken tanımla, sadece bu scope'ta) ────────────

    public void define(String name, JSValue value) {
        vars.put(name, value);
    }

    // ── Get (zincirde ara) ────────────────────────────────────────────

    public JSValue get(String name) {
        JSValue val = vars.get(name);
        if (val != null) return val;
        if (vars.containsKey(name)) return JSValue.UNDEFINED; // explicitly set to undefined
        if (parent != null) return parent.get(name);
        return JSValue.UNDEFINED;
    }

    // ── Set (zincirde bul ve güncelle; bulamazsan global'da yarat) ────

    public void set(String name, JSValue value) {
        if (vars.containsKey(name)) {
            vars.put(name, value);
            return;
        }
        if (parent != null && parent.has(name)) {
            parent.set(name, value);
            return;
        }
        // Global assignment (implicit var)
        Environment global = this;
        while (global.parent != null) global = global.parent;
        global.vars.put(name, value);
    }

    public boolean has(String name) {
        if (vars.containsKey(name)) return true;
        if (parent != null) return parent.has(name);
        return false;
    }

    public Environment getParent() { return parent; }

    public Environment getGlobal() {
        Environment e = this;
        while (e.parent != null) e = e.parent;
        return e;
    }
}
