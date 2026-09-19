package de.aetherion.quests.model;

public class Objective {

    private final ObjectiveType type;

    private final String target;

    private final int amount;


    public Objective(
            ObjectiveType type,
            String target,
            int amount
    ) {

        this.type = type;
        this.target = target;
        this.amount = amount;

    }


    public ObjectiveType getType() {

        return type;

    }


    public String getTarget() {

        return target;

    }


    public int getAmount() {

        return amount;

    }


    public String getDisplayName() {

        if (target == null || target.isBlank()) {
            return "Objective";
        }

        if (target.equalsIgnoreCase("ANY")) {
            return type == ObjectiveType.CATCH ? "§dCatch a Pet" : "Any";
        }

        if (target.equalsIgnoreCase("AETHERION_MANAGER")) {
            return "§eAetherion Manager";
        }

        if (target.equalsIgnoreCase("AETHER_SKILL")) {
            return "§eEquip a Skill";
        }

        if (target.equalsIgnoreCase("APPLY_BOOSTER") || target.equalsIgnoreCase("AETHER_ANVIL")) {
            return "§6Apply a Booster";
        }

        if (target.equalsIgnoreCase("BORDERLANDS_RITE")) {
            return "§cIgnite a Borderlands Spirit";
        }

        if (target.equalsIgnoreCase("AETHERION_PET_MENU")) {
            return "§dOpen Pet Menu";
        }

        if (target.equalsIgnoreCase("AETHER_PET")) {
            return "§dEquip a Pet";
        }

        if (target.equalsIgnoreCase("simple_pickaxe")) {
            return "§fSimple Pickaxe";
        }

        if (target.equalsIgnoreCase("mining_pickaxe")) {
            return "§fMining Pickaxe";
        }

        if (target.equalsIgnoreCase("MERCHANT_CHEST")) {
            return "Merchant Chest";
        }

        String[] parts = target.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();

        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }

            if (result.length() > 0) {
                result.append(' ');
            }

            result.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                result.append(part.substring(1));
            }
        }

        return result.toString();
    }

}