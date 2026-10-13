package com.kozmetik;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** config/kozmetik.json — menü rengi, seçimler ve davranış ayarları. */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance = new Config();
    private static Path file;

    // ---- Görünüm ----
    public int accent = 0x2FA8FF;      // vurgu rengi (RGB)
    public int panelColor = 0x0B1220;  // panel rengi (RGB)
    public int panelOpacity = 82;      // 20..100
    public int bgDim = 35;             // 0..90 arka plan karartması
    public int cornerRadius = 9;       // 0..14
    public boolean glow = true;
    public boolean rainbow = false;
    public boolean blur = true;

    // ---- Seçimler (anahtar: "default", "b:<id>" yerleşik, "f:<dosya>" özel) ----
    public String sword = "default";
    public String totem = "default";
    public String skin = "default";
    public String hitEffect = "none";
    public String killEffect = "none";

    // ---- Davranış ----
    public boolean applySword = true;
    public boolean applyTotem = true;
    public boolean applySkin = true;
    public boolean slimArms = false;   // yalnızca özel (dosya) skinler için
    public boolean effectsEnabled = true;
    public int effectIntensity = 100;  // 25..300 yüzde

    public static Config get() { return instance; }

    public static void load(Path configDir) {
        file = configDir.resolve("kozmetik.json");
        instance = new Config();
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                Config c = GSON.fromJson(r, Config.class);
                if (c != null) instance = c;
            } catch (Exception e) {
                Log.warn("Ayar dosyası okunamadı, varsayılanlar kullanılacak: " + e);
            }
        }
        instance.sanitize();
        save();
    }

    public static void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(instance, w);
            }
        } catch (IOException e) {
            Log.warn("Ayarlar kaydedilemedi: " + e);
        }
    }

    public static void reset() {
        String sword = instance.sword, totem = instance.totem, skin = instance.skin;
        String hit = instance.hitEffect, kill = instance.killEffect;
        instance = new Config();
        instance.sword = sword; instance.totem = totem; instance.skin = skin;
        instance.hitEffect = hit; instance.killEffect = kill;
        save();
    }

    void sanitize() {
        accent &= 0xFFFFFF;
        panelColor &= 0xFFFFFF;
        panelOpacity = clamp(panelOpacity, 20, 100);
        bgDim = clamp(bgDim, 0, 90);
        cornerRadius = clamp(cornerRadius, 0, 14);
        effectIntensity = clamp(effectIntensity, 25, 300);
        if (sword == null) sword = "default";
        if (totem == null) totem = "default";
        if (skin == null) skin = "default";
        if (hitEffect == null) hitEffect = "none";
        if (killEffect == null) killEffect = "none";
    }

    public static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
