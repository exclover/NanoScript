/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║           NanoScript — Gelişmiş Örnek Script                ║
 * ║           plugins/NanoScript/scripts/example.js             ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  Komutlar (hepsi tab complete destekli):                    ║
 * ║    /heal [oyuncu]                                           ║
 * ║    /feed [oyuncu]                                           ║
 * ║    /fly [oyuncu]                                            ║
 * ║    /gm <0|1|2|3|survival|creative|adventure|spectator>      ║
 * ║    /top                                                     ║
 * ║    /god [oyuncu]                                            ║
 * ║    /speed <0-10> [oyuncu]                                   ║
 * ║    /effect <tip> <süre> [güç] [oyuncu]                     ║
 * ║    /item <materyal> [miktar]                                ║
 * ║    /clear [oyuncu]                                          ║
 * ║    /spawn [oyuncu]                                          ║
 * ║    /tphere <oyuncu>                                         ║
 * ║    /tpto <oyuncu>                                           ║
 * ║    /back                                                    ║
 * ║    /ping [oyuncu]                                           ║
 * ║    /whois <oyuncu>                                          ║
 * ║    /kick <oyuncu> [sebep]                                   ║
 * ║    /broadcast <mesaj>                                       ║
 * ║    /time <day|night|noon|midnight|<tick>>                   ║
 * ║    /weather <sun|rain|thunder>                              ║
 * ║    /sudo <oyuncu> <komut>                                   ║
 * ║    /invsee <oyuncu>                                         ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  Eventler:                                                  ║
 * ║    PlayerJoinEvent, PlayerQuitEvent                         ║
 * ║    AsyncPlayerChatEvent                                     ║
 * ║    PlayerDeathEvent, PlayerRespawnEvent                     ║
 * ║    PlayerMoveEvent (AFK sistemi)                            ║
 * ║    BlockBreakEvent, BlockPlaceEvent                         ║
 * ║    EntityDamageByEntityEvent (PvP log)                      ║
 * ║    PlayerInteractEvent                                      ║
 * ║    InventoryClickEvent                                      ║
 * ║    PlayerDropItemEvent                                      ║
 * ║    FoodLevelChangeEvent                                     ║
 * ║    PlayerGameModeChangeEvent                                ║
 * ║    PlayerTeleportEvent                                      ║
 * ║    PlayerKickEvent                                          ║
 * ║    PlayerCommandPreprocessEvent (komut log)                 ║
 * ║    WeatherChangeEvent                                       ║
 * ║    EntitySpawnEvent                                         ║
 * ║    ChunkLoadEvent                                           ║
 * ╚══════════════════════════════════════════════════════════════╝
 */

var server = getServer();
var em = server.getEventManager();
var cm = server.getCommandManager();
var db = server.getStorage();

// ================================================================
//  GLOBAL DURUM
// ================================================================

var godPlayers   = {};   // { isim: true }  — god mode
var afkPlayers   = {};   // { isim: lastMoveTime }
var lastLocation = {};   // { isim: location }  — /back için
var joinTimes    = {};   // { isim: timestamp }  — oyun süresi

var AFK_TIMEOUT_MS = 5 * 60 * 1000;   // 5 dakika
var CHAT_COOLDOWN  = {};               // { isim: lastMsgTime }
var CHAT_COOLDOWN_MS = 1500;           // 1.5 sn cooldown

// ================================================================
//  YARDIMCI FONKSİYONLAR
// ================================================================

function onlinePlayers() {
    var col = server.getOnlinePlayers();
    var arr = [];
    for (var i = 0; i < col.length; i++) arr.push(col[i]);
    return arr;
}

function onlineNames() {
    return onlinePlayers().map(function(p) { return p.getName(); });
}

// Oyuncu adlarını tab complete için filtrele
function filterNames(prefix) {
    prefix = (prefix || "").toLowerCase();
    return onlineNames().filter(function(n) {
        return n.toLowerCase().indexOf(prefix) === 0;
    });
}

function getPlayerByName(name) {
    return server.getPlayer(name);
}

function resolveTarget(sender, args, index) {
    if (args.length > index) {
        var p = getPlayerByName(args[index]);
        if (!p) {
            sender.sendMessage(color("&cOyuncu bulunamadı: &e" + args[index]));
            return null;
        }
        return p;
    }
    if (sender.getName) return sender;
    sender.sendMessage(color("&cKullanım: Oyuncu adı belirt."));
    return null;
}

function isConsole(sender) {
    return !sender.getName;
}

function formatTime(ms) {
    var s = Math.floor(ms / 1000);
    var m = Math.floor(s / 60);
    var h = Math.floor(m / 60);
    s = s % 60; m = m % 60;
    if (h > 0)  return h + "s " + m + "d " + s + "sn";
    if (m > 0)  return m + "d " + s + "sn";
    return s + " saniye";
}

// ================================================================
//  EVENTLER
// ================================================================

// ── Join ──────────────────────────────────────────────────────────
em.on("PlayerJoinEvent", function(e) {
    var player = e.getPlayer();
    var name   = player.getName();

    // Katılım mesajını özelleştir
    e.setJoinMessage(color("&8[&a+&8] &e" + name + " &7sunucuya katıldı."));

    joinTimes[name] = server.now();
    afkPlayers[name] = server.now();

    // İlk kez bağlananlar
    if (!server.hasPlayedBefore(player)) {
        server.broadcast(color("&6★ &e" + name + " &6sunucuya ilk kez katıldı! Hoş geldin!"));
        server.schedule(40, function() {
            server.sendTitle(player,
                color("&6&lHoş Geldin!"),
                color("&e" + name + " &7- Sunucuya bağlandın"),
                10, 80, 20
            );
            server.playSound(player, "ENTITY_PLAYER_LEVELUP", 1.0, 0.8);
        });
    } else {
        // Normal giriş title
        server.schedule(20, function() {
            server.sendTitle(player,
                color("&aTekrar Hoş Geldin"),
                color("&7" + name + " — &eSunucuda " + server.onlineCount() + " oyuncu var"),
                10, 60, 15
            );
        });
    }

    // Tab listesi güncelle
    server.sendTabList(player,
        color("&6&lSunucu &8| &eTPS: &a" + server.getTPS().toFixed(1)),
        color("&7Online: &a" + server.onlineCount() + "/" + server.getMaxPlayers())
    );

    server.log(name + " bağlandı. Online: " + server.onlineCount());
});

// ── Quit ─────────────────────────────────────────────────────────
em.on("PlayerQuitEvent", function(e) {
    var player = e.getPlayer();
    var name   = player.getName();

    e.setQuitMessage(color("&8[&c-&8] &e" + name + " &7ayrıldı."));

    // Oyun süresini kaydet
    if (joinTimes[name]) {
        var elapsed = server.now() - joinTimes[name];
        var prev    = db.get("playtime." + name, 0);
        db.set("playtime." + name, prev + elapsed);
        delete joinTimes[name];
    }

    // Temizlik
    delete afkPlayers[name];
    delete godPlayers[name];
    delete CHAT_COOLDOWN[name];
    delete lastLocation[name];
});

// ── Chat ─────────────────────────────────────────────────────────
em.on("AsyncPlayerChatEvent", function(e) {
    var player = e.getPlayer();
    var name   = player.getName();
    var msg    = e.getMessage();

    // Cooldown kontrolü
    var now  = server.now();
    var last = CHAT_COOLDOWN[name] || 0;
    if (now - last < CHAT_COOLDOWN_MS) {
        e.setCancelled(true);
        server.schedule(1, function() {
            server.sendMessage(player, color("&cÇok hızlı yazıyorsun! Biraz bekle."));
        });
        return;
    }
    CHAT_COOLDOWN[name] = now;

    // Yasaklı kelime filtresi (isteğe göre genişlet)
    var banned = db.getArray("banned_words");
    for (var i = 0; i < banned.length; i++) {
        var word = banned.get(i).toLowerCase();
        if (msg.toLowerCase().indexOf(word) !== -1) {
            e.setCancelled(true);
            server.schedule(1, function() {
                server.sendMessage(player, color("&cMesajın uygunsuz içerik barındırıyor!"));
            });
            server.log("[CHAT-FILTER] " + name + ": " + msg);
            return;
        }
    }

    // AFK'dan çıkar
    if (afkPlayers[name] !== undefined) {
        var wasAfk = (now - afkPlayers[name]) > AFK_TIMEOUT_MS;
        afkPlayers[name] = now;
        if (wasAfk) {
            server.broadcast(color("&e" + name + " &7artık AFK değil."));
        }
    }

    // Chat formatı: [Rank] İsim: Mesaj
    var rank = getRank(player);
    e.setFormat(rank + color(" &f" + name + " &8» &7" + msg));
});

// ── Ölüm & Respawn ────────────────────────────────────────────────
em.on("PlayerDeathEvent", function(e) {
    var player = e.getEntity();
    var name   = player.getName();

    // God modda ölmez
    if (godPlayers[name]) {
        e.setCancelled(true);
        player.setHealth(player.getMaxHealth ? player.getMaxHealth() : 20);
        return;
    }

    // Ölüm mesajını özelleştir
    var cause = e.getDeathMessage() || (name + " öldü.");
    e.setDeathMessage(color("&c☠ " + cause));

    // Son konumu kaydet (respawn için)
    var loc = server.getLocation(player);
    if (loc) lastLocation[name] = loc;

    // Ölüm sayısını artır
    db.increment("deaths." + name, 1);

    server.log(name + " öldü. Toplam: " + db.get("deaths." + name, 0));
});

em.on("PlayerRespawnEvent", function(e) {
    var player = e.getPlayer();
    var name   = player.getName();

    server.schedule(5, function() {
        server.sendTitle(player,
            color("&c&lÖLDÜN"),
            color("&7Respawn ettiniz."),
            5, 40, 10
        );
        server.playSound(player, "ENTITY_WITHER_SPAWN", 0.5, 2.0);
    });
});

// ── Hareket (AFK sistemi) ─────────────────────────────────────────
em.on("PlayerMoveEvent", function(e) {
    var player = e.getPlayer();
    var name   = player.getName();
    var now    = server.now();

    // Sadece blok değişimlerinde kontrol et (her frame değil)
    var from = e.getFrom();
    var to   = e.getTo();
    if (!to) return;

    // Blok değiştiyse hareket sayıldı
    if (from.getBlockX() === to.getBlockX() &&
        from.getBlockY() === to.getBlockY() &&
        from.getBlockZ() === to.getBlockZ()) return;

    var prevTime = afkPlayers[name] || now;
    var wasAfk   = (prevTime > 0) && (now - prevTime) > AFK_TIMEOUT_MS;

    afkPlayers[name] = now;

    if (wasAfk) {
        server.broadcast(color("&e" + name + " &7AFK'dan döndü."));
    }
});

// ── Blok Kırma ───────────────────────────────────────────────────
em.on("BlockBreakEvent", function(e) {
    var player = e.getPlayer();
    var block  = e.getBlock();
    var mat    = block.getType().name();

    // Korunan bloklar
    var protectedBlocks = ["SPAWNER", "COMMAND_BLOCK", "BEDROCK", "CHAIN_COMMAND_BLOCK", "REPEATING_COMMAND_BLOCK"];
    if (protectedBlocks.indexOf(mat) !== -1 && !server.isOp(player)) {
        e.setCancelled(true);
        server.sendMessage(player, color("&cBu bloğu kıramazsın! (&e" + mat + "&c)"));
        server.playSound(player, "BLOCK_NOTE_BLOCK_BASS", 1.0, 0.5);
        return;
    }

    // Kırılan blok istatistiği
    db.increment("blocks_broken." + player.getName(), 1);
});

// ── Blok Yerleştirme ─────────────────────────────────────────────
em.on("BlockPlaceEvent", function(e) {
    var player = e.getPlayer();
    db.increment("blocks_placed." + player.getName(), 1);
});

// ── PvP Log ───────────────────────────────────────────────────────
em.on("EntityDamageByEntityEvent", function(e) {
    var damaged  = e.getEntity();
    var damager  = e.getDamager();

    // Sadece oyuncu → oyuncu hasarı
    if (!damaged.getName || !damager.getName) return;

    var damagedName = damaged.getName();
    var damagerName = damager.getName();
    var dmg = e.getFinalDamage().toFixed(1);

    // God mode
    if (godPlayers[damagedName]) {
        e.setCancelled(true);
        return;
    }

    // PvP log (debug için)
    server.log("[PvP] " + damagerName + " → " + damagedName + " (" + dmg + " hasar)");

    // Düşük can uyarısı
    var curHp = damaged.getHealth ? damaged.getHealth() - e.getFinalDamage() : 0;
    if (curHp > 0 && curHp <= 4) {
        server.schedule(1, function() {
            server.sendActionBar(damaged, color("&c❤ &lDÜŞÜK CAN! &c❤"));
        });
    }
});

// ── Oyuncu Etkileşimi ────────────────────────────────────────────
em.on("PlayerInteractEvent", function(e) {
    var player = e.getPlayer();
    var action = e.getAction().name();

    // Sağ tık yere = sadece log (isteğe göre özelleştir)
    if (action === "RIGHT_CLICK_BLOCK") {
        // Örnek: belirli bir blokla etkileşimde özel menü
        var block = e.getClickedBlock();
        if (block && block.getType().name() === "ENCHANTING_TABLE") {
            // Özel büyü masası davranışı ekleyebilirsiniz
        }
    }
});

// ── Envanter Tıklama ─────────────────────────────────────────────
em.on("InventoryClickEvent", function(e) {
    var player = e.getWhoClicked();
    if (!player || !player.getName) return;

    // Örnek: belirli envanter tipinde tıklamayı engelle
    var invType = e.getInventory().getType().name();
    if (invType === "BEACON") {
        // Beacon'a özel item koyulmasını engelle (örnek)
        // e.setCancelled(true);
    }
});

// ── Item Bırakma ─────────────────────────────────────────────────
em.on("PlayerDropItemEvent", function(e) {
    var player = e.getPlayer();
    // Örnek: belirli itemlerin bırakılmasını engelle
    var item = e.getItemDrop().getItemStack();
    if (item && item.getType().name() === "NETHER_STAR") {
        e.setCancelled(true);
        server.sendMessage(player, color("&cBu itemi bırakamazsın!"));
    }
});

// ── Açlık Değişimi ───────────────────────────────────────────────
em.on("FoodLevelChangeEvent", function(e) {
    var entity = e.getEntity();
    if (!entity || !entity.getName) return;
    var name = entity.getName();

    // Creative/Spectator modda açlık azalmasın
    var gm = server.getGameMode(entity);
    if (gm === "CREATIVE" || gm === "SPECTATOR") {
        e.setCancelled(true);
    }
});

// ── Gamemode Değişimi ────────────────────────────────────────────
em.on("PlayerGameModeChangeEvent", function(e) {
    var player  = e.getPlayer();
    var newMode = e.getNewGameMode().name();
    var name    = player.getName();

    server.log("[GM] " + name + " → " + newMode);

    server.schedule(2, function() {
        server.sendActionBar(player, color("&7Gamemode: &e" + newMode));
    });
});

// ── Teleport ─────────────────────────────────────────────────────
em.on("PlayerTeleportEvent", function(e) {
    var player = e.getPlayer();
    var from   = e.getFrom();
    var cause  = e.getCause().name();

    // /back için önceki konumu kaydet (COMMAND ile ışınlanmalar hariç tutulabilir)
    if (cause !== "COMMAND" && from) {
        lastLocation[player.getName()] = from;
    }
});

// ── Kick ─────────────────────────────────────────────────────────
em.on("PlayerKickEvent", function(e) {
    var player = e.getPlayer();
    var reason = e.getReason();
    server.log("[KICK] " + player.getName() + " atıldı: " + reason);
});

// ── Komut Önişleme (Komut Logu) ───────────────────────────────────
em.on("PlayerCommandPreprocessEvent", function(e) {
    var player = e.getPlayer();
    var cmd    = e.getMessage();

    // Sadece admin/OP komutlarını logla
    var sensitiveCommands = ["/op ", "/deop ", "/ban ", "/kick ", "/stop", "/reload"];
    for (var i = 0; i < sensitiveCommands.length; i++) {
        if (cmd.toLowerCase().indexOf(sensitiveCommands[i]) === 0) {
            server.log("[CMD-LOG] " + player.getName() + ": " + cmd);
            break;
        }
    }
});

// ── Hava Değişimi ────────────────────────────────────────────────
em.on("WeatherChangeEvent", function(e) {
    var world   = e.getWorld();
    var toStorm = e.toWeatherState();
    var msg     = toStorm
        ? color("&9☁ &7Hava &9kötüleşiyor&7...")
        : color("&e☀ &7Hava &eaçılıyor&7!");
    server.broadcast(msg);
});

// ── Mob Spawn ────────────────────────────────────────────────────
em.on("EntitySpawnEvent", function(e) {
    var entity = e.getEntity();
    var type   = entity.getType().name();

    // Örnek: Wither spawn olunca herkese bildir
    if (type === "WITHER") {
        server.broadcast(color("&4&l⚠ WITHER ÇAĞRILDI! Hazır olun! ⚠"));
    }
    // Örnek: Ender Dragon spawn olunca
    if (type === "ENDER_DRAGON") {
        server.broadcast(color("&5&l⚠ ENDER DRAGON UYANDI! ⚠"));
    }
});

// ================================================================
//  KOMUTLAR (Tab Complete ile)
// ================================================================

// ── /heal [oyuncu] ───────────────────────────────────────────────
cm.registerWithTab("heal",
    "Oyuncuyu iyileştir", "/heal [oyuncu]",
    function(sender, args) {
        var target = resolveTarget(sender, args, 0);
        if (!target) return;
        server.heal(target);
        server.feed(target);
        server.sendMessage(target, color("&a❤ Tamamen iyileştirildiniz!"));
        server.playSound(target, "ENTITY_PLAYER_LEVELUP", 1.0, 1.5);
        server.spawnParticle(server.getLocation(target), "HEART", 20);
        if (target !== sender) sender.sendMessage(color("&a" + server.getName(target) + " iyileştirildi."));
    },
    function(sender, args) {
        if (args.length <= 1) return filterNames(args.length > 0 ? args[0] : "");
        return [];
    }
);

// ── /feed [oyuncu] ───────────────────────────────────────────────
cm.registerWithTab("feed",
    "Oyuncuyu doyur", "/feed [oyuncu]",
    function(sender, args) {
        var target = resolveTarget(sender, args, 0);
        if (!target) return;
        server.feed(target);
        server.sendMessage(target, color("&a🍖 Doyuruldunuz!"));
        if (target !== sender) sender.sendMessage(color("&a" + server.getName(target) + " doyuruldu."));
    },
    function(sender, args) {
        return filterNames(args.length > 0 ? args[0] : "");
    }
);

// ── /fly [oyuncu] ────────────────────────────────────────────────
cm.registerWithTab("fly",
    "Uçma modunu aç/kapat", "/fly [oyuncu]",
    function(sender, args) {
        var target = resolveTarget(sender, args, 0);
        if (!target) return;
        var canFly = !server.canFly(target);
        server.setFly(target, canFly);
        var msg = canFly ? color("&a🕊 Uçma modu &lAÇIK") : color("&c🕊 Uçma modu &lKAPALI");
        server.sendMessage(target, msg);
        server.playSound(target, "ENTITY_BAT_TAKEOFF", 1.0, 1.0);
        if (target !== sender) sender.sendMessage(color("&a" + server.getName(target) + " → uçma: " + (canFly ? "açık" : "kapalı")));
    },
    function(sender, args) {
        return filterNames(args.length > 0 ? args[0] : "");
    }
);

// ── /gm <mod> [oyuncu] ───────────────────────────────────────────
var GM_MODES = ["survival", "creative", "adventure", "spectator", "0", "1", "2", "3"];
var GM_MAP = {
    "0": "SURVIVAL",   "survival": "SURVIVAL",   "s": "SURVIVAL",
    "1": "CREATIVE",   "creative": "CREATIVE",   "c": "CREATIVE",
    "2": "ADVENTURE",  "adventure": "ADVENTURE", "a": "ADVENTURE",
    "3": "SPECTATOR",  "spectator": "SPECTATOR", "sp": "SPECTATOR"
};

cm.registerWithTab("gm",
    "Gamemode değiştir", "/gm <mod> [oyuncu]",
    function(sender, args) {
        if (args.length < 1) {
            sender.sendMessage(color("&cKullanım: /gm <survival|creative|adventure|spectator|0|1|2|3> [oyuncu]"));
            return;
        }
        var mode = GM_MAP[args[0].toLowerCase()];
        if (!mode) {
            sender.sendMessage(color("&cGeçersiz mod! Geçerliler: survival, creative, adventure, spectator"));
            return;
        }
        var target = resolveTarget(sender, args, 1);
        if (!target) return;
        server.setGameMode(target, mode);
        server.sendMessage(target, color("&7Gamemode: &e" + mode));
        server.sendActionBar(target, color("&a✔ " + mode));
        if (target !== sender) sender.sendMessage(color("&a" + server.getName(target) + " → " + mode));
    },
    function(sender, args) {
        if (args.length <= 1) return GM_MODES.filter(function(m) { return m.indexOf((args[0] || "").toLowerCase()) === 0; });
        if (args.length === 2) return filterNames(args[1]);
        return [];
    }
);

// ── /top ─────────────────────────────────────────────────────────
cm.registerWithTab("top",
    "En üst noktaya ışınlan", "/top",
    function(sender, args) {
        if (isConsole(sender)) { sender.sendMessage("Sadece oyuncular kullanabilir."); return; }
        var loc  = server.getLocation(sender);
        var world = sender.getWorld ? sender.getWorld() : null;
        if (!loc || !world) return;
        var highY = server.getHighestY(world, loc.getBlockX(), loc.getBlockZ()) + 1;
        server.teleportXYZ(sender, world.getName(), loc.getX(), highY, loc.getZ());
        server.sendMessage(sender, color("&a⬆ En üst noktaya ışınlandınız! &7(Y: " + highY + ")"));
        server.spawnParticle(server.getLocation(sender), "CLOUD", 10);
    },
    function(sender, args) { return []; }
);

// ── /god [oyuncu] ────────────────────────────────────────────────
cm.registerWithTab("god",
    "God modunu aç/kapat", "/god [oyuncu]",
    function(sender, args) {
        var target = resolveTarget(sender, args, 0);
        if (!target) return;
        var name = server.getName(target);
        godPlayers[name] = !godPlayers[name];
        var on = godPlayers[name];
        server.sendMessage(target, on
            ? color("&6⚡ God modu &lAÇIK &6— Ölümsüzsün!")
            : color("&7⚡ God modu &lKAPALI"));
        server.playSound(target,
            on ? "ENTITY_WITHER_SPAWN" : "ENTITY_ITEM_BREAK",
            0.5, on ? 2.0 : 1.0
        );
        if (target !== sender) sender.sendMessage(color("&a" + name + " god: " + (on ? "açık" : "kapalı")));
    },
    function(sender, args) {
        return filterNames(args.length > 0 ? args[0] : "");
    }
);

// ── /speed <0-10> [oyuncu] ───────────────────────────────────────
var SPEED_VALUES = ["0","1","2","3","4","5","6","7","8","9","10"];
cm.registerWithTab("speed",
    "Hareket hızı ayarla", "/speed <0-10> [oyuncu]",
    function(sender, args) {
        if (args.length < 1) { sender.sendMessage(color("&cKullanım: /speed <0-10> [oyuncu]")); return; }
        var spd = parseFloat(args[0]);
        if (isNaN(spd) || spd < 0 || spd > 10) {
            sender.sendMessage(color("&cHız 0 ile 10 arasında olmalı!"));
            return;
        }
        var target = resolveTarget(sender, args, 1);
        if (!target) return;
        // Bukkit hız: 0.2 = normal, 0.2 * (spd/2) = istenen hız
        var walkSpeed = Math.max(0.01, Math.min(1.0, 0.1 * spd));
        if (target.setWalkSpeed) target.setWalkSpeed(walkSpeed);
        server.sendMessage(target, color("&a⚡ Hız: &e" + spd + "/10"));
        if (target !== sender) sender.sendMessage(color("&a" + server.getName(target) + " hızı: " + spd));
    },
    function(sender, args) {
        if (args.length <= 1) return SPEED_VALUES;
        if (args.length === 2) return filterNames(args[1]);
        return [];
    }
);

// ── /effect <tip> <süre> [güç] [oyuncu] ──────────────────────────
var EFFECT_TYPES = [
    "SPEED","SLOWNESS","HASTE","MINING_FATIGUE","STRENGTH","INSTANT_HEALTH",
    "INSTANT_DAMAGE","JUMP_BOOST","NAUSEA","REGENERATION","RESISTANCE",
    "FIRE_RESISTANCE","WATER_BREATHING","INVISIBILITY","BLINDNESS",
    "NIGHT_VISION","HUNGER","WEAKNESS","POISON","WITHER","HEALTH_BOOST",
    "ABSORPTION","SATURATION","GLOWING","LEVITATION","LUCK","UNLUCK"
];

cm.registerWithTab("effect",
    "Efekt ekle", "/effect <tip> <süre> [güç] [oyuncu]",
    function(sender, args) {
        if (args.length < 2) {
            sender.sendMessage(color("&cKullanım: /effect <tip> <süre_saniye> [güç=0] [oyuncu]"));
            return;
        }
        var effectType = args[0].toUpperCase();
        var duration   = parseInt(args[1]) * 20;  // saniye → tick
        var amplifier  = args.length > 2 && !getPlayerByName(args[2]) ? parseInt(args[2]) : 0;
        var target     = resolveTarget(sender, args, args.length > 2 ? 3 : 2);
        if (!target) target = isConsole(sender) ? null : sender;
        if (!target) return;

        server.addEffect(target, effectType, duration, amplifier);
        server.sendMessage(target, color("&a✦ Efekt eklendi: &e" + effectType + " &7(Güç: " + amplifier + ", Süre: " + (duration/20) + "s)"));
        if (target !== sender) sender.sendMessage(color("&a" + server.getName(target) + "'e efekt eklendi: " + effectType));
    },
    function(sender, args) {
        var len = args.length;
        if (len <= 1) {
            var prefix = args.length > 0 ? args[0].toUpperCase() : "";
            return EFFECT_TYPES.filter(function(t) { return t.indexOf(prefix) === 0; });
        }
        if (len === 2) return ["5","10","30","60","120","300"];
        if (len === 3) return ["0","1","2","3","4","5"];
        if (len === 4) return filterNames(args[3]);
        return [];
    }
);

// ── /item <materyal> [miktar] ────────────────────────────────────
var COMMON_MATERIALS = [
    "DIAMOND_SWORD","DIAMOND_PICKAXE","DIAMOND_AXE","DIAMOND_SHOVEL",
    "IRON_SWORD","IRON_PICKAXE","NETHERITE_SWORD","NETHERITE_PICKAXE",
    "BOW","CROSSBOW","TRIDENT","SHIELD",
    "DIAMOND","EMERALD","GOLD_INGOT","IRON_INGOT","NETHERITE_INGOT",
    "APPLE","GOLDEN_APPLE","ENCHANTED_GOLDEN_APPLE","BREAD","COOKED_BEEF",
    "ELYTRA","TOTEM_OF_UNDYING","NETHER_STAR",
    "STONE","DIRT","GRASS_BLOCK","OAK_LOG","OAK_PLANKS","COBBLESTONE",
    "GLASS","SAND","GRAVEL","OBSIDIAN","TNT","CHEST","FURNACE"
];

cm.registerWithTab("item",
    "Envantere item ekle", "/item <materyal> [miktar]",
    function(sender, args) {
        if (isConsole(sender)) { sender.sendMessage("Sadece oyuncular kullanabilir."); return; }
        if (args.length < 1) { sender.sendMessage(color("&cKullanım: /item <MATERYAL_ADI> [miktar]")); return; }
        var matName = args[0].toUpperCase();
        var amount  = args.length > 1 ? parseInt(args[1]) : 1;
        if (amount < 1 || amount > 64) amount = 1;
        var item = server.createItem(matName, amount);
        if (!item) {
            sender.sendMessage(color("&cGeçersiz materyal: &e" + matName));
            return;
        }
        server.giveItem(sender, item);
        sender.sendMessage(color("&a✦ Envantere eklendi: &e" + amount + "x " + matName));
    },
    function(sender, args) {
        if (args.length <= 1) {
            var prefix = args.length > 0 ? args[0].toUpperCase() : "";
            return COMMON_MATERIALS.filter(function(m) { return m.indexOf(prefix) === 0; });
        }
        if (args.length === 2) return ["1","8","16","32","64"];
        return [];
    }
);

// ── /clear [oyuncu] ──────────────────────────────────────────────
cm.registerWithTab("clear",
    "Envanteri temizle", "/clear [oyuncu]",
    function(sender, args) {
        var target = resolveTarget(sender, args, 0);
        if (!target) return;
        server.clearInventory(target);
        server.sendMessage(target, color("&aEnvanteriniz temizlendi!"));
        if (target !== sender) sender.sendMessage(color("&a" + server.getName(target) + " envanteri temizlendi."));
    },
    function(sender, args) {
        return filterNames(args.length > 0 ? args[0] : "");
    }
);

// ── /spawn [oyuncu] ──────────────────────────────────────────────
cm.registerWithTab("spawn",
    "Spawn'a ışınlan", "/spawn [oyuncu]",
    function(sender, args) {
        var target = resolveTarget(sender, args, 0);
        if (!target) return;
        server.teleportToSpawn(target);
        server.sendMessage(target, color("&aSpawn'a ışınlandınız!"));
        server.playSound(target, "ENTITY_ENDERMAN_TELEPORT", 1.0, 1.0);
        if (target !== sender) sender.sendMessage(color("&a" + server.getName(target) + " spawn'a ışınlandı."));
    },
    function(sender, args) {
        return filterNames(args.length > 0 ? args[0] : "");
    }
);

// ── /tphere <oyuncu> — Oyuncuyu sana ışınla ──────────────────────
cm.registerWithTab("tphere",
    "Oyuncuyu yanına çek", "/tphere <oyuncu>",
    function(sender, args) {
        if (isConsole(sender)) { sender.sendMessage("Sadece oyuncular kullanabilir."); return; }
        if (args.length < 1) { sender.sendMessage(color("&cKullanım: /tphere <oyuncu>")); return; }
        var target = getPlayerByName(args[0]);
        if (!target) { sender.sendMessage(color("&cOyuncu bulunamadı: &e" + args[0])); return; }
        var loc = server.getLocation(sender);
        server.teleport(target, loc);
        server.sendMessage(target, color("&a" + server.getName(sender) + " &7seni yanına ışınladı!"));
        sender.sendMessage(color("&a" + server.getName(target) + " yanına ışınlandı."));
        server.playSound(target, "ENTITY_ENDERMAN_TELEPORT", 1.0, 1.0);
    },
    function(sender, args) {
        return filterNames(args.length > 0 ? args[0] : "");
    }
);

// ── /tpto <oyuncu> — Oyuncuya ışınlan ────────────────────────────
cm.registerWithTab("tpto",
    "Oyuncuya ışınlan", "/tpto <oyuncu>",
    function(sender, args) {
        if (isConsole(sender)) { sender.sendMessage("Sadece oyuncular kullanabilir."); return; }
        if (args.length < 1) { sender.sendMessage(color("&cKullanım: /tpto <oyuncu>")); return; }
        var target = getPlayerByName(args[0]);
        if (!target) { sender.sendMessage(color("&cOyuncu bulunamadı: &e" + args[0])); return; }
        var fromLoc = server.getLocation(sender);
        if (fromLoc) lastLocation[sender.getName()] = fromLoc;
        var toLoc = server.getLocation(target);
        server.teleport(sender, toLoc);
        sender.sendMessage(color("&a" + server.getName(target) + " &7konumuna ışınlandın."));
        server.playSound(sender, "ENTITY_ENDERMAN_TELEPORT", 1.0, 1.0);
    },
    function(sender, args) {
        return filterNames(args.length > 0 ? args[0] : "");
    }
);

// ── /back — Son konuma dön ────────────────────────────────────────
cm.registerWithTab("back",
    "Son konuma geri dön", "/back",
    function(sender, args) {
        if (isConsole(sender)) { sender.sendMessage("Sadece oyuncular kullanabilir."); return; }
        var name = sender.getName();
        var loc  = lastLocation[name];
        if (!loc) {
            sender.sendMessage(color("&cGeri dönülecek konum yok!"));
            return;
        }
        var curLoc = server.getLocation(sender);
        lastLocation[name] = curLoc;
        server.teleport(sender, loc);
        sender.sendMessage(color("&aÖnceki konumuna döndün!"));
        server.playSound(sender, "ENTITY_ENDERMAN_TELEPORT", 0.8, 1.2);
    },
    function(sender, args) { return []; }
);

// ── /ping [oyuncu] ───────────────────────────────────────────────
cm.registerWithTab("ping",
    "Ping görüntüle", "/ping [oyuncu]",
    function(sender, args) {
        var target = resolveTarget(sender, args, 0);
        if (!target) return;
        var ping = server.getPing(target);
        var col  = ping < 80 ? "&a" : ping < 150 ? "&e" : "&c";
        sender.sendMessage(color(col + server.getName(target) + " &7ping: " + col + ping + "ms"));
    },
    function(sender, args) {
        return filterNames(args.length > 0 ? args[0] : "");
    }
);

// ── /whois <oyuncu> ───────────────────────────────────────────────
cm.registerWithTab("whois",
    "Oyuncu bilgileri", "/whois <oyuncu>",
    function(sender, args) {
        var target = resolveTarget(sender, args, 0);
        if (!target) return;
        var name = server.getName(target);
        var loc  = server.locationInfo(server.getLocation(target));
        var locStr = loc
            ? ("&7(" + Math.round(loc.x) + ", " + Math.round(loc.y) + ", " + Math.round(loc.z) + ")")
            : "&7?";

        sender.sendMessage(color("&8&m──────────────────────────"));
        sender.sendMessage(color("  &6&l" + name + " &7hakkında"));
        sender.sendMessage(color("  &7UUID: &f"    + server.getUUID(target)));
        sender.sendMessage(color("  &7IP: &f"      + (server.getIPAddress(target) || "?")));
        sender.sendMessage(color("  &7Ping: &f"    + server.getPing(target) + "ms"));
        sender.sendMessage(color("  &7Mod: &e"     + (server.getGameMode(target) || "?")));
        sender.sendMessage(color("  &7Can: &c"     + server.getHealth(target).toFixed(1)));
        sender.sendMessage(color("  &7Yemek: &6"   + server.getFood(target)));
        sender.sendMessage(color("  &7Seviye: &a"  + server.getLevel(target)));
        sender.sendMessage(color("  &7Konum: "     + locStr));
        sender.sendMessage(color("  &7Ölümler: &c" + db.get("deaths." + name, 0)));
        sender.sendMessage(color("  &7Kırılan: &b" + db.get("blocks_broken." + name, 0) + " blok"));
        sender.sendMessage(color("  &7God: "       + (godPlayers[name] ? "&aAçık" : "&cKapalı")));
        sender.sendMessage(color("  &7OP: "        + (server.isOp(target) ? "&aEvet" : "&cHayır")));
        sender.sendMessage(color("&8&m──────────────────────────"));
    },
    function(sender, args) {
        return filterNames(args.length > 0 ? args[0] : "");
    }
);

// ── /kick <oyuncu> [sebep] ───────────────────────────────────────
cm.registerWithTab("kick",
    "Oyuncuyu at", "/kick <oyuncu> [sebep]",
    function(sender, args) {
        if (args.length < 1) { sender.sendMessage(color("&cKullanım: /kick <oyuncu> [sebep]")); return; }
        var target = getPlayerByName(args[0]);
        if (!target) { sender.sendMessage(color("&cOyuncu bulunamadı: &e" + args[0])); return; }
        var reason = args.length > 1 ? args[1] : "Sunucudan atıldınız.";
        server.kick(target, color("&c" + reason));
        server.broadcast(color("&e" + server.getName(target) + " &catıldı. &7(" + reason + ")"));
        server.log("[KICK] " + (sender.getName ? sender.getName() : "CONSOLE") + " → " + server.getName(target) + ": " + reason);
    },
    function(sender, args) {
        if (args.length <= 1) return filterNames(args.length > 0 ? args[0] : "");
        if (args.length === 2) return ["Kurallara aykırı davranış", "AFK", "Spam", "Geçici ban"];
        return [];
    }
);

// ── /broadcast <mesaj> ───────────────────────────────────────────
cm.registerWithTab("broadcast",
    "Herkese mesaj gönder", "/broadcast <mesaj>",
    function(sender, args) {
        if (args.length < 1) { sender.sendMessage(color("&cKullanım: /broadcast <mesaj>")); return; }
        var msg = [];
        for (var i = 0; i < args.length; i++) msg.push(args[i]);
        server.broadcast(color("&6&l[Duyuru] &e" + msg.join(" ")));
        server.log("[BROADCAST] " + msg.join(" "));
    },
    function(sender, args) { return []; }
);

// ── /time <gün|gece|öğle|gece yarısı|<tick>> ─────────────────────
var TIME_OPTIONS = ["day","night","noon","midnight","0","6000","12000","18000"];
cm.registerWithTab("time",
    "Dünya zamanını ayarla", "/time <day|night|noon|midnight|tick>",
    function(sender, args) {
        if (args.length < 1) { sender.sendMessage(color("&cKullanım: /time <day|night|noon|midnight|<tick>>")); return; }
        var world = server.getDefaultWorld();
        var timeMap = { "day": 1000, "gün": 1000, "noon": 6000, "öğle": 6000, "night": 13000, "gece": 13000, "midnight": 18000 };
        var input  = args[0].toLowerCase();
        var ticks  = timeMap[input] !== undefined ? timeMap[input] : parseInt(input);
        if (isNaN(ticks)) { sender.sendMessage(color("&cGeçersiz zaman! Kullanım: day, night, noon, midnight veya tick sayısı")); return; }
        server.setTime(world, ticks);
        server.broadcast(color("&e☀ Saat değiştirildi: &a" + args[0]));
    },
    function(sender, args) {
        if (args.length <= 1) return TIME_OPTIONS;
        return [];
    }
);

// ── /weather <sun|rain|thunder> ──────────────────────────────────
var WEATHER_OPTIONS = ["sun","rain","thunder","clear","storm"];
cm.registerWithTab("weather",
    "Havayı değiştir", "/weather <sun|rain|thunder>",
    function(sender, args) {
        if (args.length < 1) { sender.sendMessage(color("&cKullanım: /weather <sun|rain|thunder>")); return; }
        var world = server.getDefaultWorld();
        var wMap  = { "sun": "CLEAR", "clear": "CLEAR", "temiz": "CLEAR", "rain": "RAIN", "yağmur": "RAIN", "storm": "RAIN", "thunder": "THUNDER", "fırtına": "THUNDER" };
        var w = wMap[args[0].toLowerCase()];
        if (!w) { sender.sendMessage(color("&cGeçersiz hava! sun, rain veya thunder kullan.")); return; }
        server.setWeather(world, w);
        var icons = { "CLEAR": "☀", "RAIN": "🌧", "THUNDER": "⚡" };
        server.broadcast(color("&9" + (icons[w] || "") + " Hava: &e" + w));
    },
    function(sender, args) {
        if (args.length <= 1) return WEATHER_OPTIONS;
        return [];
    }
);

// ── /sudo <oyuncu> <komut> ───────────────────────────────────────
cm.registerWithTab("sudo",
    "Oyuncu adına komut çalıştır (admin)", "/sudo <oyuncu> <komut>",
    function(sender, args) {
        if (args.length < 2) { sender.sendMessage(color("&cKullanım: /sudo <oyuncu> <komut>")); return; }
        var target = getPlayerByName(args[0]);
        if (!target) { sender.sendMessage(color("&cOyuncu bulunamadı: &e" + args[0])); return; }
        var cmdParts = [];
        for (var i = 1; i < args.length; i++) cmdParts.push(args[i]);
        var cmd = cmdParts.join(" ");
        server.dispatchCommand(target, cmd);
        sender.sendMessage(color("&a" + server.getName(target) + " adına çalıştırıldı: &e/" + cmd));
        server.log("[SUDO] " + (sender.getName ? sender.getName() : "CONSOLE") + " → " + server.getName(target) + ": /" + cmd);
    },
    function(sender, args) {
        if (args.length <= 1) return filterNames(args.length > 0 ? args[0] : "");
        return [];
    }
);

// ── /invsee <oyuncu> ─────────────────────────────────────────────
cm.registerWithTab("invsee",
    "Oyuncunun envanterini gör", "/invsee <oyuncu>",
    function(sender, args) {
        if (isConsole(sender)) { sender.sendMessage("Sadece oyuncular kullanabilir."); return; }
        if (args.length < 1) { sender.sendMessage(color("&cKullanım: /invsee <oyuncu>")); return; }
        var target = getPlayerByName(args[0]);
        if (!target) { sender.sendMessage(color("&cOyuncu bulunamadı: &e" + args[0])); return; }
        // Paper API: openInventory
        if (sender.openInventory) {
            sender.openInventory(target.getInventory());
        } else {
            sender.sendMessage(color("&cBu özellik bu sürümde desteklenmiyor."));
        }
    },
    function(sender, args) {
        return filterNames(args.length > 0 ? args[0] : "");
    }
);

// ================================================================
//  PERİYODİK GÖREVLER
// ================================================================

// AFK Kontrolü (her 30 saniyede)
server.repeat(20 * 30, function() {
    var now = server.now();
    var players = onlinePlayers();
    for (var i = 0; i < players.length; i++) {
        var p    = players[i];
        var name = p.getName();
        var last = afkPlayers[name] || now;
        if (now - last > AFK_TIMEOUT_MS) {
            // AFK olarak işaretle — sadece ilk geçişte bildir
            var wasNotAfk = db.get("afk." + name, false) === false;
            if (wasNotAfk) {
                db.set("afk." + name, true);
                server.broadcast(color("&e" + name + " &7AFK'ya geçti."));
                server.sendActionBar(p, color("&7AFK modundasınız. Hareket edin."));
            }
        } else {
            db.set("afk." + name, false);
        }
    }
});

// Tab listesi güncelle (her 5 saniyede)
server.repeat(20 * 5, function() {
    var players = onlinePlayers();
    var tps = server.getTPS().toFixed(1);
    for (var i = 0; i < players.length; i++) {
        server.sendTabList(players[i],
            color("&6&l Sunucu &8| &7TPS: &a" + tps),
            color("&7Online: &a" + players.length + "&7/&a" + server.getMaxPlayers())
        );
    }
});

// ================================================================
//  YARDIMCI: Rank sistemi
// ================================================================

function getRank(player) {
    if (server.isOp(player))                            return color("&4[OWNER]");
    if (server.hasPermission(player, "rank.admin"))     return color("&c[ADMIN]");
    if (server.hasPermission(player, "rank.mod"))       return color("&9[MOD]");
    if (server.hasPermission(player, "rank.vip"))       return color("&6[VIP]");
    if (server.hasPermission(player, "rank.donator"))   return color("&d[DONATOR]");
    return color("&7[Oyuncu]");
}

server.log("example.js yüklendi! " +
    "Komutlar: /heal /feed /fly /gm /god /speed /effect /item /clear /spawn " +
    "/tphere /tpto /back /ping /whois /kick /broadcast /time /weather /sudo /invsee");