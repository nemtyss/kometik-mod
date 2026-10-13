package com.kozmetik;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;

/** Menü için küçük çizim yardımcıları (yuvarlak köşeler, parlama, yazı ölçekleme...). */
public final class Ui {
    private Ui() {}

    private static int inset(int r, int i) {
        double d = r - i - 0.5;
        return r - (int) Math.round(Math.sqrt(Math.max(0.0, (double) r * r - d * d)));
    }

    /** Dolu yuvarlak köşeli dikdörtgen. */
    public static void rrect(DrawContext c, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            c.fill(x, y, x + w, y + h, color);
            return;
        }
        c.fill(x, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            int in = inset(r, i);
            c.fill(x + in, y + i, x + w - in, y + i + 1, color);
            c.fill(x + in, y + h - 1 - i, x + w - in, y + h - i, color);
        }
    }

    /** 1 piksel kalınlığında yuvarlak köşeli çerçeve. */
    public static void rborder(DrawContext c, int x, int y, int w, int h, int r, int color) {
        if (w <= 1 || h <= 1) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            c.drawBorder(x, y, w, h, color);
            return;
        }
        c.fill(x + r, y, x + w - r, y + 1, color);
        c.fill(x + r, y + h - 1, x + w - r, y + h, color);
        c.fill(x, y + r, x + 1, y + h - r, color);
        c.fill(x + w - 1, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            int a = inset(r, i);
            int b = i == 0 ? r : Math.max(a + 1, inset(r, i - 1));
            c.fill(x + a, y + i, x + b, y + i + 1, color);
            c.fill(x + w - b, y + i, x + w - a, y + i + 1, color);
            c.fill(x + a, y + h - 1 - i, x + b, y + h - i, color);
            c.fill(x + w - b, y + h - 1 - i, x + w - a, y + h - i, color);
        }
    }

    /** Dışa doğru sönen parlama (ayarlardan kapatılabilir). */
    public static void glow(DrawContext c, int x, int y, int w, int h, int r, int rgb, float strength) {
        if (!Config.get().glow) return;
        for (int k = 1; k <= 4; k++) {
            float f = 1f - k / 5f;
            int a = Math.round(255 * strength * f * f * 0.55f);
            if (a <= 0) continue;
            rborder(c, x - k, y - k, w + 2 * k, h + 2 * k, r + k, Theme.argb(rgb, a));
        }
    }

    public static void text(DrawContext c, TextRenderer tr, String s, int x, int y, int color, float scale, boolean shadow) {
        if (scale == 1f) {
            c.drawText(tr, s, x, y, color, shadow);
            return;
        }
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(x, y, 0);
        m.scale(scale, scale, 1f);
        c.drawText(tr, s, 0, 0, color, shadow);
        m.pop();
    }

    public static int textWidth(TextRenderer tr, String s, float scale) {
        return Math.round(tr.getWidth(s) * scale);
    }

    public static void textCentered(DrawContext c, TextRenderer tr, String s, int cx, int y, int color, float scale, boolean shadow) {
        text(c, tr, s, cx - textWidth(tr, s, scale) / 2, y, color, scale, shadow);
    }

    /** Yukarı/aşağı ok (chevron). */
    public static void chevron(DrawContext c, int cx, int cy, boolean up, int size, int color) {
        for (int k = 0; k <= size; k++) {
            int dy = up ? k : (size - k);
            c.fill(cx - k, cy + dy - size / 2, cx - k + 1, cy + dy - size / 2 + 1, color);
            c.fill(cx + k, cy + dy - size / 2, cx + k + 1, cy + dy - size / 2 + 1, color);
        }
    }

    /** Sol/sağ ok. */
    public static void chevronH(DrawContext c, int cx, int cy, boolean left, int size, int color) {
        for (int k = 0; k <= size; k++) {
            int dx = left ? k : (size - k);
            c.fill(cx + dx - size / 2, cy - k, cx + dx - size / 2 + 1, cy - k + 1, color);
            c.fill(cx + dx - size / 2, cy + k, cx + dx - size / 2 + 1, cy + k + 1, color);
        }
    }

    public static void cross(DrawContext c, int cx, int cy, int size, int color) {
        for (int k = -size; k <= size; k++) {
            c.fill(cx + k, cy + k, cx + k + 1, cy + k + 1, color);
            c.fill(cx + k, cy - k, cx + k + 1, cy - k + 1, color);
        }
    }

    /** Tik işareti. */
    public static void check(DrawContext c, int cx, int cy, int color) {
        c.fill(cx - 3, cy, cx - 2, cy + 1, color);
        c.fill(cx - 2, cy + 1, cx - 1, cy + 2, color);
        c.fill(cx - 1, cy + 2, cx, cy + 3, color);
        c.fill(cx, cy + 1, cx + 1, cy + 2, color);
        c.fill(cx + 1, cy, cx + 2, cy + 1, color);
        c.fill(cx + 2, cy - 1, cx + 3, cy, color);
        c.fill(cx + 3, cy - 2, cx + 4, cy - 1, color);
    }

    /** Açma/kapama düğmesi (çizim). */
    public static void toggle(DrawContext c, int x, int y, int w, int h, boolean on, int accentRgb) {
        int r = h / 2;
        rrect(c, x, y, w, h, r, on ? Theme.argb(accentRgb, 200) : 0xFF2A3347);
        int k = h - 4;
        int kx = on ? x + w - k - 2 : x + 2;
        rrect(c, kx, y + 2, k, k, k / 2, 0xFFF4F8FF);
    }

    /** Kaydırıcı (çizim). t: 0..1 */
    public static void slider(DrawContext c, int x, int y, int w, float t, int accentRgb) {
        rrect(c, x, y + 3, w, 4, 2, 0xFF222B3D);
        int fw = Math.round(w * t);
        if (fw > 0) rrect(c, x, y + 3, fw, 4, 2, Theme.argb(accentRgb, 235));
        int kx = x + fw - 4;
        rrect(c, kx, y, 8, 10, 3, 0xFFF4F8FF);
    }
}
