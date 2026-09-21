package de.aetherion.quests.model;

import de.aetherion.items.skill.AetherSkill;
import de.aetherion.quests.reward.Reward;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class Quest {


    private final String id;

    private final String title;

    private final String description;


    /*
     * =========================================================
     * QUEST SERVICE
     * =========================================================
     *
     * Verbindet eine Quest mit einem NPC-Service.
     *
     * Beispiel:
     *
     * "upgrade"
     *
     * bedeutet:
     *
     * Diese Quest gehört zum Upgrade-Service.
     *
     * Die Quest-ID selbst muss dadurch nicht mehr
     * im NPC hinterlegt werden.
     */

    private String serviceId;

    /**
     * Optional turn-in NPC id when different from the quest-giver
     * (e.g. Forager starts gather_wood, Egon receives the logs).
     */
    private String turnInNpcId;


    /*
     * =========================================================
     * REWARDS
     * =========================================================
     */

    private final List<Reward> rewards =
            new ArrayList<>();


    /*
     * =========================================================
     * OBJECTIVES
     * =========================================================
     */

    private final List<Objective> objectives =
            new ArrayList<>();


    /*
     * =========================================================
     * QUEST WAYPOINT
     * =========================================================
     *
     * Kann für jede Quest individuell
     * gesetzt werden.
     */

    private String targetWorld;

    private double targetX;

    private double targetY;

    private double targetZ;


    /*
     * Skill / account gates (optional). Checked on Accept.
     */
    private AetherSkill.Category requiredCategory;
    private int requiredCategoryLevel;
    private String requiredSkillId;
    private int requiredSkillLevel;
    private int requiredAccountLevel;


    /*
     * =========================================================
     * KONSTRUKTOR
     * =========================================================
     *
     * Der bestehende Konstruktor bleibt erhalten,
     * damit bestehende Quests weiterhin funktionieren.
     */

    public Quest(
            String id,
            String title,
            String description
    ) {

        this.id = id;

        this.title = title;

        this.description = description;

    }


    /*
     * =========================================================
     * GRUNDINFORMATIONEN
     * =========================================================
     */

    public String getId() {

        return id;

    }


    public String getTitle() {

        return title;

    }


    public String getDescription() {

        return description;

    }


    /*
     * =========================================================
     * SERVICE ID
     * =========================================================
     *
     * Setzt den NPC-Service, zu dem diese Quest gehört.
     *
     * Beispiel:
     *
     * quest.setServiceId("upgrade");
     */

    public void setServiceId(
            String serviceId
    ) {

        this.serviceId = serviceId;

    }


    /*
     * Gibt die Service-ID der Quest zurück.
     */

    public String getServiceId() {

        return serviceId;

    }


    /*
     * Prüft, ob die Quest einem Service zugeordnet ist.
     */

    public void setTurnInNpcId(String turnInNpcId) {
        this.turnInNpcId = turnInNpcId;
    }


    public String getTurnInNpcId() {
        return turnInNpcId;
    }


    public boolean hasTurnInNpc() {
        return turnInNpcId != null && !turnInNpcId.isBlank();
    }


    public boolean hasService() {

        return serviceId != null
                && !serviceId.isBlank();

    }


    /*
     * Prüft, ob die Quest zu einem bestimmten
     * NPC-Service gehört.
     */

    public boolean hasService(
            String serviceId
    ) {

        if (serviceId == null
                || serviceId.isBlank()) {

            return false;

        }


        if (this.serviceId == null
                || this.serviceId.isBlank()) {

            return false;

        }


        return this.serviceId.equalsIgnoreCase(
                serviceId
        );

    }


    /*
     * =========================================================
     * REWARDS
     * =========================================================
     */

    public void addReward(
            Reward reward
    ) {

        rewards.add(
                reward
        );

    }


    public void clearRewards() {
        rewards.clear();
    }


    public List<Reward> getRewards() {

        return Collections.unmodifiableList(
                rewards
        );

    }


    /*
     * =========================================================
     * OBJECTIVES
     * =========================================================
     */

    public void addObjective(
            Objective objective
    ) {

        objectives.add(
                objective
        );

    }


    public void clearObjectives() {
        objectives.clear();
    }


    public List<Objective> getObjectives() {

        return Collections.unmodifiableList(
                objectives
        );

    }


    /*
     * =========================================================
     * QUEST WAYPOINT SETZEN
     * =========================================================
     */

    public void setWaypoint(
            String world,
            double x,
            double y,
            double z
    ) {

        this.targetWorld = world;

        this.targetX = x;

        this.targetY = y;

        this.targetZ = z;

    }


    /*
     * =========================================================
     * QUEST WAYPOINT ENTFERNEN
     * =========================================================
     */

    public void clearWaypoint() {

        this.targetWorld = null;

        this.targetX = 0;

        this.targetY = 0;

        this.targetZ = 0;

    }


    /*
     * =========================================================
     * PRÜFEN, OB EIN WAYPOINT EXISTIERT
     * =========================================================
     */

    public boolean hasWaypoint() {

        return targetWorld != null
                && !targetWorld.isBlank();

    }


    /*
     * =========================================================
     * WAYPOINT WELT
     * =========================================================
     */

    public String getTargetWorld() {

        return targetWorld;

    }


    /*
     * =========================================================
     * WAYPOINT X
     * =========================================================
     */

    public double getTargetX() {

        return targetX;

    }


    /*
     * =========================================================
     * WAYPOINT Y
     * =========================================================
     */

    public double getTargetY() {

        return targetY;

    }


    /*
     * =========================================================
     * WAYPOINT Z
     * =========================================================
     */

    public double getTargetZ() {

        return targetZ;

    }


    /*
     * =========================================================
     * SKILL / ACCOUNT REQUIREMENTS
     * =========================================================
     */

    public void requireCategory(AetherSkill.Category category, int minLevel) {
        this.requiredCategory = category;
        this.requiredCategoryLevel = Math.max(0, minLevel);
    }


    public void requireSkill(String skillId, int minLevel) {
        this.requiredSkillId = skillId;
        this.requiredSkillLevel = Math.max(0, minLevel);
    }


    public void requireAccountLevel(int minLevel) {
        this.requiredAccountLevel = Math.max(0, minLevel);
    }


    public boolean hasSkillRequirement() {
        return requiredAccountLevel > 0
                || (requiredCategory != null && requiredCategoryLevel > 0)
                || (requiredSkillId != null && !requiredSkillId.isBlank() && requiredSkillLevel > 0);
    }


    public AetherSkill.Category getRequiredCategory() {
        return requiredCategory;
    }


    public int getRequiredCategoryLevel() {
        return requiredCategoryLevel;
    }


    public String getRequiredSkillId() {
        return requiredSkillId;
    }


    public int getRequiredSkillLevel() {
        return requiredSkillLevel;
    }


    public int getRequiredAccountLevel() {
        return requiredAccountLevel;
    }

}