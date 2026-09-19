package de.aetherion.aethermobs.pet;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

public final class PetHead {

    private PetHead() {
    }

    public static ItemStack create(
            PetInstance pet
    ) {

        if (pet == null
                || pet.getDefinition() == null) {

            return new ItemStack(
                    Material.PLAYER_HEAD
            );
        }

        return create(
                pet.getDefinition()
                        .getId()
        );
    }

    public static ItemStack create(
            String petId
    ) {

        if (petId == null
                || petId.isBlank()) {

            return new ItemStack(
                    Material.PLAYER_HEAD
            );
        }

        String id =
                petId.toLowerCase();

        return switch (id) {

            case "zombie" ->
                    new ItemStack(
                            Material.ZOMBIE_HEAD
                    );

            case "skeleton" ->
                    new ItemStack(
                            Material.SKELETON_SKULL
                    );

            case "wither" ->
                    new ItemStack(
                            Material.WITHER_SKELETON_SKULL
                    );

            case "creeper" ->
                    new ItemStack(
                            Material.CREEPER_HEAD
                    );

            case "aetherion" ->
                    hashed("f8aa3c53e467523c6c3d833bbbee37a66fc14f30530f39a6a9c0457ffe805c25");

            case "dungeon_dragon" ->
                    hashed("f9372e389d6e0dbb6ebebadb2878e13699dae119dd9b7a359f2df24568932e76");

            case "dungeon_zombie" ->
                    new ItemStack(
                            Material.ZOMBIE_HEAD
                    );

            case "dungeon_skeleton" ->
                    new ItemStack(
                            Material.SKELETON_SKULL
                    );

            case "wolf" ->
                    textured(
                            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTVhNjUxNzhmMjdiNTI5OGZkOGYxZmNjZmNiM2VhNDc3NWNhOWQyMzUwMTNmNGIzOGUzNGI4MjBjODgwZDg2MSJ9fX0="
                    );

            case "pig" ->
                    textured(
                            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjIxNjY4ZWY3Y2I3OWRkOWMyMmNlM2QxZjNmNGNiNmUyNTU5ODkzYjZkZjRhNDY5NTE0ZTY2N2MxNmFhNCJ9fX0="
                    );

            case "cow" ->
                    textured(
                            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzVhOWNkNThkNGM2N2JjY2M4ZmIxZjVmNzU2YTJkMzgxYzlmZmFjMjkyNGI3ZjRjYjcxYWE5ZmExM2ZiNWMifX19"
                    );

            case "bat" ->
                    textured(
                            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmViODgzNDVkZDkxMWUxMTRhNGJiN2IxZWIxY2QyMWM2YTUyMzczMWVkMmM3YjJkNjJhMTY0YWY3NGZlMGMwZSJ9fX0="
                    );

            case "cave_spider" ->
                    textured(
                            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWVjNTU3NDYwM2YzMDQ4ZjIxYWQ1YTNjOTRkOTcxMTU3MDYwMTFmZTZiYTY3NzgxMDkxYjhhOWFjMTBhZjU0ZiJ9fX0="
                    );

            case "guardian" ->
                    textured(
                            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMThkMmE3ZmVhN2YyZTBkOTE2YzdjNmQ3OTE0OTM3YmI4ZGQzZmJmZDdmOTQ4M2E0YTM5MTJmNWEwZmM2M2QzIn19fQ=="
                    );

            case "dolphin" ->
                    textured(
                            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGU5Njg4Yjk1MGQ4ODBiNTViN2FhMmNmY2Q3NmU1YTBmYTk0YWFjNmQxNmY3OGU4MzNmNzQ0M2VhMjlmZWQzIn19fQ=="
                    );

            case "squid" ->
                    textured(
                            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMDE0MzNiZTI0MjM2NmFmMTI2ZGE0MzRiODczNWRmMWViNWIzY2IyY2VkZTM5MTQ1OTc0ZTljNDgzNjA3YmFjIn19fQ=="
                    );

            case "glow_squid" ->
                    textured(
                            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDMxYmJlY2RhNTgyMDEzMTQ0YWFiOGMwOWFiZTI5YTIxYTEyNDNiOTE4MzI3YTRjNWNkNDAyYzJhOTU0MTgwZiJ9fX0="
                    );

            case "axolotl" ->
                    textured(
                            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2I5MTBmYmMyMTZmNzI0ZDI5NjU1MTU1YjJhMzg1OGE4MGYyMzRhMGNmZWQ2MDllMjJmYzY3MDY4M2FiNzc3YSJ9fX0="
                    );

            case "hawk" ->
                    textured(
                            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGM1NGM4ZWI5MmRkZmRmZDE0MzZmMDY4NmU2NGM0ZTRmNjMwMDNiNjRiNTcwNjY1ZTMwMDJiNzg1NjViYWRkZiJ9fX0="
                    );

            case "bee" ->
                    textured(
                            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2VlZGI4MmU0NTAyZTc3NDM0NTVkZDQzODNkMGNhNjBiNDBjYTZjYWIyYzViMDI4ZTViNGIwNDEyN2NiYzUzYiJ9fX0="
                    );

            case "pigeon" ->
                    textured(
                            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDIxNjY4MmFiOTRkNTFhODE3YjQ3NjEzZjQ3ZTI2ZWMxZDkwOTc0ZjI3ZjI4OTg2MzM2NWU0NTUxZjRmNTE5YiJ9fX0="
                    );

            case "ocelot" ->
                    hashed("de440058c7e9b57f441c5e2e9538135bc7e42ce5ea039d8c5cdb85a4c2c3a5aa");

            case "parrot" ->
                    hashed("a4ba8d66fecb1992e94b8687d6ab4a5320ab7594ac194a2615ed4df818edbc3");

            case "panda" ->
                    hashed("dca096eea506301bea6d4b17ee1605625a6f5082c71f74a639cc940439f47166");

            case "goat" ->
                    hashed("7b0ee70b42c77265b040ba7fb2e5b890cd420e0b81c93a052b8cfb0d74014bf0");

            case "llama" ->
                    hashed("7f832466dcc7d5e7702cdee4cd555dbd39637d20adf9367fb03cfd6888baaae7");

            case "fox" ->
                    hashed("d8954a42e69e0881ae6d24d4281459c144a0d5a968aed35d6d3d73a3c65d26a");

            case "rabbit" ->
                    hashed("7d1169b2694a6aba826360992365bcda5a10c89a3aa2b48c438531dd8685c3a7");

            case "farm_rabbit" ->
                    textured(
                            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWE2NjAwNjgwZjRmNWIxMzIxNTRlMzQxM2UyOWM5M2NmOTZmZjJmMjIyNGFiODdiZWU1MDk1OWUwYjBlZTkyOSJ9fX0="
                    );

            case "horse" ->
                    textured(
                            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjQ2ZmNjOWQ4ODc2NGI3MjE5ZjZlZDQ1ZWNkN2FmNzNjNDA5ZDJkYWRkYzlkZGY2MzkxMWZhYzE1ZGE2NmNjYiJ9fX0="
                    );

            case "sack_of_potatoes" ->
                    textured(
                            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjA2YmUyZGYyMTIyMzQ0YmRhNDc5ZmVlY2UzNjVlZTBlOWQ1ZGEyNzZhZmEwZThjZThkODQ4ZjM3M2RkMTMxIn19fQ=="
                    );

            case "polar_bear" ->
                    hashed("c4fe926922fbb406f343b34a10bb98992cee4410137d3f88099427b22de3ab90");

            case "yeti" ->
                    textured(
                            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTA4ZWI1YzcyOGRkYjlmZTM5NTQ2NjExOWJjYWE4OTQwOWZiODY2OWIzYjAzYjhlOWY0NDdlZTAyZmRmMGRkZSJ9fX0="
                    );

            case "snowflake" ->
                    textured(
                            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjJlYTQ1YWNhNDUwMjRkNDczMGM3YTA0ZmQ0N2ZlNGUzMTlmNWQzYjdlOWNlODBiMzFmZGM2NzE1MzllMWEzIn19fQ=="
                    );

            case "ice_dragon" ->
                    textured(
                            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2E2MGZjYmI1ZTE3YjkxZDUyMTJlMzkxNmE3YTA5OWZjYTdlZmVlNDJmZjhiNDAyYmI0NDNlZDNlOTNlMDViMiJ9fX0="
                    );

            case "fire_dragon" ->
                    textured(
                            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTc3OWE5ODRmN2IxZjVmN2VkOTI0OGE3OWJhMzZmNDY5MWJiNzJiNTEzN2QyMmM3ZDE1Zjg3YTExOTM0ZDI1YSJ9fX0="
                    );

            case "water_dragon" ->
                    textured(
                            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzM1NDcxODZmNDcyNjkzMThiMjA1MjcyZmU4ZThiZjI1ZmM2OWE3OTFlMWE3YzRiZWQ5N2MyMWMyMTEzYmQxZCJ9fX0="
                    );

            // https://minecraft-heads.com/custom-heads/head/115907-nature-dragon
            case "nature_dragon" ->
                    textured(
                            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzk4ZjkxNTE2OTQ5ODgyZDRmMTU2Yjc1NzQ0MTU3MGVhMmU1NzZkMGFmYTU2Y2E2MzcxYWE2NDlhNzM2ODBlNyJ9fX0="
                    );

            case "mining_dragon", "poison_dragon" ->
                    textured(
                            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDIxYWU2N2ZmZTg3OTg0YTczM2ZhZTA4NjE0MWU0OGRhZDBmZTYwNzA4YWVkMTkyOGU2NmI3ZDgwM2NjY2RlZiJ9fX0="
                    );

            // https://minecraft-heads.com/custom-heads/head/89311-dragon
            case "forest_dragon" ->
                    textured(
                            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2Q1MDIxMGI3OTNiN2Y2MWRiMDU1NmY5M2NlY2ZmMjZiNGEyM2U1OThiNjI2OGUyMmRkZmYxMTQ5MWM1MDRlMiJ9fX0="
                    );

            case "lightning_dragon" ->
                    textured(
                            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmNiYjA0Y2EyODM2NTdjOTM1YTg5YzkyNGU0Yjg2ZTE3OWUxYmFhYzRmYWU2YmYwYmQ5YmNkMDY2NjE0YjYxOSJ9fX0="
                    );

            case "blaze" ->
                    textured(
                            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjMwYzE0ZDFhYWRmZmU4ZDkyZjliZDhmOTM4ZTIyMGExNTMyMWVjNWYzOTM0M2Q1ZjYxZTY3MGFhNzk1ODYzMSJ9fX0="
                    );

            case "slime_minion" ->
                    textured(
                            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODk1YWVlYzZiODQyYWRhODY2OWY4NDZkNjViYzQ5NzYyNTk3ODI0YWI5NDRmMjJmNDViZjNiYmI5NDFhYmU2YyJ9fX0="
                    );

            case "ghast" ->
                    textured(
                            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGU4YTM4ZTlhZmJkM2RhMTBkMTliNTc3YzU1YzdiZmQ2YjRmMmU0MDdlNDRkNDAxN2IyM2JlOTE2N2FiZmYwMiJ9fX0="
                    );

            case "turtle" ->
                    hashed("fca6a0fd67291812f414981e88d5392fd55df86e8e6dba4407dd30c53745590d");

            case "camel" ->
                    hashed("74b8a333dfa92e7e5a95ad4ae2d84b1bafa33dc28c054925277f60e79dafc8c4");

            case "armadillo" ->
                    hashed("61197af5930e5dc9ede2b9989565acffe657213e9747057e3faf7b12f88a687c");

            case "allay" ->
                    hashed("e50294a1747310f104124c6373cc639b712baa57b7d926297b645188b7bb9ab9");

            case "cod" ->
                    textured(
                            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGZlZWZmNGI3ZmNmY2U2OGIwZjc0ZGYwZGIwYWQwYzAxZjczMDFkMGM2ZDg5MzY5OWI0MDJiZDUwYmIzNzZiMCJ9fX0="
                    );

            case "salmon" ->
                    textured(
                            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjBlYTlhMjIzNjIwY2RiNTRiMzU3NDEzZDQzYmQ4OWM0MDA4YmNhNmEyMjdmM2I3ZGI5N2Y3NzMzZWFkNWZjZiJ9fX0="
                    );

            case "pufferfish" ->
                    textured(
                            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzNiMTk1NWQzYjZlYjQyZjUwZTUzNmMxYTMyODVhYjczZWQ3ZTJiZTA1MWIwOWIyMWUxNzgxMWYxYTZkIn19fQ=="
                    );

            case "tropical_fish" ->
                    textured(
                            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNmFhNDA1ZjQ5YTkzODgwNmNlNDFjYzY2MTZjYjA3Nzc3MzIwZTcxMWI1YjZkOWIyYjJlMmM0YjUzOTYzNmMxYSJ9fX0="
                    );

            case "frog" ->
                    hashed("bf54d65db4515b2781442f914fd22e4a89fac8ebd60f4262a6a0e223dfb20ecd");

            case "witch" ->
                    hashed("20e13d18474fc94ed55aeb7069566e4687d773dac16f4c3f8722fc95bf9f2dfa");

            case "mudling" ->
                    hashed("27bcccc125a4110434a85c40ada039d050f14ef7db34a3444067310f8ce69606");

            case "deer" ->
                    hashed("c14af5ac6a96bfdee1b83c7e33c874a268936284384d66c9c61c18c9fc366bc6");

            case "squirrel" ->
                    hashed("b0cc93ffe36f927ede1c2fe44fa2d800783e3f070a9a562df854feb393e25910");

            case "boar" ->
                    hashed("8ecf3e48ae1a72cb6297d5ec030ed36c15055f941bdbe187144860327e2eb8d4");

            case "owl" ->
                    hashed("51b0b6511452f8b329d2d94ec7505fa7a77143c75ec0f9dabaefb0b999def66d");

            case "sheep" ->
                    hashed("b600b92d210e49ff4a10aeb1e411a96e327baaf93d025909e3bf5a8cea768c04");

            case "butterfly" ->
                    hashed("38658bb6eee3400235b1e680488cbd9937bcc675ee87d6cc75355ee9667df3b5");

            case "mooshroom" ->
                    hashed("2b52841f2fd589e0bc84cbabf9e1c27cb70cac98f8d6b3dd065e55a4dcb70d77");

            case "shroomling" ->
                    hashed("fe4f60079a5e5cc37f28542681ef790a974ed9a1b50f618f40019452c196199b");

            case "cat" ->
                    hashed("88eb2b52adcf8bfe846191e74940be098fe5799ee03d94eefb7056620c9975a2");

            case "iron_golem" ->
                    hashed("e13f34227283796bc017244cb46557d64bd562fa9dab0e12af5d23ad699cf697");

            case "sniffer" ->
                    hashed("7d71a1bdb41754f8da342241a0c53767e7be3e6de6d9246b8553341ae7172024");

            case "moss_sprite" ->
                    hashed("6d12113f912c81e277d067e558be27647e3a3ce07de83744d4b97fa4f3737724");

            case "swamp_hag" ->
                    hashed("20e13d18474fc94ed55aeb7069566e4687d773dac16f4c3f8722fc95bf9f2dfa");

            case "forest_spirit" ->
                    hashed("c14af5ac6a96bfdee1b83c7e33c874a268936284384d66c9c61c18c9fc366bc6");

            case "bloom_fairy" ->
                    hashed("38658bb6eee3400235b1e680488cbd9937bcc675ee87d6cc75355ee9667df3b5");

            case "sand_wraith" ->
                    hashed("74b8a333dfa92e7e5a95ad4ae2d84b1bafa33dc28c054925277f60e79dafc8c4");

            case "mycelord" ->
                    hashed("2b52841f2fd589e0bc84cbabf9e1c27cb70cac98f8d6b3dd065e55a4dcb70d77");

            case "lush_oracle" ->
                    hashed("7d71a1bdb41754f8da342241a0c53767e7be3e6de6d9246b8553341ae7172024");

            case "hacker" ->
                    textured(
                            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjI4MmExZWExZWMyZmYwYjVlZTkyMWZkZGZkZDY3YWRkYWI3YTNmMDFjNzk3NDA4ZGE5NjI5YzhhZGEwNGNiNyJ9fX0="
                    );

            default ->
                    new ItemStack(
                            Material.PLAYER_HEAD
                    );
        };
    }

    private static ItemStack hashed(String textureHash) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/"
                + textureHash
                + "\"}}}";

        return textured(
                Base64.getEncoder().encodeToString(
                        json.getBytes(StandardCharsets.UTF_8)
                )
        );
    }

    private static ItemStack textured(
            String textureValue
    ) {

        ItemStack head =
                new ItemStack(
                        Material.PLAYER_HEAD
                );

        SkullMeta meta =
                (SkullMeta) head.getItemMeta();

        if (meta == null) {
            return head;
        }

        PlayerProfile profile =
                Bukkit.createProfile(
                        UUID.nameUUIDFromBytes(
                                textureValue.getBytes()
                        )
                );

        profile.setProperty(
                new ProfileProperty(
                        "textures",
                        textureValue
                )
        );

        meta.setPlayerProfile(
                profile
        );

        head.setItemMeta(
                meta
        );

        return head;
    }
}
