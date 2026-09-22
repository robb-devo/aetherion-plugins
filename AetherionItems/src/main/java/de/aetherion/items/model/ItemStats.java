package de.aetherion.items.model;

public class ItemStats {

    /*
     * =========================================================
     * BASIS-STATS
     * =========================================================
     */

    private double miningPower;
    private double fortune;
    private double damage;
    private double defense;


    /*
     * =========================================================
     * SPECIAL-STATS
     * =========================================================
     *
     * Health:
     * -> Lapis
     * -> Erhöht die echte Minecraft Max Health
     *
     * Spread:
     * -> Emerald
     * -> Zusätzliche Blöcke / Spread
     *
     * Attack Spread:
     * -> Redstone
     * -> Zusätzliche getroffene Gegner
     */

    private double health;
    private double spread;
    private double attackSpread;
    private double speed;
    private double catchRate;
    private double critChance;
    private double critDamage;
    private double undeadDamage;
    private double undeadResist;
    private double harvestSpread;
    private double fishingSpeed;
    private double fishingCatch;


    /*
     * =========================================================
     * CORE BOOSTER-ANZAHL
     * =========================================================
     */

    private int coalBoosters;
    private int ironBoosters;
    private int goldBoosters;
    private int diamondBoosters;


    /*
     * =========================================================
     * SPECIAL BOOSTER-ANZAHL
     * =========================================================
     */

    private int emeraldBoosters;
    private int redstoneBoosters;
    private int lapisBoosters;
    private int glowstoneBoosters;
    private int wheatBoosters;
    private int carrotBoosters;
    private int oakBoosters;
    private int birchBoosters;


    /*
     * =========================================================
     * CONSTRUCTOR
     * =========================================================
     */

    public ItemStats() {

        /*
         * Core Stats
         */

        this.miningPower = 0.0;
        this.fortune = 0.0;
        this.damage = 0.0;
        this.defense = 0.0;


        /*
         * Special Stats
         */

        this.health = 0.0;
        this.spread = 0.0;
        this.attackSpread = 0.0;
        this.speed = 0.0;
        this.catchRate = 0.0;
        this.critChance = 0.0;
        this.critDamage = 0.0;
        this.undeadDamage = 0.0;
        this.undeadResist = 0.0;
        this.harvestSpread = 0.0;
        this.fishingSpeed = 0.0;
        this.fishingCatch = 0.0;


        /*
         * Core Booster
         */

        this.coalBoosters = 0;
        this.ironBoosters = 0;
        this.goldBoosters = 0;
        this.diamondBoosters = 0;


        /*
         * Special Booster
         */

        this.emeraldBoosters = 0;
        this.redstoneBoosters = 0;
        this.lapisBoosters = 0;
        this.glowstoneBoosters = 0;
        this.wheatBoosters = 0;
        this.carrotBoosters = 0;
        this.oakBoosters = 0;
        this.birchBoosters = 0;
    }


    /*
     * =========================================================
     * MINING POWER
     * =========================================================
     */

    public double getMiningPower() {
        return miningPower;
    }

    public void setMiningPower(
            double miningPower
    ) {
        this.miningPower = miningPower;
    }


    /*
     * =========================================================
     * FORTUNE
     * =========================================================
     */

    public double getFortune() {
        return fortune;
    }

    public void setFortune(
            double fortune
    ) {
        this.fortune = fortune;
    }


    /*
     * =========================================================
     * DAMAGE
     * =========================================================
     */

    public double getDamage() {
        return damage;
    }

    public void setDamage(
            double damage
    ) {
        this.damage = damage;
    }


    /*
     * =========================================================
     * DEFENSE
     * =========================================================
     */

    public double getDefense() {
        return defense;
    }

    public void setDefense(
            double defense
    ) {
        this.defense = defense;
    }


    /*
     * =========================================================
     * HEALTH
     * =========================================================
     *
     * Lapis Special Stat.
     *
     * Der Wert wird als zusätzlicher HP-Wert behandelt.
     *
     * Beispiel:
     *
     * 20 Minecraft HP
     * + 10 Aetherion Health
     * = 30 Max Health
     */

    public double getHealth() {
        return health;
    }

    public void setHealth(
            double health
    ) {
        this.health = health;
    }


    /*
     * =========================================================
     * SPREAD
     * =========================================================
     *
     * Emerald Special Stat.
     *
     * Wird für Mining / Foraging Spread verwendet.
     */

    public double getSpread() {
        return spread;
    }

    public void setSpread(
            double spread
    ) {
        this.spread = spread;
    }


    /*
     * =========================================================
     * ATTACK SPREAD
     * =========================================================
     *
     * Redstone Special Stat.
     *
     * Wird für zusätzliche getroffene Gegner verwendet.
     */

    public double getAttackSpread() {
        return attackSpread;
    }

    public void setAttackSpread(
            double attackSpread
    ) {
        this.attackSpread = attackSpread;
    }


    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }


    public double getCatchRate() {
        return catchRate;
    }

    public void setCatchRate(double catchRate) {
        this.catchRate = catchRate;
    }


    public double getCritChance() {
        return critChance;
    }

    public void setCritChance(double critChance) {
        this.critChance = critChance;
    }


    public double getCritDamage() {
        return critDamage;
    }

    public void setCritDamage(double critDamage) {
        this.critDamage = critDamage;
    }


    public double getUndeadDamage() {
        return undeadDamage;
    }

    public void setUndeadDamage(double undeadDamage) {
        this.undeadDamage = undeadDamage;
    }


    public double getUndeadResist() {
        return undeadResist;
    }

    public void setUndeadResist(double undeadResist) {
        this.undeadResist = undeadResist;
    }


    public double getHarvestSpread() {
        return harvestSpread;
    }

    public void setHarvestSpread(double harvestSpread) {
        this.harvestSpread = harvestSpread;
    }

    public double getFishingSpeed() {
        return fishingSpeed;
    }

    public void setFishingSpeed(double fishingSpeed) {
        this.fishingSpeed = fishingSpeed;
    }

    public double getFishingCatch() {
        return fishingCatch;
    }

    public void setFishingCatch(double fishingCatch) {
        this.fishingCatch = fishingCatch;
    }


    /*
     * =========================================================
     * COAL BOOSTER
     * =========================================================
     */

    public int getCoalBoosters() {
        return coalBoosters;
    }

    public void setCoalBoosters(
            int coalBoosters
    ) {
        this.coalBoosters = coalBoosters;
    }


    /*
     * =========================================================
     * IRON BOOSTER
     * =========================================================
     */

    public int getIronBoosters() {
        return ironBoosters;
    }

    public void setIronBoosters(
            int ironBoosters
    ) {
        this.ironBoosters = ironBoosters;
    }


    /*
     * =========================================================
     * GOLD BOOSTER
     * =========================================================
     */

    public int getGoldBoosters() {
        return goldBoosters;
    }

    public void setGoldBoosters(
            int goldBoosters
    ) {
        this.goldBoosters = goldBoosters;
    }


    /*
     * =========================================================
     * DIAMOND BOOSTER
     * =========================================================
     */

    public int getDiamondBoosters() {
        return diamondBoosters;
    }

    public void setDiamondBoosters(
            int diamondBoosters
    ) {
        this.diamondBoosters = diamondBoosters;
    }


    /*
     * =========================================================
     * EMERALD BOOSTER
     * =========================================================
     */

    public int getEmeraldBoosters() {
        return emeraldBoosters;
    }

    public void setEmeraldBoosters(
            int emeraldBoosters
    ) {
        this.emeraldBoosters = emeraldBoosters;
    }


    /*
     * =========================================================
     * REDSTONE BOOSTER
     * =========================================================
     */

    public int getRedstoneBoosters() {
        return redstoneBoosters;
    }

    public void setRedstoneBoosters(
            int redstoneBoosters
    ) {
        this.redstoneBoosters = redstoneBoosters;
    }


    /*
     * =========================================================
     * LAPIS BOOSTER
     * =========================================================
     */

    public int getLapisBoosters() {
        return lapisBoosters;
    }

    public void setLapisBoosters(
            int lapisBoosters
    ) {
        this.lapisBoosters = lapisBoosters;
    }


    public int getGlowstoneBoosters() {
        return glowstoneBoosters;
    }

    public void setGlowstoneBoosters(int glowstoneBoosters) {
        this.glowstoneBoosters = glowstoneBoosters;
    }


    public int getWheatBoosters() {
        return wheatBoosters;
    }

    public void setWheatBoosters(int wheatBoosters) {
        this.wheatBoosters = wheatBoosters;
    }


    public int getCarrotBoosters() {
        return carrotBoosters;
    }

    public void setCarrotBoosters(int carrotBoosters) {
        this.carrotBoosters = carrotBoosters;
    }


    public int getOakBoosters() {
        return oakBoosters;
    }

    public void setOakBoosters(int oakBoosters) {
        this.oakBoosters = oakBoosters;
    }


    public int getBirchBoosters() {
        return birchBoosters;
    }

    public void setBirchBoosters(int birchBoosters) {
        this.birchBoosters = birchBoosters;
    }


    /*
     * =========================================================
     * GESAMTANZAHL CORE BOOSTER
     * =========================================================
     */

    public int getTotalCoreBoosters() {

        return coalBoosters
                + ironBoosters
                + goldBoosters
                + diamondBoosters;
    }


    /*
     * =========================================================
     * GESAMTANZAHL SPECIAL BOOSTER
     * =========================================================
     */

    public int getTotalSpecialBoosters() {

        return emeraldBoosters
                + redstoneBoosters
                + lapisBoosters
                + glowstoneBoosters
                + wheatBoosters
                + carrotBoosters
                + oakBoosters
                + birchBoosters;
    }


    public static ItemStats upgradeFrom(
            ItemStats oldActual,
            ItemStats oldBase,
            ItemStats newBase
    ) {
        ItemStats merged = new ItemStats();

        merged.setMiningPower(scaleCore(oldActual.getMiningPower(), oldBase.getMiningPower(), newBase.getMiningPower()));
        merged.setFortune(scaleCore(oldActual.getFortune(), oldBase.getFortune(), newBase.getFortune()));
        merged.setDamage(scaleCore(oldActual.getDamage(), oldBase.getDamage(), newBase.getDamage()));
        merged.setDefense(scaleCore(oldActual.getDefense(), oldBase.getDefense(), newBase.getDefense()));

        merged.setHealth(newBase.getHealth() + extra(oldActual.getHealth(), oldBase.getHealth()));
        merged.setSpread(newBase.getSpread() + extra(oldActual.getSpread(), oldBase.getSpread()));
        merged.setAttackSpread(newBase.getAttackSpread() + extra(oldActual.getAttackSpread(), oldBase.getAttackSpread()));
        merged.setSpeed(newBase.getSpeed() + extra(oldActual.getSpeed(), oldBase.getSpeed()));
        merged.setCatchRate(newBase.getCatchRate() + extra(oldActual.getCatchRate(), oldBase.getCatchRate()));
        merged.setCritChance(newBase.getCritChance() + extra(oldActual.getCritChance(), oldBase.getCritChance()));
        merged.setCritDamage(newBase.getCritDamage() + extra(oldActual.getCritDamage(), oldBase.getCritDamage()));
        merged.setUndeadDamage(newBase.getUndeadDamage() + extra(oldActual.getUndeadDamage(), oldBase.getUndeadDamage()));
        merged.setUndeadResist(newBase.getUndeadResist() + extra(oldActual.getUndeadResist(), oldBase.getUndeadResist()));
        merged.setHarvestSpread(newBase.getHarvestSpread() + extra(oldActual.getHarvestSpread(), oldBase.getHarvestSpread()));
        merged.setFishingSpeed(newBase.getFishingSpeed() + extra(oldActual.getFishingSpeed(), oldBase.getFishingSpeed()));
        merged.setFishingCatch(newBase.getFishingCatch() + extra(oldActual.getFishingCatch(), oldBase.getFishingCatch()));

        merged.setCoalBoosters(oldActual.getCoalBoosters());
        merged.setIronBoosters(oldActual.getIronBoosters());
        merged.setGoldBoosters(oldActual.getGoldBoosters());
        merged.setDiamondBoosters(oldActual.getDiamondBoosters());
        merged.setEmeraldBoosters(oldActual.getEmeraldBoosters());
        merged.setRedstoneBoosters(oldActual.getRedstoneBoosters());
        merged.setLapisBoosters(oldActual.getLapisBoosters());
        merged.setGlowstoneBoosters(oldActual.getGlowstoneBoosters());
        merged.setWheatBoosters(oldActual.getWheatBoosters());
        merged.setCarrotBoosters(oldActual.getCarrotBoosters());
        merged.setOakBoosters(oldActual.getOakBoosters());
        merged.setBirchBoosters(oldActual.getBirchBoosters());

        return merged;
    }

    /**
     * Keep booster flats as an additive extra on the new base.
     * A zero old base used to return only {@code newBase} and strip booster-only stats.
     */
    private static double scaleCore(double oldActual, double oldBase, double newBase) {
        double extra = Math.max(0.0, oldActual - Math.max(0.0, oldBase));
        return newBase + extra;
    }

    private static double extra(double oldActual, double oldBase) {
        return Math.max(0.0, oldActual - oldBase);
    }


    /*
     * =========================================================
     * GESAMTANZAHL ALLER BOOSTER
     * =========================================================
     */

    public int getTotalBoosters() {

        return getTotalCoreBoosters()
                + getTotalSpecialBoosters();
    }

    public int boosterCount(BoosterType type) {
        if (type == null) {
            return 0;
        }
        return switch (type) {
            case COAL -> coalBoosters;
            case IRON -> ironBoosters;
            case GOLD -> goldBoosters;
            case DIAMOND -> diamondBoosters;
            case EMERALD -> emeraldBoosters;
            case REDSTONE -> redstoneBoosters;
            case LAPIS -> lapisBoosters;
            case GLOWSTONE -> glowstoneBoosters;
            case WHEAT -> wheatBoosters;
            case CARROT -> carrotBoosters;
            case OAK -> oakBoosters;
            case BIRCH -> birchBoosters;
        };
    }
}