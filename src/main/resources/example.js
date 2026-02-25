/**
 * NanoScript — Gelişmiş Örnek Script (example.js)
 * ================================================
 * Yükle   : /ns load example.js
 * Kaldır  : /ns unload example.js
 * Yenile  : /ns reload example.js
 */

const server = getServer();
const em     = server.getEventManager();
const cm     = server.getCommandManager();

// ═══════════════════════════════════════════════════════════════
//  YARDIMCI FONKSİYONLAR
// ═══════════════════════════════════════════════════════════════

function msg(player, text) {
    player.sendMessage(color("&8[&bNano&3Script&8] &r" + text));
}

function duyur(text) {
    server.broadcast("&8[&6Duyuru&8] &r" + text);
}

function pad(n) {
    return (n < 10 ? "0" : "") + n;
}

// Saat bilgisi — server.now() ile milisaniyeden hesapla
function simdi() {
    var ms  = server.now();          // System.currentTimeMillis()
    var sec = Math.floor(ms / 1000) % 60;
    var min = Math.floor(ms / 60000) % 60;
    var hr  = Math.floor(ms / 3600000) % 24;
    return pad(hr) + ":" + pad(min) + ":" + pad(sec);
}

// JSArray args için güvenli join (String.join yerine)
function joinArgs(args, sep) {
    var result = "";
    for (var i = 0; i < args.length; i++) {
        if (i > 0) result += sep;
        result += args[i];
    }
    return result;
}

// Renkli can barı
function canRengi(player) {
    var hp    = player.getHealth();
    var maxHp = player.getMaxHealth();
    var pct   = (hp / maxHp) * 100;
    var renk  = pct > 75 ? "&a" : (pct > 40 ? "&e" : "&c");
    var bar   = "";
    var dolu  = Math.round(pct / 10);
    for (var i = 0; i < 10; i++) {
        bar += (i < dolu ? renk : "&8") + "█";
    }
    return bar + "&r " + renk + Math.round(hp) + "&7/&f" + Math.round(maxHp);
}

// ═══════════════════════════════════════════════════════════════
//  1. OYUNCU KATILMA
// ═══════════════════════════════════════════════════════════════

em.on("PlayerJoinEvent", function(event) {
    var player = event.getPlayer();
    var name   = player.getName();
    var online = server.onlineCount();

    event.setJoinMessage(color("&8[&a+&8] &f" + name));

    player.sendMessage(color("&m                                        "));
    player.sendMessage(color("  &6&lHoş Geldin&r&6, &e&l" + name + "&r&6!"));
    player.sendMessage(color("  &7Sunucuda şu an &f" + online + " &7oyuncu bulunuyor."));
    player.sendMessage(color("  &7Yardım için &f/yardim &7komutunu kullan."));
    player.sendMessage(color("&m                                        "));
});

// ═══════════════════════════════════════════════════════════════
//  2. OYUNCU AYRILMA
// ═══════════════════════════════════════════════════════════════

em.on("PlayerQuitEvent", function(event) {
    var player = event.getPlayer();
    event.setQuitMessage(color("&8[&c-&8] &f" + player.getName() + " &7ayrıldı."));
});

// ═══════════════════════════════════════════════════════════════
//  3. CHAT FORMATI + SPAM KORUMASI
// ═══════════════════════════════════════════════════════════════

var sonMesaj = {};
var sonZaman = {};
var SPAM_MS  = 1500;

em.on("AsyncPlayerChatEvent", function(event) {
    var player  = event.getPlayer();
    var name    = player.getName();
    var mesaj   = event.getMessage();
    var simdiMs = server.now();

    if (sonMesaj[name] === mesaj && (simdiMs - (sonZaman[name] || 0)) < SPAM_MS) {
        event.setCancelled(true);
        server.runSync(function() {
            msg(player, "&cLütfen aynı mesajı tekrar gönderme!");
        });
        return;
    }

    sonMesaj[name] = mesaj;
    sonZaman[name] = simdiMs;

    event.setFormat(color("&7[&f" + name + "&7] &r") + mesaj);
});

// ═══════════════════════════════════════════════════════════════
//  4. OYUNCU ÖLÜM MESAJI
// ═══════════════════════════════════════════════════════════════

em.on("PlayerDeathEvent", function(event) {
    var player = event.getEntity();
    event.setDeathMessage(color("&8[&4✝&8] &f" + player.getName() + " &7hayatını kaybetti."));
});

// ═══════════════════════════════════════════════════════════════
//  5. OYUNCU YENİDEN DOĞMA
// ═══════════════════════════════════════════════════════════════

em.on("PlayerRespawnEvent", function(event) {
    var player = event.getPlayer();
    server.schedule(10, function() {
        msg(player, "&aYeniden doğdun! &7Can: &a❤ " + Math.round(player.getHealth()));
    });
});

// ═══════════════════════════════════════════════════════════════
//  6. /hp KOMUTU
// ═══════════════════════════════════════════════════════════════

cm.registerFull("hp", "Can bilgisi gösterir", "/hp [oyuncu]", function(sender, args) {
    var hedef;

    if (args.length > 0) {
        hedef = server.getPlayer(args[0]);
        if (hedef === null) {
            msg(sender, "&c'" + args[0] + "' adlı oyuncu çevrimiçi değil.");
            return;
        }
    } else {
        try {
            hedef = sender.getPlayer();
            if (hedef === null) {
                sender.sendMessage("[NanoScript] Konsol için: /hp <oyuncu>");
                return;
            }
        } catch(err) {
            sender.sendMessage("[NanoScript] Konsol için: /hp <oyuncu>");
            return;
        }
    }

    msg(sender, "&f" + hedef.getName() + " &7— " + canRengi(hedef));
});

// ═══════════════════════════════════════════════════════════════
//  7. /spawn KOMUTU
// ═══════════════════════════════════════════════════════════════

cm.registerFull("spawn", "Spawn noktasına ışınlar", "/spawn", function(sender, args) {
    // server.teleportToSpawn() — ScriptAPI'deki güvenli wrapper kullanılır
    var ok = server.teleportToSpawn(sender);
    if (ok) {
        msg(sender, "&aSpawn noktasına ışınlandın!");
    } else {
        sender.sendMessage("[NanoScript] Bu komut sadece oyuncular için.");
    }
});

// ═══════════════════════════════════════════════════════════════
//  8. /duyur KOMUTU
// ═══════════════════════════════════════════════════════════════

cm.registerFull("duyur", "Sunucuya duyuru gönderir", "/duyur <mesaj>", function(sender, args) {
    if (!sender.isOp()) {
        msg(sender, "&cBu komutu kullanmak için yetkin yok!");
        return;
    }
    if (args.length === 0) {
        msg(sender, "&eKullanım: &f/duyur <mesaj>");
        return;
    }

    var metin = joinArgs(args, " ");
    server.broadcast("&6&l[DUYURU] &r&e" + metin);
    server.log("Duyuru (" + sender.getName() + "): " + metin);
});

// ═══════════════════════════════════════════════════════════════
//  9. /cevrimici KOMUTU
// ═══════════════════════════════════════════════════════════════

cm.registerFull("cevrimici", "Online oyuncuları listeler", "/cevrimici", function(sender, args) {
    var oyuncular = server.getOnlinePlayers();
    var sayi      = server.onlineCount();

    if (sayi === 0) {
        msg(sender, "&7Şu an çevrimiçi oyuncu yok.");
        return;
    }

    var isimler = "";
    for (var i = 0; i < oyuncular.length; i++) {
        if (i > 0) isimler += "&7, ";
        isimler += "&f" + oyuncular[i].getName();
    }

    msg(sender, "&7Çevrimiçi &f(" + sayi + ")&7: " + isimler);
});

// ═══════════════════════════════════════════════════════════════
//  10. /yardim KOMUTU
// ═══════════════════════════════════════════════════════════════

cm.registerFull("yardim", "Komut listesini gösterir", "/yardim", function(sender, args) {
    sender.sendMessage(color("&m                                        "));
    sender.sendMessage(color("  &6&lKomut Listesi"));
    sender.sendMessage(color("  &f/hp &7[oyuncu]   &8— &eCan bilgisi"));
    sender.sendMessage(color("  &f/spawn          &8— &eSpawn'a ışınlan"));
    sender.sendMessage(color("  &f/cevrimici      &8— &eOnline oyuncular"));
    sender.sendMessage(color("  &f/duyur &7<mesaj> &8— &eDuyuru gönder &7(op)"));
    sender.sendMessage(color("&m                                        "));
});

// ═══════════════════════════════════════════════════════════════
//  11. OTOMATİK DUYURU (her 5 dakikada bir)
// ═══════════════════════════════════════════════════════════════

var duyurular = [
    "&eSunucumuzda eğlenceli vakit geçir!",
    "&eSpawn'a gitmek için &f/spawn &7komutunu kullan!",
    "&eCan bilgisini görmek için &f/hp &7komutunu dene.",
    "&eOnline arkadaşlarını görmek için &f/cevrimici &7yaz!",
    "&eBir sorun mu var? Bir op'a bildirmeyi unutma."
];
var duyuruIndex = 0;

server.repeat(6000, function() {
    var online = server.onlineCount();
    if (online === 0) return;
    duyur(duyurular[duyuruIndex] + " &7Online: &f" + online);
    duyuruIndex = (duyuruIndex + 1) % duyurular.length;
});

// ═══════════════════════════════════════════════════════════════
//  12. PERİYODİK SAĞLIK LOGU (her 10 dakikada bir)
// ═══════════════════════════════════════════════════════════════

server.repeat(12000, function() {
    server.log("Sağlık logu [" + simdi() + "] — Çevrimiçi: " + server.onlineCount() + " oyuncu");
});

// ═══════════════════════════════════════════════════════════════
//  YÜKLEME TAMAMLANDI
// ═══════════════════════════════════════════════════════════════

server.log("example.js yüklendi! [" + simdi() + "]");