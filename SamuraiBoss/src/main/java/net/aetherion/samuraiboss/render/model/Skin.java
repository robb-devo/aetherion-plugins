package net.aetherion.samuraiboss.render.model;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Ein "Skin" legt fest, wie die Material-Schlüssel eines Modells aussehen.
 * Dasselbe Knochenmodell kann so als Samurai (Lack & Seide), als Geist (leuchtendes Glas)
 * oder als Schattenklon (dunkles Glas) erscheinen – ohne neue Geometrie.
 */
public final class Skin {

    public final String name;
    private final Map<String, BlockData> blocks;
    private final Map<String, ItemStack> items;
    private final BlockData fallback;
    public final Set<String> hiddenTags;
    /** null = Licht der Welt verwenden. */
    public final Display.Brightness brightness;
    public final Color glow;
    /** Nur Teile mit einem dieser Tags glühen (leer = alle, wenn glow gesetzt). */
    public final Set<String> glowTags;

    private Skin(Builder b) {
        this.name = b.name;
        this.blocks = Map.copyOf(b.blocks);
        this.items = Map.copyOf(b.items);
        this.fallback = b.fallback;
        this.hiddenTags = Set.copyOf(b.hidden);
        this.brightness = b.brightness;
        this.glow = b.glow;
        this.glowTags = Set.copyOf(b.glowTags);
    }

    public BlockData block(String key) {
        BlockData d = blocks.get(key);
        return d != null ? d : fallback;
    }

    public ItemStack item(String key) {
        ItemStack s = items.get(key);
        return s != null ? s.clone() : null;
    }

    public boolean hides(CubeDef c) {
        for (String t : c.tags) if (hiddenTags.contains(t)) return true;
        return false;
    }

    public boolean glows(CubeDef c) {
        if (glow == null) return false;
        if (glowTags.isEmpty()) return true;
        for (String t : c.tags) if (glowTags.contains(t)) return true;
        return false;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    /** Kopie dieses Skins als Ausgangspunkt für einen neuen. */
    public Builder derive(String newName) {
        Builder b = new Builder(newName);
        b.blocks.putAll(blocks);
        b.items.putAll(items);
        b.fallback = fallback;
        b.hidden.addAll(hiddenTags);
        b.brightness = brightness;
        b.glow = glow;
        b.glowTags.addAll(glowTags);
        return b;
    }

    public static final class Builder {
        private final String name;
        private final Map<String, BlockData> blocks = new HashMap<>();
        private final Map<String, ItemStack> items = new HashMap<>();
        private BlockData fallback = Bukkit.createBlockData("minecraft:white_concrete");
        private final Set<String> hidden = new HashSet<>();
        private Display.Brightness brightness;
        private Color glow;
        private final Set<String> glowTags = new HashSet<>();

        private Builder(String name) {
            this.name = name;
        }

        public Builder block(String key, String blockData) {
            blocks.put(key, Bukkit.createBlockData(blockData));
            return this;
        }

        public Builder item(String key, ItemStack stack) {
            items.put(key, stack);
            return this;
        }

        public Builder fallback(String blockData) {
            this.fallback = Bukkit.createBlockData(blockData);
            return this;
        }

        public Builder hide(String... tags) {
            for (String t : tags) hidden.add(t);
            return this;
        }

        public Builder show(String... tags) {
            for (String t : tags) hidden.remove(t);
            return this;
        }

        public Builder brightness(Display.Brightness b) {
            this.brightness = b;
            return this;
        }

        public Builder glow(Color c, String... tags) {
            this.glow = c;
            glowTags.clear();
            for (String t : tags) glowTags.add(t);
            return this;
        }

        public Skin build() {
            return new Skin(this);
        }
    }
}
