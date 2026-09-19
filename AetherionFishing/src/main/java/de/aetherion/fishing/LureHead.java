package de.aetherion.fishing;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Same fish-head textures pets already use — keeps lure visuals on-brand. */
final class LureHead {

    private static final String COD =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGZlZWZmNGI3ZmNmY2U2OGIwZjc0ZGYwZGIwYWQwYzAxZjczMDFkMGM2ZDg5MzY5OWI0MDJiZDUwYmIzNzZiMCJ9fX0=";
    private static final String SALMON =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjBlYTlhMjIzNjIwY2RiNTRiMzU3NDEzZDQzYmQ4OWM0MDA4YmNhNmEyMjdmM2I3ZGI5N2Y3NzMzZWFkNWZjZiJ9fX0=";
    private static final String TROPICAL =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNmFhNDA1ZjQ5YTkzODgwNmNlNDFjYzY2MTZjYjA3Nzc3MzIwZTcxMWI1YjZkOWIyYjJlMmM0YjUzOTYzNmMxYSJ9fX0=";

    private LureHead() {
    }

    static ItemStack random() {
        int roll = ThreadLocalRandom.current().nextInt(3);
        return textured(roll == 0 ? COD : roll == 1 ? SALMON : TROPICAL);
    }

    private static ItemStack textured(String textureValue) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) {
            return head;
        }
        PlayerProfile profile = Bukkit.createProfile(
                UUID.nameUUIDFromBytes(textureValue.getBytes(StandardCharsets.UTF_8))
        );
        profile.setProperty(new ProfileProperty("textures", textureValue));
        meta.setPlayerProfile(profile);
        head.setItemMeta(meta);
        return head;
    }
}
