package de.aetherion.items.world;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

final class BuildingBannerRenderer extends MapRenderer {

    private final BuildingBannerKind kind;
    private final int panelIndex;
    private boolean drawn;

    BuildingBannerRenderer(BuildingBannerKind kind, int panelIndex) {
        super(true);
        this.kind = kind;
        this.panelIndex = Math.max(0, Math.min(BuildingBannerArt.PANELS - 1, panelIndex));
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        if (drawn) {
            return;
        }
        BuildingBannerArt.blit(BuildingBannerArt.image(kind), panelIndex, canvas);
        drawn = true;
    }
}
