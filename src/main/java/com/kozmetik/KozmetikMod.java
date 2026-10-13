package com.kozmetik;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class KozmetikMod implements ClientModInitializer {
    public static final String ID = "kozmetik";
    public static KeyBinding openKey;

    private static boolean pendingOpen;
    private static boolean started;

    @Override
    public void onInitializeClient() {
        Config.load(FabricLoader.getInstance().getConfigDir());
        CosmeticLibrary.init(FabricLoader.getInstance().getGameDir());

        openKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.kozmetik.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.kozmetik"));

        ClientTickEvents.END_CLIENT_TICK.register(KozmetikMod::onTick);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("kozmetik").executes(ctx -> {
                    pendingOpen = true;
                    return 1;
                })));

        AttackEntityCallback.EVENT.register(Effects::onAttack);
        Log.info("Kozmetik modu yüklendi. Menü tuşu: K, komut: /kozmetik");
    }

    private static void onTick(MinecraftClient mc) {
        try {
            if (!started) {
                if (mc.getResourceManager() == null || mc.getTextureManager() == null) return;
                CosmeticLibrary.reload(mc);
                SkinOverride.update();
                AtlasPatcher.markDirty();
                started = true;
            }
            while (openKey.wasPressed()) {
                if (mc.currentScreen == null && mc.player != null) mc.setScreen(new CosmeticsScreen());
            }
            if (pendingOpen && mc.currentScreen == null) {
                pendingOpen = false;
                if (mc.player != null) mc.setScreen(new CosmeticsScreen());
            }
            AtlasPatcher.tick(mc);
            Effects.tick(mc);
        } catch (Throwable t) {
            Log.error("Kozmetik tick hatası", t);
        }
    }

    /** Menüden bir seçim değiştiğinde çağrılır. */
    public static void applyAll() {
        SkinOverride.update();
        AtlasPatcher.markDirty();
        Config.save();
    }
}
