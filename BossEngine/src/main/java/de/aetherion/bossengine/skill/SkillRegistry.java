package de.aetherion.bossengine.skill;

import de.aetherion.bossengine.skill.impl.ActionBarSkill;
import de.aetherion.bossengine.skill.impl.DialogSkill;
import de.aetherion.bossengine.skill.impl.MinionSpawnSkill;
import de.aetherion.bossengine.skill.impl.ParticleAuraSkill;
import de.aetherion.bossengine.skill.impl.PotionSkill;
import de.aetherion.bossengine.skill.impl.SoundSkill;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class SkillRegistry {

    private final Map<String, SkillFactory> factories = new ConcurrentHashMap<>();
    private final Logger logger;

    public SkillRegistry(Logger logger) {
        this.logger = logger;
        registerDefaults();
    }

    public void registerDefaults() {
        register("SOUND", SoundSkill::new);
        register("PARTICLE_AURA", ParticleAuraSkill::new);
        register("POTION", PotionSkill::new);
        register("MINION_SPAWN", MinionSpawnSkill::new);
        register("ARROW_SHOT", de.aetherion.bossengine.skill.impl.ArrowShotSkill::new);
        register("EGG_SHOT", de.aetherion.bossengine.skill.impl.EggShotSkill::new);
        register("LEAP", de.aetherion.bossengine.skill.impl.LeapSkill::new);
        register("SLAM_LEAP", de.aetherion.bossengine.skill.impl.SlamLeapSkill::new);
        register("INK_SHOT", de.aetherion.bossengine.skill.impl.InkShotSkill::new);
        register("BOILING_WATER", de.aetherion.bossengine.skill.impl.BoilingWaterSkill::new);
        register("DRAGON_FIREBALL", de.aetherion.bossengine.skill.impl.DragonFireballSkill::new);
        register("DIALOG", DialogSkill::new);
        register("ACTION_BAR", ActionBarSkill::new);
        register("INVERT_CONTROLS", de.aetherion.bossengine.skill.impl.InvertControlsSkill::new);
        register("CHARM_DRAIN", de.aetherion.bossengine.skill.impl.CharmDrainSkill::new);
        register("VACUUM_PULL", de.aetherion.bossengine.skill.impl.VacuumPullSkill::new);
        register("TELEPORT_PRANK", de.aetherion.bossengine.skill.impl.TeleportPrankSkill::new);
        register("OVERHEAT", de.aetherion.bossengine.skill.impl.OverheatSkill::new);
        register("BLAST_CORES", de.aetherion.bossengine.skill.impl.BlastCoreSkill::new);
        register("BURROW_STRIKE", de.aetherion.bossengine.skill.impl.BurrowStrikeSkill::new);
        register("TAX_COLLECTOR", de.aetherion.bossengine.skill.impl.TaxCollectorSkill::new);
        register("SPECTACLE", de.aetherion.bossengine.skill.impl.SpectacleSkill::new);
        register("RING_BURST", de.aetherion.bossengine.skill.impl.RingBurstSkill::new);
        register("METEOR_RAIN", de.aetherion.bossengine.skill.impl.MeteorRainSkill::new);
    }

    public void register(String type, SkillFactory factory) {
        factories.put(type.toUpperCase(Locale.ROOT), factory);
    }

    public AbstractBossSkill create(ConfigurationSection section) {
        if (section == null) {
            return null;
        }

        String type = section.getString("type", "").toUpperCase(Locale.ROOT);
        SkillFactory factory = factories.get(type);

        if (factory == null) {
            logger.warning("Unknown boss skill type: " + type);
            return null;
        }

        return factory.create(section);
    }
}
