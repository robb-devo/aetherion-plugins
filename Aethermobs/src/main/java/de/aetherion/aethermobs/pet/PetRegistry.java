package de.aetherion.aethermobs.pet;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class PetRegistry {

    private final Map<String, PetDefinition> definitions;

    public PetRegistry() {
        this.definitions = new LinkedHashMap<>();
    }

    public void register(
            PetDefinition definition
    ) {
        definitions.put(
                definition.getId(),
                definition
        );
    }

    public PetDefinition get(
            String id
    ) {
        if (id == null) {
            return null;
        }
        PetDefinition direct = definitions.get(id);
        if (direct != null) {
            return direct;
        }
        // Legacy save id → Mining Dragon
        if ("poison_dragon".equalsIgnoreCase(id)) {
            return definitions.get("mining_dragon");
        }
        return null;
    }

    public Collection<PetDefinition> getAll() {
        return definitions.values();
    }

    public boolean exists(
            String id
    ) {
        return definitions.containsKey(id);
    }
}