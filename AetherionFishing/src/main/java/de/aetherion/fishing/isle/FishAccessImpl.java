package de.aetherion.fishing.isle;

import de.aetherion.core.api.FishAccess;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/** Core {@link FishAccess} for AetherionItems: isle check + the DEV "Fishing Island" hub. */
public final class FishAccessImpl implements FishAccess {

    private final FishIsle isle;

    public FishAccessImpl(FishIsle isle) {
        this.isle = isle;
    }

    @Override
    public boolean onFishIsle(Location at) {
        return LakeWorld.onIsle(at);
    }

    @Override
    public String devAction(Player player, String action) {
        return isle.dev().action(player, action);
    }

    @Override
    public Map<String, ItemStack> devItems(String group) {
        return isle.dev().items(group);
    }

    @Override
    public List<String> devStatus() {
        return isle.dev().status();
    }

    @Override
    public Map<String, Location> devSpots() {
        return isle.dev().spots(LakeWorld.world(), false);
    }
}
