package de.aetherion.quests.reward;

public class Reward {

    private final String name;
    private final int amount;


    public Reward(String name, int amount) {
        this.name = name;
        this.amount = amount;
    }


    public String getName() {
        return name;
    }


    public int getAmount() {
        return amount;
    }

}
