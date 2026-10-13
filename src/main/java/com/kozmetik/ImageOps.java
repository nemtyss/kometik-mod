package com.kozmetik;

/**
 * Saf Java görüntü işlemleri. Piksel formatı Minecraft'ın NativeImage formatıyla aynıdır:
 * 32 bit ABGR (alfa en üst bayt, kırmızı en alt bayt). Bellekte little-endian sırayla
 * R,G,B,A baytları oluşur, bu yüzden doğrudan GL_RGBA olarak yüklenebilir.
 */
public final class ImageOps {
    private ImageOps() {}

    public static int a(int c) { return c >>> 24; }
    public static int b(int c) { return (c >> 16) & 0xFF; }
    public static int g(int c) { return (c >> 8) & 0xFF; }
    public static int r(int c) { return c & 0xFF; }

    public static int abgr(int a, int b, int g, int r) {
        return (a & 0xFF) << 24 | (b & 0xFF) << 16 | (g & 0xFF) << 8 | (r & 0xFF);
    }

    /** Kaynağı hedef boyuta getirir: küçültürken alan ortalaması, büyütürken en yakın komşu. */
    public static int[] resample(int[] src, int sw, int sh, int dw, int dh) {
        if (sw == dw && sh == dh) return src.clone();
        int[] out = new int[dw * dh];
        boolean down = sw >= dw && sh >= dh;
        double fx = (double) sw / dw;
        double fy = (double) sh / dh;
        for (int y = 0; y < dh; y++) {
            for (int x = 0; x < dw; x++) {
                if (!down) {
                    int sx = Math.min(sw - 1, (int) Math.floor((x + 0.5) * fx));
                    int sy = Math.min(sh - 1, (int) Math.floor((y + 0.5) * fy));
                    out[y * dw + x] = src[sy * sw + sx];
                } else {
                    int x0 = (int) Math.floor(x * fx);
                    int x1 = Math.max(x0 + 1, (int) Math.floor((x + 1) * fx));
                    int y0 = (int) Math.floor(y * fy);
                    int y1 = Math.max(y0 + 1, (int) Math.floor((y + 1) * fy));
                    out[y * dw + x] = average(src, sw, x0, y0, Math.min(x1, sw), Math.min(y1, sh));
                }
            }
        }
        return out;
    }

    /** Alfa ağırlıklı ortalama (şeffaf piksellerin rengi kenarlara kirlilik bulaştırmaz). */
    private static int average(int[] src, int sw, int x0, int y0, int x1, int y1) {
        long sa = 0, sr = 0, sg = 0, sb = 0;
        int n = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int c = src[y * sw + x];
                int al = a(c);
                sa += al;
                sr += (long) r(c) * al;
                sg += (long) g(c) * al;
                sb += (long) b(c) * al;
                n++;
            }
        }
        if (n == 0 || sa == 0) return 0;
        int al = (int) Math.round((double) sa / n);
        int rr = (int) Math.round((double) sr / sa);
        int gg = (int) Math.round((double) sg / sa);
        int bb = (int) Math.round((double) sb / sa);
        return abgr(al, bb, gg, rr);
    }

    /** Mipmap seviyesi için 2x küçültme. */
    public static int[] halve(int[] src, int w, int h) {
        int nw = Math.max(1, w / 2);
        int nh = Math.max(1, h / 2);
        return resample(src, w, h, nw, nh);
    }

    // ---- Eski 64x32 skin -> 64x64 --------------------------------------------------------

    /** Minecraft'ın eski (64x32) skin dönüştürmesinin aynısı + taban katmanını opak yapma. */
    public static int[] toModernSkin(int[] src, int w, int h) {
        if (w != 64 || (h != 64 && h != 32)) throw new IllegalArgumentException("Skin 64x64 veya 64x32 olmalı");
        int[] px = new int[64 * 64];
        System.arraycopy(src, 0, px, 0, 64 * h);
        boolean legacy = h == 32;
        if (legacy) {
            copyRect(px, 4, 16, 16, 32, 4, 4);
            copyRect(px, 8, 16, 16, 32, 4, 4);
            copyRect(px, 0, 20, 24, 32, 4, 12);
            copyRect(px, 4, 20, 16, 32, 4, 12);
            copyRect(px, 8, 20, 8, 32, 4, 12);
            copyRect(px, 12, 20, 16, 32, 4, 12);
            copyRect(px, 44, 16, -8, 32, 4, 4);
            copyRect(px, 48, 16, -8, 32, 4, 4);
            copyRect(px, 40, 20, 0, 32, 4, 12);
            copyRect(px, 44, 20, -8, 32, 4, 12);
            copyRect(px, 48, 20, -16, 32, 4, 12);
            copyRect(px, 52, 20, -8, 32, 4, 12);
        }
        opaque(px, 0, 0, 32, 16);
        if (legacy) opaque(px, 32, 0, 64, 32);
        opaque(px, 0, 16, 64, 32);
        opaque(px, 16, 48, 48, 64);
        return px;
    }

    /** (x,y,w,h) dikdörtgenini (tx,ty) kadar kaydırıp yatay çevirerek kopyalar. */
    static void copyRect(int[] px, int x, int y, int tx, int ty, int w, int h) {
        for (int dy = 0; dy < h; dy++) {
            for (int dx = 0; dx < w; dx++) {
                int from = (y + dy) * 64 + (x + dx);
                int to = (y + ty + dy) * 64 + (x + tx + (w - 1 - dx));
                px[to] = px[from];
            }
        }
    }

    static void opaque(int[] px, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                px[y * 64 + x] |= 0xFF000000;
            }
        }
    }
}
