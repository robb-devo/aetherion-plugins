package de.aetherion.items.skill;

public final class SkillFlavor {

    private SkillFlavor() {
    }

    public static String tagline(AetherSkill skill, int level) {
        String[] lines = lines(skill);
        int tier = Math.min(lines.length - 1, SkillProgression.rarityTier(level));
        return lines[tier];
    }

    private static String[] lines(AetherSkill skill) {
        return switch (skill) {
            case HEAVY_HANDS -> new String[] {
                    "Your punches file complaints.",
                    "The petition gained signatures.",
                    "HR asked you to stop winning.",
                    "The complaint department hired you.",
                    "You are the reason meetings exist.",
                    "The universe has filed a counterclaim."
            };
            case MEAN_STREAK -> new String[] {
                    "Luck, but personally offended.",
                    "The dice started taking notes.",
                    "Probability sent a cease and desist.",
                    "Luck works here. It is unionized.",
                    "The odds apologized in writing.",
                    "Fate is on unpaid leave."
            };
            case SHARP_INVOICE -> new String[] {
                    "The follow-up email has an edge.",
                    "Cc'd: the wound.",
                    "Read receipt: fatal.",
                    "Accounting learned to bleed.",
                    "The invoice arrived before the hit.",
                    "Death is an outstanding balance."
            };
            case WIDE_SWING -> new String[] {
                    "Personal space is a rumor.",
                    "Neighbors are now involved.",
                    "The zip code took damage.",
                    "You hit people in other time zones.",
                    "Maps consider you a natural disaster.",
                    "Geography resigned."
            };
            case THICK_SKIN -> new String[] {
                    "Insults bounce. So do arrows, slightly.",
                    "Feelings were not consulted.",
                    "The arrows started apologizing.",
                    "You are legally a wall.",
                    "Criticism needs a permit now.",
                    "The concept of hurt filed for bankruptcy."
            };
            case SECOND_WIND -> new String[] {
                    "You were not done being difficult.",
                    "The intermission was optional.",
                    "Death booked a later slot.",
                    "You outlasted the point of the fight.",
                    "The medic started charging rent.",
                    "You are the reason respawns look nervous."
            };
            case BOSS_GRUDGE -> new String[] {
                    "Named enemies go on the list.",
                    "The list has a waiting list.",
                    "Bosses started using fake names.",
                    "Your grudge has a secretary.",
                    "The bounty is mutual now.",
                    "Even the title card flinches."
            };
            case BLOOD_TAX -> new String[] {
                    "Everything pays rent now.",
                    "The gold sword is taking notes.",
                    "Mobs itemize their deaths.",
                    "The IRS of violence called. It's you.",
                    "Coins applaud on the way out.",
                    "The economy is afraid of you personally."
            };
            case LIFE_ABSORB -> new String[] {
                    "They bleed. You bill it as recovery.",
                    "Every cut pays a medical fee.",
                    "Your HP bar has a commission structure.",
                    "Vampires filed a trademark complaint.",
                    "The first aid kit is jealous.",
                    "Violence, but make it a wellness plan."
            };
            case LAST_WORD -> new String[] {
                    "You get the last hit. And the last remark.",
                    "They die mid-sentence. On purpose.",
                    "The comeback landed first.",
                    "You copyrighted the finishing line.",
                    "Obituaries quote you now.",
                    "Silence is just your encore."
            };
            case LONG_ARM -> new String[] {
                    "Your personal space has suburbs.",
                    "Bring a seating chart.",
                    "The suburbs annexed a kingdom.",
                    "Reach became a foreign policy.",
                    "Borders are a suggestion you ignore.",
                    "The horizon is in melee range."
            };
            case ROCK_WHISPER -> new String[] {
                    "The cobble whispers. You pretend not to hear.",
                    "You started whispering back.",
                    "The rocks unionized. You still clock in.",
                    "Cobble nominated you for shop steward.",
                    "Geology wrote you a love letter. You mined it.",
                    "The stone age called. It wants royalties."
            };
            case EXTRA_POCKET -> new String[] {
                    "The extra pocket is a moral failing.",
                    "Ethics is still pending.",
                    "The pocket filed for independence.",
                    "You are smuggling luck at this point.",
                    "Customs gave up.",
                    "The loot table lives in your coat."
            };
            case PACK_RAT -> new String[] {
                    "You compress hobbies. And ore.",
                    "The chest is judging your life choices.",
                    "Compression is a personality now.",
                    "You fold the world into stacks of 64.",
                    "Physics asked you to stop.",
                    "Matter is a filing system."
            };
            case SPREAD_SHEET -> new String[] {
                    "Mining, but make it a spreadsheet.",
                    "Columns. Rows. Cobble.",
                    "Excel but with more dust.",
                    "The quarry has pivot tables.",
                    "You mine in quarterly reports.",
                    "The cell is a block. The block is a cell."
            };
            case QUARRY_MANNERS -> new String[] {
                    "Please. Thank you. Mine that.",
                    "The quarry sent a thank-you note.",
                    "Etiquette, with extra cobble.",
                    "You RSVP'd to a cave-in.",
                    "Politeness became a pickaxe.",
                    "Miss Manners runs the mine now."
            };
            case CAVE_SENSE -> new String[] {
                    "The dark is just poorly lit profit.",
                    "Night shift. No complaints.",
                    "The cave put you on payroll.",
                    "Darkness asked for a raise. You declined.",
                    "You clock in where the sun gave up.",
                    "Light is a rumor you don't need."
            };
            case BEDROCK_BORN -> new String[] {
                    "Eldervale stone knows your boots.",
                    "The tunnels lean in when you pass.",
                    "The mountain files you under family.",
                    "Every seam on the isle has your name scratched in it.",
                    "The bedrock asks how your day went.",
                    "You are load-bearing for the whole island."
            };
            case WORK_SONG -> new String[] {
                    "You swing in four-four. The seam keeps time.",
                    "The pick found the backbeat.",
                    "Three miners joined in on the chorus.",
                    "The mountain hums along. Off-key, loyally.",
                    "Stalactites tap their feet now.",
                    "The mine is a song, and you wrote it."
            };
            case GEODE_NOSE -> new String[] {
                    "You can smell a crystal through ten feet of granite.",
                    "Amethyst sneezes when you walk past.",
                    "Geodes hold their breath to look smaller. It fails.",
                    "The Assayer keeps a chair warm for you.",
                    "Crystals line up to be found.",
                    "The rock gives up its heart before you ask."
            };
            case UNION_CARD -> new String[] {
                    "The Foreman pays union rates. Mostly to you.",
                    "Your card has three stamps and a coffee stain.",
                    "Contracts arrive pre-negotiated.",
                    "The clerk rounds up out of respect.",
                    "You are the union now.",
                    "The mine pays you to show up. Then more."
            };
            case SEAM_READER -> new String[] {
                    "You read ore like a newspaper. Bad news for the ore.",
                    "Veins show their hand early.",
                    "The seam confesses before you swing.",
                    "Stone can't keep a secret around you.",
                    "Ore glows a little just to be noticed.",
                    "The whole mountain is an open book."
            };
            case DEPTH_GAUGE -> new String[] {
                    "The deeper it gets, the better it pays.",
                    "Your ears pop in the good way.",
                    "The dark added you to payroll.",
                    "You measure time in fathoms.",
                    "The Undercroft sends a welcome card.",
                    "Down is just up with better ore."
            };
            case LIGHT_FOOT -> new String[] {
                    "You leave before the trees finish complaining.",
                    "The lecture can keep up. Barely.",
                    "Footsteps filed a noise report. Denied.",
                    "You are late to being gone.",
                    "The forest still hasn't finished the sentence.",
                    "Speed so polite it doesn't exist."
            };
            case WOODWISE -> new String[] {
                    "Oak has started taking it personally.",
                    "Trees hate this one trick.",
                    "The forest wrote a bad review.",
                    "Oak started a support group.",
                    "You are the reason rings in a stump look tired.",
                    "Photosynthesis opted out."
            };
            case TIMBER_TAX -> new String[] {
                    "Paperwork for trees. They hate it.",
                    "The axe brought a clipboard.",
                    "Logs arrive pre-filed.",
                    "The forest pays in compressed stubbornness.",
                    "You audited a biome.",
                    "Nature lost the appeal."
            };
            case GREEN_THUMB -> new String[] {
                    "Plants trust you. Rocks are considering it.",
                    "The sapling asked for a raise.",
                    "You garden like it owes you money.",
                    "Chlorophyll started taking notes.",
                    "The lawn is a hostile takeover.",
                    "Life grows faster to get it over with."
            };
            case GROVE_BORN -> new String[] {
                    "The island's trees know your boots.",
                    "The cherries bloom a little earlier for you.",
                    "Seven forests, one first name basis.",
                    "The Crown leans down to say hello.",
                    "Every stump on the isle remembers you fondly.",
                    "You are, technically, part of the canopy now."
            };
            case SAP_SENSE -> new String[] {
                    "You smell a Crown Find before it lets go.",
                    "Amber hums in your pocket.",
                    "The crowns drop hints. You pick them up.",
                    "Juniper keeps a drawer just for your finds.",
                    "Heartsong is less of a rumour lately.",
                    "The canopy empties its pockets for you."
            };
            case STEADY_HANDS -> new String[] {
                    "The fell marker waits for you now.",
                    "Green looks wider from where you stand.",
                    "Misses happen to other people.",
                    "The axe slows down to hear what you think.",
                    "Titans respect a patient cut.",
                    "The bar is a formality."
            };
            case DEADFALL_DANCER -> new String[] {
                    "Widowmakers keep missing. Rude of them.",
                    "You hear the limb before it hears itself.",
                    "Falling branches file complaints about you.",
                    "Deadfall is just delivery.",
                    "The canopy threw its best. You caught the logs.",
                    "Gravity gave up on you personally."
            };
            case BOARD_RATES -> new String[] {
                    "Pell pays union rates. Mostly to you.",
                    "Your orders come with a thank-you note.",
                    "The Board rounds up out of respect.",
                    "Pell keeps your pin at eye level.",
                    "You are the Lumber Board's favourite problem.",
                    "The island pays you to show up. Then more."
            };
            case HEART_HUNTER -> new String[] {
                    "Heartwood hums when you walk past.",
                    "Trees hide their hearts. Badly, around you.",
                    "The Grove Table saves you a seat.",
                    "You can hear the rings inside the bark.",
                    "Heartwood turns up the way coins do for others.",
                    "Every trunk on the isle is a treasure chest."
            };
            case CROP_GOSSIP -> new String[] {
                    "The wheat has started a group chat.",
                    "You were added without consent.",
                    "The field is leaking spoilers.",
                    "Gossip, but photosynthetic.",
                    "The stalks nominated you for secretary.",
                    "Agriculture is a rumor mill now."
            };
            case WIDE_FURROW -> new String[] {
                    "Your personal space includes the next three rows.",
                    "Neighbors included. Professionally.",
                    "The furrow filed for extra lanes.",
                    "You harvest in zip codes.",
                    "Maps consider you a combine.",
                    "The horizon is in hoe range."
            };
            case SEED_LEDGER -> new String[] {
                    "Every grain is itemized. The field hates it.",
                    "The rake brought a clipboard.",
                    "Wheat arrives pre-filed.",
                    "You audited a harvest.",
                    "Accounting learned to mill.",
                    "The loaf is a quarterly report."
            };
            case SOIL_SENSE -> new String[] {
                    "Eldervale dirt knows your boots.",
                    "The furrows wave when you land.",
                    "You can hear which row is ripe.",
                    "The isle files you under family.",
                    "Topsoil asks for your opinion.",
                    "The island is your home field. It agrees."
            };
            case ROW_RHYTHM -> new String[] {
                    "You harvest in four-four. The wheat keeps time.",
                    "The carrots started clapping on two and four.",
                    "Every row is a bar. You never miss a beat.",
                    "The field hums along. Off-key, loyally.",
                    "Scarecrows tap their feet now.",
                    "The harvest is a song, and you wrote it."
            };
            case BLUE_RIBBON -> new String[] {
                    "You can smell a prize carrot through a wall.",
                    "Judges start sweating when you arrive.",
                    "Potatoes pose when you walk past.",
                    "The ribbon committee sends you a calendar.",
                    "Pumpkins hold their breath to look bigger.",
                    "The county fair renamed itself after you."
            };
            case BIRD_LAW -> new String[] {
                    "You read the crows their rights.",
                    "The flock hired a lawyer. He left too.",
                    "Crows cross the street to avoid you.",
                    "The scarecrow calls you boss.",
                    "Feathers settle out of court.",
                    "The sky is a no-fly zone. By statute."
            };
            case MARKET_DAY -> new String[] {
                    "Every turnip is a negotiation.",
                    "Hattie keeps the good orders in a drawer for you.",
                    "Your beets arrive with a sales pitch.",
                    "Merchants round up when you smile.",
                    "The order board has your handwriting on it.",
                    "The market opens when you do."
            };
            case BITE_ME -> new String[] {
                    "The fish started the argument. You finished it.",
                    "Extra bites, professionally.",
                    "The school nominated you for bully.",
                    "Hooks arrive with plus-ones.",
                    "The catch table sent flowers.",
                    "The ocean is overstaffed. You are why."
            };
            case SHORT_CAST -> new String[] {
                    "Wait times were a suggestion.",
                    "The bobber clocks in early.",
                    "Patience filed for unemployment.",
                    "The lure skipped the small talk.",
                    "Time is decorative now.",
                    "Fish bite out of schedule fear."
            };
            case FISH_LEDGER -> new String[] {
                    "Every nibble is itemized. The ocean hates it.",
                    "The rod brought a clipboard.",
                    "Cod arrives pre-filed.",
                    "You audited a tide.",
                    "Accounting learned to swim.",
                    "The catch is a quarterly report."
            };
            case LAKE_SENSE -> new String[] {
                    "Eldervale water knows your line.",
                    "The lake saves you a seat.",
                    "Ripples point at your bobber.",
                    "The fish call you by name. Rudely.",
                    "The tide checks your schedule first.",
                    "The lake is your home water. It agrees."
            };
            case STEADY_LINE -> new String[] {
                    "Your hands stop shaking. The fish don't.",
                    "The rod stopped arguing with you.",
                    "Gold is a wider place now.",
                    "Your line has a pulse. It's calm.",
                    "Fish sense the patience. Resent it.",
                    "The strike bar waits for you."
            };
            case TALL_TALES -> new String[] {
                    "Every fish you talk about gets heavier. Some really are.",
                    "The one that got away was this big.",
                    "The scales at the Trophy House sweat.",
                    "Your stories have a waiting list.",
                    "Legends ask for your autograph.",
                    "The lake started believing you."
            };
            case TIDE_READER -> new String[] {
                    "You can hear a shoal from the other pier.",
                    "Bubbles gossip. You listen.",
                    "The water tells you where it's boiling.",
                    "Shoals check if you're coming.",
                    "Gulls follow you now.",
                    "The tide files its plans with you."
            };
            case QUICK_HANDS -> new String[] {
                    "Cooldowns were a suggestion.",
                    "The boots are watching.",
                    "Time asked you to slow down. You didn't.",
                    "The cooldown is in therapy.",
                    "You moved before the second happened.",
                    "Clocks are decorative now."
            };
            case LUCKY_STREAK -> new String[] {
                    "The eggs can smell the desperation. They like it.",
                    "Charm remains optional.",
                    "The pets started lining up.",
                    "Luck is doing customer service.",
                    "The catch table sent flowers.",
                    "Fate is in the bag. Literally."
            };
            case NIGHT_OWL -> new String[] {
                    "The sun was slowing you down anyway.",
                    "Caves, but make it cardio.",
                    "The moon clocked your hours.",
                    "Daylight is a scheduling conflict.",
                    "You are nocturnal as a business model.",
                    "The sun took a personal day. Permanently."
            };
            case QUIET_PRIDE -> new String[] {
                    "You don't announce the tanking. You just tank.",
                    "The shield has a library voice.",
                    "Bragging was declined at the door.",
                    "You tank in lowercase.",
                    "The hit landed. You did not comment.",
                    "Silence, but armored."
            };
            case PINCH_PENNY -> new String[] {
                    "Midas still wants a receipt. A shorter one.",
                    "The fee is shrinking out of respect.",
                    "Accounting called it a loyalty discount.",
                    "Midas is on a payment plan.",
                    "The dagger works for exposure. And coins.",
                    "Gold is embarrassed to charge you."
            };
            case DIAMOND_SPINE -> new String[] {
                    "If they hit you, they can have a sample.",
                    "Refund policy: violence.",
                    "The plate started invoicing back.",
                    "Melee is a two-way subscription.",
                    "You are a mirror with opinions.",
                    "Hitting you is a lifestyle mistake."
            };
            case IRON_STOMACH -> new String[] {
                    "Lunch was a threat. You won.",
                    "The menu lost.",
                    "Calories filed for protection.",
                    "You digested the argument.",
                    "Hunger clocked out.",
                    "You are the final course."
            };
            case GOLDEN_HOUR -> new String[] {
                    "Invoices, but festive.",
                    "Petty theft, professionally wrapped.",
                    "The coins started tipping you.",
                    "Profit has a theme song.",
                    "The treasury sent a Christmas card.",
                    "Money follows you out of fear."
            };
            case CHAMBER_PACE -> new String[] {
                    "The corridor blinks. You do not.",
                    "Hallways learn your stride.",
                    "The map is late. You are not.",
                    "Chambers open on schedule — yours.",
                    "You outran the loading screen.",
                    "The dungeon asked for a timeout."
            };
            case STONE_BLOOD -> new String[] {
                    "The walls tried first.",
                    "Brick learned manners.",
                    "You bleed limestone and spite.",
                    "The floor filed a complaint.",
                    "Masonry took a sick day.",
                    "You are load-bearing."
            };
            case RELIC_APPETITE -> new String[] {
                    "Your kit is hungry. Feed it chambers.",
                    "Gear XP arrives with room service.",
                    "The vestige has a meal plan.",
                    "Relics tip for good service.",
                    "Your loadout opened a tab.",
                    "The dungeon is a tasting menu."
            };
            case FLOOR_GRUDGE -> new String[] {
                    "Floor bosses make the list twice.",
                    "Named HP bars get priority mail.",
                    "The sentinel remembers you.",
                    "Boss doors open for overdue invoices.",
                    "Floor clears come with footnotes.",
                    "You billed the entire tower."
            };
        };
    }
}
