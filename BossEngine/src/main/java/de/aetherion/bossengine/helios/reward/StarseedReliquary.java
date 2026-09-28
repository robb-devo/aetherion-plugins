package de.aetherion.bossengine.helios.reward;

import de.aetherion.bossengine.helios.core.HMath;
import de.aetherion.bossengine.helios.core.HeliosStage;
import de.aetherion.bossengine.helios.core.Lang;
import de.aetherion.bossengine.helios.core.Score;
import de.aetherion.bossengine.helios.core.Shapes;
import de.aetherion.bossengine.helios.encounter.HeliosEncounter;
import de.aetherion.bossengine.util.TextUtil;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * THE STARSEED RELIQUARY. After the supernova one point of light is left where the star was.
 * For every participant with a share, a starseed capsule (a gold cage around a white-hot seed in a
 * glass shell) detaches from it and glides down a slow spiral to its own pedestal on the Crown,
 * trailing a hairline of light. Each capsule carries its owner's name and opens only for them: the
 * lid lifts, the seed rises and streams into the player, and the share is theirs.
 *
 * <p>Same contract as the reliquary / music box / seed vault: {@code LootService.grantToChest} pays XP
 * and the recap at death and hands the bundles here. Unclaimed shares are delivered at expiry (or on
 * close): online players get them directly, offline ones on their next login ({@link PendingRewards}),
 * because the instance world is wiped afterwards. Nothing is lost.
 */
public final class StarseedReliquary {

    private static final Color GOLD = Color.fromRGB(255, 205, 90);
    private static final Color SEED = Color.fromRGB(255, 250, 230);
    private static final Vector3f POINT = new Vector3f(0f, 10f, 0f);
    private static final float PEDESTAL_R = 11.5f;
    private static final int DESCENT = 90;
    private static final int STAGGER = 12;

    private final HeliosEncounter enc;
    private final HeliosStage stage;
    private final HeliosStage.Group group;
    private final Map<UUID, Capsule> capsules = new LinkedHashMap<>();
    private final int expiresIn;
    private BlockDisplay light;
    private Shapes.Ring halo;
    private int t;
    private boolean delivered;

    public StarseedReliquary(HeliosEncounter enc, Map<UUID, List<ItemStack>> bundles, int claimTicks) {
        this.enc = enc;
        this.stage = enc.stage();
        this.group = stage.group();
        this.expiresIn = Math.max(20 * 20, claimTicks);
        light = group.block(Material.PEARLESCENT_FROGLIGHT.createBlockData(), SEED, 15, true);
        halo = new Shapes.Ring(stage, group, 16, Material.YELLOW_STAINED_GLASS, GOLD, 15, true);
        List<UUID> owners = new ArrayList<>();
        for (Map.Entry<UUID, List<ItemStack>> e : bundles.entrySet()) {
            if (e.getValue() != null && !e.getValue().isEmpty()) {
                owners.add(e.getKey());
            }
        }
        for (int i = 0; i < owners.size(); i++) {
            UUID id = owners.get(i);
            float a = HMath.HALF_PI + (i - (owners.size() - 1) * 0.5f) * Math.min(0.55f, HMath.TAU / Math.max(1, owners.size()));
            capsules.put(id, new Capsule(id, bundles.get(id), a, i * STAGGER));
        }
        enc.score().chord(Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 0.75f, 1f, 1.26f);
    }

    public void tick() {
        t++;
        float pulse = 1f + 0.12f * (float) Math.sin(t * 0.15f);
        stage.push(light, HeliosStage.cube(POINT, 0.55f * pulse, new Quaternionf().rotateY(t * 0.05f).rotateX(t * 0.03f)), 3);
        halo.pose(POINT, new Quaternionf().rotateX(0.3f).rotateY(t * 0.02f), 1.4f * pulse, 0.05f, 0.05f, t * 0.04f, 0f, 0f, 3);
        for (Capsule c : capsules.values()) {
            c.tick();
        }
        if (t == expiresIn - 20 * 30) {
            for (Capsule c : capsules.values()) {
                if (!c.claimed) {
                    Player p = Bukkit.getPlayer(c.owner);
                    if (p != null) {
                        p.sendActionBar(TextUtil.component(Lang.pick(p,
                                "&6Deine Sternensaat wartet noch &f30s&6.", "&6Your starseed waits another &f30s&6.")));
                    }
                }
            }
        }
        if (t >= expiresIn) {
            deliverAll();
        }
    }

    /** All capsules claimed (or delivered) and their opening played out. */
    public boolean finished() {
        if (capsules.isEmpty()) {
            return t > 60;
        }
        for (Capsule c : capsules.values()) {
            if (!c.claimed || c.openT < 40) {
                return false;
            }
        }
        return true;
    }

    public boolean click(Player p, Entity clicked) {
        for (Capsule c : capsules.values()) {
            if (c.hitbox != null && c.hitbox.equals(clicked)) {
                if (!c.owner.equals(p.getUniqueId())) {
                    p.sendActionBar(TextUtil.component(Lang.pick(p,
                            "&7Diese Sternensaat gehört &f" + c.name + "&7.", "&7This starseed belongs to &f" + c.name + "&7.")));
                    return true;
                }
                if (c.landed && !c.claimed) {
                    c.open(p);
                }
                return true;
            }
        }
        return false;
    }

    /** Pays every share that is still waiting. Idempotent. */
    public void deliverAll() {
        if (delivered) {
            return;
        }
        delivered = true;
        for (Capsule c : capsules.values()) {
            if (c.claimed) {
                continue;
            }
            c.claimed = true;
            c.openT = 999;
            Player p = Bukkit.getPlayer(c.owner);
            if (p != null && p.isOnline()) {
                for (ItemStack i : c.items) {
                    PendingRewards.Delivery.give(p, i);
                }
                p.sendMessage(TextUtil.component(Lang.pick(p,
                        "&6✦ &7Deine Sternensaat wurde dir nachgereicht.", "&6✦ &7Your starseed was delivered to you.")));
            } else {
                PendingRewards.store(c.owner, c.items);
            }
        }
    }

    /* ================================================================== capsule */

    private final class Capsule {
        final UUID owner;
        final String name;
        final List<ItemStack> items;
        final float angle;
        final int delay;
        final Vector3f pedestal;
        final Vector3f pos = new Vector3f(POINT);
        final BlockDisplay seed;
        final BlockDisplay shell;
        final BlockDisplay lid;
        final BlockDisplay base;
        final BlockDisplay[] posts = new BlockDisplay[4];
        final Shapes.Line trail;
        final Shapes.Disc mark;
        Interaction hitbox;
        TextDisplay label;
        boolean landed;
        boolean claimed;
        int openT = -1;

        Capsule(UUID owner, List<ItemStack> items, float angle, int delay) {
            this.owner = owner;
            Player p = Bukkit.getPlayer(owner);
            this.name = p != null ? p.getName() : Bukkit.getOfflinePlayer(owner).getName() == null ? "?" : Bukkit.getOfflinePlayer(owner).getName();
            this.items = items;
            this.angle = angle;
            this.delay = delay;
            this.pedestal = HMath.ring(PEDESTAL_R, angle, 0f);
            seed = group.block(Material.PEARLESCENT_FROGLIGHT.createBlockData(), SEED, 15, true);
            shell = group.block(Material.WHITE_STAINED_GLASS.createBlockData(), null, 15, true);
            lid = group.block(Material.GOLD_BLOCK.createBlockData(), GOLD, 15, true);
            base = group.block(Material.GOLD_BLOCK.createBlockData(), null, 13, true);
            for (int i = 0; i < posts.length; i++) {
                posts[i] = group.block(Material.GOLD_BLOCK.createBlockData(), null, 13, true);
            }
            trail = new Shapes.Line(stage, group, Material.WHITE_CONCRETE, SEED);
            mark = new Shapes.Disc(stage, group, Material.YELLOW_STAINED_GLASS, GOLD, 15);
            mark.hide(pedestal, 0);
            trail.hide(POINT, 0);
            pose(0f, 0f, 1);
        }

        void tick() {
            int local = t - delay;
            if (local < 0) {
                return;
            }
            if (!landed) {
                float f = HMath.window(local, 0, DESCENT);
                // A slow spiral down: one and a half turns, easing into the pedestal.
                float e = HMath.inOutCubic(f);
                float turns = (1f - e) * HMath.PI * 1.5f;
                float r = HMath.lerp(0.5f, PEDESTAL_R, e);
                Vector3f p = HMath.ring(r, angle + turns, HMath.lerp(POINT.y, 1.1f, e));
                p.y += (float) Math.sin(f * HMath.PI) * 1.5f;
                pos.set(p);
                pose(local * 0.12f, 0f, 3);
                trail.set(POINT, pos, 0.03f * (1f - f * 0.6f), 3);
                if (local == 0) {
                    enc.score().at(POINT, Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.6f);
                }
                if (f >= 1f) {
                    land();
                }
                return;
            }
            if (claimed && openT >= 0) {
                openT++;
                float o = HMath.window(openT, 0, 30);
                pose(0f, o, 3);
                if (openT == 30) {
                    for (BlockDisplay d : new BlockDisplay[]{seed, shell, lid}) {
                        stage.push(d, HeliosStage.gone(new Vector3f(pos).add(0f, 3f, 0f)), 8);
                    }
                }
                return;
            }
            // Resting: the seed breathes, the lid sways.
            pose(t * 0.02f, 0f, 4);
            if (t % 40 == 0) {
                Player p = Bukkit.getPlayer(owner);
                if (p != null && p.getLocation().distanceSquared(stage.at(pos)) < 36) {
                    p.sendActionBar(TextUtil.component(Lang.pick(p,
                            "&6Rechtsklick: &fdeine Sternensaat öffnen", "&6Right-click: &fopen your starseed")));
                }
            }
        }

        private void land() {
            landed = true;
            trail.hide(pos, 6);
            mark.set(new Vector3f(pedestal.x, 0.02f, pedestal.z), 1.3f, 0.04f, 0f, 1);
            mark.set(new Vector3f(pedestal.x, 0.02f, pedestal.z), 0.9f, 0.04f, 0.4f, 20);
            enc.score().chordAt(pos, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.9f, Score.semi(0), Score.semi(4), Score.semi(7));
            enc.score().at(pos, Sound.BLOCK_AMETHYST_CLUSTER_PLACE, 1f, 0.8f);
            stage.particle(Particle.END_ROD, new Vector3f(pos).add(0f, 0.6f, 0f), 12, 0.35, 0.02);
            org.bukkit.Location at = stage.at(pos.x, 0.05f, pos.z);
            hitbox = stage.world().spawn(at, Interaction.class, i -> {
                i.setPersistent(false);
                i.setInteractionWidth(1.3f);
                i.setInteractionHeight(1.7f);
                i.setResponsive(true);
            });
            label = group.text(new Vector3f(pos.x, 2.3f, pos.z), "&6✦ &f" + name, 1f, Color.fromARGB(80, 0, 0, 0));
            Player p = Bukkit.getPlayer(owner);
            if (p != null) {
                enc.score().to(p, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
            }
        }

        void open(Player p) {
            claimed = true;
            openT = 0;
            enc.score().at(pos, Sound.BLOCK_VAULT_OPEN_SHUTTER, 1f, 1.2f);
            enc.score().chordAt(pos, Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, Score.semi(0), Score.semi(7), Score.semi(12));
            for (ItemStack i : items) {
                PendingRewards.Delivery.give(p, i);
                String nm = i.hasItemMeta() && i.getItemMeta().hasDisplayName() ? i.getItemMeta().getDisplayName() : i.getType().name();
                p.sendMessage(TextUtil.component("&6✦ &7Sternensaat &8» &f" + (i.getAmount() > 1 ? i.getAmount() + "x " : "") + nm));
            }
            stage.world().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 24, 0.4, 0.8, 0.4, 0.02, null, true);
            if (hitbox != null && hitbox.isValid()) {
                hitbox.remove();
            }
            if (label != null) {
                group.kill(label);
            }
        }

        /** @param spin seed rotation, @param open 0 closed … 1 lid lifted and seed risen */
        private void pose(float spin, float open, int interp) {
            Vector3f c = new Vector3f(pos).add(0f, 0.55f, 0f);
            float bob = landed && !claimed ? 0.05f * (float) Math.sin(t * 0.1f) : 0f;
            Quaternionf rot = new Quaternionf().rotateY(spin);
            stage.push(seed, HeliosStage.cube(new Vector3f(c).add(0f, bob + open * 1.6f, 0f), 0.34f * (1f + open * 0.4f), new Quaternionf().rotateXYZ(spin, spin * 1.3f, 0f)), interp);
            stage.push(shell, HeliosStage.cube(new Vector3f(c).add(0f, open * 0.8f, 0f), 0.7f * (1f - open * 0.9f) + 0.001f, rot), interp);
            stage.push(lid, HeliosStage.box(new Vector3f(c).add(0f, 0.42f + open * 1.2f, 0f), new Vector3f(0.82f, 0.08f, 0.82f), new Quaternionf(rot).rotateZ(open * 0.6f)), interp);
            stage.push(base, HeliosStage.box(new Vector3f(c).add(0f, -0.42f, 0f), new Vector3f(0.82f, 0.08f, 0.82f), rot), interp);
            for (int i = 0; i < posts.length; i++) {
                float a = spin + HMath.HALF_PI * i + HMath.PI / 4f;
                Vector3f at = new Vector3f((float) Math.cos(a) * 0.4f, 0f, (float) Math.sin(a) * 0.4f).add(c);
                at.y += open * 0.3f * (i % 2 == 0 ? 1f : -0.3f);
                stage.push(posts[i], HeliosStage.box(at, new Vector3f(0.06f, 0.8f - open * 0.5f, 0.06f), rot), interp);
            }
        }
    }

    /** Removes the reliquary's displays and hitboxes (the stage clear also catches them). */
    public void clear() {
        for (Capsule c : capsules.values()) {
            if (c.hitbox != null && c.hitbox.isValid()) {
                c.hitbox.remove();
            }
        }
        group.clear();
    }
}
