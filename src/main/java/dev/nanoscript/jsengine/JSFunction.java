package dev.nanoscript.jsengine;

import java.util.List;

/**
 * JS Fonksiyonu — ya closure tabanlı kullanıcı fonksiyonu,
 * ya da native Java lambda.
 *
 * Kullanıcı fonksiyonu: params listesi + AST body + closure environment
 * Native fonksiyon: NativeFn lambda
 */
public class JSFunction extends JSValue {

    @FunctionalInterface
    public interface NativeFn {
        JSValue call(JSValue[] args, Environment env);
    }

    private final String name;

    // User-defined function fields
    private final List<String> params;
    private final Node body;
    private final Environment closure;

    // Native function field
    private final NativeFn native_;

    // The interpreter reference (set when function is created)
    private Interpreter interpreter;

    // ── Constructors ──────────────────────────────────────────────────

    /** User-defined function */
    public JSFunction(String name, List<String> params, Node body, Environment closure) {
        super(Type.OBJECT, null);
        this.name    = name != null ? name : "anonymous";
        this.params  = params;
        this.body    = body;
        this.closure = closure;
        this.native_ = null;
    }

    /** Native (Java) function */
    private JSFunction(String name, NativeFn fn) {
        super(Type.OBJECT, null);
        this.name    = name;
        this.params  = List.of();
        this.body    = null;
        this.closure = null;
        this.native_ = fn;
    }

    // ── Factory ───────────────────────────────────────────────────────

    public static JSFunction native1(String name, NativeFn fn) {
        return new JSFunction(name, fn);
    }

    // ── Invocation ────────────────────────────────────────────────────

    /**
     * Fonksiyonu çağırır.
     * @param thisVal  'this' değeri (null → global)
     * @param args     argümanlar
     * @return         dönüş değeri
     */
    public JSValue call(JSValue thisVal, JSValue[] args) {
        if (native_ != null) {
            try {
                return native_.call(args, null);
            } catch (JsError e) {
                throw e;
            } catch (Exception e) {
                throw new JsError("Native fonksiyon hatası '" + name + "': " + e.getMessage());
            }
        }

        if (interpreter == null)
            throw new JsError("Fonksiyon '" + name + "' için interpreter bağlı değil");

        // Yeni scope oluştur (closure'ı parent olarak kullan)
        Environment funcEnv = new Environment(closure);

        // Parametreleri bağla
        for (int i = 0; i < params.size(); i++) {
            funcEnv.define(params.get(i), i < args.length ? args[i] : JSValue.UNDEFINED);
        }

        // 'this' bağla
        funcEnv.define("this", thisVal != null ? thisVal : JSValue.UNDEFINED);

        // 'arguments' objesi
        JSArray argsArray = new JSArray();
        for (JSValue arg : args) argsArray.push(arg);
        funcEnv.define("arguments", JSValue.of(argsArray));

        try {
            interpreter.executeNode(body, funcEnv);
            return JSValue.UNDEFINED;
        } catch (ReturnSignal ret) {
            return ret.value;
        }
    }

    // ── Property access (function.length, function.name) ─────────────

    public JSValue getProp(String name) {
        return switch (name) {
            case "name"   -> JSValue.of(this.name);
            case "length" -> JSValue.of(params.size());
            case "call"   -> JSFunction.native1("call", (args, env) -> {
                JSValue self = args.length > 0 ? args[0] : JSValue.UNDEFINED;
                JSValue[] rest = args.length > 1 ? java.util.Arrays.copyOfRange(args, 1, args.length) : new JSValue[0];
                return call(self, rest);
            });
            case "apply"  -> JSFunction.native1("apply", (args, env) -> {
                JSValue self = args.length > 0 ? args[0] : JSValue.UNDEFINED;
                JSValue[] rest = args.length > 1 && args[1].isArray()
                        ? args[1].asArray().elements().toArray(new JSValue[0])
                        : new JSValue[0];
                return call(self, rest);
            });
            case "bind"   -> JSFunction.native1("bind", (bindArgs, env2) -> {
                JSFunction thisFunc = this;
                JSValue boundThis = bindArgs.length > 0 ? bindArgs[0] : JSValue.UNDEFINED;
                return JSFunction.native1("bound_" + this.name, (a, e) -> thisFunc.call(boundThis, a));
            });
            default -> JSValue.UNDEFINED;
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────

    public void setInterpreter(Interpreter interp) { this.interpreter = interp; }
    public Interpreter getInterpreter() { return interpreter; }
    public String getFnName() { return name; }
    public boolean isNative() { return native_ != null; }

    @Override
    public boolean isFunction() { return true; }

    @Override
    public String asString() { return "[function " + name + "]"; }

    @Override
    public String toString() { return asString(); }
}