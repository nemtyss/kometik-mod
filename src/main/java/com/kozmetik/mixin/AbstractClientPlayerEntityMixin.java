package com.kozmetik.mixin;

import com.kozmetik.SkinOverride;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {
    @Inject(method = "getSkinTextures", at = @At("RETURN"), cancellable = true, require = 0)
    private void kozmetik$overrideSkin(CallbackInfoReturnable<SkinTextures> cir) {
        Identifier tex = SkinOverride.texture();
        if (tex == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || (Object) this != (Object) mc.player) return;
        SkinTextures o = cir.getReturnValue();
        if (o == null) return;
        cir.setReturnValue(new SkinTextures(tex, o.textureUrl(), o.capeTexture(), o.elytraTexture(), SkinOverride.model(), o.secure()));
    }
}
