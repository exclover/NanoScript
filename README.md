# NanoScript

**NanoScript**, Paper Minecraft sunucuları için sıfırdan yazılmış hafif bir JavaScript scripting motorudur. Hiçbir dış bağımlılık gerektirmez; Rhino, GraalVM veya başka bir JS kütüphanesi kullanmaz. Lexer, Parser ve Interpreter tamamen Java ile yazılmıştır.

---

## Özellikler

- Sıfır dış bağımlılık — sadece Paper API
- Her script tamamen izole bir engine instance'ında çalışır
- Script yüklenirken kayıt edilen tüm event'ler, task'lar ve komutlar unload sırasında otomatik temizlenir
- Çalışma anında `/ns reload` ile script yeniden yüklenebilir
- `var`, `let`, `const`, arrow function, closure, template literal, destructuring ve daha fazlası desteklenir

---

## Kurulum

1. `NanoScript-x.x.x.jar` dosyasını sunucunuzun `plugins/` klasörüne kopyalayın.
2. Sunucuyu başlatın. `plugins/NanoScript/scripts/` klasörü otomatik oluşturulur.
3. JS dosyalarınızı `plugins/NanoScript/scripts/` içine koyun.
4. `/ns load <dosya.js>` veya `/ns load all` ile yükleyin.

**Gereksinimler:** Paper 1.20+, Java 17+

---

## Komutlar

Tüm komutlar `nanoscript.admin` iznini gerektirir (varsayılan: op).

| Komut | Açıklama |
|---|---|
| `/ns load <dosya.js>` | Tek bir script yükler |
| `/ns load all` | `scripts/` klasöründeki tüm `.js` dosyalarını yükler |
| `/ns unload <dosya.js>` | Tek bir scripti kaldırır |
| `/ns unload all` | Tüm aktif scriptleri kaldırır |
| `/ns reload <dosya.js>` | Tek bir scripti yeniden yükler |
| `/ns reload all` | Tüm scriptleri yeniden yükler |
| `/ns list` | Yüklü scriptleri ve istatistiklerini listeler |

**Takma ad:** `/nanoscript`

---

## Script API

Her script şu global'lere erişebilir:

```js
const server = getServer();   // Ana sunucu objesi
const em = server.getEventManager();
const cm = server.getCommandManager();
color("&aRenkli metin");      // & renk kodlarını çevirir
```

---

### `server` Objesi

#### Mesajlaşma

```js
server.broadcast("&6Herkese mesaj!");
server.log("Konsola yazar");
server.color("&aSadece renk çevirir");
```

#### Oyuncu Erişimi

```js
server.onlineCount();                  // Çevrimiçi oyuncu sayısı → number
server.getPlayer("isim");             // Player objesi veya null
server.getOnlinePlayers();            // Tüm oyuncular → JSArray
```

#### Dünya ve Komut

```js
server.getWorld("world");             // World objesi veya null
server.dispatchCommand(sender, "komut argüman");  // → boolean
```

#### Teleport (Önerilen)

> Paper 1.20+ reflection sorununu önlemek için doğrudan `player.teleport()` yerine bu metodları kullanın.

```js
server.teleportToSpawn(player);               // Dünya spawn'ına ışınla → boolean
server.teleport(player, location);            // Belirli konuma ışınla → boolean
```

#### Zamanlayıcılar

```js
// Tek seferlik — ticks sonra çalışır
var id = server.schedule(20, function() { ... });

// Tekrarlayan — her ticks'te bir çalışır
var id = server.repeat(200, function() { ... });

// Async tekrarlayan — disk/ağ işlemleri için
var id = server.repeatAsync(200, function() { ... });

// Task iptal et
server.cancel(id);

// Async context'ten main thread'e geç
server.runSync(function() { ... });

// Şu anki zaman (System.currentTimeMillis)
server.now();  // → number (ms)
```

---

### `EventManager` (em)

```js
// Normal öncelik
em.on("PlayerJoinEvent", function(event) {
    var player = event.getPlayer();
    event.setJoinMessage("...");
});

// Özel öncelik: LOWEST, LOW, NORMAL, HIGH, HIGHEST, MONITOR
em.onPriority("PlayerDamageEvent", "HIGH", function(event) {
    event.setCancelled(true);
});
```

Desteklenen event paketleri:
- `org.bukkit.event.player.*`
- `org.bukkit.event.block.*`
- `org.bukkit.event.entity.*`
- `org.bukkit.event.inventory.*`
- `org.bukkit.event.server.*`
- `org.bukkit.event.world.*`
- `io.papermc.paper.event.player.*`
- `io.papermc.paper.event.entity.*`

---

### `CommandManager` (cm)

```js
// Basit kayıt
cm.register("komut", function(sender, args) {
    sender.sendMessage("Merhaba! args[0] = " + args[0]);
});

// Açıklamalı kayıt
cm.registerFull("komut", "Açıklama", "/komut <argüman>", function(sender, args) {
    // ...
});
```

`args` bir JSArray'dir; `args.length`, `args[0]` gibi erişimler çalışır.

---

## Desteklenen JS Özellikleri

### Değişkenler ve Kontrol Akışı

```js
var x = 1;
let y = 2;
const z = 3;

if (x > 0) { ... } else { ... }
while (x < 10) { x++; }
for (var i = 0; i < 10; i++) { ... }
for (var key in obj) { ... }
for (var val of arr) { ... }
switch (x) { case 1: ...; break; default: ...; }
try { ... } catch(e) { ... } finally { ... }
throw new Error("mesaj");
```

### Fonksiyonlar

```js
function toplam(a, b) { return a + b; }

var kare = function(x) { return x * x; };

var iki_kati = (x) => x * 2;

var carp = (a, b) => {
    return a * b;
};
```

### Objeler ve Diziler

```js
var oyuncu = { isim: "Ali", yas: 20 };
oyuncu.isim;
oyuncu["isim"];

var liste = [1, 2, 3];
liste.push(4);
liste.length;
liste.forEach(function(x) { ... });
liste.map(function(x) { return x * 2; });
liste.filter(function(x) { return x > 2; });
liste.find(function(x) { return x === 3; });
```

### String Metodları

```js
"merhaba".toUpperCase()
"  metin  ".trim()
"a,b,c".split(",")
"hello world".replace("world", "NanoScript")
"abc".includes("b")
"merhaba".startsWith("mer")
"test".padStart(6, "0")
`Hoşgeldin, ${isim}!`    // Template literal
```

### Matematik ve Diğer

```js
Math.floor(3.7)    // 3
Math.round(3.5)    // 4
Math.random()      // 0–1 arası
Math.max(1, 5, 3)  // 5
Math.pow(2, 8)     // 256

parseInt("42")
parseFloat("3.14")
isNaN(NaN)

JSON.stringify({ a: 1 })
JSON.parse('{"a":1}')

typeof x          // "number", "string", "boolean", "undefined", "object", "function"
```

---

## Örnek Script

```js
const server = getServer();
const em = server.getEventManager();
const cm = server.getCommandManager();

// Katılma mesajı
em.on("PlayerJoinEvent", function(event) {
    var player = event.getPlayer();
    event.setJoinMessage(color("&8[&a+&8] &f" + player.getName()));
    player.sendMessage(color("&6Hoş geldin, &e" + player.getName() + "&6!"));
});

// Chat formatı
em.on("AsyncPlayerChatEvent", function(event) {
    var player = event.getPlayer();
    event.setFormat(color("&7[&f" + player.getName() + "&7] &r") + event.getMessage());
});

// /spawn komutu
cm.registerFull("spawn", "Spawn'a ışınlar", "/spawn", function(sender, args) {
    var ok = server.teleportToSpawn(sender);
    if (ok) {
        sender.sendMessage(color("&aSpawn'a ışınlandın!"));
    }
});

// Her 5 dakikada otomatik duyuru
var mesajlar = ["&eSunucumuza hoş geldiniz!", "&eYardım için /yardim yazın."];
var index = 0;
server.repeat(6000, function() {
    server.broadcast(mesajlar[index]);
    index = (index + 1) % mesajlar.length;
});

server.log("Script yüklendi!");
```

---

## Bilinen Kısıtlamalar

| Konu | Durum |
|---|---|
| `new Date()` | Desteklenmez. Zaman için `server.now()` kullanın |
| `player.teleport()` | Paper 1.20+ reflection sorunu. `server.teleportToSpawn()` veya `server.teleport()` kullanın |
| `args.join()` | Komut callback'inde çalışmaz. Manuel döngüyle birleştirin |
| `require()` / `import` | Desteklenmez |
| Promise / async-await | Desteklenmez |
| RegExp | Desteklenmez |

---

## Proje Yapısı

```
src/main/java/dev/nanoscript/
├── NanoScript.java              # Plugin ana sınıfı
├── api/
│   └── ScriptAPI.java           # JS'e açılan Minecraft API
├── command/
│   └── NSCommand.java           # /ns komutu
├── engine/
│   ├── ScriptManager.java       # Script yükleme/kaldırma yöneticisi
│   ├── ScriptInstance.java      # Tek script izolasyonu
│   └── CommandMapUtil.java      # Dinamik komut kaydı (reflection)
├── listener/
│   ├── DynamicListener.java     # JS event callback wrapper
│   └── DynamicCommand.java      # JS komut callback wrapper
└── jsengine/
    ├── NanoEngine.java          # Motor giriş noktası
    ├── Lexer.java               # Tokenizer
    ├── Parser.java              # AST oluşturucu
    ├── Interpreter.java         # Tree-walk interpreter
    ├── Environment.java         # Değişken kapsamı (scope chain)
    ├── JSValue.java             # JS çalışma zamanı değeri
    ├── JSObject.java            # JS objesi
    ├── JSArray.java             # JS dizisi
    ├── JSFunction.java          # JS fonksiyonu (closure + native)
    ├── JSBuiltins.java          # Math, JSON, parseInt vb.
    ├── JavaInterop.java         # Java reflection köprüsü
    ├── Node.java                # AST düğümleri (sealed interface)
    ├── Token.java / TokenType.java
    ├── JsError.java
    ├── ReturnSignal.java
    ├── BreakSignal.java
    ├── ContinueSignal.java
    ├── ThrowSignal.java
    └── ScriptAPI.java
```

---

## Lisans

MIT License — Dilediğiniz gibi kullanabilir, değiştirebilir ve dağıtabilirsiniz.
