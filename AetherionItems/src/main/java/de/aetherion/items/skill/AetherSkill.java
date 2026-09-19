package de.aetherion.items.skill;

import de.aetherion.items.model.ItemCapability;

import org.bukkit.Material;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public enum AetherSkill {

    HEAVY_HANDS(
            "heavy_hands",
            "Heavy Hands",
            Category.COMBAT,
            Material.IRON_SWORD,
            "Your punches file complaints.",
            "+7 Damage. The mobs have started a petition.",
            Flag.NONE,
            bonus(ItemCapability.DAMAGE, 6.5)
    ),
    MEAN_STREAK(
            "mean_streak",
            "Mean Streak",
            Category.COMBAT,
            Material.BIRCH_LOG,
            "Luck, but personally offended.",
            "+5% Crit Chance. The dice are on your side, reluctantly.",
            Flag.NONE,
            bonus(ItemCapability.CRIT_CHANCE, 5.0)
    ),
    SHARP_INVOICE(
            "sharp_invoice",
            "Sharp Invoice",
            Category.COMBAT,
            Material.OAK_LOG,
            "The follow-up email has an edge.",
            "+10% Crit Damage. Paperwork that bleeds.",
            Flag.NONE,
            bonus(ItemCapability.CRIT_DAMAGE, 10)
    ),
    WIDE_SWING(
            "wide_swing",
            "Wide Swing",
            Category.COMBAT,
            Material.REDSTONE,
            "Personal space is a rumor.",
            "+14 Attack Spread. Neighbors included.",
            Flag.NONE,
            bonus(ItemCapability.ATTACK_SPREAD, 14)
    ),
    THICK_SKIN(
            "thick_skin",
            "Thick Skin",
            Category.COMBAT,
            Material.SHIELD,
            "Insults bounce. So do arrows, slightly.",
            "+8 Defense. Feelings not included.",
            Flag.NONE,
            bonus(ItemCapability.DEFENSE, 8)
    ),
    SECOND_WIND(
            "second_wind",
            "Second Wind",
            Category.COMBAT,
            Material.GOLDEN_APPLE,
            "You were not done being difficult.",
            "+10 Health. The intermission was optional.",
            Flag.NONE,
            bonus(ItemCapability.HEALTH, 10)
    ),
    BOSS_GRUDGE(
            "boss_grudge",
            "Boss Grudge",
            Category.COMBAT,
            Material.WITHER_SKELETON_SKULL,
            "Named enemies go on the list.",
            "+10% damage to bosses. They know why.",
            Flag.BOSS_GRUDGE
    ),
    BLOOD_TAX(
            "blood_tax",
            "Blood Tax",
            Category.COMBAT,
            Material.GOLD_NUGGET,
            "Everything pays rent now.",
            "Kills drop extra coins. Steep payout at high levels.",
            Flag.BLOOD_TAX
    ),
    LIFE_ABSORB(
            "life_absorb",
            "Life Absorb",
            Category.COMBAT,
            Material.GLISTERING_MELON_SLICE,
            "They bleed. You bill it as recovery.",
            "Heal on hit for a cut of the damage dealt. No Speed.",
            Flag.LIFE_ABSORB
    ),
    LAST_WORD(
            "last_word",
            "Last Word",
            Category.COMBAT,
            Material.DIAMOND_SWORD,
            "You get the last hit. And the last remark.",
            "+4 Damage and +4% Crit Chance.",
            Flag.NONE,
            bonus(ItemCapability.DAMAGE, 3.8),
            bonus(ItemCapability.CRIT_CHANCE, 4.0)
    ),
    LONG_ARM(
            "long_arm",
            "Long Arm",
            Category.COMBAT,
            Material.TRIDENT,
            "Your personal space has suburbs.",
            "+16 Attack Spread. Bring a seating chart.",
            Flag.NONE,
            bonus(ItemCapability.ATTACK_SPREAD, 16)
    ),

    ROCK_WHISPER(
            "rock_whisper",
            "Rock Whisper",
            Category.MINING,
            Material.COBBLESTONE,
            "The cobble whispers. You pretend not to hear.",
            "+12 Mining Power. The rocks still talk.",
            Flag.NONE,
            bonus(ItemCapability.MINING_POWER, 12)
    ),
    EXTRA_POCKET(
            "extra_pocket",
            "Extra Pocket",
            Category.MINING,
            Material.GOLD_INGOT,
            "The extra pocket is a moral failing.",
            "+22 Fortune. Ethics pending.",
            Flag.NONE,
            bonus(ItemCapability.FORTUNE, 22)
    ),
    PACK_RAT(
            "pack_rat",
            "Pack Rat",
            Category.MINING,
            Material.CHEST,
            "You compress hobbies. And ore.",
            "+0.6% chance to compact mining drops (up to ~2.8% at max).",
            Flag.PACK_RAT
    ),
    SPREAD_SHEET(
            "spread_sheet",
            "Spread Sheet",
            Category.MINING,
            Material.EMERALD,
            "Mining, but make it a spreadsheet.",
            "+8 Spread. Extra blocks actually happen.",
            Flag.NONE,
            bonus(ItemCapability.SPREAD, 8)
    ),
    QUARRY_MANNERS(
            "quarry_manners",
            "Quarry Manners",
            Category.MINING,
            Material.IRON_PICKAXE,
            "Please. Thank you. Mine that.",
            "+10 Mining Power and +14 Fortune.",
            Flag.NONE,
            bonus(ItemCapability.MINING_POWER, 10),
            bonus(ItemCapability.FORTUNE, 13.5)
    ),
    CAVE_SENSE(
            "cave_sense",
            "Cave Sense",
            Category.MINING,
            Material.POINTED_DRIPSTONE,
            "The dark is just poorly lit profit.",
            "+10 Mining Power in caves or after dark.",
            Flag.CAVE_SENSE
    ),

    LIGHT_FOOT(
            "light_foot",
            "Light Foot",
            Category.FORAGING,
            Material.LEATHER_BOOTS,
            "You leave before the trees finish complaining.",
            "Quieter steps. Speed comes from boots, pets, or gear — not skills.",
            Flag.NONE
    ),
    WOODWISE(
            "woodwise",
            "Woodwise",
            Category.FORAGING,
            Material.OAK_LOG,
            "Oak has started taking it personally.",
            "+18 Fortune. Trees hate this one trick.",
            Flag.NONE,
            bonus(ItemCapability.FORTUNE, 18)
    ),
    TIMBER_TAX(
            "timber_tax",
            "Timber Tax",
            Category.FORAGING,
            Material.IRON_AXE,
            "Paperwork for trees. They hate it.",
            "+0.6% chance to compact oak while foraging (up to ~2.8% at max).",
            Flag.TIMBER_TAX
    ),
    GREEN_THUMB(
            "green_thumb",
            "Green Thumb",
            Category.FORAGING,
            Material.OAK_SAPLING,
            "Plants trust you. Rocks are considering it.",
            "+11 Fortune. Speed stays on boots / pets / gear.",
            Flag.NONE,
            bonus(ItemCapability.FORTUNE, 11)
    ),

    CROP_GOSSIP(
            "crop_gossip",
            "Crop Gossip",
            Category.FARMING,
            Material.WHEAT,
            "The wheat has started a group chat.",
            "+22 Fortune and +8 Harvest. The field is talking.",
            Flag.NONE,
            bonus(ItemCapability.FORTUNE, 22),
            bonus(ItemCapability.HARVEST_SPREAD, 8)
    ),
    WIDE_FURROW(
            "wide_furrow",
            "Wide Furrow",
            Category.FARMING,
            Material.IRON_HOE,
            "Your personal space includes the next three rows.",
            "+24 Harvest and +10 Fortune. Neighbors included.",
            Flag.NONE,
            bonus(ItemCapability.HARVEST_SPREAD, 24),
            bonus(ItemCapability.FORTUNE, 10)
    ),
    SEED_LEDGER(
            "seed_ledger",
            "Seed Ledger",
            Category.FARMING,
            Material.HAY_BLOCK,
            "Every grain is itemized. The field hates it.",
            "+0.6% chance to compact crops while farming (up to ~2.8% at max).",
            Flag.SEED_LEDGER
    ),

    BITE_ME(
            "bite_me",
            "Bite Me",
            Category.FISHING,
            Material.COD,
            "The fish started the argument. You finished it.",
            "+30 Fish Catch and +18 Fortune. Extra bites, professionally.",
            Flag.NONE,
            bonus(ItemCapability.FISHING_CATCH, 30),
            bonus(ItemCapability.FORTUNE, 18)
    ),
    SHORT_CAST(
            "short_cast",
            "Short Cast",
            Category.FISHING,
            Material.FISHING_ROD,
            "Wait times were a suggestion.",
            "+8 Fish Speed, +15 Fish Catch, +10 Fortune.",
            Flag.NONE,
            bonus(ItemCapability.FISHING_SPEED, 8),
            bonus(ItemCapability.FISHING_CATCH, 15),
            bonus(ItemCapability.FORTUNE, 10)
    ),
    FISH_LEDGER(
            "fish_ledger",
            "Fish Ledger",
            Category.FISHING,
            Material.DRIED_KELP_BLOCK,
            "Every nibble is itemized. The ocean hates it.",
            "+0.6% chance to compact cod while fishing (up to ~2.8% at max).",
            Flag.FISH_LEDGER
    ),

    QUICK_HANDS(
            "quick_hands",
            "Quick Hands",
            Category.UTILITY,
            Material.CLOCK,
            "Cooldowns were a suggestion.",
            "Ability cooldowns: -10%. The boots are watching.",
            Flag.QUICK_HANDS
    ),
    LUCKY_STREAK(
            "lucky_streak",
            "Lucky Streak",
            Category.UTILITY,
            Material.LEAD,
            "The eggs can smell the desperation. They like it.",
            "+10% Catch Rate. Charm optional.",
            Flag.NONE,
            bonus(ItemCapability.PET_CATCH_RATE, 5.5)
    ),
    NIGHT_OWL(
            "night_owl",
            "Night Owl",
            Category.UTILITY,
            Material.FIREWORK_STAR,
            "The sun was slowing you down anyway.",
            "Night vision vibes. Speed stays on boots / pets / gear.",
            Flag.NIGHT_OWL
    ),
    QUIET_PRIDE(
            "quiet_pride",
            "Quiet Pride",
            Category.UTILITY,
            Material.IRON_CHESTPLATE,
            "You don't announce the tanking. You just tank.",
            "+5 Defense and +6 Health.",
            Flag.NONE,
            bonus(ItemCapability.DEFENSE, 5),
            bonus(ItemCapability.HEALTH, 6)
    ),
    PINCH_PENNY(
            "pinch_penny",
            "Pinch Penny",
            Category.UTILITY,
            Material.GOLD_INGOT,
            "Midas still wants a receipt. A shorter one.",
            "Midas Dagger costs 5 coins instead of 10.",
            Flag.PINCH_PENNY
    ),
    DIAMOND_SPINE(
            "diamond_spine",
            "Diamond Spine",
            Category.UTILITY,
            Material.DIAMOND_CHESTPLATE,
            "If they hit you, they can have a sample.",
            "Reflect 8% melee damage. Stacks with the plate.",
            Flag.DIAMOND_SPINE
    ),
    IRON_STOMACH(
            "iron_stomach",
            "Iron Stomach",
            Category.UTILITY,
            Material.COOKED_BEEF,
            "Lunch was a threat. You won.",
            "+8 Health. The menu lost.",
            Flag.NONE,
            bonus(ItemCapability.HEALTH, 8)
    ),
    GOLDEN_HOUR(
            "golden_hour",
            "Golden Hour",
            Category.UTILITY,
            Material.GOLD_BLOCK,
            "Invoices, but festive.",
            "+25% coins from swords, skills, and other petty theft.",
            Flag.GOLDEN_HOUR
    ),

    CHAMBER_PACE(
            "chamber_pace",
            "Chamber Pace",
            Category.DUNGEON,
            Material.LEATHER_BOOTS,
            "The corridor blinks. You do not.",
            "Steady footing in dungeons. No free Speed — use boots / pets / gear.",
            Flag.NONE
    ),
    STONE_BLOOD(
            "stone_blood",
            "Stone Blood",
            Category.DUNGEON,
            Material.IRON_CHESTPLATE,
            "The walls tried first.",
            "+10 Defense in dungeons only.",
            Flag.NONE,
            bonus(ItemCapability.DEFENSE, 10)
    ),
    RELIC_APPETITE(
            "relic_appetite",
            "Relic Appetite",
            Category.DUNGEON,
            Material.EXPERIENCE_BOTTLE,
            "Your kit is hungry. Feed it chambers.",
            "+20% dungeon gear XP from kills. Dungeons only.",
            Flag.RELIC_APPETITE
    ),
    FLOOR_GRUDGE(
            "floor_grudge",
            "Floor Grudge",
            Category.DUNGEON,
            Material.WITHER_SKELETON_SKULL,
            "Floor bosses make the list twice.",
            "+12% damage to bosses while in a dungeon.",
            Flag.FLOOR_GRUDGE
    );

    public enum Category {
        COMBAT("§cCombat", Material.IRON_SWORD),
        MINING("§9Mining", Material.IRON_PICKAXE),
        FORAGING("§aForaging", Material.IRON_AXE),
        FARMING("§6Farming", Material.GOLDEN_HOE),
        FISHING("§bFishing", Material.FISHING_ROD),
        UTILITY("§eUtility", Material.CLOCK),
        DUNGEON("§5Dungeon", Material.IRON_BARS);

        private final String title;
        private final Material icon;

        Category(String title, Material icon) {
            this.title = title;
            this.icon = icon;
        }

        public String title() {
            return title;
        }

        public Material icon() {
            return icon;
        }
    }

    public enum Flag {
        NONE,
        PACK_RAT,
        TIMBER_TAX,
        SEED_LEDGER,
        FISH_LEDGER,
        QUICK_HANDS,
        BOSS_GRUDGE,
        BLOOD_TAX,
        LIFE_ABSORB,
        GOLDEN_HOUR,
        NIGHT_OWL,
        CAVE_SENSE,
        PINCH_PENNY,
        DIAMOND_SPINE,
        RELIC_APPETITE,
        FLOOR_GRUDGE
    }

    public record Bonus(ItemCapability capability, double amount) {
    }

    private final String id;
    private final String displayName;
    private final Category category;
    private final Material icon;
    private final String tagline;
    private final String details;
    private final Flag flag;
    private final EnumMap<ItemCapability, Double> bonuses;

    AetherSkill(
            String id,
            String displayName,
            Category category,
            Material icon,
            String tagline,
            String details,
            Flag flag,
            Bonus... bonuses
    ) {
        this.id = id;
        this.displayName = displayName;
        this.category = category;
        this.icon = icon;
        this.tagline = tagline;
        this.details = details;
        this.flag = flag;
        this.bonuses = new EnumMap<>(ItemCapability.class);
        if (bonuses != null) {
            for (Bonus bonus : bonuses) {
                if (bonus != null && bonus.capability() != null) {
                    this.bonuses.merge(bonus.capability(), bonus.amount(), Double::sum);
                }
            }
        }
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public Category category() {
        return category;
    }

    public Material icon() {
        return icon;
    }

    public String tagline() {
        return tagline;
    }

    public String details() {
        return details;
    }

    public Flag flag() {
        return flag;
    }

    public double bonus(ItemCapability capability) {
        return bonuses.getOrDefault(capability, 0.0);
    }

    public Map<ItemCapability, Double> bonuses() {
        return bonuses;
    }

    public static AetherSkill byId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String key = id.toLowerCase(Locale.ROOT);
        for (AetherSkill skill : values()) {
            if (skill.id.equals(key)) {
                return skill;
            }
        }
        return null;
    }

    private static Bonus bonus(ItemCapability capability, double amount) {
        return new Bonus(capability, amount);
    }
}
