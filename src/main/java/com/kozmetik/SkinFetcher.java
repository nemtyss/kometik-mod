package com.kozmetik;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Bir Minecraft oyuncu adından o oyuncunun skinini indirir ve skin klasörüne PNG olarak kaydeder.
 * Yalnızca Mojang'ın kendi sunucularına (api / sessionserver / textures) bağlanır.
 */
public final class SkinFetcher {
    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    public interface Callback {
        /** Arka plan thread'inden çağrılır. */
        void status(String message, boolean error);

        /** Her zaman oyun thread'inde çağrılır. Başarısızsa fileName == null. */
        void done(String fileName);
    }

    private SkinFetcher() {}

    public static boolean validName(String name) { return name != null && NAME.matcher(name).matches(); }

    public static void fetch(String name, Path skinDir, Callback cb) {
        final MinecraftClient mc = MinecraftClient.getInstance();
        if (!validName(name)) {
            cb.status("Geçersiz oyuncu adı (3-16 harf/rakam/_)", true);
            mc.execute(() -> cb.done(null));
            return;
        }
        Thread t = new Thread(() -> {
            String result = null;
            try {
                cb.status("Oyuncu aranıyor...", false);
                String[] idAndName = lookup(name);
                cb.status("Skin indiriliyor...", false);
                result = download(idAndName[0], idAndName[1], skinDir);
                cb.status("Hazır: " + idAndName[1], false);
            } catch (FileNotFoundException e) {
                cb.status("Oyuncu bulunamadı: " + name, true);
            } catch (Exception e) {
                Log.warn("Skin indirilemedi: " + e);
                cb.status("Skin alınamadı (internet?)", true);
            }
            final String fr = result;
            mc.execute(() -> cb.done(fr));
        }, "kozmetik-skin-fetch");
        t.setDaemon(true);
        t.start();
    }

    private static String[] lookup(String name) throws IOException {
        String[] endpoints = {
                "https://api.minecraftservices.com/minecraft/profile/lookup/name/",
                "https://api.mojang.com/users/profiles/minecraft/"
        };
        IOException last = new FileNotFoundException(name);
        for (String ep : endpoints) {
            try {
                byte[] body = get(ep + name, 100_000);
                JsonObject o = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
                return new String[]{o.get("id").getAsString(), o.get("name").getAsString()};
            } catch (FileNotFoundException e) {
                last = e;
            } catch (IOException | RuntimeException e) {
                last = e instanceof IOException ? (IOException) e : new IOException(e);
            }
        }
        throw last;
    }

    private static String download(String id, String canonicalName, Path dir) throws IOException {
        byte[] prof = get("https://sessionserver.mojang.com/session/minecraft/profile/" + id, 300_000);
        JsonObject root = JsonParser.parseString(new String(prof, StandardCharsets.UTF_8)).getAsJsonObject();
        String value = null;
        for (JsonElement el : root.getAsJsonArray("properties")) {
            JsonObject p = el.getAsJsonObject();
            if ("textures".equals(p.get("name").getAsString())) value = p.get("value").getAsString();
        }
        if (value == null) throw new FileNotFoundException("textures");
        JsonObject tex = JsonParser.parseString(new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("textures");
        if (tex == null || !tex.has("SKIN")) throw new FileNotFoundException("SKIN");
        JsonObject skin = tex.getAsJsonObject("SKIN");
        String url = skin.get("url").getAsString();
        boolean slim = skin.has("metadata") && skin.getAsJsonObject("metadata").has("model")
                && "slim".equals(skin.getAsJsonObject("metadata").get("model").getAsString());
        if (url.startsWith("http://")) url = "https://" + url.substring(7);
        if (!url.startsWith("https://textures.minecraft.net/")) throw new IOException("Beklenmeyen skin adresi");

        byte[] png = get(url, 1_000_000);
        checkPng(png);

        String base = CosmeticLibrary.sanitize(canonicalName);
        String file = base + (slim ? "_slim" : "") + ".png";
        Files.createDirectories(dir);
        Files.deleteIfExists(dir.resolve(base + ".png"));
        Files.deleteIfExists(dir.resolve(base + "_slim.png"));
        Files.write(dir.resolve(file), png);
        return file;
    }

    /** PNG imzası ve 64x64 / 64x32 boyut kontrolü (IHDR'den okunur). */
    static void checkPng(byte[] b) throws IOException {
        byte[] sig = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        if (b.length < 33) throw new IOException("Bozuk PNG");
        for (int i = 0; i < 8; i++) if (b[i] != sig[i]) throw new IOException("PNG değil");
        int w = ((b[16] & 0xFF) << 24) | ((b[17] & 0xFF) << 16) | ((b[18] & 0xFF) << 8) | (b[19] & 0xFF);
        int h = ((b[20] & 0xFF) << 24) | ((b[21] & 0xFF) << 16) | ((b[22] & 0xFF) << 8) | (b[23] & 0xFF);
        if (w != 64 || (h != 64 && h != 32)) throw new IOException("Beklenmeyen skin boyutu " + w + "x" + h);
    }

    private static byte[] get(String url, int maxBytes) throws IOException {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        c.setRequestProperty("User-Agent", "KozmetikMod/1.0");
        c.setRequestProperty("Accept", "application/json, image/png");
        int code = c.getResponseCode();
        if (code == 204 || code == 404) throw new FileNotFoundException(url);
        if (code != 200) throw new IOException("HTTP " + code);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > maxBytes) throw new IOException("Yanıt çok büyük");
            }
            return out.toByteArray();
        } finally {
            c.disconnect();
        }
    }
}
