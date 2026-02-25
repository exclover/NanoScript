/**
 * NanoScript Ekonomi Scripti — Kalıcı Versiyon
 * Dosya: plugins/NanoScript/scripts/economy.js
 *
 * ✅ Reload, sunucu restart → bakiyeler KORUNUR
 * Veriler: plugins/NanoScript/data/economy.json
 *
 * ESKİ YÖNTEM (bellekte, reload'da silinir):
 *   const accounts = {};           ← Bunu KULLANMA!
 *
 * YENİ YÖNTEM (JSON'a kaydedilir):
 *   const db = server.getStorage();
 *   db.set("bal.Steve", 1500);
 *   db.get("bal.Steve", 0);        → her zaman okur
 */

const server = getServer();
const db = server.getStorage();   // → economy.json (otomatik kaydeder)
const cm = server.getCommandManager();
const em = server.getEventManager();

const CURRENCY = "₺";
const START_BALANCE = 1000;

// ============================================================
// API fonksiyonları (başka scriptler de kullanabilir)
// ============================================================

function getBalance(name) {
    return db.get("bal." + name, START_BALANCE);
}

function setBalance(name, amount) {
    db.set("bal." + name, Math.max(0, amount));
}

function give(name, amount) {
    if (amount <= 0) return false;
    db.increment("bal." + name, amount);
    return true;
}

function take(name, amount) {
    if (amount <= 0) return false;
    var bal = getBalance(name);
    if (bal < amount) return false;
    db.decrement("bal." + name, amount);
    return true;
}

function transfer(from, to, amount) {
    if (!take(from, amount)) return false;
    give(to, amount);
    return true;
}

function fmt(amount) {
    return CURRENCY + amount;
}

function getTopList(limit) {
    limit = limit || 10;
    var keys = db.keys("bal");   // ["Steve","Alex",...]
    var list = [];
    for (var i = 0; i < keys.length; i++) {
        var name = keys.get(i);   // JSArray → .get(index)
        list.push({ name: name, balance: getBalance(name) });
    }
    list.sort(function(a, b) { return b.balance - a.balance; });
    return list.slice(0, limit);
}

// ============================================================
// Oyuncu ilk bağlandığında hesap oluştur
// ============================================================

em.on("PlayerJoinEvent", function(e) {
    var player = e.getPlayer();
    var name = player.getName();
    var isNew = !db.has("bal." + name);

    if (isNew) {
        db.set("bal." + name, START_BALANCE);
        server.schedule(40, function() {
            server.sendMessage(player,
                color("&a[Ekonomi] Hoş geldin! Başlangıç bakiyeniz: &e" + fmt(START_BALANCE))
            );
        });
        server.log("Yeni hesap: " + name + " (" + fmt(START_BALANCE) + ")");
    }
});

// ============================================================
// /bakiye [oyuncu]
// ============================================================

cm.registerFull("bakiye", "Bakiye görüntüle", "/bakiye [oyuncu]", function(sender, args) {
    var name = (args.length > 0) ? args[0] : (sender.getName ? sender.getName() : null);
    if (!name) { sender.sendMessage(color("&cKullanım: /bakiye [oyuncu]")); return; }

    var bal = getBalance(name);
    sender.sendMessage(color("&e=== " + name + " Bakiyesi ==="));
    sender.sendMessage(color("  &6Bakiye: &f" + fmt(bal)));
});

// ============================================================
// /pay <oyuncu> <miktar>
// ============================================================

cm.registerFull("pay", "Para gönder", "/pay <oyuncu> <miktar>", function(sender, args) {
    if (!sender.getName) { sender.sendMessage("Sadece oyuncular kullanabilir."); return; }
    if (args.length < 2) { sender.sendMessage(color("&cKullanım: /pay <oyuncu> <miktar>")); return; }

    var fromName = sender.getName();
    var toName   = args[0];
    var amount   = parseFloat(args[1]);

    if (isNaN(amount) || amount <= 0) { sender.sendMessage(color("&cGeçersiz miktar!")); return; }
    if (fromName === toName)          { sender.sendMessage(color("&cKendinize para gönderemezsiniz!")); return; }

    if (!transfer(fromName, toName, amount)) {
        sender.sendMessage(color("&cYetersiz bakiye! Bakiyeniz: &e" + fmt(getBalance(fromName))));
        return;
    }

    sender.sendMessage(color("&a" + toName + " kişisine &e" + fmt(amount) + " &agönderildi!"));
    sender.sendMessage(color("&7Yeni bakiyeniz: &f" + fmt(getBalance(fromName))));

    var target = server.getPlayer(toName);
    if (target) {
        server.sendMessage(target,
            color("&a[Ekonomi] &e" + fromName + " &asana &e" + fmt(amount) + " &agönderdi!")
        );
    }
});

// ============================================================
// /para ver|al|ayarla <oyuncu> <miktar>  (admin)
// ============================================================

cm.registerFull("para", "Para yönetimi (admin)", "/para <ver|al|ayarla> <oyuncu> <miktar>", function(sender, args) {
    if (args.length < 3) {
        sender.sendMessage(color("&cKullanım: /para <ver|al|ayarla> <oyuncu> <miktar>"));
        return;
    }

    var action = args[0].toLowerCase();
    var targetName = args[1];
    var amount = parseFloat(args[2]);

    if (isNaN(amount) || amount <= 0) { sender.sendMessage(color("&cGeçersiz miktar!")); return; }

    if (action === "ver" || action === "give") {
        give(targetName, amount);
        sender.sendMessage(color("&a" + targetName + " hesabına &e" + fmt(amount) + " &aeklendi."));
        var t = server.getPlayer(targetName);
        if (t) server.sendMessage(t, color("&a[Admin] Hesabınıza &e" + fmt(amount) + " &aeklendi!"));

    } else if (action === "al" || action === "take") {
        if (take(targetName, amount)) {
            sender.sendMessage(color("&a" + targetName + " hesabından &e" + fmt(amount) + " &aalındı."));
        } else {
            sender.sendMessage(color("&cYetersiz bakiye! (" + targetName + ": " + fmt(getBalance(targetName)) + ")"));
        }

    } else if (action === "ayarla" || action === "set") {
        setBalance(targetName, amount);
        sender.sendMessage(color("&a" + targetName + " bakiyesi &e" + fmt(amount) + " &aolarak ayarlandı."));
    } else {
        sender.sendMessage(color("&cGeçersiz eylem: ver, al, ayarla"));
    }
});

// ============================================================
// /zenginler
// ============================================================

cm.registerFull("zenginler", "Zenginler listesi", "/zenginler", function(sender, args) {
    var list = getTopList(10);
    sender.sendMessage(color("&6&l=== Zenginler Listesi ==="));
    if (list.length === 0) { sender.sendMessage(color("&7Henüz hesap yok.")); return; }
    var medals = ["&6#1", "&7#2", "&c#3"];
    for (var i = 0; i < list.length; i++) {
        var medal = i < 3 ? medals[i] : ("&7#" + (i+1));
        sender.sendMessage(color(medal + " &f" + list[i].name + " &8- &e" + fmt(list[i].balance)));
    }
});

// ============================================================
// Her 5 dakikada güvenlik kaydı (zaten otomatik kaydediyor ama ekstra güvenlik)
// ============================================================
server.repeat(20 * 60 * 5, function() {
    db.save();
});

server.log("Ekonomi yüklendi! Kalıcı depolama: plugins/NanoScript/data/economy.json");
