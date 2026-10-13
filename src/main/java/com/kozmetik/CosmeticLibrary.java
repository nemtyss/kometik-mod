package com.kozmetik;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Yerleşik (jar içi) ve özel (.minecraft/kozmetik/...) dokuların listesini yönetir. */
public final class CosmeticLibrary {
    private static final Map<Category, List<Cosmetic>> LISTS = new EnumMap<>(Category.class);
    private static Path root;
    private static boolean loaded;

    private CosmeticLibrary() {}

    public static Path root() { return root; }

    public static Path folder(Category c) { return root.resolve(c.folder); }

    public static boolean isLoaded() { return loaded; }

    public static void init(Path gameDir) {
        root = gameDir.resolve("kozmetik");
        try {
            for (Category c : Category.values()) Files.createDirectories(folder(c));
            Path readme = root.resolve("OKU-BENI.txt");
            if (!Files.exists(readme)) {
                String text = "KOZMETIK MODU - Kendi dosyalarini ekleme\r\n"
                        + "=========================================\r\n\r\n"
                        + "kilic\\  -> Kilic dokulari (.png). Normal boyut 16x16, daha buyuk de olabilir.\r\n"
                        + "totem\\  -> Totem dokulari (.png). Normal boyut 16x16.\r\n"
                        + "skin\\   -> Oyuncu skinleri (.png). 64x64 veya 64x32.\r\n"
                        + "          Ince (Alex) kollu skin icin dosya adinin icine 'slim' yaz (ornek: ejder_slim.png).\r\n\r\n"
                        + "Dosyalari koyduktan sonra oyunda menuyu ac ve 'Yenile' tusuna bas.\r\n"
                        + "Dosya adi, menude gorunen isim olur.\r\n\r\n"
                        + "Kilic dokusu arka planda SEFFAF olmali ve kilic sol alttan sag uste dogru\r\n"
                        + "cizilmis olmalidir (normal Minecraft kilici gibi).\r\n";
                Files.write(readme, text.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            Log.warn("Klasörler oluşturulamadı: " + e);
        }
    }

    public static List<Cosmetic> list(Category c) {
        List<Cosmetic> l = LISTS.get(c);
        return l == null ? new ArrayList<>() : l;
    }

    public static Cosmetic find(Category c, String key) {
        List<Cosmetic> l = LISTS.get(c);
        if (l == null || l.isEmpty()) return null;
        if (key != null) {
            for (Cosmetic x : l) if (x.key.equals(key)) return x;
        }
        return l.get(0);
    }

    // -------------------------------------------------------------------------------------

    /** Tüm listeleri baştan oluşturur. Render thread'inde çağrılmalıdır. */
    public static void reload(MinecraftClient mc) {
        TextureManager tm = mc.getTextureManager();
        for (List<Cosmetic> old : LISTS.values()) {
            for (Cosmetic c : old) {
                if (c.icon != null) {
                    try { tm.destroyTexture(c.icon); } catch (Throwable ignored) { }
                }
            }
        }
        LISTS.clear();
        for (Category cat : Category.values()) {
            List<Cosmetic> list = new ArrayList<>();
            list.add(makeDefault(mc, cat));
            loadBuiltins(mc, cat, list);
            loadFiles(cat, list);
            LISTS.put(cat, list);
        }
        registerTextures(mc);
        loaded = true;
    }

    /** Resource reload sonrası kaybolmuş dokuları yeniden kaydeder. */
    public static void ensureTextures(MinecraftClient mc) {
        if (!loaded) return;
        TextureManager tm = mc.getTextureManager();
        for (List<Cosmetic> l : LISTS.values()) {
            for (Cosmetic c : l) {
                if (c.icon != null && tm.getOrDefault(c.icon, null) == null) register(tm, c);
            }
        }
    }

    private static void registerTextures(MinecraftClient mc) {
        TextureManager tm = mc.getTextureManager();
        for (List<Cosmetic> l : LISTS.values()) {
            for (Cosmetic c : l) if (c.icon != null) register(tm, c);
        }
    }

    private static void register(TextureManager tm, Cosmetic c) {
        NativeImage img = new NativeImage(c.width, c.height, false);
        for (int y = 0; y < c.height; y++) {
            for (int x = 0; x < c.width; x++) {
                img.setColor(x, y, c.pixels[y * c.width + x]);
            }
        }
        tm.registerTexture(c.icon, new NativeImageBackedTexture(img));
    }

    // -------------------------------------------------------------------------------------

    private static final class Img {
        final int[] px;
        final int w;
        final int h;
        Img(int[] px, int w, int h) { this.px = px; this.w = w; this.h = h; }
    }

    private static Img readImage(InputStream in) throws IOException {
        try (NativeImage img = NativeImage.read(in)) {
            int w = img.getWidth();
            int h = img.getHeight();
            int[] px = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) px[y * w + x] = img.getColor(x, y);
            }
            return new Img(px, w, h);
        }
    }

    /** Oyunun (aktif kaynak paketlerinin) kendi dokusunu okur. */
    public static int[] readVanilla(MinecraftClient mc, String path, int[] sizeOut) {
        try (InputStream in = mc.getResourceManager().open(Identifier.of("minecraft", path))) {
            Img img = readImage(in);
            if (sizeOut != null) { sizeOut[0] = img.w; sizeOut[1] = img.h; }
            return img.px;
        } catch (Throwable t) {
            Log.warn("Vanilla doku okunamadı: " + path + " -> " + t);
            return null;
        }
    }

    private static Cosmetic makeDefault(MinecraftClient mc, Category cat) {
        String id = "kozmetik_default_" + cat.resourceKey();
        if (cat == Category.SKIN) {
            return new Cosmetic(cat, Cosmetic.Source.DEFAULT, "default", "Orijinal Skin", null, 0, 0, null, false);
        }
        String path = cat == Category.SWORD ? "textures/item/diamond_sword.png" : "textures/item/totem_of_undying.png";
        int[] size = new int[2];
        int[] px = readVanilla(mc, path, size);
        if (px == null) { px = new int[16 * 16]; size[0] = 16; size[1] = 16; }
        String name = cat == Category.SWORD ? "Varsayılan Kılıç" : "Varsayılan Totem";
        return new Cosmetic(cat, Cosmetic.Source.DEFAULT, "default", name, px, size[0], size[1],
                Identifier.of(KozmetikMod.ID, "dyn/" + id), false);
    }

    private static void loadBuiltins(MinecraftClient mc, Category cat, List<Cosmetic> out) {
        try (InputStream in = mc.getResourceManager().open(Identifier.of(KozmetikMod.ID, "builtin.json"))) {
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray arr = root.getAsJsonArray(cat.resourceKey());
            if (arr == null) return;
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                String id = o.get("id").getAsString();
                String name = o.get("name").getAsString();
                boolean slim = o.has("slim") && o.get("slim").getAsBoolean();
                Identifier res = Identifier.of(KozmetikMod.ID, "textures/cosmetic/" + cat.resourceKey() + "/" + id + ".png");
                try (InputStream pin = mc.getResourceManager().open(res)) {
                    Img img = readImage(pin);
                    add(cat, Cosmetic.Source.BUILTIN, "b:" + id, name, img, slim, out);
                } catch (Throwable t) {
                    Log.warn("Yerleşik doku yüklenemedi: " + id + " -> " + t);
                }
            }
        } catch (Throwable t) {
            Log.warn("builtin.json okunamadı: " + t);
        }
    }

    private static void loadFiles(Category cat, List<Cosmetic> out) {
        if (root == null) return;
        File dir = folder(cat).toFile();
        File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".png"));
        if (files == null) return;
        Arrays.sort(files, Comparator.comparing(f -> f.getName().toLowerCase()));
        for (File f : files) {
            try (InputStream in = Files.newInputStream(f.toPath())) {
                Img img = readImage(in);
                String base = f.getName().substring(0, f.getName().length() - 4);
                boolean slim = base.toLowerCase().contains("slim");
                add(cat, Cosmetic.Source.FILE, "f:" + f.getName(), prettify(base), img, slim, out);
            } catch (Throwable t) {
                Log.warn("Özel doku yüklenemedi: " + f.getName() + " -> " + t);
            }
        }
    }

    private static void add(Category cat, Cosmetic.Source src, String key, String name, Img img, boolean slim, List<Cosmetic> out) {
        if (img.w < 1 || img.h < 1 || img.w > 1024 || img.h > 1024) {
            Log.warn("Geçersiz boyut, atlandı: " + name + " (" + img.w + "x" + img.h + ")");
            return;
        }
        int[] px = img.px;
        int w = img.w;
        int h = img.h;
        if (cat == Category.SKIN) {
            if (w != 64 || (h != 64 && h != 32)) {
                Log.warn("Skin 64x64 veya 64x32 olmalı, atlandı: " + name + " (" + w + "x" + h + ")");
                return;
            }
            px = ImageOps.toModernSkin(px, w, h);
            h = 64;
        }
        String idPart = sanitize(key) + "_" + out.size();
        Identifier icon = Identifier.of(KozmetikMod.ID, "dyn/" + cat.resourceKey() + "/" + idPart);
        out.add(new Cosmetic(cat, src, key, name, px, w, h, icon, slim));
    }

    static String sanitize(String s) {
        StringBuilder sb = new StringBuilder();
        for (char ch : s.toLowerCase().toCharArray()) {
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_' || ch == '-' || ch == '.') sb.append(ch);
            else sb.append('_');
        }
        return sb.toString();
    }

    static String prettify(String base) {
        String s = base.replace('_', ' ').replace('-', ' ').trim();
        if (s.isEmpty()) return base;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
