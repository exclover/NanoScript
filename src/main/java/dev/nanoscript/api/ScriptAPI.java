package dev.nanoscript.api;

import dev.nanoscript.NanoScript;
import dev.nanoscript.engine.CommandMapUtil;
import dev.nanoscript.engine.ScriptInstance;
import dev.nanoscript.jsengine.*;
import dev.nanoscript.listener.DynamicCommand;
import dev.nanoscript.listener.DynamicListener;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.util.*;
import java.util.logging.Logger;

/**
 * NanoScript Minecraft API — Genişletilmiş sürüm
 *
 * ┌──────────────────────────────────────────────────────────────┐
 * │  YENİ: Kalıcı depolama (reload/restart'a dayanıklı)         │
 * │                                                              │
 * │  const db = server.getStorage();                            │
 * │  db.set("bakiye.Steve", 1500);   // noktalı yol desteği     │
 * │  db.get("bakiye.Steve", 0);      // varsayılan değer        │
 * │  db.increment("bakiye.Steve", 100);                         │
 * │  db.has("bakiye.Steve");                                     │
 * │  db.delete("bakiye.Steve");                                  │
 * │  db.keys("bakiye");              // → ["Steve", "Alex"]     │
 * │  db.push("log.entries", item);   // array'e ekle            │
 * │  db.getArray("log.entries");                                 │
 * │  db.save();  // zorla diske yaz                              │
 * │                                                              │
 * │  YENİ: Genişletilmiş Bukkit API                             │
 * │                                                              │
 * │  // Oyuncu                                                   │
 * │  server.getPlayer("Steve")                                   │
 * │  server.getOnlinePlayers()                                   │
 * │  server.heal(player)                                         │
 * │  server.feed(player)                                         │
 * │  server.setHealth(player, 10)                                │
 * │  server.setFood(player, 15)                                  │
 * │  server.setMaxHealth(player, 40)                             │
 * │  server.setLevel(player, 5)                                  │
 * │  server.setExp(player, 0.5)                                  │
 * │  server.setGameMode(player, "CREATIVE")                      │
 * │  server.kick(player, "Sebep")                                │
 * │  server.sendTitle(player, "Başlık", "Alt başlık")           │
 * │  server.sendActionBar(player, "Mesaj")                       │
 * │  server.sendTabList(player, "Üst", "Alt")                   │
 * │  server.playSound(player, "ENTITY_PLAYER_LEVELUP", 1, 1)    │
 * │  server.spawnParticle(loc, "HEART", 10)                      │
 * │  server.giveItem(player, item)                               │
 * │  server.clearInventory(player)                               │
 * │  server.addEffect(player, "SPEED", 200, 1)                  │
 * │  server.removeEffect(player, "SPEED")                        │
 * │  server.clearEffects(player)                                 │
 * │  server.isOp(player)                                         │
 * │  server.setOp(player, true)                                  │
 * │  server.hasPermission(player, "perm")                        │
 * │  server.getGameMode(player)                                  │
 * │  server.getHealth(player)                                    │
 * │  server.getFood(player)                                      │
 * │  server.getLevel(player)                                     │
 * │  server.getLocation(player)                                  │
 * │  server.getIPAddress(player)                                 │
 * │  server.getPing(player)                                      │
 * │  server.getUUID(player)                                      │
 * │  server.ban(player, "Sebep")                                 │
 * │  server.unban(name)                                          │
 * │  server.isBanned(name)                                       │
 * │                                                              │
 * │  // Item                                                     │
 * │  server.createItem(material, amount?)                        │
 * │  server.createNamedItem(mat, name, lore...)                  │
 * │  server.getItemInHand(player)                                │
 * │  server.getMaterial(block)                                   │
 * │                                                              │
 * │  // Dünya                                                    │
 * │  server.getBlock(world, x, y, z)                             │
 * │  server.setBlock(world, x, y, z, material)                  │
 * │  server.getHighestY(world, x, z)                             │
 * │  server.setTime(world, ticks)                                │
 * │  server.setWeather(world, "CLEAR"|"RAIN"|"THUNDER")          │
 * │  server.spawnEntity(loc, "ZOMBIE")                           │
 * │  server.createExplosion(loc, power, fire?)                   │
 * │  server.strikeLightning(loc, effect?)                        │
 * │  server.getSpawnLocation(world)                              │
 * │  server.setSpawnLocation(world, x, y, z)                    │
 * │  server.createLocation(world, x, y, z, yaw?, pitch?)        │
 * │                                                              │
 * │  // Scoreboard                                               │
 * │  server.getScoreboard()                                      │
 * │  server.createSidebar(title)                                 │
 * │  sidebar.setLine(slot, text)                                 │
 * │  sidebar.show(player)                                        │
 * │  sidebar.hide(player)                                        │
 * │                                                              │
 * │  // Sunucu                                                   │
 * │  server.getTPS()                                             │
 * │  server.getMaxPlayers()                                      │
 * │  server.getMotd()                                            │
 * │  server.isWhitelisted(name)                                  │
 * │  server.addWhitelist(name)                                   │
 * │  server.removeWhitelist(name)                                │
 * └──────────────────────────────────────────────────────────────┘
 */
public class ScriptAPI {

    private static final String[] EVENT_PACKAGES = {
            "org.bukkit.event.player.",
            "org.bukkit.event.block.",
            "org.bukkit.event.entity.",
            "org.bukkit.event.inventory.",
            "org.bukkit.event.server.",
            "org.bukkit.event.world.",
            "org.bukkit.event.weather.",
            "org.bukkit.event.vehicle.",
            "org.bukkit.event.hanging.",
            "io.papermc.paper.event.player.",
            "io.papermc.paper.event.entity.",
    };

    private final NanoScript plugin;
    private final ScriptInstance instance;
    private final NanoEngine engine;
    private final Logger logger;
    private final StorageManager storage;

    public ScriptAPI(NanoScript plugin, ScriptInstance instance, NanoEngine engine, StorageManager storage) {
        this.plugin   = plugin;
        this.instance = instance;
        this.engine   = engine;
        this.logger   = plugin.getLogger();
        this.storage  = storage;
    }

    // Eski constructor ile geriye dönük uyumluluk
    public ScriptAPI(NanoScript plugin, ScriptInstance instance, NanoEngine engine) {
        this(plugin, instance, engine, null);
    }

    // ──────────────────────────────────────────────────────────────────
    //  Ana kurulum
    // ──────────────────────────────────────────────────────────────────

    public void installGlobals() {
        JSObject serverObj = buildServerObject();
        engine.defineGlobal("getServer", fn("getServer", (args, env) -> JSValue.of(serverObj)));

        // color() — sık kullanılan global
        engine.defineGlobal("color", fn("color", (args, env) ->
                JSValue.of(args.length > 0 ? colorize(args[0].asString()) : "")
        ));
    }

    // ──────────────────────────────────────────────────────────────────
    //  server objesi
    // ──────────────────────────────────────────────────────────────────

    private JSObject buildServerObject() {
        JSObject server = new JSObject();

        JSObject eventManager   = buildEventManager();
        JSObject commandManager = buildCommandManager();

        server.set("getEventManager",   fn("getEventManager",   (a, e) -> JSValue.of(eventManager)));
        server.set("getCommandManager", fn("getCommandManager", (a, e) -> JSValue.of(commandManager)));

        // ── Depolama ──────────────────────────────────────────────────
        // server.getStorage() → script'e özel kalıcı depolama
        if (storage != null) {
            JSObject storageObj = storage.buildStorageObject(instance.getFileName().replace(".js", ""));
            server.set("getStorage", fn("getStorage", (a, e) -> JSValue.of(storageObj)));
        }

        // ── Mesajlaşma ────────────────────────────────────────────────

        server.set("broadcast", fn("broadcast", (args, env) -> {
            Bukkit.broadcastMessage(colorize(arg(args, 0, "")));
            return JSValue.UNDEFINED;
        }));

        server.set("log", fn("log", (args, env) -> {
            logger.info("[Script/" + instance.getFileName() + "] " + arg(args, 0, ""));
            return JSValue.UNDEFINED;
        }));

        server.set("color", fn("color", (args, env) ->
                JSValue.of(args.length > 0 ? colorize(args[0].asString()) : "")
        ));

        server.set("now", fn("now", (args, env) ->
                JSValue.of((double) System.currentTimeMillis())
        ));

        // ── Oyuncu erişimi ────────────────────────────────────────────

        server.set("getPlayer", fn("getPlayer", (args, env) -> {
            if (args.length == 0) return JSValue.NULL;
            Player p = Bukkit.getPlayer(args[0].asString());
            return p != null ? JSValue.wrap(p) : JSValue.NULL;
        }));

        server.set("getOnlinePlayers", fn("getOnlinePlayers", (args, env) -> {
            JSArray arr = new JSArray();
            Bukkit.getOnlinePlayers().forEach(p -> arr.push(JSValue.wrap(p)));
            return JSValue.of(arr);
        }));

        server.set("onlineCount", fn("onlineCount", (args, env) ->
                JSValue.of(Bukkit.getOnlinePlayers().size())
        ));

        // ── Oyuncu sağlık/yemek/can ───────────────────────────────────

        server.set("heal", fn("heal", (args, env) -> {
            Player p = getPlayer(args, 0); if (p == null) return JSValue.FALSE;
            double max = p.getAttribute(Attribute.MAX_HEALTH).getValue();
            p.setHealth(max);
            return JSValue.TRUE;
        }));

        server.set("feed", fn("feed", (args, env) -> {
            Player p = getPlayer(args, 0); if (p == null) return JSValue.FALSE;
            p.setFoodLevel(20);
            p.setSaturation(20f);
            return JSValue.TRUE;
        }));

        server.set("setHealth", fn("setHealth", (args, env) -> {
            Player p = getPlayer(args, 0); if (p == null) return JSValue.FALSE;
            double hp = args.length > 1 ? args[1].asNumber() : 20;
            double max = p.getAttribute(Attribute.MAX_HEALTH).getValue();
            p.setHealth(Math.min(hp, max));
            return JSValue.TRUE;
        }));

        server.set("getHealth", fn("getHealth", (args, env) -> {
            Player p = getPlayer(args, 0);
            return p != null ? JSValue.of(p.getHealth()) : JSValue.ZERO;
        }));

        server.set("setMaxHealth", fn("setMaxHealth", (args, env) -> {
            Player p = getPlayer(args, 0); if (p == null) return JSValue.FALSE;
            double max = args.length > 1 ? args[1].asNumber() : 20;
            p.getAttribute(Attribute.MAX_HEALTH).setBaseValue(max);
            return JSValue.TRUE;
        }));

        server.set("setFood", fn("setFood", (args, env) -> {
            Player p = getPlayer(args, 0); if (p == null) return JSValue.FALSE;
            p.setFoodLevel(args.length > 1 ? args[1].asInt() : 20);
            return JSValue.TRUE;
        }));

        server.set("getFood", fn("getFood", (args, env) -> {
            Player p = getPlayer(args, 0);
            return p != null ? JSValue.of(p.getFoodLevel()) : JSValue.ZERO;
        }));

        server.set("setLevel", fn("setLevel", (args, env) -> {
            Player p = getPlayer(args, 0); if (p == null) return JSValue.FALSE;
            p.setLevel(args.length > 1 ? args[1].asInt() : 0);
            return JSValue.TRUE;
        }));

        server.set("getLevel", fn("getLevel", (args, env) -> {
            Player p = getPlayer(args, 0);
            return p != null ? JSValue.of(p.getLevel()) : JSValue.ZERO;
        }));

        server.set("setExp", fn("setExp", (args, env) -> {
            Player p = getPlayer(args, 0); if (p == null) return JSValue.FALSE;
            float exp = args.length > 1 ? (float) args[1].asNumber() : 0f;
            p.setExp(Math.max(0f, Math.min(1f, exp)));
            return JSValue.TRUE;
        }));

        server.set("addExp", fn("addExp", (args, env) -> {
            Player p = getPlayer(args, 0); if (p == null) return JSValue.FALSE;
            p.giveExp(args.length > 1 ? args[1].asInt() : 0);
            return JSValue.TRUE;
        }));

        // ── Oyuncu mod & izin ─────────────────────────────────────────

        server.set("setGameMode", fn("setGameMode", (args, env) -> {
            Player p = getPlayer(args, 0); if (p == null) return JSValue.FALSE;
            String modeName = args.length > 1 ? args[1].asString().toUpperCase() : "SURVIVAL";
            try { p.setGameMode(GameMode.valueOf(modeName)); return JSValue.TRUE; }
            catch (Exception e) { return JSValue.FALSE; }
        }));

        server.set("getGameMode", fn("getGameMode", (args, env) -> {
            Player p = getPlayer(args, 0);
            return p != null ? JSValue.of(p.getGameMode().name()) : JSValue.UNDEFINED;
        }));

        server.set("isOp", fn("isOp", (args, env) -> {
            Player p = getPlayer(args, 0);
            return p != null ? JSValue.of(p.isOp()) : JSValue.FALSE;
        }));

        server.set("setOp", fn("setOp", (args, env) -> {
            Player p = getPlayer(args, 0); if (p == null) return JSValue.FALSE;
            p.setOp(args.length > 1 && args[1].asBoolean());
            return JSValue.TRUE;
        }));

        server.set("hasPermission", fn("hasPermission", (args, env) -> {
            Player p = getPlayer(args, 0); if (p == null) return JSValue.FALSE;
            return JSValue.of(args.length > 1 && p.hasPermission(args[1].asString()));
        }));

        server.set("kick", fn("kick", (args, env) -> {
            Player p = getPlayer(args, 0); if (p == null) return JSValue.FALSE;
            String reason = args.length > 1 ? colorize(args[1].asString()) : "Sunucudan atıldınız.";
            p.kickPlayer(reason);
            return JSValue.TRUE;
        }));

        server.set("ban", fn("ban", (args, env) -> {
            Player p = getPlayer(args, 0); if (p == null) return JSValue.FALSE;
            String reason = args.length > 1 ? args[1].asString() : "Yasaklandınız.";
            Bukkit.getBanList(BanList.Type.NAME).addBan(p.getName(), reason, null, null);
            p.kickPlayer(colorize("&cYasaklandınız: " + reason));
            return JSValue.TRUE;
        }));

        server.set("unban", fn("unban", (args, env) -> {
            if (args.length == 0) return JSValue.FALSE;
            Bukkit.getBanList(BanList.Type.NAME).pardon(args[0].asString());
            return JSValue.TRUE;
        }));

        server.set("isBanned", fn("isBanned", (args, env) -> {
            if (args.length == 0) return JSValue.FALSE;
            return JSValue.of(Bukkit.getBanList(BanList.Type.NAME).isBanned(args[0].asString()));
        }));

        // ── Oyuncu bilgileri ──────────────────────────────────────────

        server.set("getLocation", fn("getLocation", (args, env) -> {
            Player p = getPlayer(args, 0); if (p == null) return JSValue.NULL;
            return JSValue.wrap(p.getLocation());
        }));

        server.set("getIPAddress", fn("getIPAddress", (args, env) -> {
            Player p = getPlayer(args, 0); if (p == null) return JSValue.NULL;
            return p.getAddress() != null
                    ? JSValue.of(p.getAddress().getAddress().getHostAddress())
                    : JSValue.NULL;
        }));

        server.set("getPing", fn("getPing", (args, env) -> {
            Player p = getPlayer(args, 0); if (p == null) return JSValue.ZERO;
            return JSValue.of(p.getPing());
        }));

        server.set("getUUID", fn("getUUID", (args, env) -> {
            Player p = getPlayer(args, 0); if (p == null) return JSValue.NULL;
            return JSValue.of(p.getUniqueId().toString());
        }));

        server.set("getName", fn("getName", (args, env) -> {
            Player p = getPlayer(args, 0); if (p == null) return JSValue.NULL;
            return JSValue.of(p.getName());
        }));

        server.set("getDisplayName", fn("getDisplayName", (args, env) -> {
            Player p = getPlayer(args, 0); if (p == null) return JSValue.NULL;
            return JSValue.of(p.getDisplayName());
        }));

        server.set("setDisplayName", fn("setDisplayName", (args, env) -> {
            Player p = getPlayer(args, 0); if (p == null) return JSValue.FALSE;
            if (args.length > 1) p.setDisplayName(colorize(args[1].asString()));
            return JSValue.TRUE;
        }));

        server.set("isOnline", fn("isOnline", (args, env) -> {
            if (args.length == 0) return JSValue.FALSE;
            return JSValue.of(Bukkit.getPlayer(args[0].asString()) != null);
        }));

        server.set("hasPlayedBefore", fn("hasPlayedBefore", (args, env) -> {
            Player p = getPlayer(args, 0); if (p == null) return JSValue.FALSE;
            return JSValue.of(p.hasPlayedBefore());
        }));

        server.set("getFirstPlayed", fn("getFirstPlayed", (args, env) -> {
            Player p = getPlayer(args, 0); if (p == null) return JSValue.ZERO;
            return JSValue.of((double) p.getFirstPlayed());
        }));

        // ── Mesaj gönderme ────────────────────────────────────────────

        server.set("sendMessage", fn("sendMessage", (args, env) -> {
            Player p = getPlayer(args, 0); if (p == null) return JSValue.FALSE;
            if (args.length > 1) p.sendMessage(colorize(args[1].asString()));
            return JSValue.TRUE;
        }));

        server.set("sendTitle", fn("sendTitle", (args, env) -> {
            Player p = getPlayer(args, 0); if (p == null) return JSValue.FALSE;
            String title    = args.length > 1 ? colorize(args[1].asString()) : "";
            String subtitle = args.length > 2 ? colorize(args[2].asString()) : "";
            int fadeIn  = args.length > 3 ? args[3].asInt() : 10;
            int stay    = args.length > 4 ? args[4].asInt() : 70;
            int fadeOut = args.length > 5 ? args[5].asInt() : 20;
            p.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
            return JSValue.TRUE;
        }));

        server.set("sendActionBar", fn("sendActionBar", (args, env) -> {
            Player p = getPlayer(args, 0); if (p == null) return JSValue.FALSE;
            if (args.length > 1) p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(colorize(args[1].asString())));
                //p.sendActionBar(colorize(args[1].asString()));
            return JSValue.TRUE;
        }));

        server.set("sendTabList", fn("sendTabList", (args, env) -> {
            Player p = getPlayer(args, 0); if (p == null) return JSValue.FALSE;
            String header = args.length > 1 ? colorize(args[1].asString()) : "";
            String footer = args.length > 2 ? colorize(args[2].asString()) : "";
            p.setPlayerListHeaderFooter(header, footer);
            return JSValue.TRUE;
        }));

        // ── Uçma ─────────────────────────────────────────────────────

        server.set("setFly", fn("setFly", (args, env) -> {
            Player p = getPlayer(args, 0); if (p == null) return JSValue.FALSE;
            boolean fly = args.length > 1 && args[1].asBoolean();
            p.setAllowFlight(fly);
            p.setFlying(fly);
            return JSValue.TRUE;
        }));

        server.set("canFly", fn("canFly", (args, env) -> {
            Player p = getPlayer(args, 0); if (p == null) return JSValue.FALSE;
            return JSValue.of(p.getAllowFlight());
        }));

        // ── Ses & Parçacık ────────────────────────────────────────────

        server.set("playSound", fn("playSound", (args, env) -> {
            Player p = getPlayer(args, 0); if (p == null) return JSValue.FALSE;
            String soundName = args.length > 1 ? args[1].asString().toUpperCase() : "ENTITY_PLAYER_LEVELUP";
            float volume = args.length > 2 ? (float) args[2].asNumber() : 1f;
            float pitch  = args.length > 3 ? (float) args[3].asNumber() : 1f;
            try {
                Sound sound = Sound.valueOf(soundName);
                p.playSound(p.getLocation(), sound, volume, pitch);
                return JSValue.TRUE;
            } catch (Exception e) { return JSValue.FALSE; }
        }));

        server.set("playSoundAt", fn("playSoundAt", (args, env) -> {
            if (args.length < 2) return JSValue.FALSE;
            Object rawLoc = args[0].isJava() ? args[0].javaRaw() : null;
            if (!(rawLoc instanceof Location loc)) return JSValue.FALSE;
            String soundName = args[1].asString().toUpperCase();
            float volume = args.length > 2 ? (float) args[2].asNumber() : 1f;
            float pitch  = args.length > 3 ? (float) args[3].asNumber() : 1f;
            try {
                loc.getWorld().playSound(loc, Sound.valueOf(soundName), volume, pitch);
                return JSValue.TRUE;
            } catch (Exception e) { return JSValue.FALSE; }
        }));

        server.set("spawnParticle", fn("spawnParticle", (args, env) -> {
            Object rawLoc = args.length > 0 && args[0].isJava() ? args[0].javaRaw() : null;
            if (!(rawLoc instanceof Location loc)) return JSValue.FALSE;
            String particleName = args.length > 1 ? args[1].asString().toUpperCase() : "HEART";
            int count = args.length > 2 ? args[2].asInt() : 5;
            try {
                loc.getWorld().spawnParticle(Particle.valueOf(particleName), loc, count);
                return JSValue.TRUE;
            } catch (Exception e) { return JSValue.FALSE; }
        }));

        // ── Efektler ──────────────────────────────────────────────────

        server.set("addEffect", fn("addEffect", (args, env) -> {
            Player p = getPlayer(args, 0); if (p == null) return JSValue.FALSE;
            String effectName = args.length > 1 ? args[1].asString().toUpperCase() : "SPEED";
            int duration  = args.length > 2 ? args[2].asInt() : 200;
            int amplifier = args.length > 3 ? args[3].asInt() : 0;
            try {
                PotionEffectType type = PotionEffectType.getByName(effectName);
                if (type == null) return JSValue.FALSE;
                p.addPotionEffect(new PotionEffect(type, duration, amplifier));
                return JSValue.TRUE;
            } catch (Exception e) { return JSValue.FALSE; }
        }));

        server.set("removeEffect", fn("removeEffect", (args, env) -> {
            Player p = getPlayer(args, 0); if (p == null) return JSValue.FALSE;
            if (args.length > 1) {
                PotionEffectType type = PotionEffectType.getByName(args[1].asString().toUpperCase());
                if (type != null) p.removePotionEffect(type);
            }
            return JSValue.TRUE;
        }));

        server.set("clearEffects", fn("clearEffects", (args, env) -> {
            Player p = getPlayer(args, 0); if (p == null) return JSValue.FALSE;
            p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
            return JSValue.TRUE;
        }));

        server.set("hasEffect", fn("hasEffect", (args, env) -> {
            Player p = getPlayer(args, 0); if (p == null) return JSValue.FALSE;
            if (args.length < 2) return JSValue.FALSE;
            PotionEffectType type = PotionEffectType.getByName(args[1].asString().toUpperCase());
            return type != null ? JSValue.of(p.hasPotionEffect(type)) : JSValue.FALSE;
        }));

        // ── Envanter ──────────────────────────────────────────────────

        server.set("giveItem", fn("giveItem", (args, env) -> {
            Player p = getPlayer(args, 0); if (p == null) return JSValue.FALSE;
            if (args.length < 2 || !args[1].isJava()) return JSValue.FALSE;
            Object raw = args[1].javaRaw();
            if (raw instanceof ItemStack item) {
                p.getInventory().addItem(item);
                return JSValue.TRUE;
            }
            return JSValue.FALSE;
        }));

        server.set("clearInventory", fn("clearInventory", (args, env) -> {
            Player p = getPlayer(args, 0); if (p == null) return JSValue.FALSE;
            p.getInventory().clear();
            return JSValue.TRUE;
        }));

        server.set("getItemInHand", fn("getItemInHand", (args, env) -> {
            Player p = getPlayer(args, 0); if (p == null) return JSValue.NULL;
            ItemStack item = p.getInventory().getItemInMainHand();
            return item.getType() == Material.AIR ? JSValue.NULL : JSValue.wrap(item);
        }));

        server.set("setItemInHand", fn("setItemInHand", (args, env) -> {
            Player p = getPlayer(args, 0); if (p == null) return JSValue.FALSE;
            if (args.length < 2 || !args[1].isJava()) return JSValue.FALSE;
            Object raw = args[1].javaRaw();
            if (raw instanceof ItemStack item) {
                p.getInventory().setItemInMainHand(item);
                return JSValue.TRUE;
            }
            return JSValue.FALSE;
        }));

        server.set("hasItem", fn("hasItem", (args, env) -> {
            Player p = getPlayer(args, 0); if (p == null) return JSValue.FALSE;
            if (args.length < 2) return JSValue.FALSE;
            Material mat = Material.matchMaterial(args[1].asString().toUpperCase());
            return mat != null ? JSValue.of(p.getInventory().contains(mat)) : JSValue.FALSE;
        }));

        // ── Item oluşturma ────────────────────────────────────────────

        server.set("createItem", fn("createItem", (args, env) -> {
            if (args.length == 0) return JSValue.NULL;
            Material mat = Material.matchMaterial(args[0].asString().toUpperCase());
            if (mat == null) return JSValue.NULL;
            int amount = args.length > 1 ? args[1].asInt() : 1;
            return JSValue.wrap(new ItemStack(mat, amount));
        }));

        server.set("createNamedItem", fn("createNamedItem", (args, env) -> {
            if (args.length < 2) return JSValue.NULL;
            Material mat = Material.matchMaterial(args[0].asString().toUpperCase());
            if (mat == null) return JSValue.NULL;
            int amount = args.length > 2 && args[2].isNumber() ? args[2].asInt() : 1;
            ItemStack item = new ItemStack(mat, amount);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(colorize(args[1].asString()));
                // Kalan argümanlar string ise lore olarak ekle
                if (args.length > 3) {
                    List<String> lore = new ArrayList<>();
                    for (int i = 3; i < args.length; i++) lore.add(colorize(args[i].asString()));
                    meta.setLore(lore);
                }
                item.setItemMeta(meta);
            }
            return JSValue.wrap(item);
        }));

        server.set("getMaterial", fn("getMaterial", (args, env) -> {
            if (args.length == 0) return JSValue.NULL;
            Object raw = args[0].isJava() ? args[0].javaRaw() : null;
            if (raw instanceof Block block) return JSValue.of(block.getType().name());
            if (raw instanceof ItemStack item) return JSValue.of(item.getType().name());
            return JSValue.NULL;
        }));

        // ── Dünya ─────────────────────────────────────────────────────

        server.set("getWorld", fn("getWorld", (args, env) -> {
            if (args.length == 0) return JSValue.NULL;
            World w = Bukkit.getWorld(args[0].asString());
            return w != null ? JSValue.wrap(w) : JSValue.NULL;
        }));

        server.set("getDefaultWorld", fn("getDefaultWorld", (args, env) ->
                JSValue.wrap(Bukkit.getWorlds().get(0))
        ));

        server.set("getBlock", fn("getBlock", (args, env) -> {
            Object rawWorld = args.length > 0 && args[0].isJava() ? args[0].javaRaw() : null;
            if (!(rawWorld instanceof World world)) return JSValue.NULL;
            int x = args.length > 1 ? args[1].asInt() : 0;
            int y = args.length > 2 ? args[2].asInt() : 0;
            int z = args.length > 3 ? args[3].asInt() : 0;
            return JSValue.wrap(world.getBlockAt(x, y, z));
        }));

        server.set("setBlock", fn("setBlock", (args, env) -> {
            Object rawWorld = args.length > 0 && args[0].isJava() ? args[0].javaRaw() : null;
            if (!(rawWorld instanceof World world)) return JSValue.FALSE;
            int x = args.length > 1 ? args[1].asInt() : 0;
            int y = args.length > 2 ? args[2].asInt() : 0;
            int z = args.length > 3 ? args[3].asInt() : 0;
            String matName = args.length > 4 ? args[4].asString().toUpperCase() : "AIR";
            Material mat = Material.matchMaterial(matName);
            if (mat == null) return JSValue.FALSE;
            world.getBlockAt(x, y, z).setType(mat);
            return JSValue.TRUE;
        }));

        server.set("getHighestY", fn("getHighestY", (args, env) -> {
            Object rawWorld = args.length > 0 && args[0].isJava() ? args[0].javaRaw() : null;
            if (!(rawWorld instanceof World world)) return JSValue.ZERO;
            int x = args.length > 1 ? args[1].asInt() : 0;
            int z = args.length > 2 ? args[2].asInt() : 0;
            return JSValue.of(world.getHighestBlockYAt(x, z));
        }));

        server.set("setTime", fn("setTime", (args, env) -> {
            Object rawWorld = args.length > 0 && args[0].isJava() ? args[0].javaRaw() : null;
            if (!(rawWorld instanceof World world)) return JSValue.FALSE;
            long time = args.length > 1 ? args[1].asLong() : 6000L;
            world.setTime(time);
            return JSValue.TRUE;
        }));

        server.set("setWeather", fn("setWeather", (args, env) -> {
            Object rawWorld = args.length > 0 && args[0].isJava() ? args[0].javaRaw() : null;
            if (!(rawWorld instanceof World world)) return JSValue.FALSE;
            String weather = args.length > 1 ? args[1].asString().toUpperCase() : "CLEAR";
            switch (weather) {
                case "CLEAR", "SUN"     -> { world.setStorm(false); world.setThundering(false); }
                case "RAIN", "STORM"    -> { world.setStorm(true);  world.setThundering(false); }
                case "THUNDER"          -> { world.setStorm(true);  world.setThundering(true); }
            }
            return JSValue.TRUE;
        }));

        server.set("getSpawnLocation", fn("getSpawnLocation", (args, env) -> {
            Object rawWorld = args.length > 0 && args[0].isJava() ? args[0].javaRaw() : null;
            if (!(rawWorld instanceof World world)) return JSValue.NULL;
            return JSValue.wrap(world.getSpawnLocation());
        }));

        server.set("setSpawnLocation", fn("setSpawnLocation", (args, env) -> {
            Object rawWorld = args.length > 0 && args[0].isJava() ? args[0].javaRaw() : null;
            if (!(rawWorld instanceof World world)) return JSValue.FALSE;
            int x = args.length > 1 ? args[1].asInt() : 0;
            int y = args.length > 2 ? args[2].asInt() : 64;
            int z = args.length > 3 ? args[3].asInt() : 0;
            world.setSpawnLocation(x, y, z);
            return JSValue.TRUE;
        }));

        // ── Location ──────────────────────────────────────────────────

        server.set("createLocation", fn("createLocation", (args, env) -> {
            Object rawWorld = args.length > 0 && args[0].isJava() ? args[0].javaRaw() : null;
            World world = rawWorld instanceof World w ? w : Bukkit.getWorlds().get(0);
            double x     = args.length > 1 ? args[1].asNumber() : 0;
            double y     = args.length > 2 ? args[2].asNumber() : 64;
            double z     = args.length > 3 ? args[3].asNumber() : 0;
            float  yaw   = args.length > 4 ? (float) args[4].asNumber() : 0f;
            float  pitch = args.length > 5 ? (float) args[5].asNumber() : 0f;
            return JSValue.wrap(new Location(world, x, y, z, yaw, pitch));
        }));

        // Location bilgilerini JS objesi olarak al
        server.set("locationInfo", fn("locationInfo", (args, env) -> {
            Object rawLoc = args.length > 0 && args[0].isJava() ? args[0].javaRaw() : null;
            if (!(rawLoc instanceof Location loc)) return JSValue.NULL;
            JSObject obj = new JSObject();
            obj.set("x",     JSValue.of(loc.getX()));
            obj.set("y",     JSValue.of(loc.getY()));
            obj.set("z",     JSValue.of(loc.getZ()));
            obj.set("yaw",   JSValue.of(loc.getYaw()));
            obj.set("pitch", JSValue.of(loc.getPitch()));
            obj.set("world", JSValue.of(loc.getWorld() != null ? loc.getWorld().getName() : ""));
            return JSValue.of(obj);
        }));

        // ── Teleport ──────────────────────────────────────────────────

        server.set("teleport", fn("teleport", (args, env) -> {
            Object rawPlayer = args.length > 0 && args[0].isJava() ? args[0].javaRaw() : null;
            Object rawLoc    = args.length > 1 && args[1].isJava() ? args[1].javaRaw() : null;
            if (rawPlayer instanceof Player p && rawLoc instanceof Location loc) {
                p.teleport(loc);
                return JSValue.TRUE;
            }
            return JSValue.FALSE;
        }));

        server.set("teleportToSpawn", fn("teleportToSpawn", (args, env) -> {
            Object raw = args.length > 0 && args[0].isJava() ? args[0].javaRaw() : null;
            if (raw instanceof Player p) {
                p.teleport(p.getWorld().getSpawnLocation());
                return JSValue.TRUE;
            }
            return JSValue.FALSE;
        }));

        server.set("teleportXYZ", fn("teleportXYZ", (args, env) -> {
            Object rawPlayer = args.length > 0 && args[0].isJava() ? args[0].javaRaw() : null;
            if (!(rawPlayer instanceof Player p)) return JSValue.FALSE;
            String worldName = args.length > 1 ? args[1].asString() : p.getWorld().getName();
            World world = Bukkit.getWorld(worldName);
            if (world == null) return JSValue.FALSE;
            double x = args.length > 2 ? args[2].asNumber() : p.getLocation().getX();
            double y = args.length > 3 ? args[3].asNumber() : p.getLocation().getY();
            double z = args.length > 4 ? args[4].asNumber() : p.getLocation().getZ();
            p.teleport(new Location(world, x, y, z));
            return JSValue.TRUE;
        }));

        // ── Mob & Varlık ──────────────────────────────────────────────

        server.set("spawnEntity", fn("spawnEntity", (args, env) -> {
            Object rawLoc = args.length > 0 && args[0].isJava() ? args[0].javaRaw() : null;
            if (!(rawLoc instanceof Location loc)) return JSValue.NULL;
            String entityName = args.length > 1 ? args[1].asString().toUpperCase() : "ZOMBIE";
            try {
                EntityType type = EntityType.valueOf(entityName);
                Entity e = loc.getWorld().spawnEntity(loc, type);
                return JSValue.wrap(e);
            } catch (Exception e) { return JSValue.NULL; }
        }));

        server.set("createExplosion", fn("createExplosion", (args, env) -> {
            Object rawLoc = args.length > 0 && args[0].isJava() ? args[0].javaRaw() : null;
            if (!(rawLoc instanceof Location loc)) return JSValue.FALSE;
            float power = args.length > 1 ? (float) args[1].asNumber() : 4f;
            boolean fire = args.length > 2 && args[2].asBoolean();
            loc.getWorld().createExplosion(loc, power, fire, false);
            return JSValue.TRUE;
        }));

        server.set("strikeLightning", fn("strikeLightning", (args, env) -> {
            Object rawLoc = args.length > 0 && args[0].isJava() ? args[0].javaRaw() : null;
            if (!(rawLoc instanceof Location loc)) return JSValue.FALSE;
            boolean effect = args.length > 1 && args[1].asBoolean();
            if (effect) loc.getWorld().strikeLightningEffect(loc);
            else        loc.getWorld().strikeLightning(loc);
            return JSValue.TRUE;
        }));

        // ── Scoreboard ────────────────────────────────────────────────

        server.set("getScoreboard", fn("getScoreboard", (args, env) ->
                JSValue.wrap(Bukkit.getScoreboardManager().getMainScoreboard())
        ));

        server.set("createSidebar", fn("createSidebar", (args, env) -> {
            String title = args.length > 0 ? colorize(args[0].asString()) : "Skor";
            return JSValue.of(buildSidebarObject(title));
        }));

        // ── Sunucu bilgisi ────────────────────────────────────────────

        server.set("getTPS", fn("getTPS", (args, env) ->
                JSValue.of(TPS.getAverageTPS(1))
        ));

        server.set("getMaxPlayers", fn("getMaxPlayers", (args, env) ->
                JSValue.of(Bukkit.getMaxPlayers())
        ));

        server.set("getMotd", fn("getMotd", (args, env) ->
                JSValue.of(Bukkit.getMotd())
        ));

        server.set("getVersion", fn("getVersion", (args, env) ->
                JSValue.of(Bukkit.getVersion())
        ));

        server.set("isWhitelisted", fn("isWhitelisted", (args, env) -> {
            if (args.length == 0) return JSValue.FALSE;
            OfflinePlayer op = Bukkit.getOfflinePlayer(args[0].asString());
            return JSValue.of(op.isWhitelisted());
        }));

        server.set("addWhitelist", fn("addWhitelist", (args, env) -> {
            if (args.length == 0) return JSValue.FALSE;
            OfflinePlayer op = Bukkit.getOfflinePlayer(args[0].asString());
            op.setWhitelisted(true);
            return JSValue.TRUE;
        }));

        server.set("removeWhitelist", fn("removeWhitelist", (args, env) -> {
            if (args.length == 0) return JSValue.FALSE;
            OfflinePlayer op = Bukkit.getOfflinePlayer(args[0].asString());
            op.setWhitelisted(false);
            return JSValue.TRUE;
        }));

        // ── Komut çalıştırma ──────────────────────────────────────────

        server.set("dispatchCommand", fn("dispatchCommand", (args, env) -> {
            if (args.length < 2) return JSValue.FALSE;
            Object rawSender = args[0].isJava() ? args[0].javaRaw() : Bukkit.getConsoleSender();
            org.bukkit.command.CommandSender cs = rawSender instanceof org.bukkit.command.CommandSender s
                    ? s : Bukkit.getConsoleSender();
            return JSValue.of(Bukkit.dispatchCommand(cs, args[1].asString()));
        }));

        server.set("consoleCommand", fn("consoleCommand", (args, env) -> {
            if (args.length == 0) return JSValue.FALSE;
            return JSValue.of(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), args[0].asString()));
        }));

        // ── Scheduler ─────────────────────────────────────────────────

        server.set("schedule", fn("schedule", (args, env) -> {
            requireFn(args, 2, "server.schedule(ticks, function)");
            long ticks = args[0].asLong();
            JSFunction cb = (JSFunction) args[1];
            BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> safeCall(cb), ticks);
            instance.addTaskRef(task);
            return JSValue.of(task.getTaskId());
        }));

        server.set("repeat", fn("repeat", (args, env) -> {
            requireFn(args, 2, "server.repeat(ticks, function)");
            long ticks = args[0].asLong();
            JSFunction cb = (JSFunction) args[1];
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> safeCall(cb), 0L, ticks);
            instance.addTaskRef(task);
            return JSValue.of(task.getTaskId());
        }));

        server.set("repeatAsync", fn("repeatAsync", (args, env) -> {
            requireFn(args, 2, "server.repeatAsync(ticks, function)");
            long ticks = args[0].asLong();
            JSFunction cb = (JSFunction) args[1];
            BukkitTask task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> safeCall(cb), 0L, ticks);
            instance.addTaskRef(task);
            return JSValue.of(task.getTaskId());
        }));

        server.set("cancel", fn("cancel", (args, env) -> {
            if (args.length > 0) Bukkit.getScheduler().cancelTask(args[0].asInt());
            return JSValue.UNDEFINED;
        }));

        server.set("runSync", fn("runSync", (args, env) -> {
            if (args.length > 0 && args[0].isFunction())
                Bukkit.getScheduler().runTask(plugin, () -> safeCall((JSFunction) args[0]));
            return JSValue.UNDEFINED;
        }));

        return server;
    }

    // ──────────────────────────────────────────────────────────────────
    //  Sidebar (Scoreboard) yardımcısı
    // ──────────────────────────────────────────────────────────────────

    private JSObject buildSidebarObject(String title) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("sidebar", "dummy",
                ChatColor.translateAlternateColorCodes('&', title));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        JSObject sidebar = new JSObject();

        sidebar.set("setLine", fn("setLine", (args, env) -> {
            if (args.length < 2) return JSValue.UNDEFINED;
            int slot = args[0].asInt();
            String text = colorize(args[1].asString());
            // Satır ekleme: score'u slot olarak kullan
            Score score = obj.getScore(text);
            score.setScore(slot);
            return JSValue.UNDEFINED;
        }));

        sidebar.set("setTitle", fn("setTitle", (args, env) -> {
            if (args.length > 0) obj.setDisplayName(colorize(args[0].asString()));
            return JSValue.UNDEFINED;
        }));

        sidebar.set("show", fn("show", (args, env) -> {
            Player p = getPlayer(args, 0);
            if (p != null) p.setScoreboard(board);
            return JSValue.UNDEFINED;
        }));

        sidebar.set("hide", fn("hide", (args, env) -> {
            Player p = getPlayer(args, 0);
            if (p != null) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            return JSValue.UNDEFINED;
        }));

        sidebar.set("clear", fn("clear", (args, env) -> {
            for (String entry : board.getEntries()) board.resetScores(entry);
            return JSValue.UNDEFINED;
        }));

        return sidebar;
    }

    // ──────────────────────────────────────────────────────────────────
    //  EventManager objesi
    // ──────────────────────────────────────────────────────────────────

    private JSObject buildEventManager() {
        JSObject em = new JSObject();

        em.set("on", fn("on", (args, env) -> {
            if (args.length < 2 || !args[1].isFunction())
                throw new JsError("em.on(eventAdı, function) şeklinde kullanın");
            registerEvent(args[0].asString(), EventPriority.NORMAL, (JSFunction) args[1]);
            return JSValue.UNDEFINED;
        }));

        em.set("onPriority", fn("onPriority", (args, env) -> {
            if (args.length < 3 || !args[2].isFunction())
                throw new JsError("em.onPriority(eventAdı, öncelik, function) şeklinde kullanın");
            EventPriority priority;
            try { priority = EventPriority.valueOf(args[1].asString().toUpperCase()); }
            catch (Exception ex) { priority = EventPriority.NORMAL; }
            registerEvent(args[0].asString(), priority, (JSFunction) args[2]);
            return JSValue.UNDEFINED;
        }));

        return em;
    }

    // ──────────────────────────────────────────────────────────────────
    //  CommandManager objesi
    // ──────────────────────────────────────────────────────────────────

    private JSObject buildCommandManager() {
        JSObject cm = new JSObject();

        cm.set("register", fn("register", (args, env) -> {
            if (args.length < 2 || !args[1].isFunction())
                throw new JsError("cm.register(komutAdı, function) şeklinde kullanın");
            registerCommand(args[0].asString(), "NanoScript komutu", "/" + args[0].asString().toLowerCase(), (JSFunction) args[1]);
            return JSValue.UNDEFINED;
        }));

        cm.set("registerFull", fn("registerFull", (args, env) -> {
            if (args.length < 4 || !args[3].isFunction())
                throw new JsError("cm.registerFull(isim, açıklama, kullanım, function) şeklinde kullanın");
            registerCommand(args[0].asString(), args[1].asString(), args[2].asString(), (JSFunction) args[3]);
            return JSValue.UNDEFINED;
        }));

        // cm.registerWithTab(name, desc, usage, executeFn, tabFn)
        // tabFn: function(sender, args) { return ["öneri1", "öneri2"] }
        cm.set("registerWithTab", fn("registerWithTab", (args, env) -> {
            if (args.length < 5 || !args[3].isFunction() || !args[4].isFunction())
                throw new JsError("cm.registerWithTab(isim, açıklama, kullanım, executeFn, tabFn) şeklinde kullanın");
            registerCommandWithTab(
                    args[0].asString(), args[1].asString(), args[2].asString(),
                    (JSFunction) args[3], (JSFunction) args[4]
            );
            return JSValue.UNDEFINED;
        }));

        // cm.registerTab(name, executeFn, tabFn)  — kısa form
        cm.set("registerTab", fn("registerTab", (args, env) -> {
            if (args.length < 3 || !args[1].isFunction() || !args[2].isFunction())
                throw new JsError("cm.registerTab(isim, executeFn, tabFn) şeklinde kullanın");
            registerCommandWithTab(
                    args[0].asString(), "NanoScript komutu", "/" + args[0].asString().toLowerCase(),
                    (JSFunction) args[1], (JSFunction) args[2]
            );
            return JSValue.UNDEFINED;
        }));

        return cm;
    }

    // ──────────────────────────────────────────────────────────────────
    //  Kayıt implementasyonları
    // ──────────────────────────────────────────────────────────────────

    private void registerEvent(String eventName, EventPriority priority, JSFunction callback) {
        Class<? extends Event> eventClass = resolveEventClass(eventName);
        if (eventClass == null) {
            logger.warning("[NanoScript] Event bulunamadı: '" + eventName + "'");
            return;
        }
        DynamicListener dl = new DynamicListener(callback, eventName, engine, logger);
        Bukkit.getPluginManager().registerEvent(
                eventClass, dl, priority,
                (listener, event) -> { if (eventClass.isInstance(event)) ((DynamicListener) listener).handleEvent(event); },
                plugin, false
        );
        instance.addEventListenerRef(dl);
    }

    private void registerCommand(String name, String description, String usage, JSFunction callback) {
        String lower = name.toLowerCase();
        DynamicCommand cmd = new DynamicCommand(lower, description, usage, callback, engine, logger);
        CommandMapUtil.register(plugin, cmd);
        instance.addCommandRef(lower);
    }

    private void registerCommandWithTab(String name, String description, String usage,
                                        JSFunction callback, JSFunction tabCallback) {
        String lower = name.toLowerCase();
        DynamicCommand cmd = new DynamicCommand(lower, description, usage, callback, engine, logger) {
            @Override
            public java.util.List<String> tabComplete(org.bukkit.command.CommandSender sender,
                                                      String alias, String[] rawArgs) {
                try {
                    JSValue senderVal = JSValue.wrap(sender);
                    JSArray jsArgs = new JSArray();
                    for (String a : rawArgs) jsArgs.push(JSValue.of(a));
                    JSValue result = engine.call(tabCallback, new JSValue[]{senderVal, JSValue.of(jsArgs)});
                    java.util.List<String> list = new java.util.ArrayList<>();
                    if (result != null && result.isArray()) {
                        JSArray arr = result.asArray();
                        for (int i = 0; i < arr.length(); i++) list.add(arr.get(i).asString());
                    }
                    String current = rawArgs.length > 0 ? rawArgs[rawArgs.length - 1].toLowerCase() : "";
                    list.removeIf(s -> !s.toLowerCase().startsWith(current));
                    return list;
                } catch (Exception e) {
                    logger.warning("[NanoScript] Tab complete hatası (" + lower + "): " + e.getMessage());
                    return java.util.Collections.emptyList();
                }
            }
        };
        CommandMapUtil.register(plugin, cmd);
        instance.addCommandRef(lower);
    }

    // ──────────────────────────────────────────────────────────────────
    //  Yardımcılar
    // ──────────────────────────────────────────────────────────────────

    private Player getPlayer(JSValue[] args, int index) {
        if (args.length <= index) return null;
        JSValue val = args[index];
        if (val.isJava() && val.javaRaw() instanceof Player p) return p;
        if (val.isString()) return Bukkit.getPlayer(val.asString());
        return null;
    }

    private String arg(JSValue[] args, int i, String def) {
        return args.length > i ? args[i].asString() : def;
    }

    private void safeCall(JSFunction callback) {
        try { engine.call(callback, new JSValue[]{}); }
        catch (JsError e) { logger.warning("[NanoScript] Callback hatası (" + instance.getFileName() + "): " + e.getMessage()); }
        catch (Exception e) { logger.warning("[NanoScript] Beklenmedik hata: " + e.getMessage()); }
    }

    private void requireFn(JSValue[] args, int minLen, String usage) {
        if (args.length < minLen || !args[minLen - 1].isFunction())
            throw new JsError(usage + " şeklinde kullanın");
    }

    private String colorize(String msg) {
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    private JSFunction fn(String name, JSFunction.NativeFn impl) {
        return JSFunction.native1(name, impl);
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Event> resolveEventClass(String name) {
        try { return (Class<? extends Event>) Class.forName(name); }
        catch (ClassNotFoundException ignored) {}
        for (String pkg : EVENT_PACKAGES) {
            try { return (Class<? extends Event>) Class.forName(pkg + name); }
            catch (ClassNotFoundException ignored) {}
        }
        return null;
    }
}