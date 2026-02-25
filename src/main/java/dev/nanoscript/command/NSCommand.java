package dev.nanoscript.command;

import dev.nanoscript.NanoScript;
import dev.nanoscript.engine.ScriptManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /ns komutu
 *
 * /ns load all           → scripts/ klasöründeki tüm .js yükle
 * /ns load <dosya.js>    → tek script yükle
 * /ns unload all         → tüm scriptleri kaldır
 * /ns unload <dosya.js>  → tek scripti kaldır
 * /ns list               → aktif scriptleri listele
 * /ns reload all         → unload + load all
 * /ns reload <dosya.js>  → tek scripti yeniden yükle
 */
public class NSCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = "§8[§bNano§3Script§8] §r";
    private static final String NO_PERM = PREFIX + "§cBu komutu kullanmak için iznin yok.";
    private static final String USAGE   = PREFIX + "§eKullanım:\n" +
        "  §f/ns load all §7| /ns load <dosya.js>\n" +
        "  §f/ns unload all §7| /ns unload <dosya.js>\n" +
        "  §f/ns reload all §7| /ns reload <dosya.js>\n" +
        "  §f/ns list";

    private final NanoScript plugin;
    private final ScriptManager sm;

    public NSCommand(NanoScript plugin) {
        this.plugin = plugin;
        this.sm = plugin.getScriptManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("nanoscript.admin")) {
            sender.sendMessage(NO_PERM);
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(USAGE);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "load" -> {
                if (args.length < 2) {
                    sender.sendMessage(PREFIX + "§cKullanım: /ns load <dosya.js|all>");
                    return true;
                }
                if (args[1].equalsIgnoreCase("all")) {
                    sender.sendMessage(PREFIX + "§7Tüm scriptler yükleniyor...");
                    sender.sendMessage(sm.loadAll());
                } else {
                    sender.sendMessage(sm.loadScript(args[1]));
                }
            }
            case "unload" -> {
                if (args.length < 2) {
                    sender.sendMessage(PREFIX + "§cKullanım: /ns unload <dosya.js|all>");
                    return true;
                }
                if (args[1].equalsIgnoreCase("all")) {
                    sender.sendMessage(sm.unloadAll());
                } else {
                    sender.sendMessage(sm.unloadScript(args[1]));
                }
            }
            case "reload" -> {
                if (args.length < 2) {
                    sender.sendMessage(PREFIX + "§cKullanım: /ns reload <dosya.js|all>");
                    return true;
                }
                if (args[1].equalsIgnoreCase("all")) {
                    sender.sendMessage(PREFIX + "§7Tüm scriptler yeniden yükleniyor...");
                    sm.unloadAll();
                    sender.sendMessage(sm.loadAll());
                } else {
                    sm.unloadScript(args[1]);
                    sender.sendMessage(sm.loadScript(args[1]));
                }
            }
            case "list" -> sender.sendMessage(sm.list());
            default -> sender.sendMessage(USAGE);
        }

        return true;
    }

    // ── Tab Tamamlama ────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("nanoscript.admin")) return List.of();

        if (args.length == 1) {
            return filterStart(args[0], Arrays.asList("load", "unload", "reload", "list"));
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("load") || sub.equals("reload")) {
                // scripts/ klasöründeki .js dosyaları + "all"
                List<String> opts = new ArrayList<>(sm.getAvailableScripts());
                opts.add(0, "all");
                return filterStart(args[1], opts);
            }
            if (sub.equals("unload")) {
                // Sadece yüklü olanlar + "all"
                List<String> opts = new ArrayList<>(sm.getLoadedScriptNames());
                opts.add(0, "all");
                return filterStart(args[1], opts);
            }
        }

        return List.of();
    }

    private List<String> filterStart(String input, List<String> options) {
        return options.stream()
            .filter(o -> o.toLowerCase().startsWith(input.toLowerCase()))
            .collect(Collectors.toList());
    }
}
