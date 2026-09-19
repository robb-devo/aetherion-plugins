package de.aetherion.bossengine.util;

import de.aetherion.core.AetherKeys;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.UUID;

public final class BossKeys {

    private final NamespacedKey templateKey;
    private final NamespacedKey instanceKey;
    private final NamespacedKey minionKey;
    private final NamespacedKey spawnItemKey;
    private final NamespacedKey eggDamageKey;
    private final NamespacedKey eggKnockbackKey;
    private final NamespacedKey inkDamageKey;
    private final NamespacedKey inkBlindKey;
    private final NamespacedKey inkSlowKey;
    private final NamespacedKey inkPoisonKey;
    private final NamespacedKey stormBoltKey;
    private final NamespacedKey beamFxKey;
    private final NamespacedKey meteorKey;
    private final NamespacedKey debrisKey;
    private final NamespacedKey iceShardKey;
    private final NamespacedKey hearthBoltKey;
    private final NamespacedKey frostHearthKey;
    private final NamespacedKey compactHearthsKey;

    public BossKeys(Plugin plugin) {
        this.templateKey = AetherKeys.BOSS_ID;
        this.instanceKey = new NamespacedKey(plugin, "instance_id");
        this.minionKey = AetherKeys.BOSS_MINION;
        this.spawnItemKey = new NamespacedKey(plugin, "spawn_item");
        this.eggDamageKey = new NamespacedKey(plugin, "egg_damage");
        this.eggKnockbackKey = new NamespacedKey(plugin, "egg_knockback");
        this.inkDamageKey = new NamespacedKey(plugin, "ink_damage");
        this.inkBlindKey = new NamespacedKey(plugin, "ink_blind");
        this.inkSlowKey = new NamespacedKey(plugin, "ink_slow");
        this.inkPoisonKey = new NamespacedKey(plugin, "ink_poison");
        this.stormBoltKey = new NamespacedKey(plugin, "storm_bolt");
        this.beamFxKey = new NamespacedKey(plugin, "beam_fx");
        this.meteorKey = new NamespacedKey(plugin, "meteor");
        this.debrisKey = new NamespacedKey(plugin, "debris");
        this.iceShardKey = new NamespacedKey(plugin, "ice_shard");
        this.hearthBoltKey = new NamespacedKey(plugin, "hearth_bolt");
        this.frostHearthKey = new NamespacedKey(plugin, "frost_hearth");
        this.compactHearthsKey = new NamespacedKey(plugin, "compact_hearths");
    }

    public NamespacedKey templateKey() {
        return templateKey;
    }

    public NamespacedKey instanceKey() {
        return instanceKey;
    }

    public NamespacedKey minionKey() {
        return minionKey;
    }

    public NamespacedKey spawnItemKey() {
        return spawnItemKey;
    }

    public NamespacedKey eggDamageKey() {
        return eggDamageKey;
    }

    public NamespacedKey eggKnockbackKey() {
        return eggKnockbackKey;
    }

    public NamespacedKey inkDamageKey() {
        return inkDamageKey;
    }

    public NamespacedKey inkBlindKey() {
        return inkBlindKey;
    }

    public NamespacedKey inkSlowKey() {
        return inkSlowKey;
    }

    public NamespacedKey inkPoisonKey() {
        return inkPoisonKey;
    }

    public NamespacedKey stormBoltKey() {
        return stormBoltKey;
    }

    public NamespacedKey beamFxKey() {
        return beamFxKey;
    }

    public void tagBeamFx(Entity entity, UUID instanceId) {
        if (entity == null || instanceId == null) {
            return;
        }
        entity.getPersistentDataContainer().set(beamFxKey, PersistentDataType.STRING, instanceId.toString());
    }

    public boolean isBeamFx(Entity entity) {
        return entity != null
                && entity.getPersistentDataContainer().has(beamFxKey, PersistentDataType.STRING);
    }

    public void tagMeteor(Entity entity, double damage) {
        if (entity == null) {
            return;
        }
        entity.getPersistentDataContainer().set(meteorKey, PersistentDataType.DOUBLE, Math.max(1.0, damage));
    }

    public boolean isMeteor(Entity entity) {
        return entity != null
                && entity.getPersistentDataContainer().has(meteorKey, PersistentDataType.DOUBLE);
    }

    public double meteorDamage(Entity entity) {
        if (!isMeteor(entity)) {
            return 0;
        }
        Double damage = entity.getPersistentDataContainer().get(meteorKey, PersistentDataType.DOUBLE);
        return damage == null ? 0 : damage;
    }

    public void tagDebris(Entity entity) {
        if (entity != null) {
            entity.getPersistentDataContainer().set(debrisKey, PersistentDataType.BYTE, (byte) 1);
        }
    }

    public boolean isDebris(Entity entity) {
        return entity != null
                && entity.getPersistentDataContainer().has(debrisKey, PersistentDataType.BYTE);
    }

    public NamespacedKey iceShardKey() {
        return iceShardKey;
    }

    public void tagIceShard(Entity entity, double damage, int slowTicks) {
        if (entity == null) {
            return;
        }
        PersistentDataContainer data = entity.getPersistentDataContainer();
        data.set(iceShardKey, PersistentDataType.BYTE, (byte) 1);
        data.set(inkDamageKey, PersistentDataType.DOUBLE, Math.max(1.0, damage));
        data.set(inkSlowKey, PersistentDataType.INTEGER, Math.max(0, slowTicks));
    }

    public boolean isIceShard(Entity entity) {
        return entity != null
                && entity.getPersistentDataContainer().has(iceShardKey, PersistentDataType.BYTE);
    }

    public void tagHearthBolt(Entity entity, UUID instanceId) {
        if (entity == null || instanceId == null) {
            return;
        }
        entity.getPersistentDataContainer().set(hearthBoltKey, PersistentDataType.STRING, instanceId.toString());
    }

    public boolean isHearthBolt(Entity entity) {
        return entity != null
                && entity.getPersistentDataContainer().has(hearthBoltKey, PersistentDataType.STRING);
    }

    public void tagFrostHearth(Entity entity, UUID instanceId) {
        if (entity == null || instanceId == null) {
            return;
        }
        entity.getPersistentDataContainer().set(frostHearthKey, PersistentDataType.STRING, instanceId.toString());
    }

    /** Endless XL schem test: keep hearth square tight and clickable. Floor 2 unaffected. */
    public void tagCompactHearths(Entity entity) {
        if (entity != null) {
            entity.getPersistentDataContainer().set(compactHearthsKey, PersistentDataType.BYTE, (byte) 1);
        }
    }

    public boolean hasCompactHearths(Entity entity) {
        return entity != null
                && entity.getPersistentDataContainer().has(compactHearthsKey, PersistentDataType.BYTE);
    }

    public boolean isFrostHearth(Entity entity) {
        return frostHearthInstance(entity).isPresent();
    }

    public Optional<UUID> frostHearthInstance(Entity entity) {
        if (entity == null) {
            return Optional.empty();
        }
        String raw = entity.getPersistentDataContainer().get(frostHearthKey, PersistentDataType.STRING);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public void tagStormBolt(Entity entity) {
        if (entity != null) {
            entity.getPersistentDataContainer().set(stormBoltKey, PersistentDataType.BYTE, (byte) 1);
        }
    }

    public boolean isStormBolt(Entity entity) {
        return entity != null
                && entity.getPersistentDataContainer().has(stormBoltKey, PersistentDataType.BYTE);
    }

    public void tagBoss(Entity entity, String templateId, UUID instanceId) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        data.set(templateKey, PersistentDataType.STRING, templateId);
        data.set(instanceKey, PersistentDataType.STRING, instanceId.toString());
    }

    public void tagMinion(Entity entity, UUID bossInstanceId) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        data.set(minionKey, PersistentDataType.STRING, bossInstanceId.toString());
    }

    public boolean isBoss(Entity entity) {
        return entity != null
                && entity.getPersistentDataContainer().has(templateKey, PersistentDataType.STRING);
    }

    public boolean isMinion(Entity entity) {
        return entity != null
                && entity.getPersistentDataContainer().has(minionKey, PersistentDataType.STRING);
    }

    public Optional<String> templateId(Entity entity) {
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                entity.getPersistentDataContainer().get(templateKey, PersistentDataType.STRING)
        );
    }

    public Optional<UUID> instanceId(Entity entity) {
        if (entity == null) {
            return Optional.empty();
        }
        String raw = entity.getPersistentDataContainer().get(instanceKey, PersistentDataType.STRING);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public void tagSpawnItem(ItemMeta meta, String spawnItemId) {
        meta.getPersistentDataContainer().set(
                spawnItemKey,
                PersistentDataType.STRING,
                spawnItemId
        );
    }

    public Optional<String> spawnItemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                item.getItemMeta().getPersistentDataContainer().get(
                        spawnItemKey,
                        PersistentDataType.STRING
                )
        );
    }
}
