package dev.nanoscript;

import dev.nanoscript.command.NSCommand;
import dev.nanoscript.engine.ScriptManager;
import org.bukkit.plugin.java.JavaPlugin;

public class NanoScript extends JavaPlugin {

    private static NanoScript instance;
    private ScriptManager scriptManager;

    @Override
    public void onEnable() {
        instance = this;

        // Klasörleri oluştur
        getDataFolder().mkdirs();
        java.io.File scriptsDir = getScriptsFolder();
        if (!scriptsDir.exists()) scriptsDir.mkdirs();

        // Script yöneticisini başlat
        scriptManager = new ScriptManager(this);

        // Komutu kaydet
        NSCommand nsCommand = new NSCommand(this);
        getCommand("ns").setExecutor(nsCommand);
        getCommand("ns").setTabCompleter(nsCommand);

        // Örnek script oluştur (ilk kurulumda)
        createExampleScript();

        getLogger().info("§aNanoScript v1.0.0 aktif! §7Scripts klasörü: " + scriptsDir.getPath());
        getLogger().info("§7Kullanım: /ns load all  |  /ns load <dosya.js>  |  /ns list");
    }

    @Override
    public void onDisable() {
        if (scriptManager != null) {
            scriptManager.unloadAll();
        }
        getLogger().info("NanoScript kapatıldı. Tüm scriptler durduruldu.");
    }

    public static NanoScript getInstance() {
        return instance;
    }

    public ScriptManager getScriptManager() {
        return scriptManager;
    }

    public java.io.File getScriptsFolder() {
        return new java.io.File(getDataFolder(), "scripts");
    }

    private void createExampleScript() {
        java.io.File example = new java.io.File(getScriptsFolder(), "example.js");
        if (!example.exists()) {
            try (java.io.InputStream in = getResource("example.js")) {
                if (in != null) {
                    java.nio.file.Files.copy(in, example.toPath());
                }
            } catch (Exception ignored) {}
        }
    }
}
