package dev.nanoscript.jsengine;

import java.util.ArrayList;
import java.util.List;

/**
 * JS Dizisi — JSObject'in özel alt versiyonu.
 * Sayısal indeks erişimi ve .length desteği içerir.
 */
public class JSArray extends JSObject {

    private final List<JSValue> elements;

    public JSArray() {
        this.elements = new ArrayList<>();
    }

    public JSArray(List<JSValue> elements) {
        this.elements = new ArrayList<>(elements);
    }

    // ── Core list operations ──────────────────────────────────────────

    public void push(JSValue val) {
        elements.add(val);
    }

    public JSValue pop() {
        if (elements.isEmpty()) return JSValue.UNDEFINED;
        return elements.remove(elements.size() - 1);
    }

    public int length() {
        return elements.size();
    }

    public JSValue get(int i) {
        if (i < 0 || i >= elements.size()) return JSValue.UNDEFINED;
        return elements.get(i);
    }

    public void set(int i, JSValue val) {
        while (elements.size() <= i) elements.add(JSValue.UNDEFINED);
        elements.set(i, val);
    }

    public List<JSValue> elements() {
        return elements;
    }

    // ── Property access (string key) ─────────────────────────────────

    @Override
    public JSValue get(String key) {
        if ("length".equals(key)) return JSValue.of(elements.size());
        try {
            int i = Integer.parseInt(key);
            return get(i);
        } catch (NumberFormatException err) {
            // Array methods
            return switch (key) {
                case "push"    -> JSFunction.native1("push",    (args, env) -> { for (JSValue v : args) push(v); return JSValue.of(elements.size()); });
                case "pop"     -> JSFunction.native1("pop",     (args, env) -> pop());
                case "shift"   -> JSFunction.native1("shift",   (args, env) -> elements.isEmpty() ? JSValue.UNDEFINED : elements.remove(0));
                case "unshift" -> JSFunction.native1("unshift", (args, env) -> { for (int j = args.length - 1; j >= 0; j--) elements.add(0, args[j]); return JSValue.of(elements.size()); });
                case "join"    -> JSFunction.native1("join", (args, env) -> {
                    String sep = args.length > 0 ? args[0].asString() : ",";
                    StringBuilder sb = new StringBuilder();
                    for (int j = 0; j < elements.size(); j++) {
                        if (j > 0) sb.append(sep);
                        sb.append(elements.get(j).asString());
                    }
                    return JSValue.of(sb.toString());
                });
                case "indexOf" -> JSFunction.native1("indexOf", (args, env) -> {
                    if (args.length == 0) return JSValue.of(-1);
                    for (int j = 0; j < elements.size(); j++)
                        if (elements.get(j).looseEquals(args[0])) return JSValue.of(j);
                    return JSValue.of(-1);
                });
                case "includes" -> JSFunction.native1("includes", (args, env) -> {
                    if (args.length == 0) return JSValue.FALSE;
                    for (JSValue e : elements) if (e.looseEquals(args[0])) return JSValue.TRUE;
                    return JSValue.FALSE;
                });
                case "slice"   -> JSFunction.native1("slice", (args, env) -> {
                    int from = args.length > 0 ? Math.max(0, args[0].asInt()) : 0;
                    int to   = args.length > 1 ? Math.min(elements.size(), args[1].asInt()) : elements.size();
                    return JSValue.of(new JSArray(elements.subList(Math.min(from, elements.size()), Math.max(from, Math.min(to, elements.size())))));
                });
                case "splice"  -> JSFunction.native1("splice", (args, env) -> {
                    int start = args.length > 0 ? args[0].asInt() : 0;
                    int deleteCount = args.length > 1 ? args[1].asInt() : elements.size() - start;
                    List<JSValue> removed = new ArrayList<>();
                    for (int j = 0; j < deleteCount && start < elements.size(); j++) removed.add(elements.remove(start));
                    for (int j = 2; j < args.length; j++) elements.add(start + j - 2, args[j]);
                    return JSValue.of(new JSArray(removed));
                });
                case "reverse" -> JSFunction.native1("reverse", (args, env) -> {
                    java.util.Collections.reverse(elements); return JSValue.of(this);
                });
                case "sort"    -> JSFunction.native1("sort", (args, env) -> {
                    elements.sort((a, b) -> Double.compare(a.asNumber(), b.asNumber()));
                    return JSValue.of(this);
                });
                case "forEach" -> JSFunction.native1("forEach", (args, env) -> {
                    if (args.length > 0 && args[0].isFunction()) {
                        JSFunction fn = (JSFunction) args[0];
                        for (int j = 0; j < elements.size(); j++)
                            fn.call(null, new JSValue[]{elements.get(j), JSValue.of(j), JSValue.of(this)});
                    }
                    return JSValue.UNDEFINED;
                });
                case "map"     -> JSFunction.native1("map", (args, env) -> {
                    JSArray result = new JSArray();
                    if (args.length > 0 && args[0].isFunction()) {
                        JSFunction fn = (JSFunction) args[0];
                        for (int j = 0; j < elements.size(); j++)
                            result.push(fn.call(null, new JSValue[]{elements.get(j), JSValue.of(j)}));
                    }
                    return JSValue.of(result);
                });
                case "filter"  -> JSFunction.native1("filter", (args, env) -> {
                    JSArray result = new JSArray();
                    if (args.length > 0 && args[0].isFunction()) {
                        JSFunction fn = (JSFunction) args[0];
                        for (JSValue e : elements)
                            if (fn.call(null, new JSValue[]{e}).asBoolean()) result.push(e);
                    }
                    return JSValue.of(result);
                });
                case "find"    -> JSFunction.native1("find", (args, env) -> {
                    if (args.length > 0 && args[0].isFunction()) {
                        JSFunction fn = (JSFunction) args[0];
                        for (JSValue e : elements)
                            if (fn.call(null, new JSValue[]{e}).asBoolean()) return e;
                    }
                    return JSValue.UNDEFINED;
                });
                case "some"    -> JSFunction.native1("some", (args, env) -> {
                    if (args.length > 0 && args[0].isFunction()) {
                        JSFunction fn = (JSFunction) args[0];
                        for (JSValue e : elements)
                            if (fn.call(null, new JSValue[]{e}).asBoolean()) return JSValue.TRUE;
                    }
                    return JSValue.FALSE;
                });
                case "every"   -> JSFunction.native1("every", (args, env) -> {
                    if (args.length > 0 && args[0].isFunction()) {
                        JSFunction fn = (JSFunction) args[0];
                        for (JSValue e : elements)
                            if (!fn.call(null, new JSValue[]{e}).asBoolean()) return JSValue.FALSE;
                    }
                    return JSValue.TRUE;
                });
                case "concat"  -> JSFunction.native1("concat", (args, env) -> {
                    JSArray result = new JSArray(elements);
                    for (JSValue v : args) {
                        if (v.isArray()) result.elements().addAll(v.asArray().elements());
                        else result.push(v);
                    }
                    return JSValue.of(result);
                });
                default -> super.get(key);
            };
        }
    }

    @Override
    public void set(String key, JSValue val) {
        if ("length".equals(key)) {
            int newLen = val.asInt();
            while (elements.size() > newLen) elements.remove(elements.size() - 1);
            while (elements.size() < newLen) elements.add(JSValue.UNDEFINED);
            return;
        }
        try {
            int i = Integer.parseInt(key);
            set(i, val);
        } catch (NumberFormatException e) {
            super.set(key, val);
        }
    }

    public String toJsString() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < elements.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(elements.get(i).asString());
        }
        sb.append("]");
        return sb.toString();
    }

    // ── getProp / setProp (JSValue tarafından çağrılır) ──────────────────

    public JSValue getProp(String name) {
        return get(name);
    }

    public void setProp(String name, JSValue value) {
        set(name, value);
    }

    @Override
    public String toString() { return toJsString(); }
}