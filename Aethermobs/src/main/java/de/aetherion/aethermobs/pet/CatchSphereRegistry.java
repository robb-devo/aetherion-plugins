package de.aetherion.aethermobs.pet;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class CatchSphereRegistry {

    private final Map<String, CatchSphere> spheres;

    public CatchSphereRegistry() {
        this.spheres = new HashMap<>();

        register(CatchSphere.rarityTable(
                "common",
                "Common Catch Sphere",
                2000L,
                45.0,
                28.0,
                15.0,
                8.0,
                4.0,
                2.0
        ));

        register(CatchSphere.rarityTable(
                "rare",
                "Rare Catch Sphere",
                2500L,
                60.0,
                45.0,
                32.0,
                18.0,
                10.0,
                5.0
        ));

        register(CatchSphere.rarityTable(
                "epic",
                "Epic Catch Sphere",
                3000L,
                75.0,
                62.0,
                50.0,
                38.0,
                22.0,
                12.0
        ));

        register(CatchSphere.rarityTable(
                "legendary",
                "Legendary Catch Sphere",
                3500L,
                88.0,
                78.0,
                68.0,
                55.0,
                40.0,
                22.0
        ));

        register(CatchSphere.flat(
                "beta",
                "✦ BETA SPHERE ✦",
                3000L,
                true,
                90.0
        ));
    }

    public void register(CatchSphere sphere) {
        spheres.put(sphere.getId().toLowerCase(), sphere);
    }

    public CatchSphere get(String id) {
        if (id == null) {
            return null;
        }

        return spheres.get(id.toLowerCase());
    }

    public Collection<CatchSphere> getAll() {
        return spheres.values();
    }
}
