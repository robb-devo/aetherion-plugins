package de.aetherion.aethermobs.pet.skill;

import de.aetherion.aethermobs.pet.PetInstance;
import de.aetherion.items.model.Rarity;

public enum PetSkill {

    NONE(
            "None",
            "This pet has no equipped skill yet."
    ),

    WOLF_SPEED(
            "Pack Rush",
            "Grants Speed while this pet is equipped."
    ),

    BAT_NIGHT_VISION(
            "Echolocation",
            "Grants Night Vision while this pet is equipped."
    ),

    DOLPHIN_SWIM(
            "Ocean Grace",
            "Grants Dolphin's Grace while this pet is equipped."
    ),

    CREEPER_BURST(
            "Unstable Charge",
            "Every 5th melee hit triggers a small explosion."
    ),

    GUARDIAN_BEAM(
            "Prism Laser",
            "Fires an animated beam at nearby hostiles."
    ),

    HAWK_GLIDE(
            "Thermal Hop",
            "Double-tap space to hop and glide."
    ),

    OCELOT_FISHING(
            "River Cat",
            "Fish bite much faster while this pet is equipped."
    ),

    GOAT_JUMP(
            "Cliff Bound",
            "Grants Jump Boost while this pet is equipped."
    ),

    ALLAY_HOARD(
            "Hoard Charm",
            "Increases the chance of rare boss drops."
    ),

    GLOW_SQUID_SIGHT(
            "Abyssal Beacon",
            "Nearby wild pet names glow through walls."
    ),

    PARROT_LOOKOUT(
            "Lookout",
            "Nearby hostiles glow through walls."
    ),

    COD_SCHOOL(
            "Bait School",
            "Fish bite a bit faster while this pet is equipped."
    ),

    SALMON_RUN(
            "Upstream",
            "Grants Dolphin's Grace while you are in water."
    ),

    PUFFER_INFLATE(
            "Inflate",
            "Poisons attackers that hit you."
    ),

    TROPICAL_REEF(
            "Reef Charm",
            "Increases pet catch rate while equipped."
    ),

    FARM_CARROT(
            "Carrot Compact",
            "Chance to compress harvested carrots."
    ),

    FARM_WHEAT(
            "Hay Compact",
            "Chance to compress harvested wheat."
    ),

    FARM_POTATO(
            "Spud Laundering",
            "Chance to compress harvested potatoes. The sack does the taxes."
    ),

    YETI_HIDE(
            "Thick Hide",
            "Grants Resistance while this pet is equipped."
    ),

    SNOWFLAKE_BOLT(
            "Rime Bolt",
            "Pulses frost at nearby hostiles. A tiny mage with opinions."
    ),

    ICE_DRAGON_FROST(
            "Glacial Lock",
            "Freezes the nearest hostile, bosses included. Mythic hits two."
    ),

    FIRE_DRAGON_BREATH(
            "Cinder Plume",
            "Spits a short fire cloud. Starts shy. Level 100 does not."
    ),

    WATER_DRAGON_GYRE(
            "Abyssal Gyre",
            "Spins the nearest hostile in a water tornado."
    ),

    MINING_DRAGON_VEIN(
            "Vein Burst",
            "Cracks the nearest hostile with ore-shock. Mining's mythic temper."
    ),

    /** @deprecated legacy id — same as {@link #MINING_DRAGON_VEIN} */
    POISON_DRAGON_MIASMA(
            "Vein Burst",
            "Cracks the nearest hostile with ore-shock. Mining's mythic temper."
    ),

    LIGHTNING_DRAGON_CHAIN(
            "Chain Bolt",
            "Arcs lightning through nearby hostiles."
    ),

    BLAZE_FLARE(
            "Pilot Light",
            "A small nether flare. Rude, compact, on fire."
    ),

    SLIME_BOUNCE(
            "Bounce Tax",
            "Slime splash + trampoline feet. Sneak to stick the landing."
    ),

    GHAST_BOLT(
            "Tear Shot",
            "A delayed fire burst. The scream is optional."
    ),

    TURTLE_SHELL(
            "Shell Guard",
            "You cannot be knocked back while this pet is equipped."
    ),

    PIG_TRUFFLE(
            "Truffle Sort",
            "Chance to compress any harvested crop."
    ),

    COW_MILK(
            "Dairy Reset",
            "Clears a negative effect every few seconds."
    ),

    CAVE_SPIDER_VENOM(
            "Cave Venom",
            "Melee hits poison the target."
    ),

    ZOMBIE_HUNGER(
            "Rot Bite",
            "Melee hits inflict Hunger."
    ),

    SKELETON_MARK(
            "Bone Mark",
            "Melee hits make the target glow."
    ),

    SQUID_INK(
            "Ink Cloud",
            "Attackers are blinded."
    ),

    AXOLOTL_MEND(
            "Play Dead",
            "Grants Regeneration while you are in water."
    ),

    WITHER_AURA(
            "Wither Kiss",
            "Attackers are withered."
    ),

    AETHERION_PULSE(
            "Aether Pulse",
            "Pulses aether at nearby hostiles."
    ),

    BEE_STING(
            "Stinger",
            "Attackers are poisoned."
    ),

    PIGEON_SCOUT(
            "City Scout",
            "Nearby hostiles glow through walls."
    ),

    PANDA_SNOOZE(
            "Bamboo Nap",
            "Grants Regeneration while this pet is equipped."
    ),

    LLAMA_SPIT(
            "Diplomatic Spit",
            "Spits at nearby hostiles. Knockback included."
    ),

    FOX_POUNCE(
            "Night Pounce",
            "Night senses. Move speed from fox pet stats (no potion stack)."
    ),

    RABBIT_HOP(
            "Lucky Hop",
            "Grants Jump Boost while this pet is equipped."
    ),

    POLAR_GUARD(
            "Ice Guard",
            "Grants Strength while this pet is equipped."
    ),

    CAMEL_PACE(
            "Caravan Pace",
            "Grants Haste while this pet is equipped."
    ),

    ARMADILLO_CURL(
            "Curl Up",
            "Sneaking grants Resistance."
    ),

    DUNGEON_ROT(
            "Dungeon Rot",
            "Melee hits wither the target."
    ),

    FROG_LEAP(
            "Lily Leap",
            "Grants Jump Boost while this pet is equipped."
    ),

    WITCH_BREW(
            "Hex Draft",
            "Poisons attackers that hit you."
    ),

    MUD_COAT(
            "Mud Coat",
            "Grants Resistance while this pet is equipped."
    ),

    DEER_ALERT(
            "Woodland Watch",
            "Nearby hostiles glow through walls."
    ),

    SQUIRREL_SCRAMBLE(
            "Nut Rush",
            "Grants Speed while this pet is equipped."
    ),

    BOAR_CHARGE(
            "Tusk Charge",
            "Grants Strength while this pet is equipped."
    ),

    OWL_WATCH(
            "Night Eyes",
            "Nearby hostiles glow through walls."
    ),

    SHEEP_FLUFF(
            "Wool Guard",
            "Grants Resistance while this pet is equipped."
    ),

    BUTTERFLY_DRIFT(
            "Petal Drift",
            "Grants Speed while this pet is equipped."
    ),

    MOOSHROOM_MILK(
            "Spore Reset",
            "Clears a negative effect every few seconds."
    ),

    SHROOM_SPORES(
            "Glow Spores",
            "Grants Night Vision while this pet is equipped."
    ),

    CAT_NAP(
            "Cat Nap",
            "Grants Regeneration while this pet is equipped."
    ),

    GOLEM_GUARD(
            "Village Guard",
            "Grants Resistance while this pet is equipped."
    ),

    SNIFFER_DIG(
            "Ancient Dig",
            "Grants Haste while this pet is equipped."
    ),

    MOSS_MEND(
            "Moss Mend",
            "Grants Regeneration while this pet is equipped."
    ),

    SWAMP_HEX(
            "Bog Hex",
            "Poisons attackers that hit you."
    ),

    FOREST_BLESSING(
            "Grove Blessing",
            "Grants Regeneration while this pet is equipped."
    ),

    BLOOM_CHARM(
            "Bloom Charm",
            "Increases pet catch rate while equipped."
    ),

    SAND_VEIL(
            "Sand Veil",
            "Boosts movement speed while this pet is equipped."
    ),

    MYCELORD_AURA(
            "Spore Crown",
            "Grants Night Vision while this pet is equipped."
    ),

    LUSH_ORACLE(
            "Root Sight",
            "Grants Haste while this pet is equipped."
    ),

    HACKER_BREACH(
            "Protocol Hijack",
            "Extends reach and briefly turns nearby hostiles into allies. Bosses immune."
    );


    private final String displayName;
    private final String description;

    PetSkill(
            String displayName,
            String description
    ) {

        this.displayName =
                displayName;

        this.description =
                description;
    }

    public String getDisplayName() {

        return displayName;
    }

    public String getDescription() {

        return description;
    }

    public String describe(
            PetInstance pet
    ) {

        int level =
                pet == null
                        ? 1
                        : Math.max(
                        1,
                        pet.getLevel()
                );

        Rarity rarity =
                pet == null
                        ? null
                        : pet.getRarity();

        if (this == GUARDIAN_BEAM) {

            int beams =
                    beamCount(
                            rarity
                    );

            double damage =
                    guardianDamage(
                            rarity,
                            level
                    );

            double cooldown =
                    guardianCooldownTicks(
                            rarity,
                            level
                    )
                            / 20.0;

            String beamText =
                    beams <= 1
                            ? "1 laser"
                            : beams + " lasers";

            return "Fires "
                    + beamText
                    + " dealing "
                    + formatNumber(damage)
                    + " damage. Cooldown: "
                    + formatNumber(cooldown)
                    + "s.";
        }

        if (this == HAWK_GLIDE) {

            double cooldown =
                    hawkCooldownTicks(
                            level
                    )
                            / 20.0;

            return "Double-tap space to hop and glide. Cooldown: "
                    + formatNumber(cooldown)
                    + "s.";
        }

        if (this == OCELOT_FISHING) {

            double percent =
                    (1.0 - fishingWaitFactor(level))
                            * 100.0;

            return "Fishing wait time reduced by "
                    + formatNumber(percent)
                    + "%.";
        }

        if (this == ALLAY_HOARD) {

            double percent =
                    (bossDropMultiplier(
                            rarity,
                            level
                    )
                            - 1.0)
                            * 100.0;

            return "Rare boss drops are "
                    + formatNumber(percent)
                    + "% more likely.";
        }

        if (this == GLOW_SQUID_SIGHT) {

            return "Wild pets within "
                    + formatNumber(petSenseRange())
                    + " blocks glow through walls.";
        }

        if (this == PARROT_LOOKOUT
                || this == PIGEON_SCOUT
                || this == OWL_WATCH
                || this == DEER_ALERT) {

            return "Hostiles within "
                    + formatNumber(lookoutRange())
                    + " blocks glow through walls.";
        }

        if (this == COD_SCHOOL) {

            double percent =
                    (1.0 - codFishingWaitFactor(level))
                            * 100.0;

            return "Fishing wait time reduced by "
                    + formatNumber(percent)
                    + "%.";
        }

        if (this == SALMON_RUN) {

            return "Dolphin's Grace while swimming.";
        }

        if (this == PUFFER_INFLATE
                || this == BEE_STING
                || this == WITCH_BREW
                || this == SWAMP_HEX) {

            return "Attackers are poisoned for "
                    + formatNumber(
                    pufferPoisonTicks(level) / 20.0
            )
                    + "s.";
        }

        if (this == SQUID_INK) {

            return "Attackers are blinded for "
                    + formatNumber(
                    pufferPoisonTicks(level) / 20.0
            )
                    + "s.";
        }

        if (this == WITHER_AURA) {

            return "Attackers are withered for "
                    + formatNumber(
                    pufferPoisonTicks(level) / 20.0
            )
                    + "s.";
        }

        if (this == COW_MILK
                || this == MOOSHROOM_MILK) {

            return "Clears one negative effect every 8s.";
        }

        if (this == TURTLE_SHELL) {

            return "Immune to knockback.";
        }

        if (this == TROPICAL_REEF
                || this == BLOOM_CHARM) {

            return "+"
                    + formatNumber(
                    tropicalCatchBonus(level)
            )
                    + " Catch Rate.";
        }

        if (this == FARM_CARROT
                || this == FARM_WHEAT
                || this == FARM_POTATO
                || this == PIG_TRUFFLE) {

            String crop =
                    this == PIG_TRUFFLE
                            ? "any crop"
                            : this == FARM_CARROT
                            ? "carrots"
                            : this == FARM_WHEAT
                            ? "wheat"
                            : "potatoes";

            double compact =
                    cropCompactChance(
                            rarity,
                            level,
                            this
                    )
                            * 100.0;

            double compacted =
                    cropCompactedUpgrade(
                            rarity,
                            level,
                            this
                    )
                            * 100.0;

            if (compacted > 0.0) {

                return formatNumber(compact)
                        + "% chance to compress harvested "
                        + crop
                        + ". "
                        + formatNumber(compacted)
                        + "% of those become compacted.";
            }

            return formatNumber(compact)
                    + "% chance to compress harvested "
                    + crop
                    + ".";
        }

        if (this == FIRE_DRAGON_BREATH
                || this == WATER_DRAGON_GYRE
                || this == MINING_DRAGON_VEIN
                || this == POISON_DRAGON_MIASMA
                || this == LIGHTNING_DRAGON_CHAIN
                || this == BLAZE_FLARE
                || this == SLIME_BOUNCE
                || this == GHAST_BOLT
                || this == AETHERION_PULSE
                || this == LLAMA_SPIT) {

            double cooldown =
                    (this == FIRE_DRAGON_BREATH
                            || this == WATER_DRAGON_GYRE
                            || this == MINING_DRAGON_VEIN
                            || this == POISON_DRAGON_MIASMA
                            || this == LIGHTNING_DRAGON_CHAIN
                            || this == AETHERION_PULSE
                            ? dragonCooldownTicks(level)
                            : chargeCooldownTicks(level))
                            / 20.0;
            double damage =
                    chargeDamage(this, rarity, level);
            String extra =
                    this == LIGHTNING_DRAGON_CHAIN
                            ? " Chains "
                                    + chainHops(rarity, level)
                                    + "."
                            : this == WATER_DRAGON_GYRE
                            ? " Spins the target."
                            : this == MINING_DRAGON_VEIN || this == POISON_DRAGON_MIASMA
                            ? " Ore-shock."
                            : this == AETHERION_PULSE
                            ? " Portal flare."
                            : this == LLAMA_SPIT
                            ? " Spits. Knockback included."
                            : this == SLIME_BOUNCE
                            ? " Knockback. You bounce like slime blocks (sneak to stop)."
                            : "";
            return "Deals "
                    + formatNumber(damage)
                    + " damage."
                    + extra
                    + " Cooldown: "
                    + formatNumber(cooldown)
                    + "s.";
        }

        if (this == HACKER_BREACH) {
            int reach = hackerReachBlocks(rarity);
            int mobs = hackerHijackCount(rarity);
            double duration = hackerHijackDurationTicks(rarity) / 20.0;
            double cooldown = hackerHijackCooldownTicks(rarity) / 20.0;
            return "+"
                    + reach
                    + " block reach. Hijacks "
                    + mobs
                    + " hostile"
                    + (mobs == 1 ? "" : "s")
                    + " for "
                    + formatNumber(duration)
                    + "s. Cooldown: "
                    + formatNumber(cooldown)
                    + "s. Bosses immune.";
        }

        return description;
    }

    public static int hackerReachBlocks(Rarity rarity) {
        if (rarity == Rarity.EPIC
                || rarity == Rarity.LEGENDARY
                || rarity == Rarity.MYTHIC
                || rarity == Rarity.AETHERED) {
            return 3;
        }
        return 2;
    }

    public static int hackerHijackCount(Rarity rarity) {
        if (rarity == Rarity.EPIC
                || rarity == Rarity.LEGENDARY
                || rarity == Rarity.MYTHIC
                || rarity == Rarity.AETHERED) {
            return 2;
        }
        return 1;
    }

    public static long hackerHijackDurationTicks(Rarity rarity) {
        if (rarity == Rarity.EPIC
                || rarity == Rarity.LEGENDARY
                || rarity == Rarity.MYTHIC
                || rarity == Rarity.AETHERED) {
            return 25L * 20L;
        }
        return 30L * 20L;
    }

    public static long hackerHijackCooldownTicks(Rarity rarity) {
        if (rarity == Rarity.EPIC
                || rarity == Rarity.LEGENDARY
                || rarity == Rarity.MYTHIC
                || rarity == Rarity.AETHERED) {
            return 45L * 20L;
        }
        return 60L * 20L;
    }

    public static double guardianDamage(
            Rarity rarity,
            int level
    ) {

        double base =
                switch (rarity == null ? Rarity.EPIC : rarity) {

                    case LEGENDARY ->
                            7.5;

                    case MYTHIC, AETHERED ->
                            10.0;

                    default ->
                            5.0;
                };

        return base
                * (1.0 + 0.04 * (clampLevel(level) - 1));
    }

    public static long guardianCooldownTicks(
            Rarity rarity,
            int level
    ) {

        long base =
                switch (rarity == null ? Rarity.EPIC : rarity) {

                    case LEGENDARY ->
                            300L;

                    case MYTHIC, AETHERED ->
                            240L;

                    default ->
                            400L;
                };

        return Math.max(
                60L,
                base - 2L * (clampLevel(level) - 1)
        );
    }

    public static long chargeCooldownTicks(
            int level
    ) {

        return Math.round(
                600.0
                        - 400.0
                        * (clampLevel(level) - 1)
                        / 99.0
        );
    }

    public static long dragonCooldownTicks(
            int level
    ) {

        return Math.round(
                800.0
                        - 360.0
                        * (clampLevel(level) - 1)
                        / 99.0
        );
    }

    public static double chargeDamage(
            PetSkill skill,
            Rarity rarity,
            int level
    ) {

        double low;
        double high;
        if (skill == FIRE_DRAGON_BREATH) {
            low = 4.0;
            high = 22.0;
        } else if (skill == WATER_DRAGON_GYRE) {
            low = 3.0;
            high = 16.0;
        } else if (skill == MINING_DRAGON_VEIN || skill == POISON_DRAGON_MIASMA) {
            low = 3.0;
            high = 15.0;
        } else if (skill == LIGHTNING_DRAGON_CHAIN) {
            low = 4.5;
            high = 18.0;
        } else if (skill == BLAZE_FLARE) {
            low = 3.0;
            high = 14.0;
        } else if (skill == SLIME_BOUNCE) {
            low = 2.5;
            high = 12.0;
        } else if (skill == GHAST_BOLT) {
            low = 4.0;
            high = 17.0;
        } else if (skill == AETHERION_PULSE) {
            low = 5.0;
            high = 20.0;
        } else if (skill == LLAMA_SPIT) {
            low = 2.0;
            high = 11.0;
        } else {
            low = 3.0;
            high = 14.0;
        }
        if (rarity == Rarity.MYTHIC) {
            low *= 1.12;
            high *= 1.12;
        } else if (rarity == Rarity.LEGENDARY) {
            low *= 1.06;
            high *= 1.06;
        }
        return low + (high - low) * (clampLevel(level) - 1) / 99.0;
    }

    public static int chainHops(
            Rarity rarity,
            int level
    ) {

        int hops =
                rarity == Rarity.MYTHIC
                        ? 3
                        : 2;
        if (clampLevel(level) >= 70) {
            hops++;
        }
        return hops;
    }

    public static long hawkCooldownTicks(
            int level
    ) {

        return Math.max(
                100L,
                600L - 2L * (clampLevel(level) - 1)
        );
    }

    public static double fishingWaitFactor(
            int level
    ) {

        return Math.max(
                0.32,
                0.52 - 0.002 * (clampLevel(level) - 1)
        );
    }

    public static double bossDropMultiplier(
            Rarity rarity,
            int level
    ) {

        double bonus =
                switch (rarity == null ? Rarity.EPIC : rarity) {

                    case MYTHIC, AETHERED ->
                            0.38;

                    case LEGENDARY ->
                            0.28;

                    case EPIC ->
                            0.18;

                    default ->
                            0.10;
                };

        bonus +=
                0.0035
                        * (clampLevel(level) - 1);

        return 1.0
                + Math.min(
                0.55,
                bonus
        );
    }

    public static double petSenseRange() {
        return 48.0;
    }

    public static double lookoutRange() {
        return 18.0;
    }

    public static double codFishingWaitFactor(
            int level
    ) {

        return Math.max(
                0.58,
                0.80 - 0.0012 * (clampLevel(level) - 1)
        );
    }

    public static double tropicalCatchBonus(
            int level
    ) {

        return 3.0
                + 0.02 * (clampLevel(level) - 1);
    }

    public boolean matchesCrop(
            org.bukkit.Material drop
    ) {

        if (drop == null) {
            return false;
        }

        String name =
                drop.name();

        return switch (this) {

            case FARM_CARROT ->
                    name.contains("CARROT");

            case FARM_WHEAT ->
                    name.contains("WHEAT")
                            || name.equals("HAY_BLOCK");

            case FARM_POTATO ->
                    name.contains("POTATO");

            case PIG_TRUFFLE ->
                    name.contains("CARROT")
                            || name.contains("WHEAT")
                            || name.contains("POTATO")
                            || name.contains("BEETROOT")
                            || name.contains("NETHER_WART")
                            || name.contains("COCOA")
                            || name.contains("BERR")
                            || name.contains("MELON")
                            || name.contains("PUMPKIN");

            default ->
                    false;
        };
    }

    public static double cropCompactChance(
            de.aetherion.items.model.Rarity rarity,
            int level,
            PetSkill skill
    ) {

        double chance =
                cropCompactChance(
                        rarity,
                        level
                );

        if (skill == PIG_TRUFFLE) {
            return chance * 0.55;
        }

        return chance;
    }

    public static double cropCompactChance(
            de.aetherion.items.model.Rarity rarity,
            int level
    ) {

        double base =
                switch (rarity == null
                        ? de.aetherion.items.model.Rarity.COMMON
                        : rarity) {

                    case UNCOMMON ->
                            0.015;

                    case RARE ->
                            0.02;

                    case EPIC ->
                            0.028;

                    case LEGENDARY ->
                            0.038;

                    case MYTHIC, AETHERED ->
                            0.045;

                    default ->
                            0.01;
                };

        return Math.min(
                0.055,
                base
                        + 0.00005 * (clampLevel(level) - 1)
        );
    }

    public static double cropCompactedUpgrade(
            de.aetherion.items.model.Rarity rarity,
            int level,
            PetSkill skill
    ) {

        double chance =
                cropCompactedUpgrade(
                        rarity,
                        level
                );

        if (skill == PIG_TRUFFLE) {
            return chance * 0.55;
        }

        return chance;
    }

    public static double cropCompactedUpgrade(
            de.aetherion.items.model.Rarity rarity,
            int level
    ) {

        double base =
                switch (rarity == null
                        ? de.aetherion.items.model.Rarity.COMMON
                        : rarity) {

                    case EPIC ->
                            0.02;

                    case LEGENDARY ->
                            0.035;

                    case MYTHIC, AETHERED ->
                            0.05;

                    default ->
                            0.0;
                };

        if (base <= 0.0) {
            return 0.0;
        }

        return Math.min(
                0.08,
                base
                        + 0.0002 * (clampLevel(level) - 1)
        );
    }

    public static int pufferPoisonTicks(
            int level
    ) {

        return 40
                + 2 * (clampLevel(level) - 1);
    }

    private static int clampLevel(
            int level
    ) {

        return Math.max(
                1,
                Math.min(
                        level,
                        100
                )
        );
    }

    public static String formatNumber(
            double value
    ) {

        if (Math.abs(value - Math.rint(value)) < 0.05) {

            return String.valueOf(
                    (int) Math.rint(value)
            );
        }

        return String.format(
                java.util.Locale.US,
                "%.1f",
                value
        );
    }

    public static PetSkill fromPet(
            PetInstance pet
    ) {

        if (pet == null
                || pet.getDefinition() == null) {

            return NONE;
        }

        return fromId(
                pet.getDefinition()
                        .getId()
        );
    }

    public static PetSkill fromId(
            String petId
    ) {

        if (petId == null) {
            return NONE;
        }

        return switch (petId.toLowerCase()) {

            case "wolf" ->
                    WOLF_SPEED;

            case "bat" ->
                    BAT_NIGHT_VISION;

            case "dolphin" ->
                    DOLPHIN_SWIM;

            case "creeper" ->
                    CREEPER_BURST;

            case "guardian" ->
                    GUARDIAN_BEAM;

            case "hawk" ->
                    HAWK_GLIDE;

            case "ocelot" ->
                    OCELOT_FISHING;

            case "goat" ->
                    GOAT_JUMP;

            case "allay" ->
                    ALLAY_HOARD;

            case "glow_squid" ->
                    GLOW_SQUID_SIGHT;

            case "parrot" ->
                    PARROT_LOOKOUT;

            case "cod" ->
                    COD_SCHOOL;

            case "salmon" ->
                    SALMON_RUN;

            case "pufferfish" ->
                    PUFFER_INFLATE;

            case "tropical_fish" ->
                    TROPICAL_REEF;

            case "farm_rabbit" ->
                    FARM_CARROT;

            case "horse" ->
                    FARM_WHEAT;

            case "sack_of_potatoes" ->
                    FARM_POTATO;

            case "yeti" ->
                    YETI_HIDE;

            case "snowflake" ->
                    SNOWFLAKE_BOLT;

            case "ice_dragon" ->
                    ICE_DRAGON_FROST;

            case "fire_dragon" ->
                    FIRE_DRAGON_BREATH;

            case "water_dragon" ->
                    WATER_DRAGON_GYRE;

            case "nature_dragon" ->
                    PIG_TRUFFLE;

            case "mining_dragon", "poison_dragon" ->
                    MINING_DRAGON_VEIN;

            case "forest_dragon" ->
                    FOREST_BLESSING;

            case "lightning_dragon" ->
                    LIGHTNING_DRAGON_CHAIN;

            case "blaze" ->
                    BLAZE_FLARE;

            case "slime_minion" ->
                    SLIME_BOUNCE;

            case "ghast" ->
                    GHAST_BOLT;

            case "turtle" ->
                    TURTLE_SHELL;

            case "pig" ->
                    PIG_TRUFFLE;

            case "cow" ->
                    COW_MILK;

            case "cave_spider" ->
                    CAVE_SPIDER_VENOM;

            case "zombie" ->
                    ZOMBIE_HUNGER;

            case "skeleton", "dungeon_skeleton" ->
                    SKELETON_MARK;

            case "squid" ->
                    SQUID_INK;

            case "axolotl" ->
                    AXOLOTL_MEND;

            case "wither" ->
                    WITHER_AURA;

            case "aetherion" ->
                    AETHERION_PULSE;

            case "bee" ->
                    BEE_STING;

            case "pigeon" ->
                    PIGEON_SCOUT;

            case "panda" ->
                    PANDA_SNOOZE;

            case "llama" ->
                    LLAMA_SPIT;

            case "fox" ->
                    FOX_POUNCE;

            case "rabbit" ->
                    RABBIT_HOP;

            case "polar_bear" ->
                    POLAR_GUARD;

            case "camel" ->
                    CAMEL_PACE;

            case "armadillo" ->
                    ARMADILLO_CURL;

            case "dungeon_zombie" ->
                    DUNGEON_ROT;

            case "dungeon_dragon" ->
                    FIRE_DRAGON_BREATH;

            case "frog" ->
                    FROG_LEAP;

            case "witch" ->
                    WITCH_BREW;

            case "mudling" ->
                    MUD_COAT;

            case "deer" ->
                    DEER_ALERT;

            case "squirrel" ->
                    SQUIRREL_SCRAMBLE;

            case "boar" ->
                    BOAR_CHARGE;

            case "owl" ->
                    OWL_WATCH;

            case "sheep" ->
                    SHEEP_FLUFF;

            case "butterfly" ->
                    BUTTERFLY_DRIFT;

            case "mooshroom" ->
                    MOOSHROOM_MILK;

            case "shroomling" ->
                    SHROOM_SPORES;

            case "cat" ->
                    CAT_NAP;

            case "iron_golem" ->
                    GOLEM_GUARD;

            case "sniffer" ->
                    SNIFFER_DIG;

            case "moss_sprite" ->
                    MOSS_MEND;

            case "swamp_hag" ->
                    SWAMP_HEX;

            case "forest_spirit" ->
                    FOREST_BLESSING;

            case "bloom_fairy" ->
                    BLOOM_CHARM;

            case "sand_wraith" ->
                    SAND_VEIL;

            case "mycelord" ->
                    MYCELORD_AURA;

            case "lush_oracle" ->
                    LUSH_ORACLE;

            case "hacker" ->
                    HACKER_BREACH;

            default ->
                    NONE;
        };
    }

    public static int beamCount(
            Rarity rarity
    ) {

        if (rarity == null) {
            return 0;
        }

        return switch (rarity) {

            case EPIC ->
                    1;

            case LEGENDARY, MYTHIC ->
                    2;

            default ->
                    0;
        };
    }
}
