package de.aetherion.items.economy;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

public final class TexturedHeads {

    private TexturedHeads() {
    }

    public static ItemStack hashed(String textureHash) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/"
                + textureHash
                + "\"}}}";
        return textured(Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)));
    }

    private static ItemStack textured(String textureValue) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) {
            return head;
        }
        PlayerProfile profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(textureValue.getBytes(StandardCharsets.UTF_8)));
        profile.setProperty(new ProfileProperty("textures", textureValue));
        meta.setPlayerProfile(profile);
        head.setItemMeta(meta);
        return head;
    }
}
