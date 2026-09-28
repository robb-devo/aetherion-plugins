package de.aetherion.farming.api;

import de.aetherion.core.api.FarmAccess;
import de.aetherion.farming.AetherionFarming;
import de.aetherion.farming.FeaturedCropService;
import de.aetherion.farming.dev.FarmDistrictMarker;
import de.aetherion.farming.island.FarmIsleZones;
import de.aetherion.farming.isle.FarmIsle;
import de.aetherion.farming.portal.FarmPortalAPI;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

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

    // ------------------------------------------------------------------ Eldervale expansion

    private static FarmIsle isle() {
        AetherionFarming plugin = AetherionFarming.getInstance();
        return plugin == null ? null : plugin.farmIsle();
    }

    @Override
    public boolean onFarmIsle(Location at) {
        AetherionFarming plugin = AetherionFarming.getInstance();
        return plugin != null && at != null && FarmIsleZones.inFarmIsleFootprint(plugin, at);
    }

    @Override
    public double cropFortuneBonus(Player player, Material crop) {
        FarmIsle isle = isle();
        return isle == null ? 0.0d : isle.cropFortune(player, crop);
    }

    @Override
    public int harvestBonus(Player player, Material yield, Location at) {
        FarmIsle isle = isle();
        return isle == null ? featuredCropBonus(yield) : isle.harvestBonus(player, yield, at);
    }

    @Override
    public String harvestBonusLabel(Player player, Location at) {
        FarmIsle isle = isle();
        return isle == null ? FarmAccess.super.harvestBonusLabel(player, at) : isle.harvestBonusLabel(player, at);
    }

    @Override
    public String devAction(Player player, String action) {
        FarmIsle isle = isle();
        return isle == null ? "§cEldervale loops offline (AetherionItems missing?)." : isle.dev().action(player, action);
    }

    @Override
    public Map<String, ItemStack> devItems(String group) {
        FarmIsle isle = isle();
        return isle == null ? Map.of() : isle.dev().items(group);
    }

    @Override
    public List<String> devStatus() {
        FarmIsle isle = isle();
        return isle == null ? List.of("§cEldervale loops offline.") : isle.dev().status();
    }

    @Override
    public Map<String, Location> devSpots() {
        FarmIsle isle = isle();
        return isle == null ? Map.of() : isle.dev().spots(null, false);
    }

    @Override
    public String featuredCropName() {
        FeaturedCropService featured = AetherionFarming.getInstance() == null
                ? null
                : AetherionFarming.getInstance().featuredCrop();
        return featured == null ? null : featured.prettyName();
    }
}
