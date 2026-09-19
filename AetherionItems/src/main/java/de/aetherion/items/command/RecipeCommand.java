package de.aetherion.items.command;

import de.aetherion.items.recipe.GUI.RecipeBookGUI;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RecipeCommand implements CommandExecutor {

    private final RecipeBookGUI recipeBookGUI;


    public RecipeCommand(
            RecipeBookGUI recipeBookGUI
    ) {

        this.recipeBookGUI =
                recipeBookGUI;
    }


    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {

        if (!(sender instanceof Player player)) {

            sender.sendMessage(
                    "§cDieser Command kann nur von einem Spieler verwendet werden."
            );

            return true;
        }

        var progress = de.aetherion.items.AetherionItems.getInstance() == null
                ? null
                : de.aetherion.items.AetherionItems.getInstance().progress();
        if (progress != null && !progress.recipeBook(player)) {
            player.sendMessage(progress.hint(de.aetherion.items.progress.ProgressionService.Flag.WORKBENCH));
            return true;
        }

        recipeBookGUI.openCategories(
                player
        );

        return true;
    }
}