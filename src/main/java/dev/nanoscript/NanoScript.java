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


        // Script yöneticisini başlat
        scriptManager = new ScriptManager(this);

        // Komutu kaydet
        NSCommand nsCommand = new NSCommand(this);
        getCommand("ns").setExecutor(nsCommand);
        getCommand("ns").setTabCompleter(nsCommand);

        // Örnek script oluştur (ilk kurulumda)
        createExampleScripts();
        getLogger().info("§aNanoScript v1.0.0 aktif! §7Scripts klasörü: " + getScriptsFolder().getPath());
        getLogger().info("§7Kullanım: /ns load all  |  /ns load <dosya.js>  |  /ns list");
        scriptManager.loadAll();
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

    private void createExampleScripts() {
        java.io.File scriptsFolder = getScriptsFolder();

        // Eğer klasör yoksa önce klasörü oluştur
        if (!scriptsFolder.exists()) {
            scriptsFolder.mkdirs();

            // Kopyalanmasını istediğin dosyaların isimlerini buraya ekle
            String[] exampleFiles = {"example.js", "economy.js"};

            // Listedeki her bir dosya için kopyalama işlemini yap
            for (String fileName : exampleFiles) {
                java.io.File targetFile = new java.io.File(scriptsFolder, fileName);

                try (java.io.InputStream in = getResource(fileName)) {
                    if (in != null) {
                        java.nio.file.Files.copy(in, targetFile.toPath());
                    }
                } catch (java.io.IOException ignored) {
                    // Spesifik giriş/çıkış hatalarını yakalar
                }
            }
        }
    }

}
