package de.aetherion.aethermobs.pet;

import java.util.Locale;

public final class PetFlavor {

    private PetFlavor() {
    }

    public static String forId(String petId) {
        if (petId == null || petId.isBlank()) {
            return "§7It exists. The Aetherlex is still drafting a joke.";
        }
        return switch (petId.toLowerCase(Locale.ROOT)) {
            case "wolf" -> "§7Loyalty with teeth. The pack invoice is itemized.";
            case "pig" -> "§7A walking fortune. Also, unfortunately, bacon-shaped.";
            case "cow" -> "§7Produces milk, opinions, and a surprising amount of spread.";
            case "zombie" -> "§7Still clocked in. The raise never arrived.";
            case "skeleton" -> "§7Calcium with a work ethic. Mining optional, rattling mandatory.";
            case "bat" -> "§7Night shift. Echoes included. Small talk is echolocation.";
            case "cave_spider" -> "§7The cave's middle manager. Venom is the performance review.";
            case "creeper" -> "§7Hisses first. Explains later. HR filed it as 'enthusiasm'.";
            case "squid" -> "§7Ink, excuses, and a talent for leaving the scene.";
            case "glow_squid" -> "§7A lantern that learned to swim. Pets can't hide from this one.";
            case "axolotl" -> "§7Smiles at death. Death has started taking it personally.";
            case "guardian" -> "§7Laser-based customer service. Do not ask for the manager.";
            case "dolphin" -> "§7Faster than your decisions. Judgier than your lag.";
            case "wither" -> "§7Three heads. Zero chill. The Nether's exit interview.";
            case "aetherion" -> "§7The rumor with a hitbox. Please do not file a ticket.";
            case "hawk" -> "§7Altitude as a personality. The ground is a suggestion.";
            case "bee" -> "§7Unionized pollen. Fortune with a stinger in the fine print.";
            case "pigeon" -> "§7Urban wildlife. Spreads rumors, crumbs, and stats.";
            case "ocelot" -> "§7Beach cat. Fishes like it has a side hustle.";
            case "parrot" -> "§7Repeats your mistakes, but with better lighting.";
            case "panda" -> "§7Bamboo diplomat. Moves like a sofa with opinions.";
            case "goat" -> "§7Ram first, mountain later. Gravity is a rumor.";
            case "llama" -> "§7Spits professionally. Carries grudges and cargo.";
            case "fox" -> "§7Sleeps in the berries. Steals the plot.";
            case "rabbit" -> "§7Hopping is a tax write-off. Luck is not.";
            case "farm_rabbit" -> "§7Employee of the carrot. HR is a scarecrow.";
            case "horse" -> "§7Four legs of overtime. The saddle is emotional support.";
            case "sack_of_potatoes" -> "§7A burlap conspiracy. Contains potatoes, denial, and a union pamphlet.";
            case "polar_bear" -> "§7Ice with a job. Do not ask about the fish count.";
            case "yeti" -> "§7A walking blizzard with unionized fists.";
            case "snowflake" -> "§7A mage that never learned to stay on the ground.";
            case "ice_dragon" -> "§7Winter with a hitbox. Freezes the room, then invoices the frost.";
            case "fire_dragon" -> "§7A walking kiln. The Nether asked it to keep its voice down.";
            case "water_dragon" -> "§7Ten blocks down. One opinion. You are the current.";
            case "nature_dragon" -> "§7The harvest with wings. Crops stand taller when it lands.";
            case "mining_dragon", "poison_dragon" -> "§7Ore with wings. The cave files a pickaxe request.";
            case "forest_dragon" -> "§7The canopy's final argument. Trunks lean when it passes.";
            case "lightning_dragon" -> "§7A weather event with teeth. The sky filed a complaint.";
            case "blaze" -> "§7Rods, rage, and a personal space policy written in fire.";
            case "slime_minion" -> "§7Bounces first. Splits later. HR is a cube.";
            case "ghast" -> "§7Cries in fireballs. The ceiling has heard this story.";
            case "turtle" -> "§7A beach tank. Knockback filed a complaint and lost.";
            case "camel" -> "§7Two humps of patience. The desert still owes it money.";
            case "armadillo" -> "§7A rolling audit. Defense in a cute briefcase.";
            case "allay" -> "§7Collects drops and unresolved feelings.";
            case "dungeon_zombie" -> "§7Same job, better lighting, worse health insurance.";
            case "dungeon_skeleton" -> "§7The bones unionized. Mining power is in the contract.";
            case "dungeon_dragon" -> "§7A floor boss that learned to follow you home.";
            case "cod" -> "§7The ocean's intern. Bait with a resume.";
            case "salmon" -> "§7Upstream is a lifestyle. Downstream is for quitters.";
            case "pufferfish" -> "§7Personal space, but make it poisonous.";
            case "tropical_fish" -> "§7A walking brochure for the reef. Catch rates included.";
            case "frog" -> "§7Hops first. Asks questions never. The swamp's PR team.";
            case "witch" -> "§7Brews opinions. The cauldron is a suggestion box.";
            case "mudling" -> "§7Mostly mud. Slightly alive. Fully unionized.";
            case "deer" -> "§7Startles professionally. The forest's early-warning system.";
            case "squirrel" -> "§7Caches nuts, secrets, and your last sandwich.";
            case "boar" -> "§7Tusks with a calendar. Charge is a lifestyle.";
            case "owl" -> "§7Judges softly. Sees everything. Shares nothing.";
            case "sheep" -> "§7Fluff as armor. Baa is a legal filing.";
            case "butterfly" -> "§7Petals with a flight plan. Catch rates blush.";
            case "mooshroom" -> "§7A cow that filed for fungal benefits.";
            case "shroomling" -> "§7Tiny spores, big moods. Night vision included.";
            case "cat" -> "§7Owns the village. You are furniture.";
            case "iron_golem" -> "§7Village security. Soft heart, hard fists.";
            case "sniffer" -> "§7Digs history. Finds seeds. Judges your shovel.";
            case "moss_sprite" -> "§7The cave's softest roommate. Regenerates feelings.";
            case "swamp_hag" -> "§7The bog's final boss of vibes. Hex included.";
            case "forest_spirit" -> "§7Leaves with a soul. Fortune wears green.";
            case "bloom_fairy" -> "§7Petals, altitude, and catch rates with glitter.";
            case "sand_wraith" -> "§7A mirage with teeth. The desert finally answered.";
            case "mycelord" -> "§7King of the caps. Night vision is a decree.";
            case "lush_oracle" -> "§7Roots remember. The cave asks it for directions.";
            case "hacker" -> glitchLine();
            default -> "§7It showed up. The paperwork is still in committee.";
        };
    }

    /**
     * Hypixel-style glitch lore: obfuscated glyphs with a readable flicker.
     * {@code §k} already animates client-side.
     */
    public static String glitchLine() {
        String[] fragments = {
                "§8§kᚠᚢᚦᚨᚱᚲ§r §7signal lost §8§k₪₪₪₪",
                "§8§k████████§r §7rootkit online §8§k░▒▓█",
                "§8§k⌘⌀⌂⌀⌘§r §7access §kdenied§r §8§k⌘⌀⌂⌀⌘",
                "§8§kᛒᛖᛏᚨ§r §7protocol hijack §8§k⌬⌬⌬⌬",
                "§8§kΩΨΦΣ§r §7anonymous mask §8§kΨΩΦΣ",
                "§7§k||||||§r§8ERR§7§k||||||§r §8§kneverdies"
        };
        long tick = System.currentTimeMillis() / 400L;
        return fragments[(int) (Math.floorMod(tick, fragments.length))];
    }

    public static String glance(String petId, String petName) {
        String name = petName == null || petName.isBlank() ? "Your pet" : petName;
        if (petId == null || petId.isBlank()) {
            return "§7" + name + " §8looks at you like you owe it snacks.";
        }
        return switch (petId.toLowerCase(Locale.ROOT)) {
            case "wolf" -> "§7" + name + " §8stares. Loyalty invoice attached.";
            case "pig" -> "§7" + name + " §8oinks at you. The fortune looks... judgmental.";
            case "cow" -> "§7" + name + " §8blinks slowly. You have been moo'd.";
            case "creeper" -> "§7" + name + " §8hisses fondly. HR still hates this.";
            case "cat", "ocelot" -> "§7" + name + " §8acknowledges you. Barely.";
            case "frog" -> "§7" + name + " §8blinks. The swamp approved this message.";
            case "owl" -> "§7" + name + " §8stares. You have been audited.";
            case "sheep" -> "§7" + name + " §8baas. That was a complete sentence.";
            case "iron_golem" -> "§7" + name + " §8nods once. Security has spoken.";
            case "sniffer" -> "§7" + name + " §8sniffs you. History is unimpressed.";
            case "parrot" -> "§7" + name + " §8repeats your name, then your worst decision.";
            case "fox" -> "§7" + name + " §8grins. Something of yours is already gone.";
            case "bee" -> "§7" + name + " §8buzzes a union greeting.";
            case "allay" -> "§7" + name + " §8chimes. It found feelings. They are yours.";
            case "panda" -> "§7" + name + " §8looks stuffed. Emotionally, and otherwise.";
            case "dragon", "fire_dragon", "ice_dragon", "water_dragon", "nature_dragon",
                 "mining_dragon", "poison_dragon", "forest_dragon", "lightning_dragon", "dungeon_dragon" ->
                    "§7" + name + " §8gives you the ancient nod. You are... tolerated.";
            case "sack_of_potatoes" -> "§7" + name + " §8rustles. That's affection. Probably.";
            case "hacker" -> "§7" + name + " §8flickers. §k██§r §8You were never here.";
            default -> "§7" + name + " §8looks at you like you owe it snacks.";
        };
    }
}
