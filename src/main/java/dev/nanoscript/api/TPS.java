package dev.nanoscript.api;

import java.lang.reflect.*;
import org.bukkit.Bukkit;

/**
 * TPS okuma yardımcısı.
 *
 * Deneme sırası:
 *   1. Paper 1.15+ → Bukkit.getServer().getTPS()          (doğrudan metod)
 *   2. Spigot       → Bukkit.getServer().spigot().getTPS() (reflection)
 *   3. NMS 1.20.5+  → versiyonsuz paket  net.minecraft.server.MinecraftServer
 *   4. NMS eski     → net.minecraft.server.<versiyon>.MinecraftServer
 *   5. Fallback     → 20.0 döner (hata loglanmaz, sessizce geçer)
 */
public class TPS {
    private TPS() {}

    public static double getTPS() {
        return getAverageTPS(1);
    }

    public static double getAverageTPS(int time) {
        try {
            double[] tps = fetchTPS();
            double raw = switch (time) {
                case 1  -> tps.length > 0 ? tps[0] : 20.0;
                case 5  -> tps.length > 1 ? tps[1] : 20.0;
                case 15 -> tps.length > 2 ? tps[2] : 20.0;
                default -> 20.0;
            };
            return Math.min(Math.round(raw * 100.0) / 100.0, 20.0);
        } catch (Exception e) {
            return 20.0; // Sessiz fallback — sunucu başlarken de güvenli
        }
    }

    // ── Strateji 1: Paper doğrudan API (en temiz yol) ─────────────────
    // Paper, org.bukkit.Server arayüzüne getTPS() ekledi (Paper 1.15+)
    // CraftServer bu metodu implement eder.
    private static double[] tryPaperDirect() {
        try {
            Method m = Bukkit.getServer().getClass().getMethod("getTPS");
            return (double[]) m.invoke(Bukkit.getServer());
        } catch (Exception ignored) {
            return null;
        }
    }

    // ── Strateji 2: Spigot Server.Spigot#getTPS() ─────────────────────
    private static double[] trySpigot() {
        try {
            Method spigotMethod = Bukkit.getServer().getClass().getMethod("spigot");
            Object spigot = spigotMethod.invoke(Bukkit.getServer());
            Method tpsMethod = spigot.getClass().getMethod("getTPS");
            return (double[]) tpsMethod.invoke(spigot);
        } catch (Exception ignored) {
            return null;
        }
    }

    // ── Strateji 3: NMS versiyonsuz (1.17+) ───────────────────────────
    // Paper 1.20.5+ de sınıf yolu değişti: net.minecraft.server.MinecraftServer
    private static double[] tryNMSUnversioned() {
        try {
            Class<?> msc = Class.forName("net.minecraft.server.MinecraftServer");
            // getServer() static metodu
            Method getServer = msc.getMethod("getServer");
            Object ms = getServer.invoke(null);
            // recentTps alanı (Spigot patch)
            Field f = findField(ms.getClass(), "recentTps", "recentMsPerTick");
            if (f == null) return null;
            f.setAccessible(true);
            Object val = f.get(ms);
            if (val instanceof double[] d) return d;
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    // ── Strateji 4: NMS versiyonlu (1.8–1.16) ─────────────────────────
    private static double[] tryNMSVersioned() {
        try {
            String pkg = Bukkit.getServer().getClass().getPackage().getName();
            String version = pkg.substring(pkg.lastIndexOf('.') + 1);
            Class<?> msc = Class.forName("net.minecraft.server." + version + ".MinecraftServer");
            Method getServer = msc.getMethod("getServer");
            Object ms = getServer.invoke(null);
            Field f = findField(ms.getClass(), "recentTps");
            if (f == null) return null;
            f.setAccessible(true);
            return (double[]) f.get(ms);
        } catch (Exception ignored) {
            return null;
        }
    }

    // ── Ana fetch ─────────────────────────────────────────────────────
    private static double[] fetchTPS() {
        double[] result;

        result = tryPaperDirect();
        if (result != null && result.length >= 1) return result;

        result = trySpigot();
        if (result != null && result.length >= 1) return result;

        result = tryNMSUnversioned();
        if (result != null && result.length >= 1) return result;

        result = tryNMSVersioned();
        if (result != null && result.length >= 1) return result;

        return new double[]{20.0, 20.0, 20.0}; // tam fallback
    }

    // ── Yardımcı: üst sınıflar dahil field ara ────────────────────────
    private static Field findField(Class<?> cls, String... names) {
        Class<?> cur = cls;
        while (cur != null) {
            for (String name : names) {
                try { return cur.getDeclaredField(name); }
                catch (NoSuchFieldException ignored) {}
            }
            cur = cur.getSuperclass();
        }
        return null;
    }
}