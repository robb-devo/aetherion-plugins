package de.aetherion.aethermobs.pet.body;

import de.aetherion.aethermobs.pet.PetDefinition;
import de.aetherion.aethermobs.pet.PetHabitat;
import de.aetherion.aethermobs.pet.PetHead;
import de.aetherion.aethermobs.pet.PetInstance;
import de.aetherion.aethermobs.pet.PetSpawnType;
import de.aetherion.aethermobs.pet.body.BodyKit.Bi;
import de.aetherion.aethermobs.pet.body.BodyKit.DragonPal;
import de.aetherion.aethermobs.pet.body.BodyKit.Ears;
import de.aetherion.aethermobs.pet.body.BodyKit.Pal;
import de.aetherion.aethermobs.pet.body.BodyKit.Quad;
import de.aetherion.aethermobs.pet.body.BodyKit.Tail;
import de.aetherion.items.model.Rarity;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static de.aetherion.aethermobs.pet.body.BodyKit.bd;
import static org.bukkit.Material.*;

/**
 * The catalog: every pet id gets a skeleton and a procedural body built from block + head parts.
 * Player heads stay the face (identity); the body around them is new. Unknown ids fall back to a body
 * chosen from spawn type / habitat so a new pet never renders as a floating skull again.
 */
public final class PetBodies {

    private PetBodies() {
    }

    private static final Map<String, ItemStack> FACES = new ConcurrentHashMap<>();

    private static ItemStack face(String id) {
        return FACES.computeIfAbsent(id, PetHead::create).clone();
    }

    private static ItemStack it(Material material) {
        return new ItemStack(material);
    }

    /** Skeleton per id, for menus, smoke commands and the end-note catalog map. */
    public static Skeleton skeletonOf(PetDefinition definition) {
        if (definition == null) {
            return Skeleton.DRIFT;
        }
        return switch (definition.getId().toLowerCase(Locale.ROOT)) {
            case "wolf", "fox", "cat", "ocelot", "deer", "goat", "llama", "horse", "pig", "boar", "creeper" -> Skeleton.TROT;
            case "cow", "sheep", "polar_bear", "panda", "camel", "sniffer", "mooshroom" -> Skeleton.AMBLE;
            case "zombie", "skeleton", "dungeon_zombie", "dungeon_skeleton", "witch", "hacker", "swamp_hag" -> Skeleton.STRIDE;
            case "iron_golem", "yeti" -> Skeleton.LUMBER;
            case "rabbit", "farm_rabbit", "frog", "squirrel", "sack_of_potatoes" -> Skeleton.HOP;
            case "slime_minion" -> Skeleton.BOUNCE;
            case "cave_spider" -> Skeleton.SCUTTLE;
            case "turtle", "armadillo" -> Skeleton.CRAWL;
            case "mudling" -> Skeleton.SLITHER;
            case "shroomling", "mycelord" -> Skeleton.WADDLE;
            case "bee", "allay" -> Skeleton.HOVER;
            case "butterfly", "bloom_fairy" -> Skeleton.FLUTTER;
            case "bat", "pigeon", "parrot", "owl" -> Skeleton.FLAP;
            case "hawk" -> Skeleton.SOAR;
            case "moss_sprite", "forest_spirit", "lush_oracle", "snowflake", "sand_wraith" -> Skeleton.DRIFT;
            case "blaze", "ghast", "wither" -> Skeleton.HAUNT;
            case "cod", "salmon", "pufferfish", "tropical_fish", "axolotl", "guardian" -> Skeleton.SWIM;
            case "squid", "glow_squid" -> Skeleton.JET;
            case "dolphin" -> Skeleton.PORPOISE;
            case "ice_dragon", "fire_dragon", "nature_dragon", "forest_dragon", "mining_dragon", "poison_dragon",
                 "dungeon_dragon" -> Skeleton.DRAGON;
            case "lightning_dragon", "water_dragon", "aetherion" -> Skeleton.WYRM;
            default -> fallbackSkeleton(definition);
        };
    }

    private static Skeleton fallbackSkeleton(PetDefinition definition) {
        PetSpawnType type = definition.getSpawnType();
        if (type == PetSpawnType.AQUATIC) {
            return Skeleton.SWIM;
        }
        if (type == PetSpawnType.SKY) {
            return Skeleton.FLAP;
        }
        if (type == PetSpawnType.NETHER) {
            return Skeleton.HAUNT;
        }
        return Skeleton.TROT;
    }

    /** Readable catalog map (id -> skeleton) in registry order. */
    public static Map<String, Skeleton> map(Iterable<PetDefinition> definitions) {
        Map<String, Skeleton> out = new LinkedHashMap<>();
        for (PetDefinition d : definitions) {
            out.put(d.getId(), skeletonOf(d));
        }
        return out;
    }

    /**
     * Build the body for one pet instance.
     *
     * @param equipped companions run a touch smaller than their wild cousins (dragons much smaller)
     */
    public static Rig create(PetInstance pet, boolean equipped) {
        PetDefinition def = pet == null ? null : pet.getDefinition();
        String id = def == null ? "" : def.getId().toLowerCase(Locale.ROOT);
        Rarity rarity = pet == null || pet.getRarity() == null ? Rarity.COMMON : pet.getRarity();
        boolean rich = rarity.ordinal() >= Rarity.RARE.ordinal();
        Rig rig = build(id, def, rich, rarity);
        float base = speciesSize(id);
        float rarityScale = switch (rarity) {
            case COMMON -> 1.0f;
            case UNCOMMON -> 1.02f;
            case RARE -> 1.05f;
            case EPIC -> 1.08f;
            case LEGENDARY -> 1.12f;
            case MYTHIC -> 1.16f;
            case AETHERED -> 1.2f;
        };
        float carry = equipped ? (rig.isDragon() ? 0.62f : 0.86f) : 1.0f;
        rig.size = base * rarityScale * carry;
        return rig;
    }

    private static float speciesSize(String id) {
        return switch (id) {
            case "horse", "camel", "polar_bear", "panda", "sniffer", "iron_golem", "yeti" -> 1.0f;
            case "bee", "butterfly", "bat" -> 0.9f;
            case "cod", "tropical_fish", "pufferfish" -> 0.9f;
            default -> 1.0f;
        };
    }

    private static Rig build(String id, PetDefinition def, boolean rich, Rarity rarity) {
        return switch (id) {

            /* ---------------- canines / felines / small quads ---------------- */

            case "wolf" -> {
                Rig rig = BodyKit.quad(Skeleton.TROT, new Quad().body(0.58f, 0.28f, 0.28f).legs(0.32f, 0.1f)
                                .head(0.72f, 0.07f, 0.03f).tail(Tail.BUSHY, 0.32f, 0.09f, 28f).ears(Ears.POINTY).mane(),
                        Pal.of(LIGHT_GRAY_WOOL, GRAY_WOOL, WHITE_WOOL), face("wolf"));
                yield rig;
            }
            case "fox" -> {
                Rig rig = BodyKit.quad(Skeleton.TROT, new Quad().body(0.54f, 0.26f, 0.25f).legs(0.22f, 0.08f)
                                .head(0.68f, 0.06f, 0.03f).tail(Tail.BUSHY, 0.4f, 0.12f, 8f).ears(Ears.POINTY),
                        Pal.of(ORANGE_WOOL, BLACK_WOOL, ORANGE_WOOL), face("fox"));
                int tip = rig.bone("tail_tip", rig.roles().tail[0], 0f, 0f, -0.4f);
                rig.box(tip, bd(WHITE_WOOL), 0f, 0f, -0.04f, 0.17f, 0.17f, 0.1f, Rig.DETAIL);
                rig.box(rig.roles().body, bd(WHITE_WOOL), 0f, -0.1f, 0.18f, 0.2f, 0.07f, 0.22f, Rig.DETAIL);
                yield rig;
            }
            case "cat" -> BodyKit.quad(Skeleton.TROT, new Quad().body(0.5f, 0.23f, 0.23f).legs(0.21f, 0.07f)
                            .head(0.62f, 0.07f, 0.02f).tail(Tail.CURL, 0.42f, 0.06f, 55f).ears(Ears.POINTY),
                    Pal.of(GRAY_WOOL, LIGHT_GRAY_WOOL, GRAY_WOOL), face("cat"));
            case "ocelot" -> {
                Rig rig = BodyKit.quad(Skeleton.TROT, new Quad().body(0.52f, 0.24f, 0.24f).legs(0.23f, 0.075f)
                                .head(0.64f, 0.06f, 0.02f).tail(Tail.LONG, 0.44f, 0.06f, 20f).ears(Ears.ROUND),
                        Pal.of(YELLOW_TERRACOTTA, YELLOW_TERRACOTTA, BROWN_TERRACOTTA), face("ocelot"));
                if (rich) {
                    spots(rig, BROWN_TERRACOTTA, 0.24f, 0.24f, 0.52f);
                }
                yield rig;
            }
            case "pig" -> BodyKit.quad(Skeleton.TROT, new Quad().body(0.6f, 0.4f, 0.36f).legs(0.18f, 0.13f)
                            .head(0.8f, 0.0f, 0.02f).tail(Tail.CURL, 0.12f, 0.05f, 40f),
                    Pal.of(PINK_TERRACOTTA, PINK_TERRACOTTA, PINK_WOOL), face("pig"));
            case "boar" -> {
                Rig rig = BodyKit.quad(Skeleton.TROT, new Quad().body(0.62f, 0.38f, 0.36f).legs(0.2f, 0.12f)
                                .head(0.78f, 0.0f, 0.02f).tail(Tail.THIN, 0.14f, 0.05f, -20f).ears(Ears.POINTY),
                        Pal.of(BROWN_WOOL, BROWN_TERRACOTTA, BLACK_WOOL), face("boar"));
                rig.box(rig.roles().body, bd(BLACK_WOOL), 0f, 0.2f, 0.05f, 0.08f, 0.08f, 0.5f, Rig.DETAIL);
                int head = rig.roles().head;
                rig.box(head, bd(WHITE_CONCRETE), 0.1f, -0.08f, 0.26f, 0.04f, 0.1f, 0.04f, -30f, 0f, 0f, Rig.DETAIL);
                rig.box(head, bd(WHITE_CONCRETE), -0.1f, -0.08f, 0.26f, 0.04f, 0.1f, 0.04f, -30f, 0f, 0f, Rig.DETAIL);
                yield rig;
            }
            case "deer" -> {
                Rig rig = BodyKit.quad(Skeleton.TROT, new Quad().body(0.66f, 0.3f, 0.32f).legs(0.48f, 0.075f)
                                .head(0.66f, 0f, 0f).neck(0.28f, 55f, 0.15f).tail(Tail.STUB, 0.08f, 0.1f, 40f).ears(Ears.POINTY),
                        Pal.of(BROWN_WOOL, BROWN_TERRACOTTA, WHITE_WOOL), face("deer"));
                antlers(rig, rig.roles().head, rich ? 2 : 1, STRIPPED_BIRCH_WOOD);
                yield rig;
            }
            case "goat" -> {
                Rig rig = BodyKit.quad(Skeleton.TROT, new Quad().body(0.58f, 0.32f, 0.34f).legs(0.3f, 0.09f)
                                .head(0.7f, 0.08f, 0.03f).tail(Tail.STUB, 0.08f, 0.08f, 50f),
                        Pal.of(WHITE_WOOL, LIGHT_GRAY_WOOL, WHITE_WOOL), face("goat"));
                int head = rig.roles().head;
                rig.box(head, bd(GRAY_TERRACOTTA), 0.08f, 0.2f, -0.06f, 0.05f, 0.05f, 0.24f, -40f, 10f, 0f, Rig.DETAIL);
                rig.box(head, bd(GRAY_TERRACOTTA), -0.08f, 0.2f, -0.06f, 0.05f, 0.05f, 0.24f, -40f, -10f, 0f, Rig.DETAIL);
                yield rig;
            }
            case "llama" -> {
                Rig rig = BodyKit.quad(Skeleton.TROT, new Quad().body(0.66f, 0.38f, 0.4f).legs(0.44f, 0.11f)
                                .head(0.7f, 0f, 0f).neck(0.4f, 78f, 0.2f).tail(Tail.STUB, 0.1f, 0.12f, 30f).ears(Ears.TALL),
                        Pal.of(WHITE_TERRACOTTA, WHITE_TERRACOTTA, WHITE_WOOL), face("llama"));
                rig.box(rig.roles().body, bd(rich ? PURPLE_WOOL : RED_WOOL), 0f, 0.205f, 0.02f, 0.41f, 0.03f, 0.46f, Rig.DETAIL);
                yield rig;
            }
            case "horse" -> {
                Rig rig = BodyKit.quad(Skeleton.TROT, new Quad().body(0.92f, 0.42f, 0.44f).legs(0.6f, 0.13f)
                                .head(0.78f, 0f, 0f).neck(0.42f, 58f, 0.22f).tail(Tail.HANG, 0.42f, 0.1f, -55f).ears(Ears.POINTY),
                        Pal.of(BROWN_TERRACOTTA, BROWN_TERRACOTTA, BLACK_WOOL), face("horse"));
                rig.box(rig.roles().neck[0], bd(BLACK_WOOL), 0f, 0.11f, 0.2f, 0.06f, 0.08f, 0.44f, Rig.DETAIL);
                if (rich) {
                    for (int leg : rig.roles().legs) {
                        rig.box(leg, bd(WHITE_TERRACOTTA), 0f, -0.6f, 0f, 0.14f, 0.1f, 0.14f, Rig.EXTRA);
                    }
                }
                yield rig;
            }
            case "creeper" -> {
                Rig rig = BodyKit.quad(Skeleton.TROT, new Quad().body(0.24f, 0.28f, 0.5f).legs(0.16f, 0.12f)
                                .head(0.8f, 0.22f, -0.12f).tail(Tail.NONE, 0f, 0f, 0f),
                        Pal.of(LIME_CONCRETE_POWDER, GREEN_CONCRETE_POWDER, GREEN_CONCRETE_POWDER), face("creeper"));
                yield rig;
            }

            /* ---------------- heavy quads ---------------- */

            case "cow" -> {
                Rig rig = BodyKit.quad(Skeleton.AMBLE, new Quad().body(0.8f, 0.5f, 0.46f).legs(0.36f, 0.15f)
                                .head(0.8f, 0.04f, 0.02f).tail(Tail.HANG, 0.34f, 0.06f, -60f),
                        Pal.of(WHITE_WOOL, BLACK_WOOL, BLACK_WOOL), face("cow"));
                spots(rig, BLACK_WOOL, 0.5f, 0.46f, 0.8f);
                horns(rig, rig.roles().head, WHITE_TERRACOTTA, 0.8f);
                yield rig;
            }
            case "mooshroom" -> {
                Rig rig = BodyKit.quad(Skeleton.AMBLE, new Quad().body(0.8f, 0.5f, 0.46f).legs(0.36f, 0.15f)
                                .head(0.8f, 0.04f, 0.02f).tail(Tail.HANG, 0.34f, 0.06f, -60f),
                        Pal.of(RED_WOOL, LIGHT_GRAY_WOOL, RED_WOOL), face("mooshroom"));
                spots(rig, WHITE_WOOL, 0.5f, 0.46f, 0.8f);
                int body = rig.roles().body;
                rig.item(body, it(RED_MUSHROOM), 0.1f, 0.36f, 0.18f, 0.42f, 0f, 30f, 0f, Rig.DETAIL);
                rig.item(body, it(RED_MUSHROOM), -0.08f, 0.36f, -0.2f, 0.42f, 0f, -60f, 0f, Rig.DETAIL);
                if (rich) {
                    rig.item(rig.roles().head, it(RED_MUSHROOM), 0f, 0.3f, 0.02f, 0.5f, 0f, 0f, 0f, Rig.EXTRA);
                }
                yield rig;
            }
            case "sheep" -> {
                Rig rig = BodyKit.quad(Skeleton.AMBLE, new Quad().body(0.66f, 0.5f, 0.46f).legs(0.3f, 0.11f)
                                .head(0.7f, 0.04f, 0.04f).tail(Tail.STUB, 0.1f, 0.12f, 20f),
                        Pal.of(WHITE_WOOL, WHITE_TERRACOTTA, WHITE_WOOL), face("sheep"));
                if (rich) {
                    rig.box(rig.roles().body, bd(WHITE_WOOL), 0f, 0.24f, 0f, 0.4f, 0.12f, 0.5f, Rig.EXTRA);
                }
                yield rig;
            }
            case "polar_bear" -> BodyKit.quad(Skeleton.AMBLE, new Quad().body(0.9f, 0.54f, 0.5f).legs(0.36f, 0.18f)
                            .head(0.86f, 0.0f, 0.04f).tail(Tail.STUB, 0.1f, 0.12f, 10f).ears(Ears.ROUND),
                    Pal.of(WHITE_WOOL, WHITE_WOOL, WHITE_WOOL), face("polar_bear"));
            case "panda" -> {
                Rig rig = BodyKit.quad(Skeleton.AMBLE, new Quad().body(0.82f, 0.56f, 0.52f).legs(0.3f, 0.18f)
                                .head(0.86f, 0.02f, 0.04f).tail(Tail.STUB, 0.08f, 0.1f, 10f).ears(Ears.ROUND),
                        Pal.of(WHITE_WOOL, BLACK_WOOL, WHITE_WOOL, BLACK_WOOL), face("panda"));
                rig.box(rig.roles().body, bd(BLACK_WOOL), 0f, 0.0f, 0.24f, 0.575f, 0.535f, 0.18f, Rig.DETAIL);
                yield rig;
            }
            case "camel" -> {
                Rig rig = BodyKit.quad(Skeleton.AMBLE, new Quad().body(0.92f, 0.44f, 0.44f).legs(0.62f, 0.13f)
                                .head(0.72f, 0f, 0f).neck(0.48f, 40f, 0.2f).tail(Tail.HANG, 0.3f, 0.06f, -60f),
                        Pal.of(YELLOW_TERRACOTTA, YELLOW_TERRACOTTA, BROWN_TERRACOTTA), face("camel"));
                rig.box(rig.roles().body, bd(YELLOW_TERRACOTTA), 0f, 0.28f, -0.04f, 0.3f, 0.18f, 0.36f, Rig.CORE);
                if (rich) {
                    rig.box(rig.roles().body, bd(RED_WOOL), 0f, 0.225f, 0.22f, 0.46f, 0.04f, 0.2f, Rig.EXTRA);
                }
                yield rig;
            }
            case "sniffer" -> {
                Rig rig = BodyKit.quad(Skeleton.AMBLE, new Quad().body(0.95f, 0.58f, 0.5f).legs(0.28f, 0.16f)
                                .head(0.86f, -0.06f, 0.04f).tail(Tail.NONE, 0f, 0f, 0f),
                        Pal.of(RED_TERRACOTTA, BROWN_TERRACOTTA, MOSS_BLOCK), face("sniffer"));
                rig.box(rig.roles().body, bd(MOSS_BLOCK), 0f, 0.27f, -0.04f, 0.5f, 0.1f, 0.7f, Rig.DETAIL);
                if (rich) {
                    rig.item(rig.roles().body, it(TORCHFLOWER), 0.12f, 0.42f, -0.1f, 0.5f, 0f, 20f, 0f, Rig.EXTRA);
                }
                yield rig;
            }

            /* ---------------- bipeds ---------------- */

            case "zombie", "dungeon_zombie" -> {
                boolean dungeon = id.startsWith("dungeon");
                Rig rig = BodyKit.biped(Skeleton.STRIDE, new Bi().legs(0.32f, 0.12f).torso(0.3f, 0.36f, 0.17f)
                                .arms(0.34f, 0.1f, 80f).head(0.74f).colours(dungeon ? GRAY_WOOL : BLUE_WOOL,
                                        dungeon ? GREEN_TERRACOTTA : GREEN_CONCRETE_POWDER),
                        Pal.of(dungeon ? BLACK_WOOL : CYAN_WOOL, BLUE_WOOL, GREEN_TERRACOTTA),
                        new ItemStack(ZOMBIE_HEAD));
                if (dungeon) {
                    BodyKit.eyes(rig, rig.roles().head, RED_CONCRETE, 0.08f, 0.2f, 0.19f, 0.045f, Rig.DETAIL);
                }
                yield rig;
            }
            case "skeleton", "dungeon_skeleton" -> {
                boolean dungeon = id.startsWith("dungeon");
                Material bone = dungeon ? GRAY_CONCRETE : BONE_BLOCK;
                Rig rig = BodyKit.biped(Skeleton.STRIDE, new Bi().legs(0.34f, 0.07f).torso(0.24f, 0.34f, 0.1f)
                                .arms(0.34f, 0.065f, 0f).head(0.72f).colours(bone, bone),
                        Pal.of(dungeon ? GRAY_TERRACOTTA : WHITE_TERRACOTTA, bone, bone),
                        new ItemStack(SKELETON_SKULL));
                rig.item(rig.roles().arms[1], it(BOW), 0f, -0.3f, 0.06f, 0.5f, 0f, 90f, -20f, Rig.DETAIL);
                if (dungeon) {
                    BodyKit.eyes(rig, rig.roles().head, LIGHT_BLUE_CONCRETE, 0.08f, 0.2f, 0.19f, 0.045f, Rig.DETAIL);
                }
                yield rig;
            }
            case "witch", "swamp_hag" -> witch(id.equals("swamp_hag"), rich);
            case "hacker" -> {
                Rig rig = BodyKit.biped(Skeleton.STRIDE, new Bi().legs(0.3f, 0.11f).torso(0.3f, 0.36f, 0.18f)
                                .arms(0.32f, 0.1f, 20f).head(0.72f).colours(GRAY_CONCRETE, BLACK_WOOL),
                        Pal.of(BLACK_WOOL, GRAY_CONCRETE, BLACK_WOOL), face("hacker"));
                rig.box(rig.roles().body, bd(BLACK_WOOL), 0f, 0.38f, -0.08f, 0.34f, 0.14f, 0.12f, Rig.DETAIL);
                int screen = rig.bone("screen", rig.roles().body, 0f, 0.26f, 0.36f);
                rig.box(screen, bd(LIME_STAINED_GLASS), 0f, 0f, 0f, 0.34f, 0.2f, 0.02f, 10f, 0f, 0f, Rig.CORE).bright();
                rig.item(screen, it(COMMAND_BLOCK), 0f, -0.16f, -0.02f, 0.18f, 0f, 0f, 0f, Rig.DETAIL).bright();
                rig.roles().orbit = new int[]{screen};
                yield rig;
            }
            case "iron_golem" -> {
                Rig rig = BodyKit.biped(Skeleton.LUMBER, new Bi().legs(0.36f, 0.16f).torso(0.5f, 0.44f, 0.28f)
                                .arms(0.62f, 0.14f, 0f).head(0.72f).hunch(8f).colours(IRON_BLOCK, IRON_BLOCK),
                        Pal.of(IRON_BLOCK, IRON_BLOCK, IRON_BLOCK), face("iron_golem"));
                rig.box(rig.roles().body, bd(MOSS_BLOCK), 0.12f, 0.3f, 0.145f, 0.12f, 0.2f, 0.02f, Rig.DETAIL);
                if (rich) {
                    rig.item(rig.roles().arms[0], it(POPPY), 0f, -0.58f, 0.08f, 0.45f, 0f, 0f, 0f, Rig.EXTRA);
                }
                yield rig;
            }
            case "yeti" -> {
                Rig rig = BodyKit.biped(Skeleton.LUMBER, new Bi().legs(0.34f, 0.16f).torso(0.52f, 0.46f, 0.32f)
                                .arms(0.6f, 0.16f, 0f).head(0.8f).hunch(14f).colours(WHITE_WOOL, WHITE_WOOL),
                        Pal.of(WHITE_WOOL, WHITE_WOOL, LIGHT_BLUE_WOOL), face("yeti"));
                int head = rig.roles().head;
                rig.box(head, bd(PACKED_ICE), 0.14f, 0.38f, -0.02f, 0.06f, 0.18f, 0.06f, 0f, 0f, -24f, Rig.DETAIL);
                rig.box(head, bd(PACKED_ICE), -0.14f, 0.38f, -0.02f, 0.06f, 0.18f, 0.06f, 0f, 0f, 24f, Rig.DETAIL);
                yield rig;
            }

            /* ---------------- hoppers ---------------- */

            case "rabbit" -> BodyKit.hopper(0.24f, 0.24f, 0.32f, 0.56f, 0.18f,
                    Pal.of(BROWN_WOOL, BROWN_WOOL, WHITE_WOOL), face("rabbit"), Ears.TALL, Tail.FLUFF, 0.08f);
            case "farm_rabbit" -> BodyKit.hopper(0.25f, 0.25f, 0.33f, 0.58f, 0.18f,
                    Pal.of(WHITE_WOOL, WHITE_WOOL, WHITE_WOOL), face("farm_rabbit"), Ears.TALL, Tail.FLUFF, 0.08f);
            case "frog" -> {
                Rig rig = BodyKit.hopper(0.34f, 0.2f, 0.34f, 0.64f, 0.2f,
                        Pal.of(ORANGE_TERRACOTTA, GREEN_TERRACOTTA, ORANGE_TERRACOTTA), face("frog"), Ears.NONE, Tail.NONE, 0f);
                int body = rig.roles().body;
                rig.box(body, bd(GREEN_TERRACOTTA), 0.1f, -0.1f, 0.14f, 0.06f, 0.06f, 0.08f, Rig.DETAIL);
                rig.box(body, bd(GREEN_TERRACOTTA), -0.1f, -0.1f, 0.14f, 0.06f, 0.06f, 0.08f, Rig.DETAIL);
                yield rig;
            }
            case "squirrel" -> {
                Rig rig = BodyKit.hopper(0.18f, 0.2f, 0.26f, 0.48f, 0.14f,
                        Pal.of(ORANGE_TERRACOTTA, ORANGE_TERRACOTTA, ORANGE_WOOL), face("squirrel"), Ears.TUFT, Tail.NONE, 0f);
                int body = rig.roles().body;
                int t1 = rig.bone("tail", body, 0f, 0.02f, -0.14f, -70f, 0f, 0f);
                int t2 = rig.bone("tail2", t1, 0f, 0f, -0.24f, -60f, 0f, 0f);
                rig.box(t1, bd(ORANGE_WOOL), 0f, 0f, -0.12f, 0.16f, 0.16f, 0.26f, Rig.CORE);
                rig.box(t2, bd(ORANGE_WOOL), 0f, 0f, -0.1f, 0.18f, 0.18f, 0.22f, Rig.CORE);
                rig.roles().tail = new int[]{t1, t2};
                rig.top += 0.15f;
                yield rig;
            }
            case "sack_of_potatoes" -> {
                Rig rig = new Rig(Skeleton.HOP, false);
                Rig.Roles r = rig.roles();
                rig.bodyHalf = 0.23f;
                int body = rig.bone("body", 0, 0f, 0.24f, 0f);
                r.body = body;
                r.bodyParts.add(rig.box(body, bd(BROWN_WOOL), 0f, 0f, 0f, 0.42f, 0.46f, 0.38f, Rig.CORE).shadow());
                rig.box(body, bd(BROWN_WOOL), 0f, 0.27f, 0f, 0.26f, 0.08f, 0.24f, Rig.CORE);
                rig.box(body, bd(BROWN_TERRACOTTA), 0f, 0.23f, 0f, 0.3f, 0.04f, 0.28f, Rig.DETAIL);
                int head = rig.bone("head", body, 0f, 0.02f, 0.1f);
                r.head = head;
                rig.item(head, face("sack_of_potatoes"), 0f, 0f, 0.02f, 0.62f, 0f, 0f, 0f, Rig.CORE);
                rig.item(body, it(POTATO), 0.05f, 0.36f, 0.02f, 0.38f, 0f, 30f, 20f, Rig.DETAIL);
                if (rich) {
                    rig.item(body, it(rarity.ordinal() >= Rarity.LEGENDARY.ordinal() ? GOLDEN_CARROT : BAKED_POTATO),
                            -0.06f, 0.37f, -0.04f, 0.38f, 0f, -40f, -15f, Rig.EXTRA);
                }
                rig.top = 0.95f;
                rig.middle = 0.24f;
                yield rig;
            }
            case "slime_minion" -> {
                Rig rig = new Rig(Skeleton.BOUNCE, false);
                Rig.Roles r = rig.roles();
                rig.bodyHalf = 0.3f;
                int body = rig.bone("body", 0, 0f, 0.3f, 0f);
                r.body = body;
                r.head = body;
                rig.box(body, bd(LIME_CONCRETE), 0f, -0.04f, 0f, 0.3f, 0.3f, 0.3f, Rig.CORE);
                rig.item(body, face("slime_minion"), 0f, 0.02f, 0.12f, 0.6f, 0f, 0f, 0f, Rig.CORE);
                r.bodyParts.add(rig.box(body, bd(SLIME_BLOCK), 0f, 0f, 0f, 0.6f, 0.6f, 0.6f, Rig.CORE).shadow());
                if (rich) {
                    int baby = rig.bone("orbit_0", body, 0.42f, -0.18f, -0.1f);
                    rig.box(baby, bd(SLIME_BLOCK), 0f, 0f, 0f, 0.18f, 0.18f, 0.18f, Rig.EXTRA);
                    r.orbit = new int[]{baby};
                }
                rig.top = 0.95f;
                rig.middle = 0.3f;
                yield rig;
            }

            /* ---------------- critters on the ground ---------------- */

            case "cave_spider" -> spider(face("cave_spider"), CYAN_TERRACOTTA, BLACK_TERRACOTTA, rich);
            case "turtle" -> shell(face("turtle"), GREEN_TERRACOTTA, LIME_TERRACOTTA, LIME_TERRACOTTA, 0.62f, 0.7f, 0.5f);
            case "armadillo" -> {
                Rig rig = shell(face("armadillo"), BROWN_TERRACOTTA, PINK_TERRACOTTA, PINK_TERRACOTTA, 0.36f, 0.5f, 0.52f);
                int body = rig.roles().body;
                rig.box(body, bd(PINK_TERRACOTTA), 0f, 0.1f, 0.04f, 0.38f, 0.04f, 0.06f, Rig.DETAIL);
                rig.box(body, bd(PINK_TERRACOTTA), 0f, 0.1f, -0.1f, 0.38f, 0.04f, 0.06f, Rig.DETAIL);
                BodyKit.ears(rig, rig.roles().head, Ears.ROUND, 0.52f, PINK_TERRACOTTA);
                yield rig;
            }
            case "mudling" -> slug(face("mudling"), MUD, PACKED_MUD, rich ? 4 : 3);
            case "shroomling" -> mushroomFolk(false, rich);
            case "mycelord" -> mushroomFolk(true, rich);

            /* ---------------- insects + fairies ---------------- */

            case "bee" -> {
                Rig rig = BodyKit.insect(Skeleton.HOVER, 0.3f, 0.28f, 0.4f, 0.6f, YELLOW_WOOL,
                        WHITE_STAINED_GLASS, 0.3f, 0.2f, false, face("bee"));
                int body = rig.roles().body;
                rig.box(body, bd(BLACK_WOOL), 0f, 0f, -0.1f, 0.31f, 0.29f, 0.08f, -10f, 0f, 0f, Rig.CORE);
                rig.box(body, bd(BLACK_WOOL), 0f, -0.02f, -0.26f, 0.29f, 0.25f, 0.06f, -10f, 0f, 0f, Rig.DETAIL);
                rig.box(body, bd(GRAY_CONCRETE), 0f, -0.04f, -0.34f, 0.04f, 0.04f, 0.08f, Rig.DETAIL);
                yield rig;
            }
            case "butterfly" -> BodyKit.insect(Skeleton.FLUTTER, 0.06f, 0.06f, 0.28f, 0.42f, BLACK_CONCRETE,
                    rich ? LIGHT_BLUE_STAINED_GLASS : ORANGE_STAINED_GLASS, 0.36f, 0.32f, true, face("butterfly"));
            case "bloom_fairy" -> {
                Rig rig = BodyKit.insect(Skeleton.FLUTTER, 0.12f, 0.22f, 0.12f, 0.5f, PINK_WOOL,
                        PINK_STAINED_GLASS, 0.38f, 0.34f, true, face("bloom_fairy"));
                rig.item(rig.roles().head, it(ALLIUM), 0f, 0.2f, 0.02f, 0.4f, 0f, 0f, 0f, Rig.DETAIL);
                BodyKit.orbit(rig, 3, 0.42f, 0.05f, null, it(PINK_PETALS), 0.28f, Rig.EXTRA);
                yield rig;
            }
            case "allay" -> {
                Rig rig = BodyKit.insect(Skeleton.HOVER, 0.22f, 0.26f, 0.16f, 0.55f, LIGHT_BLUE_STAINED_GLASS,
                        WHITE_STAINED_GLASS, 0.28f, 0.24f, false, face("allay"));
                rig.box(rig.roles().body, bd(LIGHT_BLUE_CONCRETE), 0f, 0f, -0.02f, 0.12f, 0.14f, 0.08f, Rig.CORE).bright();
                if (rich) {
                    BodyKit.orbit(rig, 2, 0.34f, 0f, null, it(AMETHYST_SHARD), 0.25f, Rig.EXTRA);
                }
                yield rig;
            }

            /* ---------------- flyers ---------------- */

            case "bat" -> BodyKit.bird(Skeleton.FLAP, 0.2f, 0.22f, 0.2f, 0.55f, 0.36f, 0.24f, true, 0f,
                    Pal.of(BROWN_WOOL, BLACK_TERRACOTTA, BLACK_TERRACOTTA), face("bat"));
            case "pigeon" -> {
                Rig rig = BodyKit.bird(Skeleton.FLAP, 0.22f, 0.22f, 0.34f, 0.5f, 0.3f, 0.22f, false, 0.18f,
                        Pal.of(LIGHT_GRAY_WOOL, GRAY_WOOL, GRAY_WOOL), face("pigeon"));
                rig.box(rig.roles().body, bd(CYAN_TERRACOTTA), 0f, 0.08f, 0.16f, 0.2f, 0.08f, 0.06f, Rig.DETAIL);
                yield rig;
            }
            case "parrot" -> {
                Rig rig = BodyKit.bird(Skeleton.FLAP, 0.2f, 0.26f, 0.3f, 0.52f, 0.3f, 0.2f, false, 0.32f,
                        Pal.of(RED_WOOL, BLUE_WOOL, rich ? YELLOW_WOOL : RED_WOOL), face("parrot"));
                yield rig;
            }
            case "owl" -> {
                Rig rig = BodyKit.bird(Skeleton.FLAP, 0.3f, 0.34f, 0.3f, 0.74f, 0.4f, 0.26f, true, 0.12f,
                        Pal.of(BROWN_WOOL, BROWN_TERRACOTTA, TERRACOTTA), face("owl"));
                BodyKit.ears(rig, rig.roles().head, Ears.TUFT, 0.74f, BROWN_TERRACOTTA);
                rig.box(rig.roles().body, bd(WHITE_TERRACOTTA), 0f, -0.02f, 0.13f, 0.2f, 0.22f, 0.06f, Rig.DETAIL);
                yield rig;
            }
            case "hawk" -> {
                Rig rig = BodyKit.bird(Skeleton.SOAR, 0.24f, 0.22f, 0.42f, 0.56f, 0.62f, 0.3f, true, 0.26f,
                        Pal.of(BROWN_TERRACOTTA, BROWN_WOOL, WHITE_TERRACOTTA), face("hawk"));
                rig.box(rig.roles().head, bd(YELLOW_TERRACOTTA), 0f, -0.04f, 0.2f, 0.06f, 0.06f, 0.08f, Rig.DETAIL);
                yield rig;
            }

            /* ---------------- spirits ---------------- */

            case "moss_sprite" -> {
                Rig rig = BodyKit.spirit(Skeleton.DRIFT, MOSS_BLOCK, 0.36f, false, face("moss_sprite"), 0.56f);
                int body = rig.roles().body;
                int wl = rig.bone("wing_l", body, 0.16f, 0.1f, -0.04f);
                int wr = rig.bone("wing_r", body, -0.16f, 0.1f, -0.04f);
                rig.box(wl, bd(AZALEA_LEAVES), 0.14f, 0f, -0.06f, 0.26f, 0.02f, 0.2f, 0f, -20f, 0f, Rig.CORE);
                rig.box(wr, bd(AZALEA_LEAVES), -0.14f, 0f, -0.06f, 0.26f, 0.02f, 0.2f, 0f, 20f, 0f, Rig.CORE);
                rig.roles().wings = new int[]{wl, wr};
                if (rich) {
                    BodyKit.orbit(rig, 2, 0.4f, -0.05f, null, it(GLOW_BERRIES), 0.3f, Rig.EXTRA);
                }
                yield rig;
            }
            case "forest_spirit" -> {
                Rig rig = BodyKit.spirit(Skeleton.DRIFT, VERDANT_FROGLIGHT, 0.2f, true, face("forest_spirit"), 0.62f);
                int body = rig.roles().body;
                rig.box(body, bd(OAK_LEAVES), 0f, -0.12f, -0.02f, 0.42f, 0.36f, 0.36f, 0f, 45f, 0f, Rig.CORE);
                rig.box(body, bd(FLOWERING_AZALEA_LEAVES), 0f, -0.36f, -0.02f, 0.26f, 0.22f, 0.26f, 0f, 20f, 0f, Rig.CORE);
                antlers(rig, rig.roles().head, 3, STRIPPED_OAK_LOG);
                BodyKit.orbit(rig, 3, 0.5f, 0f, null, it(OAK_LEAVES), 0.24f, Rig.DETAIL);
                yield rig;
            }
            case "lush_oracle" -> {
                Rig rig = BodyKit.spirit(Skeleton.DRIFT, SHROOMLIGHT, 0.26f, true, null, 0f);
                int body = rig.roles().body;
                int head = rig.roles().head;
                rig.box(body, bd(MOSS_BLOCK), 0f, 0.14f, 0f, 0.44f, 0.22f, 0.44f, 0f, 45f, 0f, Rig.CORE);
                rig.box(head, bd(VERDANT_FROGLIGHT), 0f, -0.04f, 0.06f, 0.16f, 0.16f, 0.04f, Rig.CORE).bright();
                rig.box(head, bd(BLACK_CONCRETE), 0f, -0.04f, 0.085f, 0.07f, 0.07f, 0.01f, Rig.CORE);
                int[] roots = new int[3];
                for (int i = 0; i < 3; i++) {
                    double a = Math.PI * 2.0 * i / 3.0;
                    roots[i] = rig.bone("tent" + i, body, (float) Math.cos(a) * 0.12f, -0.12f, (float) Math.sin(a) * 0.12f);
                    rig.item(roots[i], it(HANGING_ROOTS), 0f, -0.18f, 0f, 0.5f, 0f, (float) Math.toDegrees(a), 0f, Rig.DETAIL);
                }
                rig.roles().tentacles = roots;
                BodyKit.orbit(rig, 3, 0.55f, 0.1f, null, it(SPORE_BLOSSOM), 0.32f, Rig.CORE);
                rig.top = 1.0f;
                yield rig;
            }
            case "snowflake" -> {
                Rig rig = BodyKit.spirit(Skeleton.DRIFT, WHITE_WOOL, 0.001f, false, face("snowflake"), 0.6f);
                int body = rig.roles().body;
                rig.box(body, bd(WHITE_WOOL), 0f, -0.1f, 0f, 0.3f, 0.22f, 0.3f, Rig.CORE);
                rig.box(body, bd(LIGHT_BLUE_WOOL), 0f, -0.3f, 0f, 0.4f, 0.2f, 0.4f, Rig.CORE);
                int head = rig.roles().head;
                rig.box(head, bd(LIGHT_BLUE_WOOL), 0f, 0.17f, -0.06f, 0.36f, 0.04f, 0.36f, Rig.DETAIL);
                rig.box(head, bd(LIGHT_BLUE_WOOL), 0f, 0.27f, -0.08f, 0.18f, 0.18f, 0.18f, -12f, 0f, 0f, Rig.DETAIL);
                BodyKit.orbit(rig, rich ? 4 : 3, 0.46f, 0f, PACKED_ICE, null, 0.1f, Rig.DETAIL);
                yield rig;
            }
            case "sand_wraith" -> {
                Rig rig = new Rig(Skeleton.DRIFT, false);
                Rig.Roles r = rig.roles();
                int body = rig.bone("body", 0, 0f, 0f, 0f);
                r.body = body;
                r.bodyParts.add(rig.box(body, bd(SMOOTH_SANDSTONE), 0f, 0f, 0f, 0.4f, 0.44f, 0.32f, Rig.CORE).shadow());
                int head = rig.bone("head", body, 0f, 0.3f, 0.02f);
                r.head = head;
                rig.box(head, bd(SANDSTONE), 0f, 0.04f, 0f, 0.36f, 0.34f, 0.34f, Rig.CORE);
                rig.box(head, bd(BLACK_CONCRETE), 0f, 0.0f, 0.15f, 0.24f, 0.22f, 0.06f, Rig.CORE);
                BodyKit.eyes(rig, head, GLOWSTONE, 0.055f, 0.02f, 0.185f, 0.05f, Rig.CORE);
                int[] cloak = new int[3];
                for (int i = 0; i < 3; i++) {
                    float x = (i - 1) * 0.13f;
                    cloak[i] = rig.bone("tent" + i, body, x, -0.2f, -0.02f);
                    rig.box(cloak[i], bd(i == 1 ? CUT_SANDSTONE : SANDSTONE), 0f, -0.16f, 0f, 0.12f, 0.32f, 0.26f, Rig.CORE);
                }
                r.tentacles = cloak;
                int al = rig.bone("arm_l", body, 0.24f, 0.12f, 0.04f);
                int ar = rig.bone("arm_r", body, -0.24f, 0.12f, 0.04f);
                rig.box(al, bd(SMOOTH_SANDSTONE), 0f, -0.14f, 0.04f, 0.09f, 0.3f, 0.09f, 30f, 0f, 0f, Rig.DETAIL);
                rig.box(ar, bd(SMOOTH_SANDSTONE), 0f, -0.14f, 0.04f, 0.09f, 0.3f, 0.09f, 30f, 0f, 0f, Rig.DETAIL);
                r.arms = new int[]{al, ar};
                rig.top = 0.9f;
                rig.middle = 0f;
                yield rig;
            }

            /* ---------------- haunts ---------------- */

            case "blaze" -> {
                Rig rig = BodyKit.spirit(Skeleton.HAUNT, YELLOW_CONCRETE, 0.001f, false, face("blaze"), 0.75f);
                int body = rig.roles().body;
                rig.box(body, bd(ORANGE_CONCRETE), 0f, -0.22f, 0f, 0.1f, 0.24f, 0.1f, Rig.CORE).bright();
                int rods = rich ? 6 : 4;
                int[] orbit = new int[rods];
                for (int i = 0; i < rods; i++) {
                    double a = Math.PI * 2.0 * i / rods;
                    float y = i % 2 == 0 ? -0.08f : -0.32f;
                    orbit[i] = rig.bone("orbit_" + i, body, (float) Math.cos(a) * 0.3f, y, (float) Math.sin(a) * 0.3f);
                    rig.item(orbit[i], it(BLAZE_ROD), 0f, 0f, 0f, 0.55f, 0f, 0f, 0f, Rig.CORE).bright();
                }
                rig.roles().orbit = orbit;
                yield rig;
            }
            case "ghast" -> {
                Rig rig = BodyKit.spirit(Skeleton.HAUNT, WHITE_CONCRETE, 0.001f, false, face("ghast"), 1.4f);
                int body = rig.roles().body;
                int tents = rich ? 6 : 4;
                int[] t = new int[tents];
                for (int i = 0; i < tents; i++) {
                    float x = ((i % 3) - 1) * 0.18f;
                    float z = (i / 3 == 0 ? 0.1f : -0.1f) + (tents == 4 ? (i < 2 ? 0.0f : -0.12f) : 0f);
                    if (tents == 4) {
                        x = (i % 2 == 0 ? 0.12f : -0.12f);
                        z = i < 2 ? 0.1f : -0.12f;
                    }
                    t[i] = rig.bone("tent" + i, body, x, -0.34f, z + 0.12f);
                    rig.box(t[i], bd(WHITE_CONCRETE), 0f, -0.18f, 0f, 0.06f, 0.36f, 0.06f, Rig.CORE);
                }
                rig.roles().tentacles = t;
                rig.top = 1.15f;
                yield rig;
            }
            case "wither" -> {
                Rig rig = new Rig(Skeleton.HAUNT, false);
                Rig.Roles r = rig.roles();
                int body = rig.bone("body", 0, 0f, 0f, 0f);
                r.body = body;
                r.bodyParts.add(rig.box(body, bd(POLISHED_BLACKSTONE), 0f, 0.14f, 0f, 0.86f, 0.1f, 0.16f, Rig.CORE).shadow());
                rig.box(body, bd(BLACKSTONE), 0f, -0.1f, 0f, 0.08f, 0.42f, 0.08f, Rig.CORE);
                rig.box(body, bd(BLACKSTONE), 0f, -0.04f, 0.02f, 0.38f, 0.05f, 0.06f, Rig.DETAIL);
                rig.box(body, bd(BLACKSTONE), 0f, -0.16f, 0.02f, 0.3f, 0.05f, 0.06f, Rig.DETAIL);
                int head = rig.bone("head", body, 0f, 0.38f, 0.02f);
                r.head = head;
                rig.item(head, new ItemStack(WITHER_SKELETON_SKULL), 0f, 0f, 0f, 0.8f, 0f, 0f, 0f, Rig.CORE);
                int hl = rig.bone("head_l", body, 0.36f, 0.3f, 0.0f);
                int hr = rig.bone("head_r", body, -0.36f, 0.3f, 0.0f);
                rig.item(hl, new ItemStack(WITHER_SKELETON_SKULL), 0f, 0f, 0f, 0.6f, 0f, 0f, 0f, Rig.CORE);
                rig.item(hr, new ItemStack(WITHER_SKELETON_SKULL), 0f, 0f, 0f, 0.6f, 0f, 0f, 0f, Rig.CORE);
                r.ears = new int[]{hl, hr};
                int tail = rig.bone("tail", body, 0f, -0.32f, -0.02f, 30f, 0f, 0f);
                rig.box(tail, bd(BLACKSTONE), 0f, -0.14f, 0f, 0.07f, 0.3f, 0.07f, Rig.CORE);
                r.tail = new int[]{tail};
                rig.top = 0.95f;
                yield rig;
            }

            /* ---------------- water ---------------- */

            case "cod" -> BodyKit.fish(0.16f, 0.22f, 0.5f, 0.5f, 0.24f,
                    Pal.of(TERRACOTTA, BROWN_TERRACOTTA, BROWN_TERRACOTTA), face("cod"));
            case "salmon" -> BodyKit.fish(0.18f, 0.26f, 0.62f, 0.54f, 0.28f,
                    Pal.of(RED_TERRACOTTA, GREEN_TERRACOTTA, RED_TERRACOTTA), face("salmon"));
            case "tropical_fish" -> {
                Rig rig = BodyKit.fish(0.12f, 0.3f, 0.34f, 0.5f, 0.26f,
                        Pal.of(ORANGE_CONCRETE, WHITE_CONCRETE, rich ? LIGHT_BLUE_CONCRETE : ORANGE_CONCRETE), face("tropical_fish"));
                rig.box(rig.roles().body, bd(WHITE_CONCRETE), 0f, 0f, -0.06f, 0.13f, 0.31f, 0.06f, Rig.DETAIL);
                yield rig;
            }
            case "pufferfish" -> {
                Rig rig = BodyKit.fish(0.001f, 0.001f, 0.001f, 0.72f, 0.2f,
                        Pal.of(YELLOW_WOOL, YELLOW_TERRACOTTA, YELLOW_TERRACOTTA), face("pufferfish"));
                int head = rig.roles().head;
                int[] spikes = new int[4];
                for (int i = 0; i < 4; i++) {
                    float x = (i % 2 == 0 ? 0.2f : -0.2f);
                    float y = (i < 2 ? 0.14f : -0.12f);
                    spikes[i] = rig.bone("spike" + i, head, x, y, 0.06f);
                    rig.box(spikes[i], bd(WHITE_TERRACOTTA), 0f, 0f, 0f, 0.04f, 0.04f, 0.1f, 0f, i % 2 == 0 ? 45f : -45f, 0f, Rig.DETAIL);
                }
                rig.roles().spikes = spikes;
                yield rig;
            }
            case "axolotl" -> {
                Rig rig = BodyKit.fish(0.2f, 0.14f, 0.44f, 0.55f, 0.22f,
                        Pal.of(PINK_WOOL, MAGENTA_WOOL, PINK_WOOL), face("axolotl"));
                int head = rig.roles().head;
                rig.box(head, bd(MAGENTA_WOOL), 0.17f, 0.08f, 0.02f, 0.04f, 0.14f, 0.1f, 0f, 0f, -30f, Rig.DETAIL);
                rig.box(head, bd(MAGENTA_WOOL), -0.17f, 0.08f, 0.02f, 0.04f, 0.14f, 0.1f, 0f, 0f, 30f, Rig.DETAIL);
                int body = rig.roles().body;
                int fl = rig.bone("leg_fl", body, 0.1f, -0.06f, 0.08f);
                int fr = rig.bone("leg_fr", body, -0.1f, -0.06f, 0.08f);
                int bl = rig.bone("leg_bl", body, 0.1f, -0.06f, -0.2f);
                int br = rig.bone("leg_br", body, -0.1f, -0.06f, -0.2f);
                for (int leg : new int[]{fl, fr, bl, br}) {
                    rig.box(leg, bd(PINK_TERRACOTTA), 0f, -0.04f, 0f, 0.05f, 0.08f, 0.05f, Rig.DETAIL);
                }
                rig.roles().legs = new int[]{fl, fr, bl, br};
                yield rig;
            }
            case "guardian" -> {
                Rig rig = BodyKit.fish(0.001f, 0.001f, 0.001f, 1.1f, 0.001f,
                        Pal.of(PRISMARINE, DARK_PRISMARINE, PRISMARINE), face("guardian"));
                int head = rig.roles().head;
                int[] spikes = new int[6];
                float[][] at = {{0.3f, 0.1f, 0f}, {-0.3f, 0.1f, 0f}, {0f, 0.3f, 0f}, {0.2f, -0.24f, 0.05f},
                        {-0.2f, -0.24f, 0.05f}, {0f, 0.1f, -0.3f}};
                for (int i = 0; i < 6; i++) {
                    spikes[i] = rig.bone("spike" + i, head, at[i][0], at[i][1], at[i][2] + 0.1f);
                    rig.box(spikes[i], bd(ORANGE_TERRACOTTA), 0f, 0f, 0f, 0.05f, 0.05f, 0.16f,
                            at[i][1] > 0.2f ? 90f : 0f, (float) Math.toDegrees(Math.atan2(at[i][0], 0.3f)), 0f, Rig.DETAIL);
                }
                rig.roles().spikes = spikes;
                int t1 = rig.roles().tail[0];
                int t2 = rig.bone("tail2", t1, 0f, 0f, -0.16f);
                int t3 = rig.bone("tail3", t2, 0f, 0f, -0.14f);
                rig.box(t1, bd(PRISMARINE), 0f, 0f, 0.06f, 0.14f, 0.14f, 0.18f, Rig.CORE);
                rig.box(t2, bd(DARK_PRISMARINE), 0f, 0f, -0.06f, 0.1f, 0.1f, 0.16f, Rig.CORE);
                rig.box(t3, bd(ORANGE_TERRACOTTA), 0f, 0f, -0.08f, 0.02f, 0.2f, 0.16f, Rig.DETAIL);
                rig.roles().tail = new int[]{t1, t2, t3};
                yield rig;
            }
            case "squid", "glow_squid" -> {
                boolean glow = id.equals("glow_squid");
                Rig rig = new Rig(Skeleton.JET, false);
                Rig.Roles r = rig.roles();
                int body = rig.bone("body", 0, 0f, 0.1f, 0f, 20f, 0f, 0f);
                r.body = body;
                r.head = body;
                Rig.Part mantle = rig.item(body, face(id), 0f, 0.06f, 0f, 1.0f, 0f, 0f, 0f, Rig.CORE);
                if (glow) {
                    mantle.bright();
                }
                r.bodyParts.add(mantle.shadow());
                int n = rich ? 6 : 4;
                int[] t = new int[n];
                for (int i = 0; i < n; i++) {
                    double a = Math.PI * 2.0 * i / n + Math.PI / n;
                    t[i] = rig.bone("tent" + i, body, (float) Math.cos(a) * 0.12f, -0.2f, (float) Math.sin(a) * 0.12f);
                    Rig.Part tent = rig.box(t[i], bd(glow ? CYAN_TERRACOTTA : BLUE_TERRACOTTA), 0f, -0.22f, 0f, 0.06f, 0.44f, 0.06f, Rig.CORE);
                    if (glow) {
                        tent.bright();
                    }
                }
                r.tentacles = t;
                rig.top = 0.7f;
                rig.middle = 0f;
                yield rig;
            }
            case "dolphin" -> {
                Rig rig = BodyKit.fish(0.3f, 0.3f, 0.78f, 0.6f, 0.001f,
                        Pal.of(GRAY_CONCRETE, LIGHT_GRAY_CONCRETE, GRAY_CONCRETE), face("dolphin"));
                Rig rebuilt = new Rig(Skeleton.PORPOISE, false);
                copy(rig, rebuilt);
                int body = rebuilt.roles().body;
                int head = rebuilt.roles().head;
                rebuilt.box(head, bd(LIGHT_GRAY_CONCRETE), 0f, -0.06f, 0.24f, 0.12f, 0.08f, 0.16f, Rig.CORE);
                rebuilt.box(body, bd(GRAY_CONCRETE), 0f, 0.2f, -0.06f, 0.04f, 0.16f, 0.16f, -30f, 0f, 0f, Rig.CORE);
                int fluke = rebuilt.roles().tail[0];
                rebuilt.box(fluke, bd(GRAY_CONCRETE), 0f, 0f, -0.08f, 0.36f, 0.03f, 0.14f, Rig.CORE);
                rebuilt.box(body, bd(LIGHT_GRAY_CONCRETE), 0f, -0.12f, 0f, 0.26f, 0.08f, 0.5f, Rig.DETAIL);
                yield rebuilt;
            }

            /* ---------------- dragons ---------------- */

            case "ice_dragon" -> BodyKit.dragon(new DragonPal(PACKED_ICE, WHITE_WOOL, BLUE_ICE, LIGHT_BLUE_STAINED_GLASS,
                    SNOW_BLOCK, SEA_LANTERN), face("ice_dragon"), true);
            case "fire_dragon" -> BodyKit.dragon(new DragonPal(NETHER_WART_BLOCK, MAGMA_BLOCK, BLACKSTONE, ORANGE_STAINED_GLASS,
                    RED_NETHER_BRICKS, SHROOMLIGHT), face("fire_dragon"), true);
            case "nature_dragon" -> {
                Rig rig = BodyKit.dragon(new DragonPal(MOSS_BLOCK, LIME_TERRACOTTA, STRIPPED_OAK_LOG, LIME_STAINED_GLASS,
                        GREEN_TERRACOTTA, VERDANT_FROGLIGHT), face("nature_dragon"), true);
                int body = rig.roles().body;
                rig.item(body, it(POPPY), 0.12f, 0.26f, 0.1f, 0.4f, 0f, 20f, 0f, Rig.DETAIL);
                rig.item(body, it(DANDELION), -0.1f, 0.26f, -0.18f, 0.4f, 0f, -30f, 0f, Rig.DETAIL);
                yield rig;
            }
            case "forest_dragon" -> {
                Rig rig = BodyKit.dragon(new DragonPal(SPRUCE_WOOD, STRIPPED_SPRUCE_WOOD, DARK_OAK_LOG, FLOWERING_AZALEA_LEAVES,
                        MOSS_BLOCK, OCHRE_FROGLIGHT), face("forest_dragon"), true);
                antlers(rig, rig.roles().head, 1, STRIPPED_DARK_OAK_LOG);
                yield rig;
            }
            case "mining_dragon", "poison_dragon" -> {
                Rig rig = BodyKit.dragon(new DragonPal(DEEPSLATE_TILES, COBBLED_DEEPSLATE, AMETHYST_BLOCK, PURPLE_STAINED_GLASS,
                        DEEPSLATE_DIAMOND_ORE, OCHRE_FROGLIGHT), face("mining_dragon"), true);
                rig.box(rig.roles().body, bd(DEEPSLATE_GOLD_ORE), 0.2f, 0.05f, -0.1f, 0.08f, 0.2f, 0.3f, Rig.DETAIL);
                yield rig;
            }
            case "dungeon_dragon" -> BodyKit.dragon(new DragonPal(BLACKSTONE, BONE_BLOCK, BONE_BLOCK, BLACK_STAINED_GLASS,
                    BONE_BLOCK, LIGHT_BLUE_CONCRETE), face("dungeon_dragon"), true);
            case "lightning_dragon" -> BodyKit.wyrm(new DragonPal(YELLOW_CONCRETE, WHITE_CONCRETE, GOLD_BLOCK, LIGHT_BLUE_STAINED_GLASS,
                    GOLD_BLOCK, PEARLESCENT_FROGLIGHT), face("lightning_dragon"), 8, WHITE_WOOL);
            case "water_dragon" -> BodyKit.wyrm(new DragonPal(PRISMARINE, LIGHT_BLUE_CONCRETE, DARK_PRISMARINE, CYAN_STAINED_GLASS,
                    PRISMARINE_BRICKS, SEA_LANTERN), face("water_dragon"), 8, CYAN_WOOL);
            case "aetherion" -> {
                Rig rig = BodyKit.wyrm(new DragonPal(PURPUR_BLOCK, END_STONE_BRICKS, GOLD_BLOCK, MAGENTA_STAINED_GLASS,
                        AMETHYST_BLOCK, PEARLESCENT_FROGLIGHT), face("aetherion"), 9, MAGENTA_WOOL);
                BodyKit.orbit(rig, 3, 0.75f, 0.2f, null, it(NETHER_STAR), 0.28f, Rig.DETAIL);
                yield rig;
            }

            default -> fallback(id, def);
        };
    }

    /*
     * =========================================================
     * BESPOKE PLANS
     * =========================================================
     */

    private static Rig witch(boolean hag, boolean rich) {
        Material robe = hag ? GREEN_TERRACOTTA : PURPLE_TERRACOTTA;
        Rig rig = BodyKit.biped(Skeleton.STRIDE, new Bi().legs(0.22f, 0.1f).torso(0.32f, 0.44f, 0.2f)
                        .arms(0.32f, 0.09f, 30f).head(0.72f).hunch(hag ? 16f : 0f).colours(robe, robe),
                Pal.of(robe, robe, BLACK_WOOL), face(hag ? "swamp_hag" : "witch"));
        Rig.Roles r = rig.roles();
        rig.box(r.body, bd(robe), 0f, 0.0f, 0f, 0.38f, 0.16f, 0.26f, Rig.CORE);
        int head = r.head;
        Material hat = hag ? BLACK_WOOL : PURPLE_WOOL;
        rig.box(head, bd(hat), 0f, 0.38f, 0f, 0.5f, 0.03f, 0.5f, Rig.CORE);
        rig.box(head, bd(hat), 0f, 0.47f, -0.02f, 0.28f, 0.16f, 0.28f, -6f, 0f, 0f, Rig.CORE);
        rig.box(head, bd(hat), 0f, 0.6f, -0.08f, 0.14f, 0.14f, 0.14f, -18f, 0f, 0f, Rig.DETAIL);
        rig.box(head, bd(hag ? VERDANT_FROGLIGHT : LIME_CONCRETE), 0f, 0.41f, 0.14f, 0.08f, 0.06f, 0.02f, Rig.DETAIL).bright();
        if (hag || rich) {
            rig.item(r.arms[1], it(hag ? BLAZE_ROD : STICK), 0f, -0.24f, 0.06f, 0.6f, 0f, 0f, 0f, Rig.DETAIL);
        }
        if (hag) {
            int orb = rig.bone("orbit_0", r.body, 0.36f, 0.48f, 0.2f);
            rig.item(orb, it(ENDER_EYE), 0f, 0f, 0f, 0.3f, 0f, 0f, 0f, Rig.CORE).bright();
            r.orbit = new int[]{orb};
        }
        return rig;
    }

    private static Rig spider(ItemStack face, Material body, Material legs, boolean rich) {
        Rig rig = new Rig(Skeleton.SCUTTLE, false);
        Rig.Roles r = rig.roles();
        int b = rig.bone("body", 0, 0f, 0.2f, 0f);
        r.body = b;
        r.bodyParts.add(rig.box(b, bd(body), 0f, 0.04f, -0.2f, 0.34f, 0.26f, 0.36f, Rig.CORE).shadow());
        rig.box(b, bd(legs), 0f, 0.02f, 0.02f, 0.2f, 0.16f, 0.16f, Rig.CORE);
        int h = rig.bone("head", b, 0f, 0.02f, 0.14f);
        r.head = h;
        rig.item(h, face, 0f, 0f, 0.06f, 0.56f, 0f, 0f, 0f, Rig.CORE);
        int pairs = rich ? 4 : 3;
        int[] legBones = new int[pairs * 2];
        for (int i = 0; i < pairs; i++) {
            float z = 0.1f - i * (0.26f / Math.max(1, pairs - 1));
            float splay = (i - (pairs - 1) / 2f) * -22f;
            for (int side = 0; side < 2; side++) {
                float sign = side == 0 ? 1f : -1f;
                int leg = rig.bone((side == 0 ? "leg_l" : "leg_r") + i, b, sign * 0.1f, -0.02f, z, 0f, sign * splay, 0f);
                // Knee high at the body, tip on the ground: a spider stands on its legs.
                rig.box(leg, bd(legs), sign * 0.17f, -0.09f, 0f, 0.38f, 0.04f, 0.04f, 0f, 0f, sign * -30f, Rig.CORE);
                legBones[i * 2 + side] = leg;
            }
        }
        r.legs = legBones;
        rig.top = 0.7f;
        rig.middle = 0.2f;
        return rig;
    }

    private static Rig shell(ItemStack face, Material shell, Material shellTop, Material skin, float w, float l, float head) {
        Rig rig = new Rig(Skeleton.CRAWL, false);
        Rig.Roles r = rig.roles();
        float bodyY = 0.12f;
        rig.bodyHalf = 0.1f;
        int b = rig.bone("body", 0, 0f, bodyY, 0f);
        r.body = b;
        r.bodyParts.add(rig.box(b, bd(shell), 0f, 0.02f, 0f, w, 0.16f, l, Rig.CORE).shadow());
        rig.box(b, bd(shellTop), 0f, 0.12f, 0f, w * 0.72f, 0.1f, l * 0.72f, Rig.CORE);
        int h = rig.bone("head", b, 0f, 0.0f, l / 2f);
        r.head = h;
        rig.item(h, face, 0f, 0.02f, head * 0.14f, head, 0f, 0f, 0f, Rig.CORE);
        float lx = w / 2f - 0.02f;
        float lz = l / 2f - 0.08f;
        int fl = rig.bone("leg_fl", b, lx, -0.04f, lz);
        int fr = rig.bone("leg_fr", b, -lx, -0.04f, lz);
        int bl = rig.bone("leg_bl", b, lx, -0.04f, -lz);
        int br = rig.bone("leg_br", b, -lx, -0.04f, -lz);
        for (int leg : new int[]{fl, fr, bl, br}) {
            rig.box(leg, bd(skin), 0f, -0.03f, 0f, 0.14f, 0.07f, 0.1f, Rig.CORE);
        }
        r.legs = new int[]{fl, fr, bl, br};
        int t = rig.bone("tail", b, 0f, -0.02f, -l / 2f);
        rig.box(t, bd(skin), 0f, 0f, -0.04f, 0.06f, 0.05f, 0.1f, Rig.DETAIL);
        r.tail = new int[]{t};
        rig.top = 0.7f;
        rig.middle = bodyY;
        return rig;
    }

    private static Rig slug(ItemStack face, Material body, Material belly, int segments) {
        Rig rig = new Rig(Skeleton.SLITHER, false);
        Rig.Roles r = rig.roles();
        int head = rig.bone("head", 0, 0f, 0.2f, 0.28f);
        r.head = head;
        r.body = head;
        rig.item(head, face, 0f, 0.02f, 0.04f, 0.62f, 0f, 0f, 0f, Rig.CORE);
        int[] segs = new int[segments];
        float s = 0.34f;
        for (int i = 0; i < segments; i++) {
            segs[i] = rig.bone("seg" + i, 0, 0f, s / 2f, 0.08f - i * 0.24f);
            Rig.Part p = rig.box(segs[i], bd(i % 2 == 0 ? body : belly), 0f, 0f, 0f, s, s, 0.3f, Rig.CORE);
            if (i == 0) {
                r.bodyParts.add(p.shadow());
            }
            s *= 0.82f;
        }
        r.segments = segs;
        rig.top = 0.75f;
        rig.middle = 0.16f;
        return rig;
    }

    private static Rig mushroomFolk(boolean king, boolean rich) {
        Rig rig = new Rig(Skeleton.WADDLE, false);
        Rig.Roles r = rig.roles();
        float stemW = king ? 0.34f : 0.24f;
        float stemH = king ? 0.42f : 0.26f;
        float legH = king ? 0.16f : 0.1f;
        int body = rig.bone("body", 0, 0f, legH, 0f);
        r.body = body;
        r.bodyParts.add(rig.box(body, bd(MUSHROOM_STEM), 0f, stemH / 2f, 0f, stemW, stemH, stemW, Rig.CORE).shadow());
        int head = rig.bone("head", body, 0f, stemH, 0f);
        r.head = head;
        Material cap = king ? BROWN_MUSHROOM_BLOCK : RED_MUSHROOM_BLOCK;
        float capW = king ? 0.86f : 0.48f;
        rig.box(head, bd(cap), 0f, 0.08f, 0f, capW, king ? 0.2f : 0.16f, capW, Rig.CORE);
        rig.box(head, bd(cap), 0f, king ? 0.22f : 0.18f, 0f, capW * 0.66f, 0.1f, capW * 0.66f, Rig.CORE);
        if (king) {
            for (int i = 0; i < 4; i++) {
                double a = Math.PI / 2.0 * i + Math.PI / 4.0;
                rig.box(head, bd(GOLD_BLOCK), (float) Math.cos(a) * 0.16f, 0.33f, (float) Math.sin(a) * 0.16f,
                        0.06f, 0.1f, 0.06f, Rig.DETAIL).bright();
            }
            rig.box(body, bd(PURPLE_WOOL), 0f, stemH * 0.55f, -stemW / 2f - 0.02f, stemW + 0.12f, stemH * 0.9f, 0.03f, -6f, 0f, 0f, Rig.DETAIL);
            int al = rig.bone("arm_l", body, stemW / 2f + 0.05f, stemH * 0.75f, 0f);
            int ar = rig.bone("arm_r", body, -stemW / 2f - 0.05f, stemH * 0.75f, 0f);
            rig.box(al, bd(MUSHROOM_STEM), 0f, -0.12f, 0f, 0.09f, 0.26f, 0.09f, Rig.CORE);
            rig.box(ar, bd(MUSHROOM_STEM), 0f, -0.12f, 0f, 0.09f, 0.26f, 0.09f, Rig.CORE);
            r.arms = new int[]{al, ar};
        }
        // Face on the stem: two dark eyes, a royal pair of glints for the king.
        float eyeY = -stemH * 0.3f;
        BodyKit.eyes(rig, head, king ? OCHRE_FROGLIGHT : BLACK_CONCRETE, stemW * 0.22f, eyeY, stemW / 2f + 0.012f, 0.05f, Rig.CORE);
        float lx = stemW * 0.26f;
        int ll = rig.bone("leg_l", 0, lx, legH, 0f);
        int lr = rig.bone("leg_r", 0, -lx, legH, 0f);
        rig.box(ll, bd(MUSHROOM_STEM), 0f, -legH / 2f, 0.02f, 0.09f, legH, 0.12f, Rig.CORE);
        rig.box(lr, bd(MUSHROOM_STEM), 0f, -legH / 2f, 0.02f, 0.09f, legH, 0.12f, Rig.CORE);
        r.legs = new int[]{ll, lr};
        if (rich && !king) {
            rig.item(head, it(RED_MUSHROOM), 0.14f, 0.28f, 0.05f, 0.35f, 0f, 30f, 0f, Rig.EXTRA);
        }
        rig.top = legH + stemH + (king ? 0.7f : 0.55f);
        rig.middle = legH + stemH * 0.5f;
        return rig;
    }

    /** Body for ids the catalog has never seen: picked from spawn type / habitat, never a lone skull. */
    private static Rig fallback(String id, PetDefinition def) {
        ItemStack face = face(id);
        PetSpawnType type = def == null ? PetSpawnType.SURFACE : def.getSpawnType();
        PetHabitat habitat = def == null ? PetHabitat.ANY : def.getHabitat();
        if (type == PetSpawnType.AQUATIC) {
            return BodyKit.fish(0.18f, 0.24f, 0.5f, 0.54f, 0.24f, Pal.of(LIGHT_BLUE_TERRACOTTA, CYAN_TERRACOTTA, CYAN_TERRACOTTA), face);
        }
        if (type == PetSpawnType.SKY) {
            return BodyKit.bird(Skeleton.FLAP, 0.22f, 0.22f, 0.32f, 0.52f, 0.32f, 0.22f, false, 0.18f,
                    Pal.of(LIGHT_GRAY_WOOL, GRAY_WOOL, GRAY_WOOL), face);
        }
        if (type == PetSpawnType.NETHER) {
            Rig rig = BodyKit.spirit(Skeleton.HAUNT, MAGMA_BLOCK, 0.3f, true, face, 0.6f);
            BodyKit.orbit(rig, 2, 0.35f, -0.1f, null, it(BLAZE_POWDER), 0.25f, Rig.DETAIL);
            return rig;
        }
        Material coat = switch (habitat) {
            case SNOW -> WHITE_WOOL;
            case DESERT -> YELLOW_TERRACOTTA;
            case SWAMP -> GREEN_TERRACOTTA;
            case MUSHROOM -> RED_WOOL;
            case JUNGLE -> LIME_TERRACOTTA;
            case DARK -> BLACK_WOOL;
            default -> BROWN_WOOL;
        };
        return BodyKit.quad(Skeleton.TROT, new Quad().body(0.56f, 0.3f, 0.3f).legs(0.26f, 0.1f).head(0.7f, 0.05f, 0.03f)
                .tail(Tail.THIN, 0.24f, 0.08f, 25f).ears(Ears.POINTY), Pal.of(coat, coat, coat), face);
    }

    /*
     * =========================================================
     * DETAIL HELPERS
     * =========================================================
     */

    private static void spots(Rig rig, Material m, float w, float h, float l) {
        int body = rig.roles().body;
        // Two patches that wrap over the back read from both sides and from above.
        rig.box(body, bd(m), 0f, h * 0.2f + 0.006f, l * 0.14f, w + 0.012f, h * 0.6f, l * 0.26f, Rig.DETAIL);
        rig.box(body, bd(m), 0f, h * 0.15f + 0.006f, -l * 0.22f, w + 0.012f, h * 0.7f, l * 0.2f, Rig.DETAIL);
    }

    private static void horns(Rig rig, int head, Material m, float headScale) {
        float h = headScale * 0.5f;
        rig.box(head, bd(m), h * 0.48f, h * 0.42f, 0.0f, h * 0.24f, h * 0.1f, h * 0.1f, 0f, 0f, 20f, Rig.DETAIL);
        rig.box(head, bd(m), -h * 0.48f, h * 0.42f, 0.0f, h * 0.24f, h * 0.1f, h * 0.1f, 0f, 0f, -20f, Rig.DETAIL);
    }

    private static void antlers(Rig rig, int head, int tines, Material m) {
        for (int side = -1; side <= 1; side += 2) {
            int a = rig.bone(side < 0 ? "antler_r" : "antler_l", head, side * 0.1f, 0.18f, -0.02f, 0f, 0f, side * -18f);
            rig.box(a, bd(m), 0f, 0.13f, 0f, 0.035f, 0.26f, 0.035f, Rig.DETAIL);
            for (int i = 0; i < tines; i++) {
                float y = 0.08f + i * 0.08f;
                rig.box(a, bd(m), side * 0.05f, y, 0.02f, 0.11f, 0.03f, 0.03f, 0f, side * 20f, side * -30f, Rig.DETAIL);
            }
        }
    }

    /** Re-home a built template onto a different skeleton (same bones, same parts). */
    private static void copy(Rig from, Rig to) {
        to.bones.clear();
        to.bones.addAll(from.bones);
        to.parts.addAll(from.parts);
        Rig.Roles a = from.roles();
        Rig.Roles b = to.roles();
        b.body = a.body;
        b.head = a.head;
        b.tail = a.tail;
        b.legs = a.legs;
        b.wings = a.wings;
        b.fins = a.fins;
        b.bodyParts.addAll(a.bodyParts);
        to.top = from.top;
        to.middle = from.middle;
    }
}
