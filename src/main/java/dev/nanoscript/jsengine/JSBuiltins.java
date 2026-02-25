package dev.nanoscript.jsengine;

import java.util.*;

/**
 * Yerleşik JS fonksiyonları ve nesneleri.
 * Math, JSON, parseInt, parseFloat, isNaN, console, String, Array, Object...
 */
public class JSBuiltins {

    /** Global ortamı yerleşik fonksiyonlarla doldur */
    public static void install(Environment env) {

        // ── console ───────────────────────────────────────────────────
        JSObject console = new JSObject();
        console.set("log",   fn("log",   (args, e) -> { System.out.println(joinArgs(args)); return JSValue.UNDEFINED; }));
        console.set("error", fn("error", (args, e) -> { System.err.println("[ERR] " + joinArgs(args)); return JSValue.UNDEFINED; }));
        console.set("warn",  fn("warn",  (args, e) -> { System.out.println("[WARN] " + joinArgs(args)); return JSValue.UNDEFINED; }));
        env.define("console", JSValue.of(console));

        // ── Type conversion ────────────────────────────────────────────
        env.define("parseInt",  fn("parseInt",  (args, e) -> {
            if (args.length == 0) return JSValue.NAN;
            try {
                int radix = args.length > 1 ? args[1].asInt() : 10;
                String s = args[0].asString().trim();
                if (s.startsWith("0x") || s.startsWith("0X")) return JSValue.of(Long.parseLong(s.substring(2), 16));
                return JSValue.of((double) Long.parseLong(s, radix));
            } catch (Exception ex) { return JSValue.NAN; }
        }));
        env.define("parseFloat", fn("parseFloat", (args, e) -> {
            if (args.length == 0) return JSValue.NAN;
            try { return JSValue.of(Double.parseDouble(args[0].asString().trim())); }
            catch (Exception ex) { return JSValue.NAN; }
        }));
        env.define("isNaN",      fn("isNaN",     (args, e) -> JSValue.of(args.length == 0 || Double.isNaN(args[0].asNumber()))));
        env.define("isFinite",   fn("isFinite",  (args, e) -> JSValue.of(args.length > 0 && Double.isFinite(args[0].asNumber()))));
        env.define("String",     fn("String",    (args, e) -> JSValue.of(args.length > 0 ? args[0].asString() : "")));
        env.define("Number",     fn("Number",    (args, e) -> JSValue.of(args.length > 0 ? args[0].asNumber() : 0.0)));
        env.define("Boolean",    fn("Boolean",   (args, e) -> JSValue.of(args.length > 0 && args[0].asBoolean())));

        // ── Math ───────────────────────────────────────────────────────
        JSObject math = new JSObject();
        math.set("PI",     JSValue.of(Math.PI));
        math.set("E",      JSValue.of(Math.E));
        math.set("abs",    fn("abs",   (args, e) -> JSValue.of(Math.abs(num(args, 0)))));
        math.set("ceil",   fn("ceil",  (args, e) -> JSValue.of(Math.ceil(num(args, 0)))));
        math.set("floor",  fn("floor", (args, e) -> JSValue.of(Math.floor(num(args, 0)))));
        math.set("round",  fn("round", (args, e) -> JSValue.of((double) Math.round(num(args, 0)))));
        math.set("sqrt",   fn("sqrt",  (args, e) -> JSValue.of(Math.sqrt(num(args, 0)))));
        math.set("pow",    fn("pow",   (args, e) -> JSValue.of(Math.pow(num(args, 0), num(args, 1)))));
        math.set("log",    fn("log",   (args, e) -> JSValue.of(Math.log(num(args, 0)))));
        math.set("log2",   fn("log2",  (args, e) -> JSValue.of(Math.log(num(args, 0)) / Math.log(2))));
        math.set("log10",  fn("log10", (args, e) -> JSValue.of(Math.log10(num(args, 0)))));
        math.set("sin",    fn("sin",   (args, e) -> JSValue.of(Math.sin(num(args, 0)))));
        math.set("cos",    fn("cos",   (args, e) -> JSValue.of(Math.cos(num(args, 0)))));
        math.set("tan",    fn("tan",   (args, e) -> JSValue.of(Math.tan(num(args, 0)))));
        math.set("min",    fn("min",   (args, e) -> { double m = Double.MAX_VALUE; for (JSValue a : args) m = Math.min(m, a.asNumber()); return JSValue.of(m); }));
        math.set("max",    fn("max",   (args, e) -> { double m = -Double.MAX_VALUE; for (JSValue a : args) m = Math.max(m, a.asNumber()); return JSValue.of(m); }));
        math.set("random", fn("random",(args, e) -> JSValue.of(Math.random())));
        math.set("trunc",  fn("trunc", (args, e) -> { double d = num(args, 0); return JSValue.of(d < 0 ? Math.ceil(d) : Math.floor(d)); }));
        math.set("sign",   fn("sign",  (args, e) -> { double d = num(args, 0); return JSValue.of(d < 0 ? -1 : d > 0 ? 1 : 0); }));
        env.define("Math", JSValue.of(math));

        // ── JSON ───────────────────────────────────────────────────────
        JSObject json = new JSObject();
        json.set("stringify", fn("stringify", (args, e) -> {
            if (args.length == 0) return JSValue.UNDEFINED;
            return JSValue.of(jsonStringify(args[0], args.length > 2 ? args[2].asString() : ""));
        }));
        json.set("parse", fn("parse", (args, e) -> {
            if (args.length == 0) return JSValue.NULL;
            return jsonParse(args[0].asString());
        }));
        env.define("JSON", JSValue.of(json));

        // ── Array ──────────────────────────────────────────────────────
        JSObject arrayObj = new JSObject();
        arrayObj.set("isArray",    fn("isArray",    (args, e) -> JSValue.of(args.length > 0 && args[0].isArray())));
        arrayObj.set("from",       fn("from",       (args, e) -> {
            JSArray arr = new JSArray();
            if (args.length > 0) {
                if (args[0].isArray()) args[0].asArray().elements().forEach(arr::push);
                else if (args[0].isString()) { for (char ch : args[0].asString().toCharArray()) arr.push(JSValue.of(String.valueOf(ch))); }
            }
            return JSValue.of(arr);
        }));
        arrayObj.set("of", fn("of", (args, e) -> {
            JSArray arr = new JSArray(List.of(args));
            return JSValue.of(arr);
        }));
        env.define("Array", JSValue.of(arrayObj));

        // ── Object ─────────────────────────────────────────────────────
        JSObject objObj = new JSObject();
        objObj.set("keys",    fn("keys",    (args, e) -> {
            JSArray arr = new JSArray();
            if (args.length > 0 && args[0].isObject()) args[0].asObject().ownKeys().forEach(k -> arr.push(JSValue.of(k)));
            return JSValue.of(arr);
        }));
        objObj.set("values",  fn("values",  (args, e) -> {
            JSArray arr = new JSArray();
            if (args.length > 0 && args[0].isObject()) args[0].asObject().ownProps().values().forEach(arr::push);
            return JSValue.of(arr);
        }));
        objObj.set("entries", fn("entries", (args, e) -> {
            JSArray arr = new JSArray();
            if (args.length > 0 && args[0].isObject()) {
                for (Map.Entry<String, JSValue> en : args[0].asObject().ownProps().entrySet()) {
                    JSArray pair = new JSArray(); pair.push(JSValue.of(en.getKey())); pair.push(en.getValue());
                    arr.push(JSValue.of(pair));
                }
            }
            return JSValue.of(arr);
        }));
        objObj.set("assign",  fn("assign",  (args, e) -> {
            if (args.length < 1) return JSValue.UNDEFINED;
            JSObject target = args[0].asObject();
            if (target == null) return args[0];
            for (int i = 1; i < args.length; i++) {
                if (args[i].isObject()) args[i].asObject().ownProps().forEach(target::set);
            }
            return args[0];
        }));
        env.define("Object", JSValue.of(objObj));

        // ── String constructor ─────────────────────────────────────────
        // String.fromCharCode
        JSObject strObj = new JSObject();
        strObj.set("fromCharCode", fn("fromCharCode", (args, e) -> {
            StringBuilder sb = new StringBuilder();
            for (JSValue a : args) sb.append((char) a.asInt());
            return JSValue.of(sb.toString());
        }));
        env.define("String", fn("String", (args, _e) -> JSValue.of(args.length > 0 ? args[0].asString() : "")));

        // ── Misc globals ───────────────────────────────────────────────
        env.define("undefined", JSValue.UNDEFINED);
        env.define("NaN",       JSValue.NAN);
        env.define("Infinity",  JSValue.of(Double.POSITIVE_INFINITY));
        env.define("null",      JSValue.NULL);

        env.define("encodeURIComponent", fn("encodeURIComponent", (args, e) -> {
            try { return JSValue.of(java.net.URLEncoder.encode(args[0].asString(), "UTF-8").replace("+", "%20")); }
            catch (Exception ex) { return args.length > 0 ? args[0] : JSValue.UNDEFINED; }
        }));

        env.define("setTimeout",   fn("setTimeout",   (args, e) -> JSValue.of(0))); // stub
        env.define("clearTimeout", fn("clearTimeout", (args, e) -> JSValue.UNDEFINED)); // stub

        // ── Date ───────────────────────────────────────────────────────
        // new Date() ile kullanılabilir
        env.define("Date", JSFunction.native1("Date", (dateArgs, e2) -> {
            JSObject dateObj = new JSObject();
            java.util.Calendar cal = java.util.Calendar.getInstance();
            dateObj.set("getFullYear",     fn("getFullYear",     (a, _e) -> JSValue.of(cal.get(java.util.Calendar.YEAR))));
            dateObj.set("getMonth",        fn("getMonth",        (a, _e) -> JSValue.of(cal.get(java.util.Calendar.MONTH))));
            dateObj.set("getDate",         fn("getDate",         (a, _e) -> JSValue.of(cal.get(java.util.Calendar.DAY_OF_MONTH))));
            dateObj.set("getDay",          fn("getDay",          (a, _e) -> JSValue.of(cal.get(java.util.Calendar.DAY_OF_WEEK) - 1)));
            dateObj.set("getHours",        fn("getHours",        (a, _e) -> JSValue.of(cal.get(java.util.Calendar.HOUR_OF_DAY))));
            dateObj.set("getMinutes",      fn("getMinutes",      (a, _e) -> JSValue.of(cal.get(java.util.Calendar.MINUTE))));
            dateObj.set("getSeconds",      fn("getSeconds",      (a, _e) -> JSValue.of(cal.get(java.util.Calendar.SECOND))));
            dateObj.set("getMilliseconds", fn("getMilliseconds", (a, _e) -> JSValue.of(cal.get(java.util.Calendar.MILLISECOND))));
            dateObj.set("getTime",         fn("getTime",         (a, _e) -> JSValue.of((double) cal.getTimeInMillis())));
            dateObj.set("toString",        fn("toString",        (a, _e) -> JSValue.of(new java.util.Date(cal.getTimeInMillis()).toString())));
            return JSValue.of(dateObj);
        }));

    }

    // ── String methods (called from JSValue.getProp) ──────────────────

    public static JSValue getStringMethod(String name) {
        return switch (name) {
            case "toUpperCase"    -> fn("toUpperCase",    (args, e) -> { /* handled in interp */ return JSValue.UNDEFINED; });
            case "toLowerCase"    -> fn("toLowerCase",    (args, e) -> JSValue.UNDEFINED);
            case "trim"           -> fn("trim",           (args, e) -> JSValue.UNDEFINED);
            case "split"          -> fn("split",          (args, e) -> JSValue.UNDEFINED);
            case "replace"        -> fn("replace",        (args, e) -> JSValue.UNDEFINED);
            case "includes"       -> fn("includes",       (args, e) -> JSValue.UNDEFINED);
            case "startsWith"     -> fn("startsWith",     (args, e) -> JSValue.UNDEFINED);
            case "endsWith"       -> fn("endsWith",       (args, e) -> JSValue.UNDEFINED);
            case "indexOf"        -> fn("indexOf",        (args, e) -> JSValue.UNDEFINED);
            case "substring"      -> fn("substring",      (args, e) -> JSValue.UNDEFINED);
            case "slice"          -> fn("slice",          (args, e) -> JSValue.UNDEFINED);
            case "charAt"         -> fn("charAt",         (args, e) -> JSValue.UNDEFINED);
            case "charCodeAt"     -> fn("charCodeAt",     (args, e) -> JSValue.UNDEFINED);
            case "repeat"         -> fn("repeat",         (args, e) -> JSValue.UNDEFINED);
            case "padStart"       -> fn("padStart",       (args, e) -> JSValue.UNDEFINED);
            case "padEnd"         -> fn("padEnd",         (args, e) -> JSValue.UNDEFINED);
            default               -> JSValue.UNDEFINED;
        };
    }

    /** String method dispatch — called from Interpreter.callStringMethod */
    public static JSValue callStringMethod(String str, String method, JSValue[] args) {
        return switch (method) {
            case "toUpperCase"  -> JSValue.of(str.toUpperCase());
            case "toLowerCase"  -> JSValue.of(str.toLowerCase());
            case "trim"         -> JSValue.of(str.trim());
            case "trimStart", "trimLeft"  -> JSValue.of(str.stripLeading());
            case "trimEnd", "trimRight"   -> JSValue.of(str.stripTrailing());
            case "length"       -> JSValue.of(str.length());
            case "charAt"       -> {
                int i = args.length > 0 ? args[0].asInt() : 0;
                yield i >= 0 && i < str.length() ? JSValue.of(String.valueOf(str.charAt(i))) : JSValue.of("");
            }
            case "charCodeAt"   -> {
                int i = args.length > 0 ? args[0].asInt() : 0;
                yield i >= 0 && i < str.length() ? JSValue.of((double) str.charAt(i)) : JSValue.NAN;
            }
            case "indexOf"      -> JSValue.of(args.length > 0 ? str.indexOf(args[0].asString()) : -1);
            case "lastIndexOf"  -> JSValue.of(args.length > 0 ? str.lastIndexOf(args[0].asString()) : -1);
            case "includes"     -> JSValue.of(args.length > 0 && str.contains(args[0].asString()));
            case "startsWith"   -> JSValue.of(args.length > 0 && str.startsWith(args[0].asString()));
            case "endsWith"     -> JSValue.of(args.length > 0 && str.endsWith(args[0].asString()));
            case "slice", "substring" -> {
                int from = args.length > 0 ? clamp(args[0].asInt(), 0, str.length()) : 0;
                int to   = args.length > 1 ? clamp(args[1].asInt(), 0, str.length()) : str.length();
                if (method.equals("slice") && args.length > 0 && args[0].asInt() < 0)
                    from = Math.max(0, str.length() + args[0].asInt());
                if (method.equals("slice") && args.length > 1 && args[1].asInt() < 0)
                    to = Math.max(0, str.length() + args[1].asInt());
                yield from <= to ? JSValue.of(str.substring(from, Math.min(to, str.length()))) : JSValue.of("");
            }
            case "split"        -> {
                JSArray arr = new JSArray();
                if (args.length == 0) { arr.push(JSValue.of(str)); yield JSValue.of(arr); }
                String sep = args[0].asString();
                int limit = args.length > 1 ? args[1].asInt() : Integer.MAX_VALUE;
                String[] parts = sep.isEmpty() ? str.split("") : str.split(java.util.regex.Pattern.quote(sep), -1);
                for (int i = 0; i < Math.min(parts.length, limit); i++) arr.push(JSValue.of(parts[i]));
                yield JSValue.of(arr);
            }
            case "replace"      -> {
                if (args.length < 2) yield JSValue.of(str);
                yield JSValue.of(str.replace(args[0].asString(), args[1].asString()));
            }
            case "replaceAll"   -> {
                if (args.length < 2) yield JSValue.of(str);
                yield JSValue.of(str.replace(args[0].asString(), args[1].asString()));
            }
            case "repeat"       -> {
                int n = args.length > 0 ? args[0].asInt() : 0;
                yield JSValue.of(str.repeat(Math.max(0, n)));
            }
            case "padStart"     -> {
                int len = args.length > 0 ? args[0].asInt() : 0;
                String pad = args.length > 1 ? args[1].asString() : " ";
                StringBuilder sb = new StringBuilder();
                while (sb.length() + str.length() < len) sb.append(pad);
                yield JSValue.of(sb.substring(0, Math.max(0, len - str.length())) + str);
            }
            case "padEnd"       -> {
                int len = args.length > 0 ? args[0].asInt() : 0;
                StringBuilder sb = new StringBuilder(str);
                String pad = args.length > 1 ? args[1].asString() : " ";
                while (sb.length() < len) sb.append(pad);
                yield JSValue.of(sb.substring(0, len));
            }
            case "concat"       -> {
                StringBuilder sb = new StringBuilder(str);
                for (JSValue a : args) sb.append(a.asString());
                yield JSValue.of(sb.toString());
            }
            case "match"        -> JSValue.NULL; // simplified
            case "toString", "valueOf" -> JSValue.of(str);
            default             -> JSValue.UNDEFINED;
        };
    }

    // ── JSON helpers ──────────────────────────────────────────────────

    private static String jsonStringify(JSValue val, String indent) {
        return stringify(val, indent, 0);
    }

    private static String stringify(JSValue val, String indent, int depth) {
        if (val.isNullish()) return "null";
        if (val.isBoolean()) return val.asBoolean() ? "true" : "false";
        if (val.isNumber()) return val.asString();
        if (val.isString()) return "\"" + val.asString().replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n") + "\"";
        if (val.isArray()) {
            JSArray arr = val.asArray();
            if (arr.length() == 0) return "[]";
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < arr.length(); i++) {
                if (i > 0) sb.append(",");
                sb.append(stringify(arr.get(i), indent, depth + 1));
            }
            return sb.append("]").toString();
        }
        if (val.isObject()) {
            JSObject obj = val.asObject();
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, JSValue> en : obj.ownProps().entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(en.getKey()).append("\":").append(stringify(en.getValue(), indent, depth + 1));
                first = false;
            }
            return sb.append("}").toString();
        }
        return "null";
    }

    private static JSValue jsonParse(String json) {
        json = json.trim();
        if (json.equals("null"))  return JSValue.NULL;
        if (json.equals("true"))  return JSValue.TRUE;
        if (json.equals("false")) return JSValue.FALSE;
        if (json.startsWith("\"") && json.endsWith("\"")) return JSValue.of(json.substring(1, json.length()-1));
        try { return JSValue.of(Double.parseDouble(json)); } catch (Exception ignored) {}
        // For complex objects, use a minimal approach
        return JSValue.of("[unparsed json]");
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private static JSFunction fn(String name, JSFunction.NativeFn impl) {
        return JSFunction.native1(name, impl);
    }

    private static double num(JSValue[] args, int i) {
        return i < args.length ? args[i].asNumber() : 0.0;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String joinArgs(JSValue[] args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(" ");
            sb.append(args[i].asString());
        }
        return sb.toString();
    }
}