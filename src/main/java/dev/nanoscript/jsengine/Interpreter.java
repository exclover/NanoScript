package dev.nanoscript.jsengine;

import java.util.ArrayList;
import java.util.List;

/**
 * NanoJS AST Yorumlayıcısı (Tree-Walk Interpreter)
 *
 * Her Node tipini recursive olarak değerlendirir.
 * Temel tasarım prensipleri:
 *  - Saf Java, sıfır bağımlılık
 *  - Hızlı allocation: küçük objeler, minimal GC baskısı
 *  - İstisna tabanlı kontrol akışı (Return/Break/Continue)
 *  - Java nesnelerine reflection ile erişim (Bukkit API)
 */
public class Interpreter {

    private final Environment globalEnv;

    public Interpreter() {
        this.globalEnv = new Environment(null);
        JSBuiltins.install(globalEnv);
    }

    public Environment getGlobalEnv() { return globalEnv; }

    // ──────────────────────────────────────────────────────────────────
    //  Entry points
    // ──────────────────────────────────────────────────────────────────

    public JSValue execute(Node.Program program) {
        return execute(program, globalEnv);
    }

    public JSValue execute(Node.Program program, Environment env) {
        // First pass: hoist function declarations
        hoistFunctions(program.body(), env);
        JSValue last = JSValue.UNDEFINED;
        for (Node stmt : program.body()) {
            last = executeNode(stmt, env);
        }
        return last;
    }

    /** Private entry used by ScriptInstance for block execution */
    public JSValue executeNode(Node node, Environment env) {
        if (node == null) return JSValue.UNDEFINED;

        return switch (node) {

            // ── Statements ───────────────────────────────────────────

            case Node.Program p -> execute(p, env);

            case Node.Block b -> {
                Environment blockEnv = new Environment(env);
                hoistFunctions(b.body(), blockEnv);
                JSValue result = JSValue.UNDEFINED;
                for (Node s : b.body()) result = executeNode(s, blockEnv);
                yield result;
            }

            case Node.ExprStmt s -> evalExpr(s.expr(), env);

            case Node.VarDecl d -> {
                for (Node.VarDecl.Declarator decl : d.decls()) {
                    JSValue val = decl.init() != null ? evalExpr(decl.init(), env) : JSValue.UNDEFINED;
                    env.define(decl.name(), val);
                }
                yield JSValue.UNDEFINED;
            }

            case Node.FuncDecl f -> {
                // Already hoisted, but handle re-definition
                JSFunction fn = makeFunction(f.name(), f.params(), f.body(), env);
                env.define(f.name(), fn);
                yield JSValue.UNDEFINED;
            }

            case Node.IfStmt i -> {
                boolean test = evalExpr(i.test(), env).asBoolean();
                if (test) { yield executeNode(i.then(), env); }
                else if (i.else_() != null) { yield executeNode(i.else_(), env); }
                yield JSValue.UNDEFINED;
            }

            case Node.WhileStmt w -> {
                while (evalExpr(w.test(), env).asBoolean()) {
                    try { executeNode(w.body(), env); }
                    catch (BreakSignal b) { break; }
                    catch (ContinueSignal c) { /* continue */ }
                }
                yield JSValue.UNDEFINED;
            }

            case Node.DoWhileStmt d -> {
                do {
                    try { executeNode(d.body(), env); }
                    catch (BreakSignal b) { break; }
                    catch (ContinueSignal c) { /* continue */ }
                } while (evalExpr(d.test(), env).asBoolean());
                yield JSValue.UNDEFINED;
            }

            case Node.ForStmt f -> {
                Environment forEnv = new Environment(env);
                if (f.init() != null) executeNode(f.init(), forEnv);
                outer:
                while (f.test() == null || evalExpr(f.test(), forEnv).asBoolean()) {
                    try { executeNode(f.body(), forEnv); }
                    catch (BreakSignal b) { break; }
                    catch (ContinueSignal c) { /* continue to update */ }
                    if (f.update() != null) evalExpr(f.update(), forEnv);
                }
                yield JSValue.UNDEFINED;
            }

            case Node.ForInStmt f -> {
                JSValue obj = evalExpr(f.obj(), env);
                Environment forEnv = new Environment(env);
                forEnv.define(f.var(), JSValue.UNDEFINED);
                if (f.isOf()) {
                    // for..of: iterate values
                    if (obj.isArray()) {
                        for (JSValue val : obj.asArray().elements()) {
                            forEnv.set(f.var(), val);
                            try { executeNode(f.body(), forEnv); }
                            catch (BreakSignal b) { break; }
                            catch (ContinueSignal c) { /* continue */ }
                        }
                    } else if (obj.isString()) {
                        for (char ch : obj.asString().toCharArray()) {
                            forEnv.set(f.var(), JSValue.of(String.valueOf(ch)));
                            try { executeNode(f.body(), forEnv); }
                            catch (BreakSignal b) { break; }
                            catch (ContinueSignal c) { /* continue */ }
                        }
                    }
                } else {
                    // for..in: iterate keys
                    if (obj.isObject()) {
                        for (String key : obj.asObject().ownKeys()) {
                            forEnv.set(f.var(), JSValue.of(key));
                            try { executeNode(f.body(), forEnv); }
                            catch (BreakSignal b) { break; }
                            catch (ContinueSignal c) { /* continue */ }
                        }
                    }
                }
                yield JSValue.UNDEFINED;
            }

            case Node.ReturnStmt r ->
                    throw new ReturnSignal(r.value() != null ? evalExpr(r.value(), env) : JSValue.UNDEFINED);

            case Node.BreakStmt b    -> throw new BreakSignal(b.label());
            case Node.ContinueStmt c -> throw new ContinueSignal(c.label());

            case Node.ThrowStmt t -> {
                JSValue val = evalExpr(t.value(), env);
                throw new ThrowSignal(val);
            }

            case Node.TryStmt t -> {
                try {
                    executeNode(t.body(), env);
                } catch (ThrowSignal thrown) {
                    if (t.catchBody() != null) {
                        Environment catchEnv = new Environment(env);
                        if (t.catchVar() != null) catchEnv.define(t.catchVar(), thrown.value);
                        try { executeNode(t.catchBody(), catchEnv); }
                        catch (ReturnSignal | BreakSignal | ContinueSignal sig) {
                            if (t.finallyBody() != null) executeNode(t.finallyBody(), env);
                            throw sig;
                        }
                    }
                } catch (ReturnSignal | BreakSignal | ContinueSignal sig) {
                    if (t.finallyBody() != null) executeNode(t.finallyBody(), env);
                    throw sig;
                } finally {
                    if (t.finallyBody() != null) executeNode(t.finallyBody(), env);
                }
                yield JSValue.UNDEFINED;
            }

            case Node.SwitchStmt s -> {
                JSValue disc = evalExpr(s.disc(), env);
                boolean matched = false;
                outer:
                for (Node.SwitchStmt.SwitchCase c : s.cases()) {
                    if (!matched && c.test() != null) {
                        JSValue test = evalExpr(c.test(), env);
                        matched = disc.strictEquals(test);
                    } else if (c.test() == null) {
                        matched = true; // default
                    }
                    if (matched) {
                        for (Node stmt : c.body()) {
                            try { executeNode(stmt, env); }
                            catch (BreakSignal b) { break outer; }
                        }
                    }
                }
                yield JSValue.UNDEFINED;
            }

            // ── Expressions as statements ────────────────────────────
            default -> evalExpr(node, env);
        };
    }

    // ──────────────────────────────────────────────────────────────────
    //  Expression evaluator
    // ──────────────────────────────────────────────────────────────────

    public JSValue evalExpr(Node node, Environment env) {
        return switch (node) {

            case Node.Lit l -> {
                Object val = l.value();
                if (val == null)             yield JSValue.NULL;
                if (val instanceof Boolean b) yield JSValue.of(b);
                if (val instanceof Double d)  yield JSValue.of(d);
                if (val instanceof String s)  yield JSValue.of(s);
                yield JSValue.UNDEFINED;
            }

            case Node.Ident id -> env.get(id.name());

            case Node.Template t -> {
                StringBuilder sb = new StringBuilder();
                for (Node part : t.parts()) sb.append(evalExpr(part, env).asString());
                yield JSValue.of(sb.toString());
            }

            case Node.ArrayLit a -> {
                JSArray arr = new JSArray();
                for (Node el : a.elements()) {
                    if (el instanceof Node.Spread s) {
                        JSValue spread = evalExpr(s.expr(), env);
                        if (spread.isArray()) spread.asArray().elements().forEach(arr::push);
                    } else {
                        arr.push(evalExpr(el, env));
                    }
                }
                yield JSValue.of(arr);
            }

            case Node.ObjLit o -> {
                JSObject obj = new JSObject();
                for (Node.ObjLit.ObjProp prop : o.props()) {
                    JSValue val = evalExpr(prop.value(), env);
                    obj.set(prop.key(), val);
                }
                yield JSValue.of(obj);
            }

            case Node.FuncExpr f -> makeFunction(f.name(), f.params(), f.body(), env);
            case Node.ArrowFunc a -> makeFunction(null, a.params(), a.body(), env);

            case Node.Assign assign -> evalAssign(assign, env);

            case Node.Binary bin -> evalBinary(bin.op(), evalExpr(bin.left(), env), evalExpr(bin.right(), env));

            case Node.Logical log -> {
                JSValue left = evalExpr(log.left(), env);
                yield switch (log.op()) {
                    case "&&" -> !left.asBoolean() ? left : evalExpr(log.right(), env);
                    case "||" -> left.asBoolean() ? left : evalExpr(log.right(), env);
                    case "??" -> !left.isNullish() ? left : evalExpr(log.right(), env);
                    default   -> JSValue.UNDEFINED;
                };
            }

            case Node.Unary u -> evalUnary(u, env);
            case Node.Update u -> evalUpdate(u, env);
            case Node.Ternary t -> evalExpr(t.test(), env).asBoolean() ? evalExpr(t.then(), env) : evalExpr(t.else_(), env);

            case Node.Member m -> {
                JSValue obj = evalExpr(m.obj(), env);
                yield getProperty(obj, m.prop());
            }

            case Node.Index idx -> {
                JSValue obj = evalExpr(idx.obj(), env);
                JSValue key = evalExpr(idx.key(), env);
                yield getProperty(obj, key.asString());
            }

            case Node.Call call -> evalCall(call, env);

            case Node.New_ n -> evalNew(n, env);

            // Spread in other contexts
            case Node.Spread s -> evalExpr(s.expr(), env);

            default -> executeNode(node, env); // fallback to statement eval
        };
    }

    // ──────────────────────────────────────────────────────────────────
    //  Assignment
    // ──────────────────────────────────────────────────────────────────

    private JSValue evalAssign(Node.Assign assign, Environment env) {
        JSValue value = evalExpr(assign.value(), env);

        // Compound assignment: first read current value
        if (!assign.op().equals("=")) {
            JSValue current = evalExpr(assign.target(), env);
            value = switch (assign.op()) {
                case "+=" -> evalBinary("+", current, value);
                case "-=" -> evalBinary("-", current, value);
                case "*=" -> evalBinary("*", current, value);
                case "/=" -> evalBinary("/", current, value);
                case "%=" -> evalBinary("%", current, value);
                default   -> value;
            };
        }

        // Write target
        setTarget(assign.target(), value, env);
        return value;
    }

    private void setTarget(Node target, JSValue value, Environment env) {
        switch (target) {
            case Node.Ident id -> env.set(id.name(), value);
            case Node.Member m -> {
                JSValue obj = evalExpr(m.obj(), env);
                obj.setProp(m.prop(), value);
            }
            case Node.Index idx -> {
                JSValue obj = evalExpr(idx.obj(), env);
                JSValue key = evalExpr(idx.key(), env);
                obj.setProp(key.asString(), value);
            }
            default -> throw new JsError("Geçersiz atama hedefi: " + target.getClass().getSimpleName());
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  Binary operators
    // ──────────────────────────────────────────────────────────────────

    private JSValue evalBinary(String op, JSValue left, JSValue right) {
        return switch (op) {
            case "+" -> {
                // String concat wins over addition if either side is string
                if (left.isString() || right.isString())
                    yield JSValue.of(left.asString() + right.asString());
                yield JSValue.of(left.asNumber() + right.asNumber());
            }
            case "-"  -> JSValue.of(left.asNumber() - right.asNumber());
            case "*"  -> JSValue.of(left.asNumber() * right.asNumber());
            case "/"  -> JSValue.of(left.asNumber() / right.asNumber());
            case "%"  -> JSValue.of(left.asNumber() % right.asNumber());
            case "**" -> JSValue.of(Math.pow(left.asNumber(), right.asNumber()));
            case "<"  -> JSValue.of(left.asNumber() < right.asNumber());
            case ">"  -> JSValue.of(left.asNumber() > right.asNumber());
            case "<=" -> JSValue.of(left.asNumber() <= right.asNumber());
            case ">=" -> JSValue.of(left.asNumber() >= right.asNumber());
            case "==" , "!=" -> {
                boolean eq = left.looseEquals(right);
                yield JSValue.of(op.equals("==") ? eq : !eq);
            }
            case "===", "!==" -> {
                boolean eq = left.strictEquals(right);
                yield JSValue.of(op.equals("===") ? eq : !eq);
            }
            case "&"  -> JSValue.of((double)(left.asInt() & right.asInt()));
            case "|"  -> JSValue.of((double)(left.asInt() | right.asInt()));
            case "^"  -> JSValue.of((double)(left.asInt() ^ right.asInt()));
            case "<<" -> JSValue.of((double)(left.asInt() << right.asInt()));
            case ">>" -> JSValue.of((double)(left.asInt() >> right.asInt()));
            case ">>>"-> JSValue.of((double)(left.asInt() >>> right.asInt()));
            case "instanceof" -> JSValue.of(left.isJava() && right.isJava() && right.javaRaw() instanceof Class<?> cls && cls.isInstance(left.javaRaw()));
            case "in" -> {
                if (right.isObject()) yield JSValue.of(right.asObject().has(left.asString()));
                yield JSValue.FALSE;
            }
            default -> throw new JsError("Bilinmeyen operator: " + op);
        };
    }

    // ──────────────────────────────────────────────────────────────────
    //  Unary / Update
    // ──────────────────────────────────────────────────────────────────

    private JSValue evalUnary(Node.Unary u, Environment env) {
        return switch (u.op()) {
            case "-"      -> JSValue.of(-evalExpr(u.operand(), env).asNumber());
            case "+"      -> JSValue.of(evalExpr(u.operand(), env).asNumber());
            case "!"      -> JSValue.of(!evalExpr(u.operand(), env).asBoolean());
            case "~"      -> JSValue.of((double)(~evalExpr(u.operand(), env).asInt()));
            case "typeof" -> {
                JSValue val;
                try { val = evalExpr(u.operand(), env); }
                catch (JsError e) { yield JSValue.of("undefined"); }
                yield JSValue.of(switch (val.getType()) {
                    case UNDEFINED -> "undefined";
                    case NULL      -> "object";
                    case BOOLEAN   -> "boolean";
                    case NUMBER    -> "number";
                    case STRING    -> "string";
                    case OBJECT    -> val.isFunction() ? "function" : "object";
                    default        -> val.isFunction() ? "function" : "object";
                });
            }
            case "void"   -> { evalExpr(u.operand(), env); yield JSValue.UNDEFINED; }
            case "delete" -> {
                if (u.operand() instanceof Node.Member m) {
                    JSValue obj = evalExpr(m.obj(), env);
                    if (obj.isObject()) obj.asObject().delete(m.prop());
                }
                yield JSValue.TRUE;
            }
            default -> JSValue.UNDEFINED;
        };
    }

    private JSValue evalUpdate(Node.Update u, Environment env) {
        JSValue current = evalExpr(u.operand(), env);
        double num = current.asNumber();
        double updated = u.op().equals("++") ? num + 1 : num - 1;
        setTarget(u.operand(), JSValue.of(updated), env);
        return u.prefix() ? JSValue.of(updated) : JSValue.of(num);
    }

    // ──────────────────────────────────────────────────────────────────
    //  Function calls
    // ──────────────────────────────────────────────────────────────────

    private JSValue evalCall(Node.Call call, Environment env) {
        List<JSValue> argsList = evalArgs(call.args(), env);
        JSValue[] args = argsList.toArray(new JSValue[0]);

        // ── Member call: obj.method(args)
        if (call.callee() instanceof Node.Member m) {
            JSValue obj = evalExpr(m.obj(), env);
            String methodName = m.prop();
            return callMethod(obj, methodName, args, env);
        }

        // ── Indexed call: obj["method"](args)
        if (call.callee() instanceof Node.Index idx) {
            JSValue obj = evalExpr(idx.obj(), env);
            String methodName = evalExpr(idx.key(), env).asString();
            return callMethod(obj, methodName, args, env);
        }

        // ── Direct call: foo(args)
        JSValue callee = evalExpr(call.callee(), env);
        return invokeCallable(callee, JSValue.UNDEFINED, args);
    }

    private JSValue callMethod(JSValue obj, String methodName, JSValue[] args, Environment env) {
        // Number methods (toFixed, toString, etc.)
        if (obj.isNumber()) {
            return callNumberMethod(obj.asNumber(), methodName, args);
        }

        // String methods
        if (obj.isString()) {
            return JSBuiltins.callStringMethod(obj.asString(), methodName, args);
        }

        // JSObject methods
        if (obj.isObject()) {
            JSValue prop = obj.asObject().get(methodName);
            if (prop.isFunction()) {
                return invokeCallable(prop, obj, args);
            }
            if (prop.isUndefined()) {
                // Array methods already handled in JSArray.get()
                JSValue arrProp = obj.getProp(methodName);
                if (arrProp.isFunction()) {
                    return invokeCallable(arrProp, obj, args);
                }
            }
            throw new JsError("'" + methodName + "' bir fonksiyon değil");
        }

        // JSFunction property (like .call, .apply, .bind)
        if (obj.isFunction()) {
            JSValue prop = ((JSFunction) obj).getProp(methodName);
            if (prop.isFunction()) return invokeCallable(prop, obj, args);
        }

        // Java object method call
        if (obj.isJava()) {
            return JavaInterop.invoke(obj.javaRaw(), methodName, args);
        }

        // Check if it's a JSCallableMethod (lazy property)
        if (obj.isJava() && obj.javaRaw() instanceof JavaInterop.JSCallableMethod m) {
            return JavaInterop.invoke(m.target, methodName, args);
        }

        throw new JsError("'" + obj.asString() + "' üzerinde '" + methodName + "' metodu bulunamadı");
    }

    private JSValue invokeCallable(JSValue callee, JSValue thisVal, JSValue[] args) {
        if (callee instanceof JSFunction fn) {
            return fn.call(thisVal, args);
        }
        // Handle JSCallableMethod wrapped as JAVA value
        if (callee.isJava() && callee.javaRaw() instanceof JavaInterop.JSCallableMethod m) {
            return JavaInterop.invoke(m.target, m.methodName, args);
        }
        throw new JsError("'" + callee.asString() + "' bir fonksiyon değil");
    }

    private List<JSValue> evalArgs(List<Node> argNodes, Environment env) {
        List<JSValue> result = new ArrayList<>();
        for (Node argNode : argNodes) {
            if (argNode instanceof Node.Spread s) {
                JSValue spread = evalExpr(s.expr(), env);
                if (spread.isArray()) result.addAll(spread.asArray().elements());
                else result.add(spread);
            } else {
                result.add(evalExpr(argNode, env));
            }
        }
        return result;
    }

    // ──────────────────────────────────────────────────────────────────
    //  new operator
    // ──────────────────────────────────────────────────────────────────

    private JSValue evalNew(Node.New_ n, Environment env) {
        JSValue callee = evalExpr(n.callee(), env);
        List<JSValue> argsList = evalArgs(n.args(), env);
        JSValue[] args = argsList.toArray(new JSValue[0]);

        // JS constructor function
        if (callee instanceof JSFunction fn) {
            JSObject instance = new JSObject();
            JSValue result = fn.call(JSValue.of(instance), args);
            // If constructor returned an object, use it; otherwise use instance
            return (result.isObject() || result.isJava()) ? result : JSValue.of(instance);
        }

        throw new JsError("new: '" + callee.asString() + "' bir constructor değil");
    }

    // ──────────────────────────────────────────────────────────────────
    //  Property access
    // ──────────────────────────────────────────────────────────────────

    private JSValue getProperty(JSValue obj, String prop) {
        if (obj.isNullish())
            throw new JsError("null/undefined üzerinde '" + prop + "' erişilemiyor");

        // String methods
        if (obj.isString()) {
            if ("length".equals(prop)) return JSValue.of(obj.asString().length());
            // String index access
            try {
                int i = Integer.parseInt(prop);
                String s = obj.asString();
                return i >= 0 && i < s.length() ? JSValue.of(String.valueOf(s.charAt(i))) : JSValue.UNDEFINED;
            } catch (NumberFormatException e) {
                // Return a bound string method
                return JSFunction.native1(prop, (args, en) -> JSBuiltins.callStringMethod(obj.asString(), prop, args));
            }
        }

        // JSFunction properties
        if (obj instanceof JSFunction fn) {
            return fn.getProp(prop);
        }

        // JSArray / JSObject
        if (obj.isObject()) {
            return obj.asObject().get(prop);
        }

        // Java object property
        if (obj.isJava()) {
            Object raw = obj.javaRaw();
            Object result = JavaInterop.getProperty(raw, prop);
            if (result instanceof JavaInterop.JSCallableMethod) {
                // Return a callable that invokes the java method
                String methodName = prop;
                return JSFunction.native1(methodName, (args, en) -> JavaInterop.invoke(raw, methodName, args));
            }
            return JSValue.wrap(result);
        }

        return JSValue.UNDEFINED;
    }

    // ──────────────────────────────────────────────────────────────────
    //  Function creation helper
    // ──────────────────────────────────────────────────────────────────

    private JSFunction makeFunction(String name, List<String> params, Node body, Environment closure) {
        JSFunction fn = new JSFunction(name, params, body, closure);
        fn.setInterpreter(this);
        return fn;
    }

    // ──────────────────────────────────────────────────────────────────
    //  Function hoisting
    // ──────────────────────────────────────────────────────────────────

    private void hoistFunctions(List<Node> stmts, Environment env) {
        for (Node stmt : stmts) {
            if (stmt instanceof Node.FuncDecl f) {
                JSFunction fn = makeFunction(f.name(), f.params(), f.body(), env);
                env.define(f.name(), fn);
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  Public call helper (for NanoEngine, DynamicListener etc.)
    // ──────────────────────────────────────────────────────────────────

    /**
     * JS fonksiyonunu Java tarafından çağırır.
     * Örnek: eventListener.call(new JSValue[]{jsEvent})
     */
    public JSValue callFunction(JSValue fn, JSValue thisVal, JSValue[] args) {
        return invokeCallable(fn, thisVal, args);
    }

    public JSValue callFunction(JSValue fn, JSValue[] args) {
        return callFunction(fn, JSValue.UNDEFINED, args);
    }

    // ──────────────────────────────────────────────────────────────────
    //  Number method dispatch
    // ──────────────────────────────────────────────────────────────────

    private JSValue callNumberMethod(double num, String method, JSValue[] args) {
        return switch (method) {
            case "toFixed" -> {
                int decimals = args.length > 0 ? args[0].asInt() : 0;
                decimals = Math.max(0, Math.min(20, decimals));
                yield JSValue.of(String.format("%." + decimals + "f", num));
            }
            case "toPrecision" -> {
                int precision = args.length > 0 ? args[0].asInt() : 1;
                yield JSValue.of(String.format("%." + Math.max(1, precision) + "g", num));
            }
            case "toString" -> {
                int radix = args.length > 0 ? args[0].asInt() : 10;
                if (radix == 10) yield JSValue.of(JSValue.of(num).asString());
                yield JSValue.of(Long.toString((long) num, radix));
            }
            case "toLocaleString", "valueOf" -> JSValue.of(JSValue.of(num).asString());
            default -> throw new JsError(
                    "'" + JSValue.of(num).asString() + "' üzerinde '" + method + "' metodu bulunamadı");
        };
    }
}