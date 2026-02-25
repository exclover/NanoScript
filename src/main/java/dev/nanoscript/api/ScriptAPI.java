package dev.nanoscript.api;

import dev.nanoscript.NanoScript;
import dev.nanoscript.engine.CommandMapUtil;
import dev.nanoscript.engine.ScriptInstance;
import dev.nanoscript.jsengine.*;
import dev.nanoscript.listener.DynamicCommand;
import dev.nanoscript.listener.DynamicListener;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.scheduler.BukkitTask;

import java.util.logging.Logger;

/**
 * NanoScript Minecraft API — server objesi merkezli tasarım.
 *
 * ┌─────────────────────────────────────────────────────────┐
 * │  JS kullanımı:                                          │
 * │                                                         │
 * │  const server = getServer();                            │
 * │                                                         │
 * │  const em = server.getEventManager();                   │
 * │  em.on("PlayerJoinEvent", function(e) { ... });         │
 * │                                                         │
 * │  const cm = server.getCommandManager();                 │
 * │  cm.register("test", function(sender, args) { ... });   │
 * │                                                         │
 * │  server.broadcast("&aMerhaba!");                        │
 * │  server.log("konsol logu");                             │
 * │  server.onlineCount();                                  │
 * │  server.getPlayer("isim");                              │
 * │  server.getOnlinePlayers();                             │
 * │  server.getWorld("world");                              │
 * │                                                         │
 * │  var id = server.repeat(20, function() { ... });        │
 * │  var id = server.schedule(100, function() { ... });     │
 * │  server.cancel(id);                                     │
 * │  server.runSync(function() { ... });                    │
 * │                                                         │
 * │  // Tek global yardımcı:                                │
 * │  color("&aRenkli");                                     │
 * └─────────────────────────────────────────────────────────┘
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

    public ScriptAPI(NanoScript plugin, ScriptInstance instance, NanoEngine engine) {
        this.plugin   = plugin;
        this.instance = instance;
        this.engine   = engine;
        this.logger   = plugin.getLogger();
    }

    // ──────────────────────────────────────────────────────────────────
    //  Ana kurulum
    // ──────────────────────────────────────────────────────────────────

    public void installGlobals() {
        JSObject serverObj = buildServerObject();

        // getServer() → server objesi döner
        engine.defineGlobal("getServer", fn("getServer", (args, env) -> JSValue.of(serverObj)));

        // color() — sık kullanılan tek global yardımcı
        engine.defineGlobal("color", fn("color", (args, env) ->
            JSValue.of(args.length > 0 ? colorize(args[0].asString()) : "")
        ));
    }

    // ──────────────────────────────────────────────────────────────────
    //  server objesi
    // ──────────────────────────────────────────────────────────────────

    private JSObject buildServerObject() {
        JSObject server = new JSObject();

        // Manager getter'ları — her çağrıda aynı singleton obje döner
        JSObject eventManager   = buildEventManager();
        JSObject commandManager = buildCommandManager();

        server.set("getEventManager",   fn("getEventManager",   (a, e) -> JSValue.of(eventManager)));
        server.set("getCommandManager", fn("getCommandManager", (a, e) -> JSValue.of(commandManager)));

        // ── Mesajlaşma ────────────────────────────────────────────────

        server.set("broadcast", fn("broadcast", (args, env) -> {
            Bukkit.broadcastMessage(colorize(args.length > 0 ? args[0].asString() : ""));
            return JSValue.UNDEFINED;
        }));

        server.set("log", fn("log", (args, env) -> {
            logger.info("[Script/" + instance.getFileName() + "] " + (args.length > 0 ? args[0].asString() : ""));
            return JSValue.UNDEFINED;
        }));

        server.set("color", fn("color", (args, env) ->
            JSValue.of(args.length > 0 ? colorize(args[0].asString()) : "")
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

        // ── Dünya / komut ─────────────────────────────────────────────

        server.set("getWorld", fn("getWorld", (args, env) -> {
            if (args.length == 0) return JSValue.NULL;
            org.bukkit.World w = Bukkit.getWorld(args[0].asString());
            return w != null ? JSValue.wrap(w) : JSValue.NULL;
        }));

        server.set("dispatchCommand", fn("dispatchCommand", (args, env) -> {
            if (args.length < 2) return JSValue.FALSE;
            Object rawSender = args[0].isJava() ? args[0].javaRaw() : Bukkit.getConsoleSender();
            org.bukkit.command.CommandSender cs = rawSender instanceof org.bukkit.command.CommandSender s
                ? s : Bukkit.getConsoleSender();
            return JSValue.of(Bukkit.dispatchCommand(cs, args[1].asString()));
        }));

        // server.now() — System.currentTimeMillis() (new Date() sorununu önler)
        server.set("now", fn("now", (args, env) ->
            JSValue.of((double) System.currentTimeMillis())
        ));

        // ── Teleport yardımcıları ─────────────────────────────────────
        // server.teleport(player, location) — reflection overload sorununu çözer
        server.set("teleport", fn("teleport", (args, env) -> {
            if (args.length < 2) return JSValue.FALSE;
            Object rawPlayer = args[0].isJava() ? args[0].javaRaw() : null;
            Object rawLoc    = args[1].isJava() ? args[1].javaRaw() : null;
            if (rawPlayer instanceof Player p && rawLoc instanceof org.bukkit.Location loc) {
                p.teleport(loc);
                return JSValue.TRUE;
            }
            return JSValue.FALSE;
        }));

        // server.teleportToSpawn(player) — dünya spawn'ına güvenli ışınlama
        server.set("teleportToSpawn", fn("teleportToSpawn", (args, env) -> {
            if (args.length == 0) return JSValue.FALSE;
            Object raw = args[0].isJava() ? args[0].javaRaw() : null;
            if (raw instanceof Player p) {
                org.bukkit.Location spawnLoc = p.getWorld().getSpawnLocation();
                p.teleport(spawnLoc);
                return JSValue.TRUE;
            }
            return JSValue.FALSE;
        }));

        // ── Scheduler ─────────────────────────────────────────────────

        // server.schedule(ticks, fn) — tek seferlik
        server.set("schedule", fn("schedule", (args, env) -> {
            requireFn(args, 2, "server.schedule(ticks, function)");
            long ticks = (long) args[0].asNumber();
            JSFunction cb = (JSFunction) args[1];
            BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> safeCall(cb), ticks);
            instance.addTaskRef(task);
            return JSValue.of(task.getTaskId());
        }));

        // server.repeat(ticks, fn) — tekrarlayan
        server.set("repeat", fn("repeat", (args, env) -> {
            requireFn(args, 2, "server.repeat(ticks, function)");
            long ticks = (long) args[0].asNumber();
            JSFunction cb = (JSFunction) args[1];
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> safeCall(cb), 0L, ticks);
            instance.addTaskRef(task);
            return JSValue.of(task.getTaskId());
        }));

        // server.repeatAsync(ticks, fn) — async tekrarlayan (disk IO için)
        server.set("repeatAsync", fn("repeatAsync", (args, env) -> {
            requireFn(args, 2, "server.repeatAsync(ticks, function)");
            long ticks = (long) args[0].asNumber();
            JSFunction cb = (JSFunction) args[1];
            BukkitTask task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> safeCall(cb), 0L, ticks);
            instance.addTaskRef(task);
            return JSValue.of(task.getTaskId());
        }));

        // server.cancel(taskId)
        server.set("cancel", fn("cancel", (args, env) -> {
            if (args.length > 0) Bukkit.getScheduler().cancelTask(args[0].asInt());
            return JSValue.UNDEFINED;
        }));

        // server.runSync(fn) — async context'ten main thread'e geç
        server.set("runSync", fn("runSync", (args, env) -> {
            if (args.length > 0 && args[0].isFunction())
                Bukkit.getScheduler().runTask(plugin, () -> safeCall((JSFunction) args[0]));
            return JSValue.UNDEFINED;
        }));

        return server;
    }

    // ──────────────────────────────────────────────────────────────────
    //  EventManager objesi
    // ──────────────────────────────────────────────────────────────────

    private JSObject buildEventManager() {
        JSObject em = new JSObject();

        // em.on(eventName, callback)
        em.set("on", fn("on", (args, env) -> {
            if (args.length < 2 || !args[1].isFunction())
                throw new JsError("em.on(eventAdı, function) şeklinde kullanın");
            registerEvent(args[0].asString(), EventPriority.NORMAL, (JSFunction) args[1]);
            return JSValue.UNDEFINED;
        }));

        // em.on(eventName, priority, callback)  — öncelikli kayıt
        // Örnek: em.on("PlayerDamageEvent", "HIGH", function(e){...})
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

        // cm.register(name, callback)
        cm.set("register", fn("register", (args, env) -> {
            if (args.length < 2 || !args[1].isFunction())
                throw new JsError("cm.register(komutAdı, function) şeklinde kullanın");
            registerCommand(args[0].asString(), "NanoScript komutu", "/" + args[0].asString().toLowerCase(), (JSFunction) args[1]);
            return JSValue.UNDEFINED;
        }));

        // cm.registerFull(name, description, usage, callback)
        cm.set("registerFull", fn("registerFull", (args, env) -> {
            if (args.length < 4 || !args[3].isFunction())
                throw new JsError("cm.registerFull(isim, açıklama, kullanım, function) şeklinde kullanın");
            registerCommand(args[0].asString(), args[1].asString(), args[2].asString(), (JSFunction) args[3]);
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
            logger.warning("[NanoScript] Event bulunamadı: '" + eventName + "'"
                + " — 'PlayerJoinEvent' formatında yaz veya tam paket adını ver");
            return;
        }

        DynamicListener dl = new DynamicListener(callback, eventName, engine, logger);

        Bukkit.getPluginManager().registerEvent(
            eventClass, dl, priority,
            (listener, event) -> {
                if (eventClass.isInstance(event))
                    ((DynamicListener) listener).handleEvent(event);
            },
            plugin, false
        );

        instance.addEventListenerRef(dl);
        logger.fine("[NanoScript] Event kaydedildi: " + eventName + " [" + priority + "] (" + instance.getFileName() + ")");
    }

    private void registerCommand(String name, String description, String usage, JSFunction callback) {
        String lower = name.toLowerCase();
        DynamicCommand cmd = new DynamicCommand(lower, description, usage, callback, engine, logger);
        CommandMapUtil.register(plugin, cmd);
        instance.addCommandRef(lower);
        logger.fine("[NanoScript] Komut kaydedildi: /" + lower + " (" + instance.getFileName() + ")");
    }

    // ──────────────────────────────────────────────────────────────────
    //  Yardımcılar
    // ──────────────────────────────────────────────────────────────────

    private void safeCall(JSFunction callback) {
        try {
            engine.call(callback, new JSValue[]{});
        } catch (JsError e) {
            logger.warning("[NanoScript] Callback hatası (" + instance.getFileName() + "): " + e.getMessage());
        } catch (Exception e) {
            logger.warning("[NanoScript] Beklenmedik hata (" + instance.getFileName() + "): " + e.getMessage());
        }
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

        if (name.contains(".")) {
            String[] parts = name.split("\\.", 2);
            for (String pkg : EVENT_PACKAGES) {
                if (pkg.contains(parts[0] + ".")) {
                    try { return (Class<? extends Event>) Class.forName(pkg + parts[1]); }
                    catch (ClassNotFoundException ignored) {}
                }
            }
        }
        return null;
    }
}