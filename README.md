# 🟢 NanoScript

**Paper sunucuları için sıfır bağımlılıklı JavaScript scripting motoru.**  
Tek bir `.js` dosyasıyla komut, event ve zamanlı görev yaz — plugin derleme yok, restart gerekmez.

```
/ns load all        → tüm scriptleri yükle
/ns reload all      → tüm scriptleri yeniden yükle
/ns unload heal.js  → tek bir scripti kaldır
/ns list            → yüklü scriptleri ve kaynak sayılarını listele
```

---

## 📋 İçindekiler

- [Kurulum](#-kurulum)
- [Hızlı Başlangıç](#-hızlı-başlangıç)
- [Script Yapısı](#-script-yapısı)
- [Sunucu API](#-sunucu-api)
  - [Oyuncu](#oyuncu)
  - [Mesajlaşma](#mesajlaşma)
  - [Dünya & Blok](#dünya--blok)
  - [Item & Envanter](#item--envanter)
  - [Efektler](#efektler)
  - [Ses & Parçacık](#ses--parçacık)
  - [Scoreboard](#scoreboard)
  - [Zamanlayıcı](#zamanlayıcı)
- [Event Sistemi](#-event-sistemi)
- [Komut Sistemi](#-komut-sistemi)
- [Kalıcı Depolama](#-kalıcı-depolama)
- [Tam Örnekler](#-tam-örnekler)
  - [Ekonomi Sistemi](#1-ekonomi-sistemi)
  - [Admin Komutları](#2-admin-komutları)
  - [AFK Sistemi](#3-afk-sistemi)
  - [Özel Chat Formatı](#4-özel-chat-formatı)
  - [Korunan Bölge](#5-korunan-bölge)
- [JavaScript Desteklenen Özellikler](#-javascript-desteklenen-özellikler)
- [Hata Ayıklama](#-hata-ayıklama)
- [İzinler](#-i̇zinler)

---

## 📦 Kurulum

1. `NanoScript.jar` dosyasını `plugins/` klasörüne koy
2. Sunucuyu başlat — `plugins/NanoScript/scripts/` klasörü otomatik oluşur
3. Script dosyalarını bu klasöre at
4. `/ns load all` ile yükle

**Gereksinimler:** Paper 1.20+ · Java 17+

---

## 🚀 Hızlı Başlangıç

`plugins/NanoScript/scripts/hello.js` oluştur:

```js
var server = getServer();
var cm = server.getCommandManager();
var em = server.getEventManager();

// Basit komut
cm.register("merhaba", function(sender, args) {
    sender.sendMessage(color("&aHello World!"));
});

// Basit event
em.on("PlayerJoinEvent", function(e) {
    var player = e.getPlayer();
    server.broadcast(color("&e" + player.getName() + " &7katıldı!"));
});
```

```
/ns load hello.js
```

---

## 📐 Script Yapısı

Her script şu üç nesneyle başlar:

```js
var server = getServer();          // Tüm API buradan erişilir
var em     = server.getEventManager();    // Event kayıt sistemi
var cm     = server.getCommandManager();  // Komut kayıt sistemi
var db     = server.getStorage();         // Kalıcı veri depolama
```

`color()` global olarak her yerde kullanılabilir:

```js
color("&aYeşil &cKırmızı &e&lKalın Sarı")
```

---

## 🔧 Sunucu API

### Oyuncu

```js
// Oyuncu al
var p = server.getPlayer("Steve");          // null döner bulamazsa
var players = server.getOnlinePlayers();    // JSArray — tüm online oyuncular

// Can & Yemek
server.heal(player);                        // canı maks yap
server.feed(player);                        // yemeği 20 yap
server.setHealth(player, 10);              // belirli can
server.setFood(player, 15);
server.setMaxHealth(player, 40);

// Mod & Hız
server.setGameMode(player, "CREATIVE");     // SURVIVAL, CREATIVE, ADVENTURE, SPECTATOR
server.getGameMode(player);                 // → "SURVIVAL"
server.setFly(player, true);
server.canFly(player);                      // → true/false

// Seviye & XP
server.setLevel(player, 10);
server.getLevel(player);
server.addExp(player, 500);

// Bilgi
server.getPing(player);                     // ms
server.getUUID(player);
server.getIPAddress(player);
server.isOp(player);
server.setOp(player, true);
server.hasPermission(player, "my.perm");
server.isBanned("Steve");
server.ban(player, "Sebep");
server.unban("Steve");
server.kick(player, "Sebep");

// Konum
server.getLocation(player);                // Location nesnesi
server.teleport(player, location);
server.teleportToSpawn(player);
server.teleportXYZ(player, "world", 100, 64, 200);
server.locationInfo(location);             // → {x, y, z, yaw, pitch, world}
```

### Mesajlaşma

```js
server.sendMessage(player, "&aYeşil mesaj");
server.broadcast("&eHerkese mesaj");
server.sendTitle(player, "&6Başlık", "&7Alt başlık");
server.sendTitle(player, "&6Başlık", "&7Alt", fadeIn, stay, fadeOut); // tick değerleri
server.sendActionBar(player, "&c❤ Düşük can!");
server.sendTabList(player, "&6Üst metin", "&7Alt metin");
server.log("Konsola log");
```

### Dünya & Blok

```js
var world = server.getDefaultWorld();
var world = server.getWorld("world_nether");

server.setTime(world, 1000);               // 1000=gündüz, 13000=gece
server.setWeather(world, "CLEAR");         // CLEAR, RAIN, THUNDER
server.getHighestY(world, x, z);
server.getSpawnLocation(world);
server.setSpawnLocation(world, x, y, z);

var block = server.getBlock(world, x, y, z);
server.setBlock(world, x, y, z, "STONE");

var loc = server.createLocation(world, x, y, z);
var loc = server.createLocation(world, x, y, z, yaw, pitch);

server.spawnEntity(loc, "ZOMBIE");
server.createExplosion(loc, 4.0, false);
server.strikeLightning(loc, false);        // true = sadece efekt, zarar yok
```

### Item & Envanter

```js
var item = server.createItem("DIAMOND_SWORD");
var item = server.createItem("APPLE", 32);
var item = server.createNamedItem("DIAMOND", "&bÖzel Elmas", 1, "&7Nadir item");

server.giveItem(player, item);
server.clearInventory(player);
server.getItemInHand(player);              // null döner eğer eli boşsa
server.setItemInHand(player, item);
server.hasItem(player, "DIAMOND");
server.getMaterial(block);                 // → "STONE"
server.getMaterial(item);                  // → "APPLE"
```

### Efektler

```js
server.addEffect(player, "SPEED", 200, 1);      // tip, tick, güç
server.addEffect(player, "REGENERATION", 100, 0);
server.removeEffect(player, "SPEED");
server.clearEffects(player);
server.hasEffect(player, "STRENGTH");

// Geçerli efekt isimleri:
// SPEED, SLOWNESS, HASTE, MINING_FATIGUE, STRENGTH, INSTANT_HEALTH,
// INSTANT_DAMAGE, JUMP_BOOST, NAUSEA, REGENERATION, RESISTANCE,
// FIRE_RESISTANCE, WATER_BREATHING, INVISIBILITY, BLINDNESS,
// NIGHT_VISION, HUNGER, WEAKNESS, POISON, WITHER, HEALTH_BOOST,
// ABSORPTION, SATURATION, GLOWING, LEVITATION, LUCK, UNLUCK
```

### Ses & Parçacık

```js
server.playSound(player, "ENTITY_PLAYER_LEVELUP", 1.0, 1.0);  // ses, volume, pitch
server.playSoundAt(location, "ENTITY_GENERIC_EXPLODE", 1.0, 1.0);
server.spawnParticle(location, "HEART", 10);
server.spawnParticle(location, "FLAME", 20);
```

### Scoreboard

```js
var sb = server.createSidebar("&6&lSunucu");
sb.setLine(10, "&eOnline: 5");
sb.setLine(9, "&aTPS: 20.0");
sb.setLine(8, "");
sb.setLine(7, "&7v1.0.0");
sb.setTitle("&6&lYeni Başlık");
sb.show(player);
sb.hide(player);
sb.clear();
```

### Zamanlayıcı

```js
// Tek seferlik gecikme (ticks, 20 tick = 1 saniye)
server.schedule(40, function() {
    server.broadcast("&a2 saniye geçti!");
});

// Periyodik görev
var taskId = server.repeat(20 * 5, function() {
    // Her 5 saniyede çalışır
});

// Async periyodik görev (IO işlemleri için)
server.repeatAsync(20 * 60, function() {
    // Her dakika, async thread'de çalışır
});

// Görevi iptal et
server.cancel(taskId);

// Main thread'de çalıştır (async callback'ten)
server.runSync(function() {
    server.broadcast("Ana thread'de!");
});
```

---

## 📡 Event Sistemi

```js
em.on("EventAdı", function(e) {
    // handler
});

// Öncelik ile kayıt
em.onPriority("PlayerMoveEvent", "HIGHEST", function(e) {
    // LOWEST, LOW, NORMAL, HIGH, HIGHEST, MONITOR
});
```

### Sık Kullanılan Eventler

| Event | Önemli Metodlar |
|---|---|
| `PlayerJoinEvent` | `e.getPlayer()`, `e.setJoinMessage(msg)` |
| `PlayerQuitEvent` | `e.getPlayer()`, `e.setQuitMessage(msg)` |
| `AsyncPlayerChatEvent` | `e.getPlayer()`, `e.getMessage()`, `e.setFormat(fmt)`, `e.setCancelled(true)` |
| `PlayerMoveEvent` | `e.getPlayer()`, `e.getFrom()`, `e.getTo()` |
| `PlayerDeathEvent` | `e.getEntity()`, `e.setDeathMessage(msg)`, `e.setCancelled(true)` |
| `PlayerRespawnEvent` | `e.getPlayer()`, `e.getRespawnLocation()` |
| `BlockBreakEvent` | `e.getPlayer()`, `e.getBlock()`, `e.setCancelled(true)` |
| `BlockPlaceEvent` | `e.getPlayer()`, `e.getBlock()`, `e.setCancelled(true)` |
| `EntityDamageByEntityEvent` | `e.getEntity()`, `e.getDamager()`, `e.getFinalDamage()`, `e.setCancelled(true)` |
| `PlayerInteractEvent` | `e.getPlayer()`, `e.getAction()`, `e.getClickedBlock()` |
| `PlayerCommandPreprocessEvent` | `e.getPlayer()`, `e.getMessage()`, `e.setCancelled(true)` |
| `PlayerGameModeChangeEvent` | `e.getPlayer()`, `e.getNewGameMode()` |
| `PlayerTeleportEvent` | `e.getPlayer()`, `e.getFrom()`, `e.getTo()`, `e.getCause()` |
| `WeatherChangeEvent` | `e.getWorld()`, `e.toWeatherState()` |
| `EntitySpawnEvent` | `e.getEntity()`, `e.getLocation()`, `e.setCancelled(true)` |
| `InventoryClickEvent` | `e.getWhoClicked()`, `e.getInventory()`, `e.setCancelled(true)` |
| `PlayerDropItemEvent` | `e.getPlayer()`, `e.getItemDrop()`, `e.setCancelled(true)` |
| `FoodLevelChangeEvent` | `e.getEntity()`, `e.getFoodLevel()`, `e.setCancelled(true)` |

---

## ⌨️ Komut Sistemi

### Basit Komut

```js
cm.register("komut", function(sender, args) {
    // args → JSArray
    // sender → CommandSender (Player veya ConsoleCommandSender)
    sender.sendMessage(color("&aMerhaba!"));
});
```

### Açıklamalı Komut

```js
cm.registerFull("komut", "Açıklama", "/komut <arg>", function(sender, args) {
    if (args.length < 1) {
        sender.sendMessage(color("&cKullanım: /komut <arg>"));
        return;
    }
    sender.sendMessage(color("&aArg: &e" + args[0]));
});
```

### Tab Complete ile Komut

```js
cm.registerWithTab(
    "komut", "Açıklama", "/komut <oyuncu>",
    function(sender, args) {
        // execute fonksiyonu
        var target = server.getPlayer(args[0]);
        if (!target) { sender.sendMessage(color("&cOyuncu bulunamadı!")); return; }
        server.heal(target);
    },
    function(sender, args) {
        // tab complete fonksiyonu — string array dön
        if (args.length <= 1) {
            // Online oyuncu adlarını filtrele
            var names = [];
            var players = server.getOnlinePlayers();
            for (var i = 0; i < players.length; i++) {
                names.push(players[i].getName());
            }
            return names;
        }
        return [];
    }
);
```

### Kısa Tab Complete Formu

```js
cm.registerTab("komut",
    function(sender, args) { /* execute */ },
    function(sender, args) { return ["seçenek1", "seçenek2"]; }
);
```

> **Not:** `sender.getName` kontrolü ile konsol/oyuncu ayrımı yapılır:
> ```js
> if (!sender.getName) { sender.sendMessage("Sadece oyuncular!"); return; }
> var playerName = sender.getName();
> ```

---

## 💾 Kalıcı Depolama

Her script kendi izole JSON dosyasına veri yazar: `plugins/NanoScript/data/<scriptAdı>.json`

Reload, unload, sunucu restart → veriler korunur.

```js
var db = server.getStorage();

// ── Temel işlemler ─────────────────────────────────────────────
db.set("anahtar", 1500);
db.get("anahtar");             // → 1500
db.get("anahtar", 0);          // → varsayılan değer (key yoksa)
db.has("anahtar");             // → true/false
db.delete("anahtar");

// ── Noktalı yol (iç içe key) ───────────────────────────────────
// "bakiye.Steve" → JSON: { "bakiye": { "Steve": 1500 } }
db.set("bakiye.Steve", 1500);
db.get("bakiye.Steve", 0);
db.delete("bakiye.Steve");

// ── Sayısal işlemler ───────────────────────────────────────────
db.increment("bakiye.Steve", 500);   // +500 ekle, yeni değeri döner
db.decrement("bakiye.Steve", 100);   // -100 çıkar

// ── Nesne kaydetme ─────────────────────────────────────────────
db.setObj("oyuncu.Steve", { skor: 10, rozet: "vip" });
var obj = db.getObj("oyuncu.Steve");  // → JSObject

// ── Array işlemleri ────────────────────────────────────────────
db.push("log.girisler", "Steve 12:30 bağlandı");
var arr = db.getArray("log.girisler");  // → JSArray
for (var i = 0; i < arr.length; i++) {
    server.log(arr[i]);
}

// ── Key listesi ────────────────────────────────────────────────
var keys = db.keys("bakiye");  // "bakiye" altındaki tüm keyler → JSArray
for (var i = 0; i < keys.length; i++) {
    var isim = keys[i];
    server.log(isim + ": " + db.get("bakiye." + isim));
}

// ── Diğer ──────────────────────────────────────────────────────
db.getOrSet("ilk_giris.Steve", server.now());  // varsa al, yoksa kaydet+dön
db.save();                                      // zorla diske yaz
db.clear();                                     // tüm veriyi sil
db.debug();                                     // tüm veriyi JSON string olarak döner
```

---

## 📚 Tam Örnekler

### 1. Ekonomi Sistemi

`plugins/NanoScript/scripts/economy.js`

```js
var server = getServer();
var db = server.getStorage();
var cm = server.getCommandManager();
var em = server.getEventManager();

var PARA_BIRIMI = "₺";
var BASLANGIC   = 1000;

// ── API ────────────────────────────────────────────────────────
function bakiyeAl(isim) {
    return db.get("bal." + isim, BASLANGIC);
}

function bakiyeAyarla(isim, miktar) {
    db.set("bal." + isim, Math.max(0, miktar));
}

function paraVer(isim, miktar) {
    if (miktar <= 0) return false;
    db.increment("bal." + isim, miktar);
    return true;
}

function paraAl(isim, miktar) {
    if (miktar <= 0) return false;
    if (bakiyeAl(isim) < miktar) return false;
    db.decrement("bal." + isim, miktar);
    return true;
}

function transfer(kimden, kime, miktar) {
    if (!paraAl(kimden, miktar)) return false;
    paraVer(kime, miktar);
    return true;
}

// ── İlk giriş ─────────────────────────────────────────────────
em.on("PlayerJoinEvent", function(e) {
    var isim = e.getPlayer().getName();
    if (!db.has("bal." + isim)) {
        db.set("bal." + isim, BASLANGIC);
        server.schedule(40, function() {
            server.sendMessage(e.getPlayer(),
                color("&a[Ekonomi] Hoş geldin! Bakiyen: &e" + PARA_BIRIMI + BASLANGIC)
            );
        });
    }
});

// ── /bakiye [oyuncu] ───────────────────────────────────────────
cm.registerWithTab("bakiye", "Bakiyeni gör", "/bakiye [oyuncu]",
    function(sender, args) {
        var isim = args.length > 0 ? args[0] : (sender.getName ? sender.getName() : null);
        if (!isim) { sender.sendMessage(color("&cKullanım: /bakiye [oyuncu]")); return; }
        sender.sendMessage(color("&e" + isim + " &7bakiyesi: &a" + PARA_BIRIMI + bakiyeAl(isim)));
    },
    function(sender, args) {
        if (args.length <= 1) {
            var names = [];
            var pl = server.getOnlinePlayers();
            for (var i = 0; i < pl.length; i++) names.push(pl[i].getName());
            return names;
        }
        return [];
    }
);

// ── /pay <oyuncu> <miktar> ─────────────────────────────────────
cm.registerFull("pay", "Para gönder", "/pay <oyuncu> <miktar>",
    function(sender, args) {
        if (!sender.getName) { sender.sendMessage("Sadece oyuncular!"); return; }
        if (args.length < 2) { sender.sendMessage(color("&cKullanım: /pay <oyuncu> <miktar>")); return; }

        var kimden = sender.getName();
        var kime   = args[0];
        var miktar = parseFloat(args[1]);

        if (isNaN(miktar) || miktar <= 0) { sender.sendMessage(color("&cGeçersiz miktar!")); return; }
        if (kimden === kime) { sender.sendMessage(color("&cKendinize para gönderemezsiniz!")); return; }

        if (!transfer(kimden, kime, miktar)) {
            sender.sendMessage(color("&cYetersiz bakiye! (&e" + PARA_BIRIMI + bakiyeAl(kimden) + "&c)"));
            return;
        }

        sender.sendMessage(color("&a" + kime + " kişisine &e" + PARA_BIRIMI + miktar + " &agönderildi."));
        var hedef = server.getPlayer(kime);
        if (hedef) server.sendMessage(hedef, color("&a[+] &e" + kimden + " &asana &e" + PARA_BIRIMI + miktar + " &agönderdi!"));
    }
);

// ── /zenginler ─────────────────────────────────────────────────
cm.registerFull("zenginler", "En zengin 10 oyuncu", "/zenginler",
    function(sender, args) {
        var keys = db.keys("bal");
        var liste = [];
        for (var i = 0; i < keys.length; i++) {
            var isim = keys[i];
            liste.push({ isim: isim, bakiye: bakiyeAl(isim) });
        }
        liste.sort(function(a, b) { return b.bakiye - a.bakiye; });

        sender.sendMessage(color("&6&l=== Zenginler Listesi ==="));
        var madalya = ["&6#1", "&7#2", "&c#3"];
        for (var i = 0; i < Math.min(10, liste.length); i++) {
            var m = i < 3 ? madalya[i] : ("&7#" + (i+1));
            sender.sendMessage(color(m + " &f" + liste[i].isim + " &8— &e" + PARA_BIRIMI + liste[i].bakiye));
        }
    }
);
```

---

### 2. Admin Komutları

```js
var server = getServer();
var cm = server.getCommandManager();

// ── /heal [oyuncu] ─────────────────────────────────────────────
cm.registerWithTab("heal", "İyileştir", "/heal [oyuncu]",
    function(sender, args) {
        var target = args.length > 0 ? server.getPlayer(args[0]) : sender;
        if (!target) { sender.sendMessage(color("&cOyuncu bulunamadı!")); return; }
        server.heal(target);
        server.feed(target);
        server.sendMessage(target, color("&a❤ Tamamen iyileştirildiniz!"));
        server.playSound(target, "ENTITY_PLAYER_LEVELUP", 1.0, 1.5);
        if (target !== sender)
            sender.sendMessage(color("&a" + target.getName() + " iyileştirildi."));
    },
    function(sender, args) {
        if (args.length <= 1) {
            var names = [];
            var pl = server.getOnlinePlayers();
            for (var i = 0; i < pl.length; i++) names.push(pl[i].getName());
            return names;
        }
        return [];
    }
);

// ── /gm <mod> [oyuncu] ─────────────────────────────────────────
var GM_MAP = {
    "0":"SURVIVAL","s":"SURVIVAL","survival":"SURVIVAL",
    "1":"CREATIVE","c":"CREATIVE","creative":"CREATIVE",
    "2":"ADVENTURE","a":"ADVENTURE","adventure":"ADVENTURE",
    "3":"SPECTATOR","sp":"SPECTATOR","spectator":"SPECTATOR"
};

cm.registerWithTab("gm", "Gamemode", "/gm <mod> [oyuncu]",
    function(sender, args) {
        if (args.length < 1) { sender.sendMessage(color("&cKullanım: /gm <0-3|survival|creative>")); return; }
        var mod = GM_MAP[args[0].toLowerCase()];
        if (!mod) { sender.sendMessage(color("&cGeçersiz mod!")); return; }
        var target = args.length > 1 ? server.getPlayer(args[1]) : sender;
        if (!target) { sender.sendMessage(color("&cOyuncu bulunamadı!")); return; }
        server.setGameMode(target, mod);
        server.sendMessage(target, color("&7Mod: &e" + mod));
        if (target !== sender) sender.sendMessage(color("&a" + target.getName() + " → " + mod));
    },
    function(sender, args) {
        if (args.length <= 1) return ["survival","creative","adventure","spectator","0","1","2","3"];
        if (args.length === 2) {
            var names = [];
            var pl = server.getOnlinePlayers();
            for (var i = 0; i < pl.length; i++) names.push(pl[i].getName());
            return names;
        }
        return [];
    }
);
```

---

### 3. AFK Sistemi

```js
var server = getServer();
var em = server.getEventManager();

var AFK_SURE_MS  = 5 * 60 * 1000;  // 5 dakika
var sonHareket   = {};              // { isim: timestamp }
var afkDurumu    = {};              // { isim: true/false }

em.on("PlayerJoinEvent",  function(e) { sonHareket[e.getPlayer().getName()] = server.now(); });
em.on("PlayerQuitEvent",  function(e) {
    var isim = e.getPlayer().getName();
    delete sonHareket[isim];
    delete afkDurumu[isim];
});

em.on("PlayerMoveEvent", function(e) {
    var from = e.getFrom(), to = e.getTo();
    if (!to) return;
    if (from.getBlockX() === to.getBlockX() &&
        from.getBlockY() === to.getBlockY() &&
        from.getBlockZ() === to.getBlockZ()) return;

    var isim = e.getPlayer().getName();
    var simdiydi = afkDurumu[isim];
    sonHareket[isim] = server.now();
    afkDurumu[isim] = false;

    if (simdiydi) {
        server.broadcast(color("&e" + isim + " &7AFK'dan döndü."));
    }
});

// Her 30 saniyede AFK kontrol
server.repeat(20 * 30, function() {
    var su_an = server.now();
    var oyuncular = server.getOnlinePlayers();
    for (var i = 0; i < oyuncular.length; i++) {
        var isim = oyuncular[i].getName();
        var son  = sonHareket[isim] || su_an;
        var afk  = (su_an - son) > AFK_SURE_MS;

        if (afk && !afkDurumu[isim]) {
            afkDurumu[isim] = true;
            server.broadcast(color("&e" + isim + " &7AFK'ya geçti."));
            server.sendActionBar(oyuncular[i], color("&7AFK modundasınız."));
        }
    }
});
```

---

### 4. Özel Chat Formatı

```js
var server = getServer();
var em = server.getEventManager();
var db = server.getStorage();

var COOLDOWN    = {};
var COOLDOWN_MS = 2000;

function getRank(player) {
    if (server.isOp(player))                          return color("&4[OWNER] ");
    if (server.hasPermission(player, "rank.admin"))   return color("&c[ADMIN] ");
    if (server.hasPermission(player, "rank.mod"))     return color("&9[MOD] ");
    if (server.hasPermission(player, "rank.vip"))     return color("&6[VIP] ");
    return color("&7[Oyuncu] ");
}

em.on("AsyncPlayerChatEvent", function(e) {
    var player = e.getPlayer();
    var isim   = player.getName();
    var mesaj  = e.getMessage();
    var su_an  = server.now();

    // Cooldown
    if (su_an - (COOLDOWN[isim] || 0) < COOLDOWN_MS) {
        e.setCancelled(true);
        server.schedule(1, function() {
            server.sendMessage(player, color("&cÇok hızlı yazıyorsun!"));
        });
        return;
    }
    COOLDOWN[isim] = su_an;

    // Format: [RANK] İsim » Mesaj
    var rank = getRank(player);
    e.setFormat(rank + color("&f" + isim + " &8» &7") + mesaj);
});
```

---

### 5. Korunan Bölge

```js
var server = getServer();
var em = server.getEventManager();

// Korunan bölge koordinatları
var BOLGELER = [
    { isim: "Spawn", dunya: "world", x1: -50, z1: -50, x2: 50, z2: 50 },
    { isim: "Market", dunya: "world", x1: 100, z1: 100, x2: 200, z2: 200 }
];

function bolgede(player, isim) {
    var loc = server.locationInfo(server.getLocation(player));
    if (!loc) return false;
    for (var i = 0; i < BOLGELER.length; i++) {
        var b = BOLGELER[i];
        if (b.isim !== isim) continue;
        if (loc.world !== b.dunya) continue;
        if (loc.x >= b.x1 && loc.x <= b.x2 && loc.z >= b.z1 && loc.z <= b.z2)
            return true;
    }
    return false;
}

function herhangi_bolgede(loc) {
    if (!loc) return null;
    for (var i = 0; i < BOLGELER.length; i++) {
        var b = BOLGELER[i];
        if (loc.world !== b.dunya) continue;
        if (loc.x >= b.x1 && loc.x <= b.x2 && loc.z >= b.z1 && loc.z <= b.z2)
            return b.isim;
    }
    return null;
}

// Blok kırmayı engelle
em.on("BlockBreakEvent", function(e) {
    var player = e.getPlayer();
    if (server.isOp(player)) return;
    if (bolgede(player, "Spawn") || bolgede(player, "Market")) {
        e.setCancelled(true);
        server.sendMessage(player, color("&cBu bölgede blok kıramazsın!"));
    }
});

// Blok koymayı engelle
em.on("BlockPlaceEvent", function(e) {
    var player = e.getPlayer();
    if (server.isOp(player)) return;
    if (bolgede(player, "Spawn") || bolgede(player, "Market")) {
        e.setCancelled(true);
        server.sendMessage(player, color("&cBu bölgede blok koyamazsın!"));
    }
});

// PvP'yi engelle
em.on("EntityDamageByEntityEvent", function(e) {
    var damaged = e.getEntity();
    var damager = e.getDamager();
    if (!damaged.getName || !damager.getName) return;

    var loc = server.locationInfo(server.getLocation(damaged));
    if (herhangi_bolgede(loc)) {
        e.setCancelled(true);
    }
});

// Bölgeye girince mesaj
em.on("PlayerMoveEvent", function(e) {
    var from = e.getFrom(), to = e.getTo();
    if (!to) return;
    if (from.getBlockX() === to.getBlockX() && from.getBlockZ() === to.getBlockZ()) return;

    var player = e.getPlayer();
    var fromInfo = server.locationInfo(from);
    var toInfo   = server.locationInfo(to);

    var fromBolge = herhangi_bolgede(fromInfo);
    var toBolge   = herhangi_bolgede(toInfo);

    if (fromBolge !== toBolge) {
        if (toBolge) {
            server.sendActionBar(player, color("&a⬤ &e" + toBolge + " &7bölgesine girdiniz."));
        } else if (fromBolge) {
            server.sendActionBar(player, color("&c⬤ &e" + fromBolge + " &7bölgesinden çıktınız."));
        }
    }
});
```

---

## 🧩 JavaScript Desteklenen Özellikler

| Özellik | Durum |
|---|---|
| `var`, `let`, `const` | ✅ |
| Arrow fonksiyonlar `(a) => a + 1` | ✅ |
| Template literal `` `Hello ${name}` `` | ✅ |
| `if/else`, `switch/case` | ✅ |
| `for`, `for...in`, `for...of` | ✅ |
| `while`, `do...while` | ✅ |
| `try/catch/finally`, `throw` | ✅ |
| Fonksiyon hoisting | ✅ |
| Closure | ✅ |
| `this` bağlaması | ✅ |
| Spread operatörü `...` | ✅ |
| Ternary `a ? b : c` | ✅ |
| Nullish coalescing `??` | ✅ |
| Opsiyonel zincirleme `?.` | ❌ |
| `class` sözdizimi | ❌ |
| `import/export` | ❌ |
| `async/await` | ❌ |
| **Math** — tüm metodlar | ✅ |
| **JSON** — `stringify/parse` (basit) | ✅ |
| **Array** — `map, filter, find, sort, forEach, ...` | ✅ |
| **String** — `split, replace, trim, includes, ...` | ✅ |
| **Number** — `toFixed, toString, toPrecision` | ✅ |
| **Object** — `keys, values, entries, assign` | ✅ |
| **Date** — temel metodlar | ✅ |
| `parseInt`, `parseFloat`, `isNaN`, `isFinite` | ✅ |
| `console.log` | ✅ (sunucu konsolu) |

---

## 🐛 Hata Ayıklama

Script hataları konsola detaylı olarak loglanır:

```
[NanoScript] economy.js hata: Satır 42: 'undefined' üzerinde 'getName' erişilemiyor
[NanoScript] PlayerJoinEvent handler hatası: ...
[NanoScript] /bakiye hatası: ...
```

**Yaygın hatalar:**

```js
// ❌ Java Collection üzerinde .length() çağrısı
col.length()   →   col.length

// ❌ JSArray üzerinde .get(i) çağrısı
arr.get(0)     →   arr[0]

// ❌ server.getOnlinePlayers() üzerinde iterator
var iter = col.iterator(); while(iter.hasNext()) ...
// ✅ Doğrusu:
for (var i = 0; i < col.length; i++) { var p = col[i]; }

// ❌ toFixed sayısal değerin doğrudan kullanımı
20.toFixed(1)         // parse hatası
// ✅ Doğrusu:
server.getTPS().toFixed(1)   // JSValue üzerinde çalışır
```

**Storage debug:**

```js
var db = server.getStorage();
server.log(db.debug());  // tüm JSON verisini konsola yazar
```

---

## 🔐 İzinler

| İzin | Açıklama | Varsayılan |
|---|---|---|
| `nanoscript.admin` | `/ns` komutuna tam erişim | OP |

Script içinde özel izin kontrolü:

```js
if (!server.isOp(sender) && !server.hasPermission(sender, "benim.iznim")) {
    sender.sendMessage(color("&cBu komutu kullanma iznin yok!"));
    return;
}
```

---

## 📁 Klasör Yapısı

```
plugins/
└── NanoScript/
    ├── scripts/
    │   ├── example.js       ← örnek script (ilk kurulumda otomatik gelir)
    │   ├── economy.js       ← örnek script (ilk kurulumda otomatik gelir)
    │   └── benim_scriptim.js
    └── data/
        ├── economy.json     ← economy.js verileri (otomatik oluşur)
        └── benim_scriptim.json
```

---

<p align="center">
  <b>NanoScript</b> · Paper 1.20+ · Java 17+
</p>
