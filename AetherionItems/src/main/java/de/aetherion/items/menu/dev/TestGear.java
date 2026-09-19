package de.aetherion.items.menu.dev;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.item.CustomItem;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Sandbox-only gear for Test Arena prototypes — real Aetherion items. */
public final class TestGear {

    private TestGear() {
    }

    public static List<ItemStack> all() {
        CustomItem custom = AetherionItems.getInstance() == null
                ? null
                : AetherionItems.getInstance().getCustomItem();
        List<ItemStack> items = new ArrayList<>();
        if (custom == null) {
            return items;
        }
        items.add(custom.createEchoBlade());
        items.add(custom.createParityGauntlets());
        items.add(custom.createRulebreakerCharm());
        items.add(custom.createMetronomeBow());
        items.add(custom.createSoftlockPlate());
        items.add(custom.createNullstepBoots());
        items.add(custom.createBrokerContract());
        items.add(custom.createStormcallerMaul());
        items.add(custom.createResonanceScythe());
        items.add(custom.createJudgmentStaff());
        items.add(custom.createDashDagger());
        items.add(custom.createCycloneRod());
        items.add(custom.createPrismStaff());
        items.add(custom.createCascadeShortbow());
        return items;
    }
}
