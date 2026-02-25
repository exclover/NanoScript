package dev.nanoscript.engine;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Bukkit'in CommandMap'ine reflection ile erişir.
 * Dinamik komut kaydı/silme için kullanılır.
 *
 * Paper 1.20+ ile "knownCommands" field adı değişti;
 * findField() hem eski hem yeni adı dener.
 */
public class CommandMapUtil {

    private static CommandMap commandMap;
    private static Map<String, Command> knownCommands;

    static {
        try {
            Field f = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            f.setAccessible(true);
            commandMap = (CommandMap) f.get(Bukkit.getServer());

            // Paper 1.20+ field adını değiştirdi; olası tüm isimleri dene
            Field k = findField(commandMap.getClass(), "knownCommands", "commands");
            if (k != null) {
                k.setAccessible(true);
                //noinspection unchecked
                knownCommands = (Map<String, Command>) k.get(commandMap);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Sınıf hiyerarşisinde (superclass dahil) verilen adlardan birini taşıyan Field'ı bulur.
     */
    private static Field findField(Class<?> cls, String... names) {
        Class<?> current = cls;
        while (current != null) {
            for (String name : names) {
                try {
                    return current.getDeclaredField(name);
                } catch (NoSuchFieldException ignored) {}
            }
            current = current.getSuperclass();
        }
        return null;
    }

    public static CommandMap getCommandMap() {
        return commandMap;
    }

    /**
     * Komutu CommandMap'e kaydet.
     */
    public static void register(Plugin plugin, Command command) {
        if (commandMap != null) {
            commandMap.register(plugin.getName().toLowerCase(), command);
        }
    }

    /**
     * Komutu CommandMap'ten sil.
     */
    public static void unregister(Plugin plugin, String name) {
        if (knownCommands == null) return;
        String lower    = name.toLowerCase();
        String prefixed = plugin.getName().toLowerCase() + ":" + lower;
        knownCommands.remove(lower);
        knownCommands.remove(prefixed);
    }
}