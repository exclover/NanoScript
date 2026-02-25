package dev.nanoscript.jsengine;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Java nesneleri üzerinde reflection ile method/field erişimi.
 *
 * Örnek: event.getPlayer().getName() çağrısı için:
 *   1. getPlayer metodunu bul
 *   2. Çağır, Player nesnesi döner
 *   3. getName metodunu bul ve çağır
 *
 * Overload çözümleme: isim + argüman sayısına göre en iyi eşleşmeyi seç.
 */
public class JavaInterop {

    // Method cache: ClassName#methodName+arity → Method
    private static final Map<String, Method[]> METHOD_CACHE = new HashMap<>();

    // ── Property get (field veya no-arg getter) ───────────────────────

    public static Object getProperty(Object obj, String name) {
        if (obj == null) return null;
        Class<?> cls = obj.getClass();

        // 1. Try getter: getName → getName() / isActive → isActive()
        String getter = "get" + capitalize(name);
        String boolGetter = "is" + capitalize(name);

        try {
            Method m = cls.getMethod(getter);
            return m.invoke(obj);
        } catch (Exception ignored) {}

        try {
            Method m = cls.getMethod(boolGetter);
            return m.invoke(obj);
        } catch (Exception ignored) {}

        // 2. Dönüş değeri: JS'te property erişimi aslında method call olabilir
        // Bu durumda JSCallableJavaMethod döndür (ileride çağrılabilir olacak)
        return new JSCallableMethod(obj, name);
    }

    // ── Method invocation ─────────────────────────────────────────────

    public static JSValue invoke(Object obj, String methodName, JSValue[] args) {
        if (obj == null) throw new JsError("null üzerinde '" + methodName + "' çağrılamaz");

        Class<?> cls = obj.getClass();
        Method best = findBestMethod(cls, methodName, args);

        if (best == null) {
            throw new JsError("Method bulunamadı: " + cls.getSimpleName() + "." + methodName
                + "(" + args.length + " arg)");
        }

        try {
            Object[] javaArgs = convertArgs(best.getParameterTypes(), args);
            Object result = best.invoke(obj, javaArgs);
            return JSValue.wrap(result);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new JsError("Java method hatası " + methodName + ": " + cause.getMessage());
        }
    }

    // ── Best method finder ────────────────────────────────────────────

    static Method findBestMethod(Class<?> cls, String name, JSValue[] args) {
        String cacheKey = cls.getName() + "#" + name + "/" + args.length;
        Method[] cached = METHOD_CACHE.get(cacheKey);
        if (cached != null && cached.length > 0) return cached[0];

        List<Method> candidates = new ArrayList<>();
        collectMethods(cls, name, args.length, candidates);

        if (candidates.isEmpty()) {
            // Try with varargs or flexible arity
            collectMethods(cls, name, -1, candidates);
        }

        if (candidates.isEmpty()) return null;

        // Score each candidate
        Method best = null;
        int bestScore = -1;
        for (Method m : candidates) {
            int score = scoreMethod(m, args);
            if (score > bestScore) { bestScore = score; best = m; }
        }

        METHOD_CACHE.put(cacheKey, best != null ? new Method[]{best} : new Method[0]);
        return best;
    }

    private static void collectMethods(Class<?> cls, String name, int arity, List<Method> out) {
        Class<?> current = cls;
        while (current != null) {
            for (Method m : current.getDeclaredMethods()) {
                if (!m.getName().equals(name)) continue;
                if (arity >= 0 && m.getParameterCount() != arity && !m.isVarArgs()) continue;
                m.setAccessible(true);
                out.add(m);
            }
            // Also check interfaces
            for (Class<?> iface : current.getInterfaces()) {
                collectMethods(iface, name, arity, out);
            }
            current = current.getSuperclass();
        }
    }

    private static int scoreMethod(Method m, JSValue[] args) {
        Class<?>[] paramTypes = m.getParameterTypes();
        int score = 100;
        for (int i = 0; i < Math.min(paramTypes.length, args.length); i++) {
            score += scoreParamMatch(paramTypes[i], args[i]);
        }
        return score;
    }

    private static int scoreParamMatch(Class<?> paramType, JSValue arg) {
        if (paramType == String.class && arg.isString()) return 10;
        if ((paramType == int.class || paramType == Integer.class) && arg.isNumber()) return 10;
        if ((paramType == double.class || paramType == Double.class) && arg.isNumber()) return 10;
        if ((paramType == long.class || paramType == Long.class) && arg.isNumber()) return 9;
        if ((paramType == float.class || paramType == Float.class) && arg.isNumber()) return 9;
        if ((paramType == boolean.class || paramType == Boolean.class) && arg.isBoolean()) return 10;
        if (paramType == Object.class) return 1;
        if (!arg.isNullish() && arg.isJava() && paramType.isInstance(arg.javaRaw())) return 10;
        return 0;
    }

    // ── Argument type conversion ──────────────────────────────────────

    static Object[] convertArgs(Class<?>[] paramTypes, JSValue[] args) {
        Object[] result = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            JSValue arg = i < args.length ? args[i] : JSValue.UNDEFINED;
            result[i] = convertArg(paramTypes[i], arg);
        }
        return result;
    }

    static Object convertArg(Class<?> target, JSValue val) {
        if (val.isNullish()) return null;

        if (target == String.class)          return val.asString();
        if (target == int.class || target == Integer.class) return val.asInt();
        if (target == long.class || target == Long.class)   return val.asLong();
        if (target == double.class || target == Double.class) return val.asNumber();
        if (target == float.class || target == Float.class) return (float) val.asNumber();
        if (target == boolean.class || target == Boolean.class) return val.asBoolean();
        if (target == byte.class || target == Byte.class)   return (byte) val.asInt();
        if (target == short.class || target == Short.class) return (short) val.asInt();
        if (target == char.class || target == Character.class) {
            String s = val.asString();
            return s.isEmpty() ? '\0' : s.charAt(0);
        }
        if (target == Object.class) {
            if (val.isJava()) return val.javaRaw();
            if (val.isString()) return val.asString();
            if (val.isNumber()) return val.asNumber();
            if (val.isBoolean()) return val.asBoolean();
            return val;
        }
        // Try to unwrap java object
        if (val.isJava()) {
            Object raw = val.javaRaw();
            if (target.isInstance(raw)) return raw;
        }
        return val.javaRaw();
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ── Inner class: lazy method reference ───────────────────────────

    /**
     * Bir Java objesinin method'una gecikmeli referans.
     * JS'te obj.foo(...) yapıldığında önce getProp("foo") çağrılır.
     * Bu JSCallableMethod döner. Sonra Call node'u bu nesneyi çağırır.
     */
    public static class JSCallableMethod {
        public final Object target;
        public final String methodName;

        public JSCallableMethod(Object target, String methodName) {
            this.target = target;
            this.methodName = methodName;
        }
    }
}
