package com.kozmetik;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/** Kılıç / Totem / Skin / Efekt / Ayarlar sekmeli ana menü. */
public class CosmeticsScreen extends Screen {

    private enum Tab {
        SWORDS("Kılıçlar", "Kullanmak istediğin kılıcı seç."),
        TOTEMS("Totemler", "Ölümsüzlük totemin için bir görünüm seç."),
        SKINS("Skinler", "Oyuncu skinini değiştir. Skini yalnızca sen görürsün."),
        EFFECTS("Efektler", "Vuruş ve öldürme efektlerini seç."),
        SETTINGS("Ayarlar", "Menü rengini ve davranışları özelleştir.");

        final String label;
        final String subtitle;

        Tab(String label, String subtitle) {
            this.label = label;
            this.subtitle = subtitle;
        }
    }

    private static final int[] ACCENT_PRESETS = {
            0x2FA8FF, 0x22E1D1, 0x3DDC84, 0xFFD23F, 0xFF9A3D, 0xFF4D4D, 0xFF5FB8, 0xA15BFF, 0xEAF2FF
    };
    private static final int[] PANEL_PRESETS = {
            0x0B1220, 0x050507, 0x140B22, 0x1E0B10, 0x0B1A14, 0x1A1D24
    };

    private static Tab lastTab = Tab.SWORDS;

    private static final class Hit {
        final int x, y, w, h;
        final Runnable click;
        final DoubleConsumer drag;
        final int clipY0, clipY1;

        Hit(int x, int y, int w, int h, Runnable click, DoubleConsumer drag, int clipY0, int clipY1) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.click = click; this.drag = drag;
            this.clipY0 = clipY0; this.clipY1 = clipY1;
        }

        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h && my >= clipY0 && my < clipY1;
        }
    }

    private interface CellPainter {
        void paint(DrawContext ctx, int index, int x, int y, int w, int h, boolean hover);
    }

    private Tab tab = lastTab;
    private final int[] page = new int[Tab.values().length];
    private final List<Hit> hits = new ArrayList<>();
    private Hit activeDrag;
    private int mx, my;
    private long lastNano;
    private float indX = -1f, indW = 0f;
    private int effectMode; // 0 = vuruş, 1 = öldürme
    private int settingsScroll;
    private int settingsMax;
    private String tipName, tipSub;
    private ItemStack[] tabIcons;
    private String nameInput = "";
    private boolean nameFocus;
    private volatile String skinStatus = "";
    private volatile boolean skinStatusError;
    private volatile boolean fetching;

    // yerleşim
    private int px, py, pw, ph, ip, th, hdrY, conTop, conBot;

    public CosmeticsScreen() {
        super(Text.literal("Kozmetik"));
    }

    @Override
    protected void init() {
        if (CosmeticLibrary.isLoaded() && client != null) CosmeticLibrary.ensureTextures(client);
        tabIcons = new ItemStack[]{
                new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.TOTEM_OF_UNDYING),
                new ItemStack(Items.PLAYER_HEAD), new ItemStack(Items.BLAZE_POWDER), new ItemStack(Items.COMPARATOR)
        };
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() {
        Config.save();
        super.close();
    }

    // ===================================================================================
    //  Yardımcılar
    // ===================================================================================

    private boolean in(int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void click() {
        if (client != null) client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private void addHit(int x, int y, int w, int h, Runnable r) {
        hits.add(new Hit(x, y, w, h, r, null, Integer.MIN_VALUE, Integer.MAX_VALUE));
    }

    private void item(DrawContext ctx, ItemStack st, int x, int y, float scale) {
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(x, y, 0);
        m.scale(scale, scale, 1f);
        ctx.drawItem(st, 0, 0);
        m.pop();
    }

    private int radius() { return Math.max(3, Config.get().cornerRadius * 2 / 3); }

    private void button(DrawContext ctx, int x, int y, int w, int h, String label, Runnable onClick) {
        boolean hov = in(x, y, w, h);
        int ac = Theme.accentRgb();
        int r = Math.max(3, Config.get().cornerRadius / 2 + 1);
        Ui.rrect(ctx, x, y, w, h, r, hov ? Theme.argb(ac, 75) : Theme.cell(false));
        Ui.rborder(ctx, x, y, w, h, r, hov ? Theme.argb(ac, 240) : Theme.cellBorder(false));
        Ui.textCentered(ctx, textRenderer, label, x + w / 2, y + (h - 8) / 2, hov ? 0xFFFFFFFF : Theme.TEXT, 1f, true);
        addHit(x, y, w, h, onClick);
    }

    private int btnW(String label) { return textRenderer.getWidth(label) + 16; }

    private static int indexOf(List<Cosmetic> l, String key) {
        for (int i = 0; i < l.size(); i++) if (l.get(i).key.equals(key)) return i;
        return 0;
    }

    private static void openFolder(Path p) {
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) pb = new ProcessBuilder("explorer.exe", p.toString());
            else if (os.contains("mac")) pb = new ProcessBuilder("open", p.toString());
            else pb = new ProcessBuilder("xdg-open", p.toString());
            pb.start();
        } catch (Exception e) {
            Log.warn("Klasör açılamadı: " + e);
        }
    }

    private void setTab(Tab t) {
        tab = t;
        lastTab = t;
        activeDrag = null;
        nameFocus = false;
    }

    // ===================================================================================
    //  Çizim
    // ===================================================================================

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        mx = mouseX;
        my = mouseY;
        hits.clear();
        tipName = null;
        tipSub = null;

        long now = System.nanoTime();
        float dt = lastNano == 0 ? 0.016f : Math.min(0.1f, (now - lastNano) / 1e9f);
        lastNano = now;

        Config cfg = Config.get();
        int ac = Theme.accentRgb();
        layout();

        if (cfg.blur) super.renderBackground(ctx, mouseX, mouseY, delta);
        if (cfg.bgDim > 0) {
            int a = cfg.bgDim * 255 / 100;
            ctx.fillGradient(0, 0, width, height, Theme.argb(0x02040A, a / 2), Theme.argb(0x000000, Math.min(255, a + 30)));
        }

        int pr = cfg.cornerRadius + 5;
        Ui.glow(ctx, px, py, pw, ph, pr, ac, 0.6f);
        Ui.rrect(ctx, px, py, pw, ph, pr, Theme.panel());
        Ui.rborder(ctx, px, py, pw, ph, pr, Theme.argb(ac, 175));

        renderTabs(ctx, dt, ac);
        renderHeader(ctx, ac);

        switch (tab) {
            case SWORDS: renderCosmeticGrid(ctx, Category.SWORD, ac); break;
            case TOTEMS: renderCosmeticGrid(ctx, Category.TOTEM, ac); break;
            case SKINS: renderSkins(ctx, ac); break;
            case EFFECTS: renderEffects(ctx, ac); break;
            default: renderSettings(ctx, ac); break;
        }

        renderTooltip(ctx, ac);
    }

    private void layout() {
        int m = Math.max(4, Math.min(width, height) / 28);
        pw = Math.min(width - 2 * m, 800);
        ph = Math.min(height - 2 * m, 470);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        ip = Math.max(8, pw / 52);
        th = Math.max(26, Math.min(40, ph / 8));
        hdrY = py + th + 8;
        conTop = hdrY + 40;
        conBot = py + ph - ip;
    }

    // ---- Sekmeler -----------------------------------------------------------------------

    private void renderTabs(DrawContext ctx, float dt, int ac) {
        int closeSize = Math.max(18, th - 12);
        String closeLabel = "Kapat (ESC)";
        int closeLabelW = textRenderer.getWidth(closeLabel);
        int labelX = px + pw - ip - closeLabelW;
        int closeX = labelX - closeSize - 8;
        int closeY = py + (th - closeSize) / 2 + 2;

        boolean ch = in(closeX, closeY, closeSize + 8 + closeLabelW, closeSize);
        int r = Math.max(3, Config.get().cornerRadius / 2 + 1);
        Ui.rrect(ctx, closeX, closeY, closeSize, closeSize, r, ch ? Theme.argb(0xFF4D4D, 90) : Theme.cell(false));
        Ui.rborder(ctx, closeX, closeY, closeSize, closeSize, r, ch ? 0xFFFF6B6B : Theme.cellBorder(false));
        Ui.cross(ctx, closeX + closeSize / 2, closeY + closeSize / 2, 4, 0xFFFFFFFF);
        Ui.text(ctx, textRenderer, closeLabel, labelX, closeY + (closeSize - 8) / 2, Theme.TEXT_DIM, 1f, true);
        addHit(closeX, closeY, closeSize + 8 + closeLabelW, closeSize, this::close);

        int regionX = px + ip;
        int regionW = closeX - 12 - regionX;
        Tab[] tabs = Tab.values();
        float slot = regionW / (float) tabs.length;
        int iconSize = th >= 34 ? 20 : 16;
        float targetX = 0, targetW = 0;

        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            int lw = textRenderer.getWidth(t.label);
            int cw = iconSize + 7 + lw;
            int cx = Math.round(regionX + slot * (i + 0.5f));
            int x0 = cx - cw / 2;
            int y0 = py + (th - iconSize) / 2 + 1;
            boolean active = t == tab;
            boolean hov = in(x0 - 8, py + 2, cw + 16, th - 2);

            if (active) {
                targetX = x0;
                targetW = cw;
            }
            item(ctx, tabIcons[i], x0, y0, iconSize / 16f);
            int col = active ? 0xFFFFFFFF : (hov ? 0xFFD6E4FA : Theme.TEXT_DIM);
            Ui.text(ctx, textRenderer, t.label, x0 + iconSize + 7, py + (th - 8) / 2 + 1, col, 1f, true);
            final Tab tt = t;
            addHit(x0 - 8, py + 2, cw + 16, th - 2, () -> setTab(tt));
        }

        if (indX < 0) { indX = targetX; indW = targetW; }
        float k = 1f - (float) Math.exp(-dt * 16f);
        indX += (targetX - indX) * k;
        indW += (targetW - indW) * k;
        int ux = Math.round(indX) - 4;
        int uw = Math.round(indW) + 8;
        int uy = py + th - 2;
        if (Config.get().glow) {
            ctx.fill(ux - 2, uy - 1, ux + uw + 2, uy + 4, Theme.argb(ac, 28));
            ctx.fill(ux, uy - 1, ux + uw, uy + 3, Theme.argb(ac, 55));
        }
        ctx.fill(ux, uy, ux + uw, uy + 2, 0xFF000000 | ac);
    }

    private void renderHeader(DrawContext ctx, int ac) {
        int x = px + ip;
        item(ctx, tabIcons[tab.ordinal()], x, hdrY + 2, 1.5f);
        Ui.text(ctx, textRenderer, tab.label, x + 34, hdrY + 1, 0xFFFFFFFF, 1.5f, true);
        Ui.text(ctx, textRenderer, tab.subtitle, x + 34, hdrY + 19, Theme.TEXT_DIM, 1f, true);
    }

    // ---- Izgara + sayfalama -------------------------------------------------------------

    private static final int NAV_W = 34;
    private static final int FOOT_H = 24;

    private int pagesOf(int count, int perPage) { return Math.max(1, (count + perPage - 1) / perPage); }

    /** Izgarayı çizer ve sayfa sayısını döndürür. */
    private int grid(DrawContext ctx, int gx, int gy, int gw, int gh, int cols, int count, int selected,
                     CellPainter painter, IntConsumer onClick, IntFunction<String> nameOf, IntFunction<String> subOf) {
        Config cfg = Config.get();
        int ac = Theme.accentRgb();
        int gap = 6;
        cols = Math.max(2, cols);
        int cw = (gw - (cols - 1) * gap) / cols;
        int rows = 2;
        int chh = Math.min((gh - gap) / 2, (int) (cw * 0.95f));
        if (chh < 26) {
            rows = 1;
            chh = Math.min(gh, (int) (cw * 0.95f));
        }
        int per = cols * rows;
        int pages = pagesOf(count, per);
        int pi = tab.ordinal();
        page[pi] = Config.clamp(page[pi], 0, pages - 1);
        int start = page[pi] * per;
        int rr = radius();

        for (int i = start; i < Math.min(count, start + per); i++) {
            int idx = i - start;
            int cx = gx + (idx % cols) * (cw + gap);
            int cy = gy + (idx / cols) * (chh + gap);
            boolean hover = in(cx, cy, cw, chh);
            boolean sel = i == selected;
            if (sel) Ui.glow(ctx, cx, cy, cw, chh, rr, ac, 0.95f);
            Ui.rrect(ctx, cx, cy, cw, chh, rr, Theme.cell(hover || sel));
            if (sel) Ui.rrect(ctx, cx, cy, cw, chh, rr, Theme.argb(ac, 30));
            Ui.rborder(ctx, cx, cy, cw, chh, rr, sel ? (0xFF000000 | ac) : Theme.cellBorder(hover));
            if (sel) Ui.rborder(ctx, cx + 1, cy + 1, cw - 2, chh - 2, Math.max(0, rr - 1), Theme.argb(ac, 110));
            painter.paint(ctx, i, cx, cy, cw, chh, hover);
            final int ii = i;
            addHit(cx, cy, cw, chh, () -> onClick.accept(ii));
            if (hover) {
                tipName = nameOf.apply(i);
                tipSub = subOf.apply(i);
            }
        }
        return pages;
    }

    private void nav(DrawContext ctx, int nx, int ny, int nh, int pages) {
        int ac = Theme.accentRgb();
        int pi = tab.ordinal();
        int cur = page[pi];
        // kaydırma çubuğu
        int trackX = nx + 2;
        Ui.rrect(ctx, trackX, ny, 3, nh, 1, 0xFF1A2234);
        int thumbH = Math.max(14, nh / pages);
        int thumbY = pages <= 1 ? ny : ny + (nh - thumbH) * cur / (pages - 1);
        Ui.rrect(ctx, trackX, thumbY, 3, thumbH, 1, Theme.argb(ac, 220));

        int cx = nx + 20;
        int midY = ny + nh / 2;
        boolean canUp = cur > 0;
        boolean canDown = cur < pages - 1;
        boolean hu = canUp && in(cx - 12, midY - 40, 24, 22);
        boolean hd = canDown && in(cx - 12, midY + 20, 24, 22);
        Ui.chevron(ctx, cx, midY - 28, true, 5, canUp ? (hu ? 0xFFFFFFFF : (0xFF000000 | ac)) : 0xFF3A4660);
        Ui.chevron(ctx, cx, midY + 30, false, 5, canDown ? (hd ? 0xFFFFFFFF : (0xFF000000 | ac)) : 0xFF3A4660);
        Ui.textCentered(ctx, textRenderer, (cur + 1) + "/" + pages, cx, midY - 4, Theme.TEXT_DIM, 1f, true);
        if (canUp) addHit(cx - 12, midY - 40, 24, 22, () -> page[pi]--);
        if (canDown) addHit(cx - 12, midY + 20, 24, 22, () -> page[pi]++);
    }

    private int iconSize(int cw, int ch) {
        int s = (int) (Math.min(cw, ch) * 0.62f);
        s -= s % 2;
        return Math.max(12, s);
    }

    // ---- Kılıç / Totem sekmeleri ---------------------------------------------------------

    private void renderCosmeticGrid(DrawContext ctx, Category cat, int ac) {
        Config cfg = Config.get();
        List<Cosmetic> list = CosmeticLibrary.list(cat);
        String selKey = cat == Category.SWORD ? cfg.sword : cfg.totem;
        int selected = indexOf(list, selKey);

        int gx = px + ip;
        int gy = conTop;
        int gw = pw - 2 * ip - NAV_W - 6;
        int gh = conBot - FOOT_H - 8 - gy;
        int cols = Config.clamp(gw / 46, 4, 10);

        int pages = grid(ctx, gx, gy, gw, gh, cols, list.size(), selected,
                (c, i, x, y, w, h, hover) -> {
                    Cosmetic co = list.get(i);
                    if (co.icon == null) return;
                    int s = iconSize(w, h);
                    c.drawTexture(co.icon, x + (w - s) / 2, y + (h - s) / 2, s, s, 0f, 0f, co.width, co.height, co.width, co.height);
                },
                i -> {
                    Cosmetic co = list.get(i);
                    if (cat == Category.SWORD) cfg.sword = co.key; else cfg.totem = co.key;
                    KozmetikMod.applyAll();
                },
                i -> list.get(i).name,
                i -> list.get(i).tag());
        nav(ctx, px + pw - ip - NAV_W, gy, gh, pages);

        // alt çubuk
        int fy = conBot - FOOT_H;
        Cosmetic cur = list.isEmpty() ? null : list.get(selected);
        Ui.text(ctx, textRenderer, "Seçili:", gx, fy + 8, Theme.TEXT_DIM, 1f, true);
        if (cur != null) {
            Ui.text(ctx, textRenderer, cur.name, gx + textRenderer.getWidth("Seçili:") + 5, fy + 8, 0xFF000000 | ac, 1f, true);
        }
        boolean applyOn = cat == Category.SWORD ? cfg.applySword : cfg.applyTotem;
        if (!applyOn) {
            Ui.text(ctx, textRenderer, "Uygulama kapalı (Ayarlar'dan aç)", gx + 150, fy + 8, 0xFFFFB347, 1f, true);
        }

        String b1 = "Klasörü Aç";
        String b2 = "Yenile";
        int w2 = btnW(b2);
        int w1 = btnW(b1);
        int bx2 = px + pw - ip - w2;
        int bx1 = bx2 - 6 - w1;
        button(ctx, bx2, fy + 1, w2, FOOT_H - 4, b2, () -> {
            if (client != null) CosmeticLibrary.reload(client);
            KozmetikMod.applyAll();
        });
        button(ctx, bx1, fy + 1, w1, FOOT_H - 4, b1, () -> openFolder(CosmeticLibrary.folder(cat)));
    }

    // ---- Skinler (oyuncu skini değiştirme) ----------------------------------------------

    private static final int SKIN_FOOT_H = 50;

    private void renderSkins(DrawContext ctx, int ac) {
        Config cfg = Config.get();
        List<Cosmetic> list = CosmeticLibrary.list(Category.SKIN);
        int selected = indexOf(list, cfg.skin);

        int pvW = Config.clamp(pw / 4, 110, 170);
        int gx = px + ip;
        int gy = conTop;
        int gw = pw - 2 * ip - NAV_W - pvW - 16;
        int gh = conBot - SKIN_FOOT_H - 8 - gy;
        int cols = Config.clamp(gw / 46, 3, 6);

        final int[] hovered = {-1};
        int pages = grid(ctx, gx, gy, gw, gh, cols, list.size(), selected,
                (c, i, x, y, w, h, hover) -> {
                    Cosmetic co = list.get(i);
                    int s = iconSize(w, h);
                    if (hover) hovered[0] = i;
                    if (co.icon == null) {
                        item(c, new ItemStack(Items.PLAYER_HEAD), x + (w - s) / 2, y + (h - s) / 2, s / 16f);
                    } else {
                        int hx = x + (w - s) / 2;
                        int hy = y + (h - s) / 2;
                        c.drawTexture(co.icon, hx, hy, s, s, 8f, 8f, 8, 8, 64, 64);
                        c.drawTexture(co.icon, hx, hy, s, s, 40f, 8f, 8, 8, 64, 64);
                    }
                },
                i -> {
                    cfg.skin = list.get(i).key;
                    KozmetikMod.applyAll();
                },
                i -> list.get(i).name,
                i -> list.get(i).tag());
        nav(ctx, gx + gw + 6, gy, gh, pages);

        // önizleme kartı
        int cardX = px + pw - ip - pvW;
        int rr = radius() + 2;
        Ui.rrect(ctx, cardX, gy, pvW, gh, rr, Theme.cell(false));
        Ui.rborder(ctx, cardX, gy, pvW, gh, rr, Theme.cellBorder(false));
        Cosmetic show = hovered[0] >= 0 ? list.get(hovered[0]) : (list.isEmpty() ? null : list.get(selected));
        boolean slimToggle = cfg.skin.startsWith("f:");
        if (show != null) {
            Ui.textCentered(ctx, textRenderer, show.name, cardX + pvW / 2, gy + 6, 0xFFFFFFFF, 1f, true);
            Ui.textCentered(ctx, textRenderer, show.tag(), cardX + pvW / 2, gy + 17, Theme.TEXT_DIM, 1f, true);
            Identifier tex = null;
            boolean slim = false;
            if (show.icon != null) {
                tex = show.icon;
                slim = show.slim();
            } else if (client != null && client.player != null && SkinOverride.texture() == null) {
                SkinTextures st = client.player.getSkinTextures();
                tex = st.texture();
                slim = st.model() == SkinTextures.Model.SLIM;
            }
            int availH = gh - 30 - (slimToggle ? 22 : 4);
            int sc = Math.max(1, Math.min((pvW - 12) / 16, availH / 32));
            int fx = cardX + (pvW - 16 * sc) / 2;
            int fy = gy + 30 + Math.max(0, (availH - 32 * sc) / 2);
            if (tex != null) {
                drawSkinFront(ctx, tex, slim, fx, fy, sc);
            } else {
                item(ctx, new ItemStack(Items.PLAYER_HEAD), cardX + pvW / 2 - 16, gy + gh / 2 - 24, 2f);
                Ui.textCentered(ctx, textRenderer, "Kendi skinin", cardX + pvW / 2, gy + gh / 2 + 14, Theme.TEXT_DIM, 1f, true);
            }
        }
        if (slimToggle) {
            int ty = gy + gh - 18;
            Ui.text(ctx, textRenderer, "İnce kol", cardX + 8, ty + 2, Theme.TEXT, 1f, true);
            Ui.toggle(ctx, cardX + pvW - 8 - 24, ty, 24, 12, cfg.slimArms, ac);
            addHit(cardX + 4, ty - 2, pvW - 8, 16, () -> {
                cfg.slimArms = !cfg.slimArms;
                KozmetikMod.applyAll();
            });
        }

        // ---- 1. satır: oyuncu adından skin al
        int rowA = conBot - SKIN_FOOT_H + 4;
        String lab = "Oyuncu adı:";
        Ui.text(ctx, textRenderer, lab, gx, rowA + 5, Theme.TEXT_DIM, 1f, true);
        int fieldX = gx + textRenderer.getWidth(lab) + 6;
        int fieldW = Config.clamp(pw / 4, 100, 150);
        int fr = Math.max(3, cfg.cornerRadius / 2 + 1);
        boolean hovF = in(fieldX, rowA, fieldW, 18);
        Ui.rrect(ctx, fieldX, rowA, fieldW, 18, fr, 0xFF0A101C);
        Ui.rborder(ctx, fieldX, rowA, fieldW, 18, fr, nameFocus ? (0xFF000000 | ac) : Theme.cellBorder(hovF));
        ctx.enableScissor(fieldX + 3, rowA, fieldX + fieldW - 3, rowA + 18);
        if (nameInput.isEmpty() && !nameFocus) {
            Ui.text(ctx, textRenderer, "örn: Notch", fieldX + 5, rowA + 5, 0xFF56627A, 1f, false);
        } else {
            String cur = nameInput + ((nameFocus && (System.currentTimeMillis() / 500) % 2 == 0) ? "_" : "");
            Ui.text(ctx, textRenderer, cur, fieldX + 5, rowA + 5, 0xFFFFFFFF, 1f, true);
        }
        ctx.disableScissor();
        addHit(fieldX, rowA, fieldW, 18, () -> nameFocus = true);

        String getLabel = fetching ? "Bekle..." : "Skini Al";
        int gbW = btnW(getLabel);
        int gbX = fieldX + fieldW + 6;
        button(ctx, gbX, rowA - 1, gbW, 20, getLabel, this::startSkinFetch);
        String st = skinStatus;
        if (st != null && !st.isEmpty()) {
            int sxs = gbX + gbW + 8;
            int maxW = px + pw - ip - sxs;
            if (maxW > 30) {
                int col = skinStatusError ? 0xFFFF7B7B : (st.startsWith("Hazır") ? 0xFF7BE0A0 : Theme.TEXT_DIM);
                ctx.enableScissor(sxs, rowA, sxs + maxW, rowA + 18);
                Ui.text(ctx, textRenderer, st, sxs, rowA + 5, col, 1f, true);
                ctx.disableScissor();
            }
        }

        // ---- 2. satır: seçili + klasör/yenile
        int rowB = conBot - 22;
        Cosmetic cur = list.isEmpty() ? null : list.get(selected);
        Ui.text(ctx, textRenderer, "Seçili:", gx, rowB + 8, Theme.TEXT_DIM, 1f, true);
        if (cur != null) Ui.text(ctx, textRenderer, cur.name, gx + textRenderer.getWidth("Seçili:") + 5, rowB + 8, 0xFF000000 | ac, 1f, true);
        if (!cfg.applySkin) Ui.text(ctx, textRenderer, "Uygulama kapalı (Ayarlar'dan aç)", gx + 150, rowB + 8, 0xFFFFB347, 1f, true);
        String b1 = "Klasörü Aç";
        String b2 = "Yenile";
        int w2 = btnW(b2);
        int w1 = btnW(b1);
        int bx2 = px + pw - ip - w2;
        int bx1 = bx2 - 6 - w1;
        button(ctx, bx2, rowB + 1, w2, 20, b2, () -> {
            if (client != null) CosmeticLibrary.reload(client);
            KozmetikMod.applyAll();
        });
        button(ctx, bx1, rowB + 1, w1, 20, b1, () -> openFolder(CosmeticLibrary.folder(Category.SKIN)));
    }

    private void startSkinFetch() {
        if (fetching) return;
        String n = nameInput.trim();
        if (!SkinFetcher.validName(n)) {
            skinStatus = "Geçersiz oyuncu adı (3-16 harf/rakam/_)";
            skinStatusError = true;
            return;
        }
        fetching = true;
        skinStatus = "Başlatılıyor...";
        skinStatusError = false;
        SkinFetcher.fetch(n, CosmeticLibrary.folder(Category.SKIN), new SkinFetcher.Callback() {
            @Override
            public void status(String message, boolean error) {
                skinStatus = message;
                skinStatusError = error;
            }

            @Override
            public void done(String fileName) {
                fetching = false;
                if (fileName != null) {
                    CosmeticLibrary.reload(MinecraftClient.getInstance());
                    Config.get().skin = "f:" + fileName;
                    KozmetikMod.applyAll();
                }
            }
        });
    }

    /** Skinin ön görünüşü (taban + üst katman). Birim: skin pikseli * sc */
    private void drawSkinFront(DrawContext ctx, Identifier tex, boolean slim, int x, int y, int sc) {
        int aw = slim ? 3 : 4;
        int ax = slim ? 1 : 0;
        // kafa
        part(ctx, tex, x + 4 * sc, y, 8, 8, 8, 8, sc);
        part(ctx, tex, x + 4 * sc, y, 8, 8, 40, 8, sc);
        // gövde
        part(ctx, tex, x + 4 * sc, y + 8 * sc, 8, 12, 20, 20, sc);
        part(ctx, tex, x + 4 * sc, y + 8 * sc, 8, 12, 20, 36, sc);
        // kollar (ekranın solu = oyuncunun sağ kolu)
        part(ctx, tex, x + ax * sc, y + 8 * sc, aw, 12, 44, 20, sc);
        part(ctx, tex, x + ax * sc, y + 8 * sc, aw, 12, 44, 36, sc);
        part(ctx, tex, x + 12 * sc, y + 8 * sc, aw, 12, 36, 52, sc);
        part(ctx, tex, x + 12 * sc, y + 8 * sc, aw, 12, 52, 52, sc);
        // bacaklar
        part(ctx, tex, x + 4 * sc, y + 20 * sc, 4, 12, 4, 20, sc);
        part(ctx, tex, x + 4 * sc, y + 20 * sc, 4, 12, 4, 36, sc);
        part(ctx, tex, x + 8 * sc, y + 20 * sc, 4, 12, 20, 52, sc);
        part(ctx, tex, x + 8 * sc, y + 20 * sc, 4, 12, 4, 52, sc);
    }

    private void part(DrawContext ctx, Identifier tex, int dx, int dy, int w, int h, int u, int v, int sc) {
        ctx.drawTexture(tex, dx, dy, w * sc, h * sc, (float) u, (float) v, w, h, 64, 64);
    }

    // ---- Efektler ------------------------------------------------------------------------

    private void renderEffects(DrawContext ctx, int ac) {
        Config cfg = Config.get();
        List<Effects.Effect> list = Effects.all();
        String selKey = effectMode == 0 ? cfg.hitEffect : cfg.killEffect;
        int selected = 0;
        for (int i = 0; i < list.size(); i++) if (list.get(i).id.equals(selKey)) selected = i;

        // Vuruş / Öldürme anahtarı (başlığın sağında)
        String[] modes = {"Vuruş Efekti", "Öldürme Efekti"};
        int segH = 20;
        int segY = hdrY + 4;
        int total = 0;
        int[] ws = new int[2];
        for (int i = 0; i < 2; i++) {
            ws[i] = btnW(modes[i]);
            total += ws[i];
        }
        int sx = px + pw - ip - total;
        int rr = Math.max(3, cfg.cornerRadius / 2 + 1);
        Ui.rrect(ctx, sx - 2, segY - 2, total + 4, segH + 4, rr + 2, Theme.cell(false));
        Ui.rborder(ctx, sx - 2, segY - 2, total + 4, segH + 4, rr + 2, Theme.cellBorder(false));
        int cx0 = sx;
        for (int i = 0; i < 2; i++) {
            boolean act = i == effectMode;
            boolean hov = in(cx0, segY, ws[i], segH);
            if (act) Ui.rrect(ctx, cx0, segY, ws[i], segH, rr, Theme.argb(ac, 190));
            else if (hov) Ui.rrect(ctx, cx0, segY, ws[i], segH, rr, Theme.argb(ac, 50));
            Ui.textCentered(ctx, textRenderer, modes[i], cx0 + ws[i] / 2, segY + 6, act ? 0xFF06101E : Theme.TEXT, 1f, !act);
            final int mi = i;
            addHit(cx0, segY, ws[i], segH, () -> { effectMode = mi; page[Tab.EFFECTS.ordinal()] = 0; });
            cx0 += ws[i];
        }

        int gx = px + ip;
        int gy = conTop;
        int gw = pw - 2 * ip - NAV_W - 6;
        int gh = conBot - FOOT_H - 8 - gy;
        int cols = Config.clamp(gw / 46, 4, 10);
        final int mode = effectMode;
        int pages = grid(ctx, gx, gy, gw, gh, cols, list.size(), selected,
                (c, i, x, y, w, h, hover) -> {
                    int s = iconSize(w, h);
                    item(c, new ItemStack(list.get(i).icon), x + (w - s) / 2, y + (h - s) / 2, s / 16f);
                },
                i -> {
                    if (mode == 0) cfg.hitEffect = list.get(i).id; else cfg.killEffect = list.get(i).id;
                    Config.save();
                    if (list.get(i).particle != null) Effects.preview(list.get(i), mode == 1);
                },
                i -> list.get(i).name,
                i -> mode == 0 ? "Vuruş efekti" : "Öldürme efekti");
        nav(ctx, px + pw - ip - NAV_W, gy, gh, pages);

        // alt çubuk: aç/kapat, yoğunluk, önizleme
        int fy = conBot - FOOT_H;
        Ui.text(ctx, textRenderer, "Efektler", gx, fy + 8, Theme.TEXT, 1f, true);
        int tgx = gx + textRenderer.getWidth("Efektler") + 8;
        Ui.toggle(ctx, tgx, fy + 6, 24, 12, cfg.effectsEnabled, ac);
        addHit(tgx - 4, fy + 2, 32, 20, () -> { cfg.effectsEnabled = !cfg.effectsEnabled; Config.save(); });

        int sliderX = tgx + 24 + 24 + textRenderer.getWidth("Yoğunluk");
        Ui.text(ctx, textRenderer, "Yoğunluk", tgx + 24 + 14, fy + 8, Theme.TEXT_DIM, 1f, true);
        int sliderW = Math.max(60, Math.min(140, pw / 5));
        float t = (cfg.effectIntensity - 25) / 275f;
        Ui.slider(ctx, sliderX, fy + 6, sliderW, t, ac);
        Ui.text(ctx, textRenderer, cfg.effectIntensity + "%", sliderX + sliderW + 8, fy + 8, Theme.TEXT, 1f, true);
        final int sxx = sliderX;
        final int sww = sliderW;
        hits.add(new Hit(sliderX - 4, fy + 2, sliderW + 8, 18, null, m -> {
            float v = (float) ((m - sxx) / sww);
            cfg.effectIntensity = 25 + Math.round(Config.clamp(Math.round(v * 1000), 0, 1000) / 1000f * 275f);
        }, Integer.MIN_VALUE, Integer.MAX_VALUE));

        String bp = "Önizle";
        int wp = btnW(bp);
        button(ctx, px + pw - ip - wp, fy + 1, wp, FOOT_H - 4, bp, () -> {
            Effects.Effect e = list.get(indexOfEffect(list, effectMode == 0 ? cfg.hitEffect : cfg.killEffect));
            Effects.preview(e, effectMode == 1);
        });
    }

    private static int indexOfEffect(List<Effects.Effect> l, String id) {
        for (int i = 0; i < l.size(); i++) if (l.get(i).id.equals(id)) return i;
        return 0;
    }

    // ---- Ayarlar -------------------------------------------------------------------------

    private void renderSettings(DrawContext ctx, int ac) {
        Config cfg = Config.get();
        int vx = px + ip;
        int vy = conTop - 4;
        int vw = pw - 2 * ip;
        int vh = conBot - vy;

        ctx.enableScissor(vx - 4, vy, vx + vw + 4, vy + vh);
        boolean twoCols = vw >= 420;
        int colW = twoCols ? (vw - 20) / 2 : vw;
        int lx = vx;
        int rx = twoCols ? vx + colW + 20 : vx;
        int y0 = vy - settingsScroll;

        // ---- sol sütun: renkler
        int y = y0;
        y = section(ctx, lx, y, colW, "Menü Rengi", String.format("#%06X", cfg.accent & 0xFFFFFF), vy, vh);
        y = swatches(ctx, lx, y, ACCENT_PRESETS, cfg.accent, v -> cfg.accent = v, vy, vh);
        y = sliderRow(ctx, lx, y, colW, "Kırmızı", (cfg.accent >> 16) & 0xFF, 0, 255, v -> cfg.accent = (cfg.accent & 0x00FFFF) | (v << 16), vy, vh);
        y = sliderRow(ctx, lx, y, colW, "Yeşil", (cfg.accent >> 8) & 0xFF, 0, 255, v -> cfg.accent = (cfg.accent & 0xFF00FF) | (v << 8), vy, vh);
        y = sliderRow(ctx, lx, y, colW, "Mavi", cfg.accent & 0xFF, 0, 255, v -> cfg.accent = (cfg.accent & 0xFFFF00) | v, vy, vh);
        y += 4;
        y = section(ctx, lx, y, colW, "Panel", String.format("#%06X", cfg.panelColor & 0xFFFFFF), vy, vh);
        y = swatches(ctx, lx, y, PANEL_PRESETS, cfg.panelColor, v -> cfg.panelColor = v, vy, vh);
        y = sliderRow(ctx, lx, y, colW, "Panel opaklığı", cfg.panelOpacity, 20, 100, v -> cfg.panelOpacity = v, vy, vh);
        y = sliderRow(ctx, lx, y, colW, "Arka plan karartması", cfg.bgDim, 0, 90, v -> cfg.bgDim = v, vy, vh);
        y = sliderRow(ctx, lx, y, colW, "Köşe yuvarlaklığı", cfg.cornerRadius, 0, 14, v -> cfg.cornerRadius = v, vy, vh);
        int leftEnd = y;

        // ---- sağ sütun: davranış
        y = twoCols ? y0 : leftEnd + 6;
        y = section(ctx, rx, y, colW, "Görünüm", null, vy, vh);
        y = toggleRow(ctx, rx, y, colW, "Parlama efekti", cfg.glow, () -> cfg.glow = !cfg.glow, vy, vh);
        y = toggleRow(ctx, rx, y, colW, "Gökkuşağı teması", cfg.rainbow, () -> cfg.rainbow = !cfg.rainbow, vy, vh);
        y = toggleRow(ctx, rx, y, colW, "Arka plan bulanıklığı", cfg.blur, () -> cfg.blur = !cfg.blur, vy, vh);
        y += 4;
        y = section(ctx, rx, y, colW, "Uygulama", null, vy, vh);
        y = toggleRow(ctx, rx, y, colW, "Kılıç dokusunu uygula", cfg.applySword, () -> { cfg.applySword = !cfg.applySword; KozmetikMod.applyAll(); }, vy, vh);
        y = toggleRow(ctx, rx, y, colW, "Totem dokusunu uygula", cfg.applyTotem, () -> { cfg.applyTotem = !cfg.applyTotem; KozmetikMod.applyAll(); }, vy, vh);
        y = toggleRow(ctx, rx, y, colW, "Skini uygula", cfg.applySkin, () -> { cfg.applySkin = !cfg.applySkin; KozmetikMod.applyAll(); }, vy, vh);
        y = toggleRow(ctx, rx, y, colW, "Özel skinlerde ince kol", cfg.slimArms, () -> { cfg.slimArms = !cfg.slimArms; KozmetikMod.applyAll(); }, vy, vh);
        y = toggleRow(ctx, rx, y, colW, "Vuruş/öldürme efektleri", cfg.effectsEnabled, () -> cfg.effectsEnabled = !cfg.effectsEnabled, vy, vh);
        y += 6;

        // sıfırla düğmesi (kaydırma ile birlikte hareket eder)
        String rl = "Renkleri ve ayarları sıfırla";
        int rw = btnW(rl);
        int ry = y;
        boolean vis = ry + 20 > vy && ry < vy + vh;
        if (vis) {
            boolean hov = in(rx, ry, rw, 20) && my >= vy && my < vy + vh;
            int r = Math.max(3, cfg.cornerRadius / 2 + 1);
            Ui.rrect(ctx, rx, ry, rw, 20, r, hov ? Theme.argb(0xFF4D4D, 85) : Theme.cell(false));
            Ui.rborder(ctx, rx, ry, rw, 20, r, hov ? 0xFFFF6B6B : Theme.cellBorder(false));
            Ui.textCentered(ctx, textRenderer, rl, rx + rw / 2, ry + 6, Theme.TEXT, 1f, true);
            hits.add(new Hit(rx, ry, rw, 20, () -> { Config.reset(); KozmetikMod.applyAll(); }, null, vy, vy + vh));
        }
        y += 24;

        int contentEnd = Math.max(leftEnd, y);
        ctx.disableScissor();

        int contentH = contentEnd - y0;
        settingsMax = Math.max(0, contentH - vh + 4);
        settingsScroll = Config.clamp(settingsScroll, 0, settingsMax);
        if (settingsMax > 0) {
            int trackX = vx + vw + 2;
            Ui.rrect(ctx, trackX, vy, 3, vh, 1, 0xFF1A2234);
            int thumbH = Math.max(16, vh * vh / (contentH + 1));
            int thumbY = vy + (vh - thumbH) * settingsScroll / settingsMax;
            Ui.rrect(ctx, trackX, thumbY, 3, thumbH, 1, Theme.argb(ac, 220));
        }
    }

    private int section(DrawContext ctx, int x, int y, int w, String title, String right, int vy, int vh) {
        if (y + 12 > vy && y < vy + vh) {
            int ac = Theme.accentRgb();
            Ui.text(ctx, textRenderer, title, x, y + 2, 0xFFFFFFFF, 1f, true);
            if (right != null) Ui.text(ctx, textRenderer, right, x + w - textRenderer.getWidth(right), y + 2, 0xFF000000 | ac, 1f, true);
            ctx.fill(x, y + 12, x + w, y + 13, Theme.argb(ac, 60));
        }
        return y + 17;
    }

    private int swatches(DrawContext ctx, int x, int y, int[] colors, int current, IntConsumer set, int vy, int vh) {
        int size = 14;
        int gap = 5;
        if (y + size > vy && y < vy + vh) {
            for (int i = 0; i < colors.length; i++) {
                int sx = x + i * (size + gap);
                boolean sel = (colors[i] & 0xFFFFFF) == (current & 0xFFFFFF);
                boolean hov = in(sx, y, size, size) && my >= vy && my < vy + vh;
                if (sel) Ui.glow(ctx, sx, y, size, size, 3, colors[i], 0.8f);
                Ui.rrect(ctx, sx, y, size, size, 3, 0xFF000000 | colors[i]);
                Ui.rborder(ctx, sx, y, size, size, 3, sel ? 0xFFFFFFFF : (hov ? 0xFFB8C6E0 : 0xFF2A3347));
                final int col = colors[i];
                hits.add(new Hit(sx, y, size, size, () -> set.accept(col), null, vy, vy + vh));
            }
        }
        return y + size + 8;
    }

    private int sliderRow(DrawContext ctx, int x, int y, int w, String label, int value, int min, int max,
                          IntConsumer set, int vy, int vh) {
        int h = 24;
        if (y + h > vy && y < vy + vh) {
            Ui.text(ctx, textRenderer, label, x, y, Theme.TEXT_DIM, 1f, true);
            String vs = String.valueOf(value);
            Ui.text(ctx, textRenderer, vs, x + w - textRenderer.getWidth(vs), y, Theme.TEXT, 1f, true);
            float t = (value - min) / (float) (max - min);
            Ui.slider(ctx, x, y + 11, w, t, Theme.accentRgb());
            final int sx = x;
            final int sw = w;
            hits.add(new Hit(x - 4, y + 8, w + 8, 16, null, mouse -> {
                double f = Math.max(0.0, Math.min(1.0, (mouse - sx) / sw));
                set.accept(min + (int) Math.round(f * (max - min)));
            }, vy, vy + vh));
        }
        return y + h;
    }

    private int toggleRow(DrawContext ctx, int x, int y, int w, String label, boolean value, Runnable flip, int vy, int vh) {
        int h = 18;
        if (y + h > vy && y < vy + vh) {
            boolean hov = in(x, y, w, h) && my >= vy && my < vy + vh;
            Ui.text(ctx, textRenderer, label, x, y + 5, hov ? 0xFFFFFFFF : Theme.TEXT, 1f, true);
            Ui.toggle(ctx, x + w - 26, y + 3, 26, 12, value, Theme.accentRgb());
            hits.add(new Hit(x, y, w, h, flip, null, vy, vy + vh));
        }
        return y + h;
    }

    // ---- İpucu kutusu --------------------------------------------------------------------

    private void renderTooltip(DrawContext ctx, int ac) {
        if (tipName == null) return;
        int w = Math.max(textRenderer.getWidth(tipName), tipSub == null ? 0 : textRenderer.getWidth(tipSub)) + 14;
        int h = tipSub == null ? 18 : 29;
        int tx = Math.min(mx + 12, width - w - 4);
        int ty = my - h - 6;
        if (ty < 4) ty = my + 16;
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(0, 0, 400);
        Ui.rrect(ctx, tx, ty, w, h, 4, 0xF2090E1A);
        Ui.rborder(ctx, tx, ty, w, h, 4, Theme.argb(ac, 230));
        Ui.text(ctx, textRenderer, tipName, tx + 7, ty + 5, 0xFFFFFFFF, 1f, true);
        if (tipSub != null) Ui.text(ctx, textRenderer, tipSub, tx + 7, ty + 17, Theme.TEXT_DIM, 1f, true);
        m.pop();
    }

    // ===================================================================================
    //  Girdi
    // ===================================================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            nameFocus = false;
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (!h.contains(mouseX, mouseY)) continue;
                if (h.drag != null) {
                    activeDrag = h;
                    h.drag.accept(mouseX);
                } else if (h.click != null) {
                    click();
                    h.click.run();
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (activeDrag != null) {
            activeDrag.drag.accept(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (activeDrag != null) {
            activeDrag = null;
            KozmetikMod.applyAll();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int dir = verticalAmount > 0 ? -1 : (verticalAmount < 0 ? 1 : 0);
        if (dir == 0) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        if (tab == Tab.SETTINGS) {
            settingsScroll = Config.clamp(settingsScroll + dir * 16, 0, settingsMax);
        } else {
            page[tab.ordinal()] = Math.max(0, page[tab.ordinal()] + dir);
        }
        return true;
    }

    private void typeChar(char chr) {
        boolean ok = (chr >= 'a' && chr <= 'z') || (chr >= 'A' && chr <= 'Z') || (chr >= '0' && chr <= '9') || chr == '_';
        if (ok && nameInput.length() < 16) nameInput += chr;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (tab == Tab.SKINS && nameFocus) {
            typeChar(chr);
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (tab == Tab.SKINS && nameFocus) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { nameFocus = false; return true; }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) { startSkinFetch(); return true; }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!nameInput.isEmpty()) nameInput = nameInput.substring(0, nameInput.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && client != null) {
                String clip = client.keyboard.getClipboard();
                if (clip != null) {
                    for (char ch : clip.trim().toCharArray()) typeChar(ch);
                }
                return true;
            }
            return true; // odak metin kutusundayken diğer kısayolları yut
        }
        if (KozmetikMod.openKey != null && KozmetikMod.openKey.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_5) {
            setTab(Tab.values()[keyCode - GLFW.GLFW_KEY_1]);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            page[tab.ordinal()] = Math.max(0, page[tab.ordinal()] - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            page[tab.ordinal()] = page[tab.ordinal()] + 1;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
