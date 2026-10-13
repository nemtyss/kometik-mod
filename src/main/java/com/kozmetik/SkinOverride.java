package com.kozmetik;

import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;

/** Yerel oyuncunun skin'ini değiştirmek için mixin'in okuduğu durum. */
public final class SkinOverride {
    private static Identifier texture;
    private static SkinTextures.Model model = SkinTextures.Model.WIDE;

    private SkinOverride() {}

    public static void update() {
        Config cfg = Config.get();
        Cosmetic c = CosmeticLibrary.find(Category.SKIN, cfg.skin);
        if (!cfg.applySkin || c == null || c.isDefault() || c.icon == null) {
            texture = null;
            return;
        }
        texture = c.icon;
        model = c.slim() ? SkinTextures.Model.SLIM : SkinTextures.Model.WIDE;
    }

    public static Identifier texture() { return texture; }

    public static SkinTextures.Model model() { return model; }
}
