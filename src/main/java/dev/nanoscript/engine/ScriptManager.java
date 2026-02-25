package dev.nanoscript.engine;

import dev.nanoscript.NanoScript;
import org.bukkit.Bukkit;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ScriptManager — tüm .js dosyalarının yükleme/boşaltma merkezi.
 * Her script kendi izole ScriptInstance'ında çalışır.
 * Thread-safe ConcurrentHashMap kullanılır.
 */
public class ScriptManager {

    private final NanoScript plugin;

    // Aktif scriptler: dosya adı (örn. "test.js") -> ScriptInstance
    private final Map<String, ScriptInstance> loadedScripts = new ConcurrentHashMap<>();

    public ScriptManager(NanoScript plugin) {
        this.plugin = plugin;
    }

    // ─────────────────────────────────────────────
    //  LOAD
    // ─────────────────────────────────────────────

    /**
     * Tek bir .js dosyasını yükler.
     * @return Sonuç mesajı (renkli)
     */
    public String loadScript(String fileName) {
        // Uzantı yoksa ekle
        if (!fileName.endsWith(".js")) fileName += ".js";

        // Zaten yüklüyse önce unload
        if (loadedScripts.containsKey(fileName)) {
            unloadScript(fileName);
        }

        File file = new File(plugin.getScriptsFolder(), fileName);

        if (!file.exists()) {
            return "§c[NanoScript] §f" + fileName + " §cbulunamadı! (scripts/ klasörüne koy)";
        }

        if (!file.isFile() || !file.canRead()) {
            return "§c[NanoScript] §f" + fileName + " §cokunamıyor!";
        }

        try {
            ScriptInstance instance = new ScriptInstance(plugin, file);
            String error = instance.load();

            if (error != null) {
                return "§c[NanoScript] §f" + fileName + " §chata: §e" + error;
            }

            loadedScripts.put(fileName, instance);
            plugin.getLogger().info("[NanoScript] Yüklendi: " + fileName);
            return "§a[NanoScript] §f" + fileName + " §ayüklendi.";

        } catch (Exception e) {
            plugin.getLogger().severe("[NanoScript] " + fileName + " yüklenirken beklenmedik hata: " + e.getMessage());
            return "§c[NanoScript] §f" + fileName + " §cyüklenirken beklenmedik hata: §e" + e.getMessage();
        }
    }

    /**
     * scripts/ klasöründeki tüm .js dosyalarını yükler.
     * @return Yüklenen / hata veren sayılarını içeren özet mesaj
     */
    public String loadAll() {
        File folder = plugin.getScriptsFolder();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".js"));

        if (files == null || files.length == 0) {
            return "§e[NanoScript] scripts/ klasöründe .js dosyası bulunamadı.";
        }

        int success = 0, fail = 0;
        for (File file : files) {
            String result = loadScript(file.getName());
            if (result.startsWith("§a")) success++;
            else fail++;
        }

        String msg = "§a[NanoScript] §fload all tamamlandı: §a" + success + " başarılı";
        if (fail > 0) msg += "§f, §c" + fail + " hatalı";
        return msg;
    }

    // ─────────────────────────────────────────────
    //  UNLOAD
    // ─────────────────────────────────────────────

    /**
     * Tek bir scripti durdurur ve tüm kaynaklarını serbest bırakır.
     */
    public String unloadScript(String fileName) {
        if (!fileName.endsWith(".js")) fileName += ".js";

        ScriptInstance instance = loadedScripts.remove(fileName);
        if (instance == null) {
            return "§e[NanoScript] §f" + fileName + " §ezaten yüklü değil.";
        }

        instance.unload();
        plugin.getLogger().info("[NanoScript] Kaldırıldı: " + fileName);
        return "§6[NanoScript] §f" + fileName + " §6kaldırıldı.";
    }

    /**
     * Tüm aktif scriptleri durdurur.
     */
    public String unloadAll() {
        if (loadedScripts.isEmpty()) {
            return "§e[NanoScript] Kaldırılacak aktif script yok.";
        }

        int count = loadedScripts.size();
        // Kopyadan iterate et (ConcurrentModification önle)
        new ArrayList<>(loadedScripts.keySet()).forEach(this::unloadScript);
        return "§6[NanoScript] §f" + count + " §6script kaldırıldı.";
    }

    // ─────────────────────────────────────────────
    //  LIST
    // ─────────────────────────────────────────────

    /**
     * Yüklü scriptlerin listesini döner.
     */
    public String list() {
        if (loadedScripts.isEmpty()) {
            return "§e[NanoScript] Aktif script yok.";
        }

        StringBuilder sb = new StringBuilder("§6[NanoScript] §fAktif scriptler §7(" + loadedScripts.size() + ")§f:\n");
        loadedScripts.forEach((name, instance) -> {
            sb.append("  §a● §f").append(name)
              .append(" §7| Eventler: §b").append(instance.getEventCount())
              .append(" §7| Komutlar: §d").append(instance.getCommandCount())
              .append(" §7| Task'lar: §e").append(instance.getTaskCount())
              .append("\n");
        });
        return sb.toString().trim();
    }

    // ─────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────

    public boolean isLoaded(String fileName) {
        if (!fileName.endsWith(".js")) fileName += ".js";
        return loadedScripts.containsKey(fileName);
    }

    public Set<String> getLoadedScriptNames() {
        return Collections.unmodifiableSet(loadedScripts.keySet());
    }

    /** scripts/ klasöründeki tüm .js dosya adlarını döner (yüklü olsun olmasın) */
    public List<String> getAvailableScripts() {
        File folder = plugin.getScriptsFolder();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".js"));
        if (files == null) return Collections.emptyList();
        List<String> names = new ArrayList<>();
        for (File f : files) names.add(f.getName());
        return names;
    }
}
