package com.kozmetik;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.nio.IntBuffer;
import java.util.HashSet;
import java.util.Set;

/**
 * Kılıç ve totem görünümünü, oyunun eşya atlasındaki ilgili sprite'ın piksellerini doğrudan
 * değiştirerek uygular. Böylece elde, envanterde, yerde, item frame'de ve totem animasyonunda
 * her yerde aynı doku görünür. Kaynak paketleri yeniden yüklenince (F3+T) otomatik yeniden uygulanır.
 */
public final class AtlasPatcher {
    private static final String[] SWORDS = {
            "wooden_sword", "stone_sword", "iron_sword", "golden_sword", "diamond_sword", "netherite_sword"
    };
    private static final String TOTEM = "totem_of_undying";

    private static boolean dirty = true;
    private static Sprite lastProbe;
    private static int counter;
    /** Bu atlas örneğinde bizim değiştirdiğimiz sprite yolları (geri yüklemek için). */
    private static final Set<String> patched = new HashSet<>();

    private AtlasPatcher() {}

    public static void markDirty() { dirty = true; }

    public static void tick(MinecraftClient mc) {
        if (!CosmeticLibrary.isLoaded()) return;
        counter++;
        if (!dirty && counter % 10 != 0) return;
        try {
            SpriteAtlasTexture atlas = mc.getBakedModelManager().getAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
            if (atlas == null) return;
            Sprite probe = atlas.getSprite(Identifier.of("minecraft", "item/diamond_sword"));
            if (probe != lastProbe) {
                // atlas yeniden kuruldu (kaynak paketi yüklemesi) -> hepsi sıfırlandı
                lastProbe = probe;
                patched.clear();
                CosmeticLibrary.ensureTextures(mc);
                SkinOverride.update();
                dirty = true;
            }
            if (!dirty) return;
            dirty = false;
            applyAll(mc, atlas);
        } catch (Throwable t) {
            dirty = false;
            Log.error("Atlas dokusu uygulanamadı", t);
        }
    }

    private static void applyAll(MinecraftClient mc, SpriteAtlasTexture atlas) {
        Config cfg = Config.get();

        Cosmetic sword = CosmeticLibrary.find(Category.SWORD, cfg.sword);
        boolean customSword = cfg.applySword && sword != null && !sword.isDefault();
        for (String s : SWORDS) {
            patch(mc, atlas, "item/" + s, customSword ? sword : null);
        }

        Cosmetic totem = CosmeticLibrary.find(Category.TOTEM, cfg.totem);
        boolean customTotem = cfg.applyTotem && totem != null && !totem.isDefault();
        patch(mc, atlas, "item/" + TOTEM, customTotem ? totem : null);
    }

    /** custom == null ise orijinal dokuyu geri yükler (yalnızca daha önce değiştirdiysek). */
    private static void patch(MinecraftClient mc, SpriteAtlasTexture atlas, String path, Cosmetic custom) {
        if (custom == null && !patched.contains(path)) return;

        Identifier id = Identifier.of("minecraft", path);
        Sprite sprite = atlas.getSprite(id);
        if (sprite == null || !id.equals(sprite.getContents().getId())) return; // eksik doku sprite'ı
        int sw = sprite.getContents().getWidth();
        int sh = sprite.getContents().getHeight();

        int[] px;
        if (custom != null) {
            px = ImageOps.resample(custom.pixels, custom.width, custom.height, sw, sh);
        } else {
            int[] size = new int[2];
            int[] vanilla = CosmeticLibrary.readVanilla(mc, "textures/" + path + ".png", size);
            if (vanilla == null) return;
            px = ImageOps.resample(vanilla, size[0], size[1], sw, sh);
        }
        upload(atlas, sprite, px, sw, sh);
        if (custom != null) patched.add(path); else patched.remove(path);
    }

    private static void upload(SpriteAtlasTexture atlas, Sprite sprite, int[] px, int w, int h) {
        atlas.bindTexture();
        int levels = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL);
        levels = Math.max(0, Math.min(4, levels));

        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);

        int[] cur = px;
        int cw = w;
        int ch = h;
        for (int level = 0; level <= levels; level++) {
            IntBuffer buf = BufferUtils.createIntBuffer(cw * ch);
            buf.put(cur);
            buf.flip();
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, level, sprite.getX() >> level, sprite.getY() >> level,
                    cw, ch, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
            if (cw == 1 && ch == 1) break;
            cur = ImageOps.halve(cur, cw, ch);
            cw = Math.max(1, cw / 2);
            ch = Math.max(1, ch / 2);
        }
    }
}
