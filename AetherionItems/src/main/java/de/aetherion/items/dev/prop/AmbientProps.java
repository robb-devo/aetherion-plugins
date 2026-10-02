package de.aetherion.items.dev.prop;

import de.aetherion.items.AetherionItems;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Registry for DEV ambient BlockDisplay props. */
public final class AmbientProps {

    private final Map<String, AmbientProp> byId = new LinkedHashMap<>();

    public AmbientProps(AetherionItems plugin) {
        List<AmbientProp> props = List.of(
                new WaterTroughProp(plugin),
                new MossyStumpProp(plugin),
                new OreCrateProp(plugin),
                new LanternPostProp(plugin),
                new LobsterTrapProp(plugin),
                new BarrelStackProp(plugin),
                new WaySignProp(plugin),
                new FlowerCartProp(plugin),
                new StoneWellProp(plugin),
                new CampfireRingProp(plugin),
                new ProduceCratesProp(plugin),
                new KelpRackProp(plugin),
                new OreCartProp(plugin),
                new BeeSkepProp(plugin),
                new ClotheslineProp(plugin),
                new NoticeBoardProp(plugin),
                new GrindstoneBenchProp(plugin),
                new HerbDryingRackProp(plugin),
                new AnvilRuinProp(plugin),
                new TorchBrazierProp(plugin),
                new BeachedRowboatProp(plugin),
                new CrystalOutcropProp(plugin),
                new WagonWheelProp(plugin),
                new ParcelPostProp(plugin),
                new MushroomShelfProp(plugin),
                new FishDryingFlakesProp(plugin),
                new MineTimberProp(plugin),
                new PicnicSetProp(plugin),
                new AetherionEggProp(plugin),
                new MineRailProp(plugin),
                new AshenKatanaProp(plugin)
        );
        PluginManager pm = plugin.getServer().getPluginManager();
        for (AmbientProp prop : props) {
            byId.put(prop.id(), prop);
            pm.registerEvents(prop, plugin);
        }
    }

    public ItemStack tool(String id) {
        AmbientProp prop = byId.get(id);
        return prop == null ? null : prop.create();
    }

    public Collection<AmbientProp> all() {
        return byId.values();
    }
}
