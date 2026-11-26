package de.aetherion.pit.brawl;

import de.aetherion.pit.hub.HubModels;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Firework;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** What happens where somebody went over the edge, chosen at the outfitter. Shown for the one who pushed. */
public enum BrawlEffect {

    PUFF(0, Material.FEATHER, "Wind Puff", "Windstoß", "A puff of cloud. Classic.", "Ein Wölkchen. Klassiker."),
    PETALS(60, Material.PINK_PETALS, "Cherry Storm", "Kirschsturm", "A burst of cherry petals.", "Ein Schwall Kirschblüten."),
    SQUEAK(90, Material.YELLOW_DYE, "Squeak!", "Quietsch!", "A rubber duckling pops up and squeaks.",
            "Ein Gummi-Entchen taucht auf und quietscht."),
    SOUL(120, Material.SOUL_LANTERN, "Soul Wisp", "Seelenlicht", "Blue soul flames spiral up.", "Blaue Seelenflammen wirbeln hoch."),
    THUNDER(160, Material.LIGHTNING_ROD, "Thunderclap", "Donnerschlag", "A flash and a crack of thunder.",
            "Ein Blitz und ein Donnerknall."),
    STARS(220, Material.FIREWORK_ROCKET, "Starfall", "Sternregen", "A burst of shooting stars.", "Ein Schwarm Sternschnuppen.");

    public final int price;
    public final Material icon;
    public final String en;
    public final String de;
    public final String loreEn;
    public final String loreDe;

    BrawlEffect(int price, Material icon, String en, String de, String loreEn, String loreDe) {
        this.price = price;
        this.icon = icon;
        this.en = en;
        this.de = de;
        this.loreEn = loreEn;
        this.loreDe = loreDe;
    }

    public String name(boolean german) {
        return german ? de : en;
    }

    public void play(Plugin plugin, Location at) {
        World w = at.getWorld();
        if (w == null) {
            return;
        }
        Location c = at.clone().add(0, 0.6, 0);
        switch (this) {
            case PUFF -> {
                w.spawnParticle(Particle.CLOUD, c, 30, 0.5, 0.4, 0.5, 0.04);
                w.spawnParticle(Particle.POOF, c, 12, 0.3, 0.3, 0.3, 0.05);
                w.playSound(c, Sound.ENTITY_BREEZE_WIND_BURST, 0.9f, 1.0f);
            }
            case PETALS -> {
                w.spawnParticle(Particle.CHERRY_LEAVES, c.clone().add(0, 0.6, 0), 70, 0.9, 0.7, 0.9, 0.0);
                w.spawnParticle(Particle.DUST, c, 20, 0.5, 0.5, 0.5, 0, new Particle.DustOptions(Color.fromRGB(0xF8BBD0), 1.2f));
                w.playSound(c, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.4f);
                w.playSound(c, Sound.BLOCK_CHERRY_LEAVES_BREAK, 1.0f, 1.0f);
            }
            case SQUEAK -> {
                w.playSound(c, Sound.ENTITY_CHICKEN_HURT, 1.2f, 1.9f);
                w.playSound(c, Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.6f, 1.7f);
                w.spawnParticle(Particle.NOTE, c.clone().add(0, 0.5, 0), 4, 0.4, 0.2, 0.4, 1.0);
                ItemDisplay duck = w.spawn(c, ItemDisplay.class, d -> {
                    d.setPersistent(false);
                    d.addScoreboardTag(SkyBrawl.TAG);
                    d.setItemStack(HubModels.item(HubModels.DUCKLING));
                    d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                    d.setBrightness(new Display.Brightness(15, 15));
                    d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.05f, 0.05f, 0.05f),
                            HubModels.flip()));
                });
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    duck.setInterpolationDelay(0);
                    duck.setInterpolationDuration(8);
                    duck.setTransformation(new Transformation(new Vector3f(0, 0.5f, 0),
                            new Quaternionf().rotateY((float) Math.PI), new Vector3f(1.1f, 1.1f, 1.1f), HubModels.flip()));
                }, 2L);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    duck.setInterpolationDelay(0);
                    duck.setInterpolationDuration(10);
                    duck.setTransformation(new Transformation(new Vector3f(0, 1.6f, 0),
                            new Quaternionf().rotateY((float) Math.PI * 3), new Vector3f(0.05f, 0.05f, 0.05f), HubModels.flip()));
                }, 30L);
                Bukkit.getScheduler().runTaskLater(plugin, duck::remove, 42L);
            }
            case SOUL -> {
                for (int i = 0; i < 24; i++) {
                    double a = i * Math.PI / 6;
                    Location p = c.clone().add(Math.cos(a) * 0.6, i * 0.08, Math.sin(a) * 0.6);
                    w.spawnParticle(Particle.SOUL_FIRE_FLAME, p, 1, 0, 0, 0, 0.01);
                }
                w.spawnParticle(Particle.SOUL, c, 8, 0.4, 0.4, 0.4, 0.02);
                w.playSound(c, Sound.PARTICLE_SOUL_ESCAPE, 1.4f, 0.9f);
                w.playSound(c, Sound.BLOCK_SOUL_SAND_BREAK, 0.8f, 0.7f);
            }
            case THUNDER -> {
                w.spawnParticle(Particle.FLASH, c, 1, 0, 0, 0, 0);
                w.spawnParticle(Particle.ELECTRIC_SPARK, c, 45, 0.6, 0.8, 0.6, 0.3);
                w.playSound(c, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.9f, 1.2f);
                w.playSound(c, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.35f, 1.6f);
            }
            case STARS -> {
                Firework fw = w.spawn(c.clone().add(0, 0.8, 0), Firework.class, f -> {
                    FireworkMeta meta = f.getFireworkMeta();
                    meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.STAR)
                            .withColor(Color.fromRGB(0xFFF59D), Color.fromRGB(0x80DEEA)).withFade(Color.WHITE).trail(true).build());
                    meta.setPower(0);
                    f.setFireworkMeta(meta);
                    f.addScoreboardTag(SkyBrawl.TAG);
                });
                Bukkit.getScheduler().runTaskLater(plugin, fw::detonate, 1L);
            }
        }
    }
}
