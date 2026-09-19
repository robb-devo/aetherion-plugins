package de.aetherion.quests.placeholder;


import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.ui.QuestCompass;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

import org.bukkit.entity.Player;

import java.util.Locale;


public class QuestPlaceholderExpansion extends PlaceholderExpansion {


    private final AetherionQuests plugin;
    private final QuestCompass compass;


    public QuestPlaceholderExpansion(AetherionQuests plugin, QuestCompass compass) {
        this.plugin = plugin;
        this.compass = compass;
    }


    @Override
    public String getIdentifier() {
        return "aetherionquests";
    }


    @Override
    public String getAuthor() {
        return "Aetherion";
    }


    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }


    @Override
    public boolean persist() {
        return true;
    }


    @Override
    public String onPlaceholderRequest(Player player, String params) {

        if (player == null || params == null || compass == null) {
            return "";
        }

        return switch (params.toLowerCase(Locale.ROOT)) {
            case "arrow" -> compass.arrow(player);
            case "distance" -> compass.distance(player);
            case "compass" -> compass.line(player);
            case "target" -> compass.targetName(player);
            case "title" -> compass.questTitle(player);
            default -> null;
        };

    }

}
