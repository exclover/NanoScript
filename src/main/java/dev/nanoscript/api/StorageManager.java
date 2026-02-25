package dev.nanoscript.api;

import com.google.gson.*;
import dev.nanoscript.NanoScript;
import dev.nanoscript.jsengine.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * NanoScript Kalıcı Depolama Sistemi
 *
 * Her script için ayrı bir JSON dosyası tutar:
 *   plugins/NanoScript/data/<scriptAdı>.json
 *
 * Reload, sunucu yeniden başlatma, plugin unload → veriler korunur.
 *
 * JS kullanımı:
 *   const db = server.getStorage();          // script'e özel storage
 *
 *   db.set("bakiye.Steve", 1500);            // noktalı yol = nested key
 *   db.get("bakiye.Steve");                  // → 1500
 *   db.get("bakiye.Steve", 0);               // → default değer
 *   db.has("bakiye.Steve");                  // → true/false
 *   db.delete("bakiye.Steve");
 *
 *   db.setObj("oyuncu.Steve", { k: 1 });     // JS objesi kaydet
 *   db.getObj("oyuncu.Steve");               // JS objesi olarak al
 *
 *   db.keys("bakiye");                       // → ["Steve","Alex",...]
 *   db.increment("bakiye.Steve", 100);       // +100 ekle
 *   db.decrement("bakiye.Steve", 50);        // -50 çıkar
 *
 *   db.save();                               // zorla diske yaz
 *   db.clear();                              // tüm veriyi sil
 */
public class StorageManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final NanoScript plugin;
    private final Logger logger;
    private final File dataDir;

    // script adı → bellekteki JsonObject
    private final Map<String, JsonObject> cache = new ConcurrentHashMap<>();
    // script adı → disk dosyası
    private final Map<String, File> files = new ConcurrentHashMap<>();
    // Dirty flag: değiştirilmiş ama henüz diske yazılmamış
    private final Set<String> dirty = Collections.synchronizedSet(new HashSet<>());

    public StorageManager(NanoScript plugin) {
        this.plugin  = plugin;
        this.logger  = plugin.getLogger();
        this.dataDir = new File(plugin.getDataFolder(), "data");
        if (!dataDir.exists()) dataDir.mkdirs();
    }

    // ──────────────────────────────────────────────────────────────────
    //  Script'e özel storage nesnesi — JS'e verilecek JSObject
    // ──────────────────────────────────────────────────────────────────

    /**
     * Belirli bir script için JS storage objesini oluşturur.
     * Bu obje script'e script-local veri erişimi sağlar.
     */
    public JSObject buildStorageObject(String scriptName) {
        // Dosyayı yükle (cache'e al)
        loadIfNeeded(scriptName);

        JSObject db = new JSObject();

        // ── db.set(key, value) ────────────────────────────────────────
        db.set("set", fn("set", (args, env) -> {
            requireArgs(args, 2, "db.set(key, value)");
            String key = args[0].asString();
            setPath(scriptName, key, jsToJson(args[1]));
            scheduleSave(scriptName);
            return JSValue.UNDEFINED;
        }));

        // ── db.get(key, default?) ─────────────────────────────────────
        db.set("get", fn("get", (args, env) -> {
            requireArgs(args, 1, "db.get(key)");
            String key = args[0].asString();
            JsonElement el = getPath(scriptName, key);
            if (el == null || el.isJsonNull())
                return args.length > 1 ? args[1] : JSValue.UNDEFINED;
            return jsonToJs(el);
        }));

        // ── db.has(key) ───────────────────────────────────────────────
        db.set("has", fn("has", (args, env) -> {
            requireArgs(args, 1, "db.has(key)");
            JsonElement el = getPath(scriptName, args[0].asString());
            return JSValue.of(el != null && !el.isJsonNull());
        }));

        // ── db.delete(key) ────────────────────────────────────────────
        db.set("delete", fn("delete", (args, env) -> {
            requireArgs(args, 1, "db.delete(key)");
            deletePath(scriptName, args[0].asString());
            scheduleSave(scriptName);
            return JSValue.UNDEFINED;
        }));

        // ── db.setObj(key, jsObject) ──────────────────────────────────
        db.set("setObj", fn("setObj", (args, env) -> {
            requireArgs(args, 2, "db.setObj(key, object)");
            setPath(scriptName, args[0].asString(), jsToJson(args[1]));
            scheduleSave(scriptName);
            return JSValue.UNDEFINED;
        }));

        // ── db.getObj(key) ────────────────────────────────────────────
        db.set("getObj", fn("getObj", (args, env) -> {
            requireArgs(args, 1, "db.getObj(key)");
            JsonElement el = getPath(scriptName, args[0].asString());
            if (el == null || !el.isJsonObject()) return JSValue.NULL;
            return jsonToJs(el);
        }));

        // ── db.keys(prefix?) ──────────────────────────────────────────
        // Belirli bir nesnenin tüm anahtarlarını döner
        db.set("keys", fn("keys", (args, env) -> {
            String prefix = args.length > 0 ? args[0].asString() : null;
            JsonObject root = cache.get(scriptName);
            JsonElement target = prefix != null ? getPath(scriptName, prefix) : root;
            if (target == null || !target.isJsonObject()) return JSValue.of(new JSArray());
            JSArray arr = new JSArray();
            for (String k : target.getAsJsonObject().keySet()) arr.push(JSValue.of(k));
            return JSValue.of(arr);
        }));

        // ── db.increment(key, amount?) ────────────────────────────────
        db.set("increment", fn("increment", (args, env) -> {
            requireArgs(args, 1, "db.increment(key, amount?)");
            String key = args[0].asString();
            double amount = args.length > 1 ? args[1].asNumber() : 1.0;
            JsonElement cur = getPath(scriptName, key);
            double current = (cur != null && cur.isJsonPrimitive()) ? cur.getAsDouble() : 0.0;
            double newVal = current + amount;
            setPath(scriptName, key, new JsonPrimitive(newVal));
            scheduleSave(scriptName);
            return JSValue.of(newVal);
        }));

        // ── db.decrement(key, amount?) ────────────────────────────────
        db.set("decrement", fn("decrement", (args, env) -> {
            requireArgs(args, 1, "db.decrement(key, amount?)");
            String key = args[0].asString();
            double amount = args.length > 1 ? args[1].asNumber() : 1.0;
            JsonElement cur = getPath(scriptName, key);
            double current = (cur != null && cur.isJsonPrimitive()) ? cur.getAsDouble() : 0.0;
            double newVal = current - amount;
            setPath(scriptName, key, new JsonPrimitive(newVal));
            scheduleSave(scriptName);
            return JSValue.of(newVal);
        }));

        // ── db.getOrSet(key, default) ─────────────────────────────────
        // Varsa al, yoksa default'u kaydet ve döndür
        db.set("getOrSet", fn("getOrSet", (args, env) -> {
            requireArgs(args, 2, "db.getOrSet(key, default)");
            String key = args[0].asString();
            JsonElement el = getPath(scriptName, key);
            if (el != null && !el.isJsonNull()) return jsonToJs(el);
            setPath(scriptName, key, jsToJson(args[1]));
            scheduleSave(scriptName);
            return args[1];
        }));

        // ── db.push(key, value) ───────────────────────────────────────
        // Bir array anahtarına eleman ekle
        db.set("push", fn("push", (args, env) -> {
            requireArgs(args, 2, "db.push(key, value)");
            String key = args[0].asString();
            JsonElement el = getPath(scriptName, key);
            JsonArray arr;
            if (el != null && el.isJsonArray()) arr = el.getAsJsonArray();
            else arr = new JsonArray();
            arr.add(jsToJson(args[1]));
            setPath(scriptName, key, arr);
            scheduleSave(scriptName);
            return JSValue.of(arr.size());
        }));

        // ── db.getArray(key) ──────────────────────────────────────────
        db.set("getArray", fn("getArray", (args, env) -> {
            requireArgs(args, 1, "db.getArray(key)");
            JsonElement el = getPath(scriptName, args[0].asString());
            if (el == null || !el.isJsonArray()) return JSValue.of(new JSArray());
            JSArray result = new JSArray();
            for (JsonElement item : el.getAsJsonArray()) result.push(jsonToJs(item));
            return JSValue.of(result);
        }));

        // ── db.save() ─────────────────────────────────────────────────
        db.set("save", fn("save", (args, env) -> {
            flushToDisk(scriptName);
            return JSValue.UNDEFINED;
        }));

        // ── db.clear() ────────────────────────────────────────────────
        db.set("clear", fn("clear", (args, env) -> {
            cache.put(scriptName, new JsonObject());
            scheduleSave(scriptName);
            return JSValue.UNDEFINED;
        }));

        // ── db.debug() ────────────────────────────────────────────────
        db.set("debug", fn("debug", (args, env) -> {
            JsonObject root = cache.getOrDefault(scriptName, new JsonObject());
            return JSValue.of(GSON.toJson(root));
        }));

        return db;
    }

    // ──────────────────────────────────────────────────────────────────
    //  Tüm scriptleri diske yaz (sunucu kapanırken çağrılır)
    // ──────────────────────────────────────────────────────────────────

    public void saveAll() {
        for (String name : new HashSet<>(dirty)) {
            flushToDisk(name);
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  İç implementasyon
    // ──────────────────────────────────────────────────────────────────

    private void loadIfNeeded(String scriptName) {
        if (cache.containsKey(scriptName)) return;

        File f = new File(dataDir, scriptName + ".json");
        files.put(scriptName, f);

        if (f.exists()) {
            try (Reader r = new FileReader(f)) {
                JsonElement el = JsonParser.parseReader(r);
                cache.put(scriptName, el.isJsonObject() ? el.getAsJsonObject() : new JsonObject());
                logger.fine("[Storage] " + scriptName + ".json yüklendi.");
            } catch (Exception e) {
                logger.warning("[Storage] " + scriptName + ".json okunamadı: " + e.getMessage());
                cache.put(scriptName, new JsonObject());
            }
        } else {
            cache.put(scriptName, new JsonObject());
        }
    }

    /** Noktalı yol ile iç içe key erişimi: "bakiye.Steve" → root["bakiye"]["Steve"] */
    private JsonElement getPath(String scriptName, String path) {
        JsonObject root = cache.getOrDefault(scriptName, new JsonObject());
        if (!path.contains(".")) return root.get(path);

        String[] parts = path.split("\\.", -1);
        JsonElement cur = root;
        for (int i = 0; i < parts.length - 1; i++) {
            if (!cur.isJsonObject()) return null;
            cur = cur.getAsJsonObject().get(parts[i]);
            if (cur == null) return null;
        }
        return cur.isJsonObject() ? cur.getAsJsonObject().get(parts[parts.length - 1]) : null;
    }

    /** Noktalı yol ile iç içe key set etme */
    private void setPath(String scriptName, String path, JsonElement value) {
        JsonObject root = cache.computeIfAbsent(scriptName, k -> new JsonObject());
        if (!path.contains(".")) {
            root.add(path, value);
            return;
        }

        String[] parts = path.split("\\.", -1);
        JsonObject cur = root;
        for (int i = 0; i < parts.length - 1; i++) {
            JsonElement next = cur.get(parts[i]);
            if (next == null || !next.isJsonObject()) {
                JsonObject newObj = new JsonObject();
                cur.add(parts[i], newObj);
                cur = newObj;
            } else {
                cur = next.getAsJsonObject();
            }
        }
        cur.add(parts[parts.length - 1], value);
    }

    private void deletePath(String scriptName, String path) {
        JsonObject root = cache.getOrDefault(scriptName, new JsonObject());
        if (!path.contains(".")) { root.remove(path); return; }

        String[] parts = path.split("\\.", -1);
        JsonObject cur = root;
        for (int i = 0; i < parts.length - 1; i++) {
            JsonElement next = cur.get(parts[i]);
            if (next == null || !next.isJsonObject()) return;
            cur = next.getAsJsonObject();
        }
        cur.remove(parts[parts.length - 1]);
    }

    private void scheduleSave(String scriptName) {
        dirty.add(scriptName);
        // 20 tick (1 saniye) sonra async kaydet — sık değişimlerde thrash önler
        org.bukkit.Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            if (dirty.contains(scriptName)) flushToDisk(scriptName);
        }, 20L);
    }

    private synchronized void flushToDisk(String scriptName) {
        dirty.remove(scriptName);
        File f = files.computeIfAbsent(scriptName, k -> new File(dataDir, k + ".json"));
        JsonObject data = cache.getOrDefault(scriptName, new JsonObject());
        try (Writer w = new FileWriter(f)) {
            GSON.toJson(data, w);
        } catch (Exception e) {
            logger.warning("[Storage] Kayıt hatası (" + scriptName + "): " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  JSValue ↔ JsonElement dönüşümleri
    // ──────────────────────────────────────────────────────────────────

    private JsonElement jsToJson(JSValue val) {
        if (val.isNullish())  return JsonNull.INSTANCE;
        if (val.isBoolean())  return new JsonPrimitive(val.asBoolean());
        if (val.isNumber())   return new JsonPrimitive(val.asNumber());
        if (val.isString())   return new JsonPrimitive(val.asString());
        if (val.isArray()) {
            JsonArray arr = new JsonArray();
            for (JSValue el : val.asArray().elements()) arr.add(jsToJson(el));
            return arr;
        }
        if (val.isObject()) {
            JsonObject obj = new JsonObject();
            JSObject jsObj = val.asObject();
            for (Map.Entry<String, JSValue> e : jsObj.ownProps().entrySet()) {
                obj.add(e.getKey(), jsToJson(e.getValue()));
            }
            return obj;
        }
        // Java nesneleri → toString
        return new JsonPrimitive(val.asString());
    }

    private JSValue jsonToJs(JsonElement el) {
        if (el == null || el.isJsonNull()) return JSValue.NULL;
        if (el.isJsonPrimitive()) {
            JsonPrimitive p = el.getAsJsonPrimitive();
            if (p.isBoolean()) return JSValue.of(p.getAsBoolean());
            if (p.isNumber())  return JSValue.of(p.getAsDouble());
            return JSValue.of(p.getAsString());
        }
        if (el.isJsonArray()) {
            JSArray arr = new JSArray();
            for (JsonElement item : el.getAsJsonArray()) arr.push(jsonToJs(item));
            return JSValue.of(arr);
        }
        if (el.isJsonObject()) {
            JSObject obj = new JSObject();
            for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
                obj.set(e.getKey(), jsonToJs(e.getValue()));
            }
            return JSValue.of(obj);
        }
        return JSValue.UNDEFINED;
    }

    // ──────────────────────────────────────────────────────────────────
    //  Küçük yardımcılar
    // ──────────────────────────────────────────────────────────────────

    private JSFunction fn(String name, JSFunction.NativeFn impl) {
        return JSFunction.native1(name, impl);
    }

    private void requireArgs(JSValue[] args, int min, String usage) {
        if (args.length < min) throw new JsError(usage + " şeklinde kullanın");
    }
}
