package com.kozmetik;

import net.minecraft.util.Identifier;

/** Menüde seçilebilen tek bir öğe: kılıç, totem veya skin dokusu. */
public final class Cosmetic {
    public enum Source { DEFAULT, BUILTIN, FILE }

    public final Category category;
    public final Source source;
    /** "default", "b:<id>" (yerleşik) veya "f:<dosya.png>" (özel) */
    public final String key;
    public final String name;
    /** ARGB değil, NativeImage ABGR formatı. Varsayılan skin için null. */
    public final int[] pixels;
    public final int width;
    public final int height;
    /** Dinamik doku kimliği. Varsayılan skin için null. */
    public final Identifier icon;
    public final boolean slimFlag;

    public Cosmetic(Category category, Source source, String key, String name,
                    int[] pixels, int width, int height, Identifier icon, boolean slimFlag) {
        this.category = category;
        this.source = source;
        this.key = key;
        this.name = name;
        this.pixels = pixels;
        this.width = width;
        this.height = height;
        this.icon = icon;
        this.slimFlag = slimFlag;
    }

    public boolean isDefault() { return source == Source.DEFAULT; }

    public String tag() {
        switch (source) {
            case DEFAULT: return "Varsayılan";
            case BUILTIN: return "Yerleşik";
            default: return "Özel dosya";
        }
    }

    /** Skin için ince (Alex) kol modeli kullanılacak mı? */
    public boolean slim() {
        return slimFlag || (source == Source.FILE && Config.get().slimArms);
    }
}
