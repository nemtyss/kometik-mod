package com.kozmetik;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Vuruş ve öldürme efektleri (yalnızca senin ekranında görünen partiküller). */
public final class Effects {
    public enum Pattern { BURST, RING, RISE, SPIRAL, SPRAY }

    public static final class Effect {
        public final String id;
        public final String name;
        public final Item icon;
        public final ParticleEffect particle;
        public final Pattern pattern;
        public final int hitCount;
        public final int killCount;
        public final SoundEvent killSound;

        Effect(String id, String name, Item icon, ParticleEffect particle, Pattern pattern,
               int hitCount, int killCount, SoundEvent killSound) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.particle = particle;
            this.pattern = pattern;
            this.hitCount = hitCount;
            this.killCount = killCount;
            this.killSound = killSound;
        }
    }

    private static List<Effect> all;

    public static List<Effect> all() {
        if (all == null) {
            List<Effect> l = new ArrayList<>();
            l.add(new Effect("none", "Efekt Yok", Items.BARRIER, null, Pattern.BURST, 0, 0, null));
            l.add(new Effect("kritik", "Kritik Vuruş", Items.GLOWSTONE_DUST, ParticleTypes.CRIT, Pattern.BURST, 14, 40, null));
            l.add(new Effect("buyulu", "Büyülü Kıvılcım", Items.ENCHANTED_BOOK, ParticleTypes.ENCHANTED_HIT, Pattern.BURST, 14, 45, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME));
            l.add(new Effect("kalp", "Kalp Yağmuru", Items.GOLDEN_APPLE, ParticleTypes.HEART, Pattern.RISE, 4, 14, SoundEvents.ENTITY_PLAYER_LEVELUP));
            l.add(new Effect("ates", "Ateş", Items.BLAZE_POWDER, ParticleTypes.FLAME, Pattern.SPRAY, 12, 40, null));
            l.add(new Effect("ruh_atesi", "Ruh Ateşi", Items.SOUL_TORCH, ParticleTypes.SOUL_FIRE_FLAME, Pattern.SPRAY, 12, 40, null));
            l.add(new Effect("yildiz", "Yıldız Tozu", Items.END_ROD, ParticleTypes.END_ROD, Pattern.BURST, 12, 36, null));
            l.add(new Effect("totem", "Totem Patlaması", Items.TOTEM_OF_UNDYING, ParticleTypes.TOTEM_OF_UNDYING, Pattern.BURST, 14, 50, SoundEvents.ITEM_TOTEM_USE));
            l.add(new Effect("duman", "Gölge Dumanı", Items.COAL, ParticleTypes.SMOKE, Pattern.BURST, 14, 40, null));
            l.add(new Effect("elektrik", "Elektrik", Items.LIGHTNING_ROD, ParticleTypes.ELECTRIC_SPARK, Pattern.BURST, 16, 50, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER));
            l.add(new Effect("ejder", "Ejder Nefesi", Items.DRAGON_BREATH, ParticleTypes.DRAGON_BREATH, Pattern.SPIRAL, 12, 40, null));
            l.add(new Effect("ruh", "Ruhlar", Items.SOUL_SAND, ParticleTypes.SOUL, Pattern.RISE, 6, 20, null));
            l.add(new Effect("kar", "Kar Tanesi", Items.SNOWBALL, ParticleTypes.SNOWFLAKE, Pattern.BURST, 16, 50, null));
            l.add(new Effect("kiraz", "Kiraz Yaprağı", Items.CHERRY_LEAVES, ParticleTypes.CHERRY_LEAVES, Pattern.SPRAY, 10, 34, null));
            l.add(new Effect("lav", "Lav Damlası", Items.LAVA_BUCKET, ParticleTypes.LAVA, Pattern.BURST, 8, 26, null));
            l.add(new Effect("havai", "Havai Fişek", Items.FIREWORK_ROCKET, ParticleTypes.FIREWORK, Pattern.BURST, 14, 60, SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST));
            l.add(new Effect("portal", "Portal", Items.OBSIDIAN, ParticleTypes.PORTAL, Pattern.SPIRAL, 20, 60, null));
            l.add(new Effect("cadi", "Cadı Büyüsü", Items.SPLASH_POTION, ParticleTypes.WITCH, Pattern.BURST, 14, 40, null));
            l.add(new Effect("mutlu", "Zümrüt Parıltısı", Items.EMERALD, ParticleTypes.HAPPY_VILLAGER, Pattern.BURST, 10, 32, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE));
            l.add(new Effect("nota", "Müzik Notası", Items.NOTE_BLOCK, ParticleTypes.NOTE, Pattern.RISE, 5, 16, null));
            all = l;
        }
        return all;
    }

    public static Effect find(String id) {
        for (Effect e : all()) if (e.id.equals(id)) return e;
        return all().get(0);
    }

    // ---- Çalışma durumu ----
    private static int tick;
    private static Entity lastTarget;
    private static int lastHitTick;
    private static double lastX, lastY, lastZ;

    private Effects() {}

    public static ActionResult onAttack(PlayerEntity player, World world, Hand hand, Entity entity, EntityHitResult hit) {
        try {
            if (!world.isClient()) return ActionResult.PASS;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || player != mc.player) return ActionResult.PASS;
            Config cfg = Config.get();
            if (!cfg.effectsEnabled) return ActionResult.PASS;
            if (entity instanceof LivingEntity) {
                lastTarget = entity;
                lastHitTick = tick;
                lastX = entity.getX();
                lastY = entity.getBodyY(0.5);
                lastZ = entity.getZ();
            }
            Effect e = find(cfg.hitEffect);
            if (e.particle != null) {
                spawn(world, e, entity.getX(), entity.getBodyY(0.6), entity.getZ(), entity.getWidth(), entity.getHeight(), false);
            }
        } catch (Throwable t) {
            Log.error("Vuruş efekti hatası", t);
        }
        return ActionResult.PASS;
    }

    public static void tick(MinecraftClient mc) {
        tick++;
        if (lastTarget == null) return;
        if (mc.world == null || tick - lastHitTick > 140) {
            lastTarget = null;
            return;
        }
        try {
            if (lastTarget instanceof LivingEntity) {
                LivingEntity le = (LivingEntity) lastTarget;
                if (le.getHealth() <= 0.0F) {
                    Config cfg = Config.get();
                    Effect e = find(cfg.killEffect);
                    if (cfg.effectsEnabled && e.particle != null) {
                        spawn(mc.world, e, lastX, lastY, lastZ, le.getWidth(), le.getHeight(), true);
                    }
                    lastTarget = null;
                    return;
                }
                lastX = le.getX();
                lastY = le.getBodyY(0.5);
                lastZ = le.getZ();
            }
        } catch (Throwable t) {
            lastTarget = null;
            Log.error("Öldürme efekti hatası", t);
        }
    }

    /** Menüdeki "Önizle" düğmesi: oyuncunun önünde efekti oynatır. */
    public static void preview(Effect e, boolean kill) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null || e.particle == null) return;
        double yaw = Math.toRadians(mc.player.getYaw());
        double px = mc.player.getX() - Math.sin(yaw) * 2.5;
        double pz = mc.player.getZ() + Math.cos(yaw) * 2.5;
        spawn(mc.world, e, px, mc.player.getY() + 1.0, pz, 0.6, 1.8, kill);
    }

    private static void spawn(World w, Effect e, double x, double y, double z, double width, double height, boolean kill) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double mult = Config.get().effectIntensity / 100.0;
        int n = Math.max(1, (int) Math.round((kill ? e.killCount : e.hitCount) * mult));
        double rad = Math.max(0.3, width * 0.8);
        switch (e.pattern) {
            case RING:
                for (int i = 0; i < n; i++) {
                    double a = Math.PI * 2 * i / n;
                    w.addParticle(e.particle, x + Math.cos(a) * rad, y - height * 0.4, z + Math.sin(a) * rad,
                            Math.cos(a) * 0.05, 0.02, Math.sin(a) * 0.05);
                }
                break;
            case RISE:
                for (int i = 0; i < n; i++) {
                    w.addParticle(e.particle, x + (r.nextDouble() - 0.5) * rad * 1.5, y + (r.nextDouble() - 0.3) * height * 0.6,
                            z + (r.nextDouble() - 0.5) * rad * 1.5, 0.0, 0.06 + r.nextDouble() * 0.06, 0.0);
                }
                break;
            case SPIRAL:
                for (int i = 0; i < n; i++) {
                    double a = i * 0.7;
                    double hy = (double) i / n * height * 1.4;
                    w.addParticle(e.particle, x + Math.cos(a) * rad, y - height * 0.5 + hy, z + Math.sin(a) * rad,
                            -Math.sin(a) * 0.04, 0.03, Math.cos(a) * 0.04);
                }
                break;
            case SPRAY:
                for (int i = 0; i < n; i++) {
                    w.addParticle(e.particle, x + (r.nextDouble() - 0.5) * rad, y + (r.nextDouble() - 0.5) * height * 0.5,
                            z + (r.nextDouble() - 0.5) * rad, (r.nextDouble() - 0.5) * 0.18, 0.08 + r.nextDouble() * 0.16,
                            (r.nextDouble() - 0.5) * 0.18);
                }
                break;
            default: // BURST
                for (int i = 0; i < n; i++) {
                    w.addParticle(e.particle, x + (r.nextDouble() - 0.5) * rad, y + (r.nextDouble() - 0.5) * height * 0.6,
                            z + (r.nextDouble() - 0.5) * rad, (r.nextDouble() - 0.5) * 0.5, r.nextDouble() * 0.35,
                            (r.nextDouble() - 0.5) * 0.5);
                }
                break;
        }
        if (kill && e.killSound != null) {
            w.playSound(x, y, z, e.killSound, SoundCategory.PLAYERS, 0.7F, 1.0F, false);
        }
        if (kill && e.pattern != Pattern.RING) {
            // öldürmede ekstra halka
            int ring = Math.max(8, n / 2);
            for (int i = 0; i < ring; i++) {
                double a = Math.PI * 2 * i / ring;
                w.addParticle(e.particle, x + Math.cos(a) * rad, y - height * 0.45, z + Math.sin(a) * rad,
                        Math.cos(a) * 0.08, 0.03, Math.sin(a) * 0.08);
            }
        }
    }
}
