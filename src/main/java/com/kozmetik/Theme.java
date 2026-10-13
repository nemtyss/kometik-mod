package com.kozmetik;

/** Menü renkleri. Tüm renkler 0xAARRGGBB biçimindedir (ekran çizimi için). */
public final class Theme {
    private Theme() {}

    public static final int TEXT = 0xFFEAF2FF;
    public static final int TEXT_DIM = 0xFF8FA3C2;

    public static int argb(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }

    public static int mix(int rgbA, int rgbB, float t) {
        int r = (int) (((rgbA >> 16) & 0xFF) * (1 - t) + ((rgbB >> 16) & 0xFF) * t);
        int g = (int) (((rgbA >> 8) & 0xFF) * (1 - t) + ((rgbB >> 8) & 0xFF) * t);
        int b = (int) ((rgbA & 0xFF) * (1 - t) + (rgbB & 0xFF) * t);
        return (r << 16) | (g << 8) | b;
    }

    public static int hsv(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        float c = v * s;
        float x = c * (1 - Math.abs((h * 6) % 2 - 1));
        float m = v - c;
        float r, g, b;
        int sector = (int) (h * 6);
        switch (sector) {
            case 0: r = c; g = x; b = 0; break;
            case 1: r = x; g = c; b = 0; break;
            case 2: r = 0; g = c; b = x; break;
            case 3: r = 0; g = x; b = c; break;
            case 4: r = x; g = 0; b = c; break;
            default: r = c; g = 0; b = x; break;
        }
        return (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
    }

    /** Vurgu rengi (RGB). Gökkuşağı modunda zamanla değişir. */
    public static int accentRgb() {
        Config c = Config.get();
        if (c.rainbow) {
            float h = (System.currentTimeMillis() % 7000L) / 7000f;
            return hsv(h, 0.62f, 1f);
        }
        return c.accent & 0xFFFFFF;
    }

    public static int accent() { return 0xFF000000 | accentRgb(); }

    public static int accent(int alpha) { return argb(accentRgb(), alpha); }

    public static int panel() {
        Config c = Config.get();
        return argb(c.panelColor, Math.round(c.panelOpacity * 2.55f));
    }

    public static int cell(boolean hover) {
        Config c = Config.get();
        int base = mix(c.panelColor & 0xFFFFFF, 0x6C8CC8, hover ? 0.22f : 0.10f);
        return argb(base, hover ? 205 : 170);
    }

    public static int cellBorder(boolean hover) {
        Config c = Config.get();
        int base = mix(c.panelColor & 0xFFFFFF, accentRgb(), hover ? 0.75f : 0.30f);
        return argb(base, hover ? 230 : 150);
    }
}
