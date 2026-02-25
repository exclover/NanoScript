package dev.nanoscript.engine;

import dev.nanoscript.NanoScript;
import dev.nanoscript.api.ScriptAPI;
import dev.nanoscript.api.StorageManager;
import dev.nanoscript.jsengine.*;
import dev.nanoscript.listener.DynamicCommand;
import dev.nanoscript.listener.DynamicListener;
import org.bukkit.event.HandlerList;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Tek bir .js dosyasının tam izolasyonu.
 *
 * Artık Rhino yerine NanoJS Engine kullanılıyor:
 *  - Sıfırdan yazılmış Lexer + Parser + Interpreter
 *  - Sıfır dış bağımlılık (Maven'da sadece Paper API var)
 *  - Her script kendi NanoEngine instance'ına sahip (tam izolasyon)
 *  - unload() anında tüm kaynaklar temizlenir
 */
public class ScriptInstance {

    private final NanoScript plugin;
    private final File file;

    // JS motoru
    private NanoEngine engine;
    private ScriptAPI api;
    private StorageManager storage;



    // Kayıtlı kaynaklar
    private final List<DynamicListener> eventListeners = new ArrayList<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private final List<String> registeredCommands = new ArrayList<>();

    public ScriptInstance(NanoScript plugin, File file) {
        this.plugin = plugin;
        this.file = file;
        this.storage = new StorageManager(plugin);
    }

    // ──────────────────────────────────────────────────────────────────
    //  Load
    // ──────────────────────────────────────────────────────────────────

    /**
     * Dosyayı yükler ve çalıştırır.
     * @return null = başarı, String = hata mesajı
     */
    public String load() {
        try {
            // Yeni izole motor oluştur
            engine = new NanoEngine();

            // ScriptAPI'yi motora bağla
            api = new ScriptAPI(plugin, this, engine, storage);
            api.installGlobals();

            // Dosyayı çalıştır
            engine.executeFile(file);

            return null; // başarı

        } catch (JsError e) {
            cleanup();
            return e.getMessage();
        } catch (Exception e) {
            cleanup();
            return "Beklenmedik hata: " + e.getMessage();
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  Unload
    // ──────────────────────────────────────────────────────────────────

    /**
     * Scripti tamamen kaldırır:
     * event listener'lar, task'lar, komutlar temizlenir.
     */
    public void unload() {
        // 1. Event listener'ları kaldır
        for (DynamicListener dl : eventListeners) {
            try { HandlerList.unregisterAll(dl); } catch (Exception ignored) {}
        }
        eventListeners.clear();

        // 2. Scheduler task'larını iptal et
        for (BukkitTask task : tasks) {
            try { task.cancel(); } catch (Exception ignored) {}
        }
        tasks.clear();

        // 3. Dinamik komutları kaldır
        for (String cmd : registeredCommands) {
            CommandMapUtil.unregister(plugin, cmd);
        }
        registeredCommands.clear();

        // 4. Motoru temizle (GC'ye bırak)
        cleanup();
    }

    private void cleanup() {
        engine = null;
        api = null;
    }

    // ──────────────────────────────────────────────────────────────────
    //  Resource registration (ScriptAPI tarafından çağrılır)
    // ──────────────────────────────────────────────────────────────────

    public void addEventListenerRef(DynamicListener dl)  { eventListeners.add(dl); }
    public void addTaskRef(BukkitTask task)               { tasks.add(task); }
    public void addCommandRef(String name)                { registeredCommands.add(name); }
    public void removeTaskRef(BukkitTask task)            { tasks.remove(task); }

    // ──────────────────────────────────────────────────────────────────
    //  Status
    // ──────────────────────────────────────────────────────────────────

    public int getEventCount()   { return eventListeners.size(); }
    public int getTaskCount()    { return tasks.size(); }
    public int getCommandCount() { return registeredCommands.size(); }
    public String getFileName()  { return file.getName(); }
    public NanoEngine getEngine(){ return engine; }
}
