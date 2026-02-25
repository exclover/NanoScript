package dev.nanoscript.jsengine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/**
 * NanoJS Motor — Dışarıya açılan tek giriş noktası.
 *
 * Kullanım:
 *   NanoEngine engine = new NanoEngine();
 *   engine.defineGlobal("NS", JSValue.of(myApiObject));
 *   engine.execute("var x = 1 + 2;");
 *   JSValue result = engine.getGlobal("x"); // JSValue[NUMBER: 3]
 */
public class NanoEngine {

    private final Interpreter interpreter;
    private final Environment globalEnv;

    public NanoEngine() {
        this.interpreter  = new Interpreter();
        this.globalEnv    = interpreter.getGlobalEnv();
    }

    // ──────────────────────────────────────────────────────────────────
    //  Execution
    // ──────────────────────────────────────────────────────────────────

    /**
     * Kaynak kodu ayrıştırır ve çalıştırır.
     * @return son ifadenin değeri
     * @throws JsError parse veya runtime hatalarında
     */
    public JSValue execute(String source) {
        try {
            List<Token> tokens = new Lexer(source).tokenize();
            Node.Program ast = new Parser(tokens).parse();
            return interpreter.execute(ast);
        } catch (JsError e) {
            throw e;
        } catch (ReturnSignal r) {
            return r.value;
        } catch (ThrowSignal t) {
            throw new JsError("Script throw: " + t.value.asString());
        } catch (Exception e) {
            throw new JsError("Çalışma hatası: " + e.getMessage());
        }
    }

    /**
     * Dosyadan JS yükler ve çalıştırır.
     * @return son ifadenin değeri
     * @throws JsError parse, IO veya runtime hatalarında
     */
    public JSValue executeFile(File file) {
        try {
            String source = Files.readString(file.toPath());
            return execute(source);
        } catch (IOException e) {
            throw new JsError("Dosya okunamadı: " + file.getName() + " — " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  Global variable access
    // ──────────────────────────────────────────────────────────────────

    /** Global bir değişkeni tanımlar veya günceller */
    public void defineGlobal(String name, JSValue value) {
        globalEnv.define(name, value);
    }

    /** Global bir değişkeni okur */
    public JSValue getGlobal(String name) {
        return globalEnv.get(name);
    }

    // ──────────────────────────────────────────────────────────────────
    //  Function call from Java
    // ──────────────────────────────────────────────────────────────────

    /**
     * JS fonksiyonunu Java tarafından çağırır.
     * Örnek: engine.call(listenerFn, new JSValue[]{ JSValue.wrap(event) })
     */
    public JSValue call(JSValue fn, JSValue[] args) {
        return interpreter.callFunction(fn, args);
    }

    public JSValue call(JSValue fn, JSValue thisVal, JSValue[] args) {
        return interpreter.callFunction(fn, thisVal, args);
    }

    // ──────────────────────────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────────────────────────

    public Interpreter getInterpreter() { return interpreter; }
    public Environment getGlobalEnvironment() { return globalEnv; }


}
