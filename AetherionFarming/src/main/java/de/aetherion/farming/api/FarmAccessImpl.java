package de.aetherion.farming.api;

import de.aetherion.core.api.FarmAccess;
import de.aetherion.farming.AetherionFarming;
import de.aetherion.farming.FeaturedCropService;
import de.aetherion.farming.dev.FarmDistrictMarker;
import de.aetherion.farming.portal.FarmPortalAPI;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class FarmAccessImpl implements FarmAccess {

    @Override
    public boolean available() {
        return FarmPortalAPI.available();
    }

    @Override
    public String ensureIsland(boolean forceRebuild) {
        return FarmPortalAPI.ensureIsland(forceRebuild);
    }

    @Override
    public ItemStack hubPortalTool() {
        return FarmPortalAPI.createHubPortalTool();
    }

    @Override
    public void setIslandExitHere(Player player) {
        FarmPortalAPI.setIslandExitHere(player);
    }

    @Override
    public void teleportToIsland(Player player) {
        FarmPortalAPI.teleportToIsland(player);
    }

    @Override
    public String refreshAmbience() {
        return FarmPortalAPI.refreshAmbience();
    }

    @Override
    public String statusLine() {
        return FarmPortalAPI.statusLine();
    }

    @Override
    public ItemStack canePatchTool() {
        AetherionFarming plugin = AetherionFarming.getInstance();
        return plugin == null || plugin.canePatchTool() == null
                ? null
                : plugin.canePatchTool().create();
    }

    @Override
    public ItemStack districtMarker(String districtId) {
        AetherionFarming plugin = AetherionFarming.getInstance();
        if (plugin == null || plugin.districtMarker() == null || districtId == null) {
            return null;
        }
        try {
            FarmDistrictMarker.District district =
                    FarmDistrictMarker.District.valueOf(districtId.trim().toUpperCase());
            return plugin.districtMarker().create(district);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Override
    public ItemStack scarecrowTool() {
        AetherionFarming plugin = AetherionFarming.getInstance();
        return plugin == null || plugin.scarecrow() == null
                ? null
                : plugin.scarecrow().create();
    }

    @Override
    public ItemStack hayWagonTool() {
        AetherionFarming plugin = AetherionFarming.getInstance();
        return plugin == null || plugin.hayWagon() == null
                ? null
                : plugin.hayWagon().create();
    }

    @Override
    public String seedFarmIsle(boolean force) {
        AetherionFarming plugin = AetherionFarming.getInstance();
        if (plugin == null || plugin.seeder() == null) {
            return "§cAetherionFarming offline — seeder unavailable.";
        }
        return plugin.seeder().start(force);
    }

    @Override
    public int featuredCropBonus(Material yield) {
        FeaturedCropService featured = AetherionFarming.getInstance() == null
                ? null
                : AetherionFarming.getInstance().featuredCrop();
        if (featured == null || !featured.isFeatured(yield)) {
            return 0;
        }
        return featured.bonusAmount();
    }

    @Override
    public String featuredCropName() {
        FeaturedCropService featured = AetherionFarming.getInstance() == null
                ? null
                : AetherionFarming.getInstance().featuredCrop();
        return featured == null ? null : featured.prettyName();
    }
}
