package de.aetherion.aethermobs.command;

import de.aetherion.aethermobs.AetherMobs;
import de.aetherion.aethermobs.model.PetVariant;
import de.aetherion.aethermobs.pet.PetDefinition;
import de.aetherion.aethermobs.pet.PetEntity;
import de.aetherion.aethermobs.pet.PetInstance;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;

public class PetTestCommand implements CommandExecutor {

    private final AetherMobs plugin;

    public PetTestCommand(
            AetherMobs plugin
    ) {
        this.plugin = plugin;
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
                    "Only players can use this command."
            );

            return true;
        }

        if (args.length > 0
                && args[0].equalsIgnoreCase("collection")) {

            showCollection(
                    player
            );

            return true;
        }

        if (args.length > 0
                && (args[0].equalsIgnoreCase("sphere")
                || args[0].equalsIgnoreCase("beta"))) {

            giveSphere(player, args);
            return true;
        }

        if (args.length > 0
                && args[0].equalsIgnoreCase("shiny")) {

            spawnShinyTestPet(
                    player
            );

            return true;
        }

        if (args.length > 0
                && args[0].equalsIgnoreCase("dragon")) {

            spawnDragonTestPet(
                    player
            );

            return true;
        }

        Collection<PetDefinition> definitions =
                plugin.getPetRegistry()
                        .getAll();

        if (definitions.isEmpty()) {

            player.sendMessage(
                    "No pets are registered."
            );

            return true;
        }

        List<PetDefinition> petList =
                List.copyOf(
                        definitions
                );

        PetDefinition definition =
                petList.get(
                        (int) (
                                Math.random()
                                        * petList.size()
                        )
                );

        PetInstance pet =
                plugin.getPetGenerator()
                        .generate(
                                definition
                        );

        Location spawnLocation =
                player.getLocation()
                        .clone()
                        .add(
                                1.5,
                                0.5,
                                1.5
                        );

        PetEntity petEntity =
                new PetEntity(
                        pet
                );

        petEntity.spawn(
                spawnLocation,
                true
        );

        plugin.getPetSpawnManager()
                .registerTestPet(
                        petEntity
                );

        sendPetInfo(
                player,
                pet,
                "§6§lAetherMobs Test Pet"
        );

        return true;
    }

    private void giveSphere(Player player, String[] args) {
        if (!player.isOp()) {
            player.sendMessage("§cOnly operators can give catch spheres.");
            return;
        }

        String id = "beta";

        if (args.length >= 2) {
            id = args[1].toLowerCase();
        } else if (args[0].equalsIgnoreCase("sphere")) {
            id = "beta";
        }

        if (plugin.getCatchSphereRegistry().get(id) == null) {
            player.sendMessage("§cUnknown sphere. Use common, rare, epic or beta.");
            return;
        }

        int amount = id.equals("beta") ? 1 : 16;
        var item = plugin.getBetaSphereManager().createCatchSphere(id);
        item.setAmount(amount);
        player.getInventory().addItem(item);
        player.sendMessage("§d✦ §fGave §d" + amount + "x " + id + " §fcatch sphere.");
    }

    private void spawnShinyTestPet(
            Player player
    ) {

        Collection<PetDefinition> definitions =
                plugin.getPetRegistry()
                        .getAll();

        if (definitions.isEmpty()) {

            player.sendMessage(
                    "No pets are registered."
            );

            return;
        }

        List<PetDefinition> petList =
                List.copyOf(
                        definitions
                );

        PetDefinition definition =
                petList.get(
                        (int) (
                                Math.random()
                                        * petList.size()
                        )
                );

        PetInstance pet =
                plugin.getPetGenerator()
                        .generate(
                                definition
                        );

        /*
         * =====================================================
         * FORCE SHINY
         * =====================================================
         */

        if (pet.getVariant()
                != PetVariant.SHINY) {

            pet.setVariant(
                    PetVariant.SHINY
            );

            pet.getStats()
                    .setCoreValue(
                            pet.getStats()
                                    .getCoreValue()
                                    * 2.0
                    );

            for (
                    var entry :
                    List.copyOf(
                            pet.getStats()
                                    .getBonusStats()
                                    .entrySet()
                    )
            ) {

                pet.getStats()
                        .setBonusStat(
                                entry.getKey(),
                                entry.getValue()
                                        * 2.0
                        );
            }
        }

        Location spawnLocation =
                player.getLocation()
                        .clone()
                        .add(
                                1.5,
                                0.5,
                                1.5
                        );

        PetEntity petEntity =
                new PetEntity(
                        pet
                );

        petEntity.spawn(
                spawnLocation,
                true
        );

        /*
         * IMPORTANT:
         *
         * Register the test pet in the same registry
         * used by the normal catch system.
         */

        plugin.getPetSpawnManager()
                .registerTestPet(
                        petEntity
                );

        sendPetInfo(
                player,
                pet,
                shinyHeader()
        );
    }

    private void spawnDragonTestPet(
            Player player
    ) {

        PetDefinition definition =
                plugin.getPetRegistry()
                        .get(
                                "aetherion"
                        );

        if (definition == null) {

            player.sendMessage(
                    "§cAetherion is not registered."
            );

            return;
        }

        PetInstance pet =
                plugin.getPetGenerator()
                        .generate(
                                definition
                        );

        Location spawnLocation =
                player.getLocation()
                        .clone()
                        .add(
                                1.5,
                                0.5,
                                1.5
                        );

        PetEntity petEntity =
                new PetEntity(
                        pet
                );

        petEntity.spawn(
                spawnLocation,
                true
        );

        plugin.getPetSpawnManager()
                .registerTestPet(
                        petEntity
                );

        sendPetInfo(
                player,
                pet,
                "§5§l✦ AETHERION TEST PET ✦"
        );
    }

    private String shinyHeader() {

        return "§d§l✨ "
                + rainbowText("SHINY")
                + " §d§lPET ✨";
    }

    private String rainbowText(
            String text
    ) {

        String[] colors = {
                "§c",
                "§6",
                "§e",
                "§a",
                "§b",
                "§d"
        };

        StringBuilder result =
                new StringBuilder();

        for (int i = 0; i < text.length(); i++) {

            result.append(
                    colors[
                            i % colors.length
                            ]
            );

            result.append(
                    text.charAt(i)
            );
        }

        return result.toString();
    }

    private void sendPetInfo(
            Player player,
            PetInstance pet,
            String header
    ) {

        player.sendMessage("");
        player.sendMessage(
                header
        );

        player.sendMessage(
                "§7Pet: §f"
                        + pet.getDefinition()
                        .getDisplayName()
        );

        player.sendMessage(
                "§7Rarity: §f"
                        + pet.getRarity()
        );

        player.sendMessage(
                "§7Variant: §d"
                        + pet.getVariant()
        );

        player.sendMessage(
                "§7Level: §f"
                        + pet.getLevel()
        );

        player.sendMessage(
                "§7Core Stat: §f"
                        + pet.getStats()
                        .getCoreStat()
        );

        player.sendMessage(
                "§7Core Value: §f"
                        + String.format(
                        "%.2f",
                        pet.getStats()
                                .getCoreValue()
                )
        );

        player.sendMessage(
                "§7Bonus Stats: §f"
                        + pet.getStats()
                        .getBonusStats()
        );

        player.sendMessage("");
    }

    private void showCollection(
            Player player
    ) {

        var collection =
                plugin.getPetCollection(
                        player
                );

        player.sendMessage("");
        player.sendMessage(
                "§d§l✦ PET COLLECTION ✦"
        );
        player.sendMessage("");

        if (collection.getPets()
                .isEmpty()) {

            player.sendMessage(
                    "§7Your collection is empty."
            );

            player.sendMessage("");
            return;
        }

        player.sendMessage(
                "§7Pets collected: §f"
                        + collection.getSize()
        );

        player.sendMessage("");

        int index = 1;

        for (
                PetInstance pet :
                collection.getPets()
        ) {

            String rarity =
                    pet.getRarity()
                            .toString()
                            .toLowerCase();

            rarity =
                    Character.toUpperCase(
                            rarity.charAt(0)
                    )
                            + rarity.substring(1);

            String variant =
                    pet.getVariant()
                            .toString()
                            .toLowerCase();

            player.sendMessage(
                    "§f"
                            + index
                            + ". "
                            + getRarityColor(
                            pet.getRarity()
                    )
                            + pet.getDefinition()
                            .getDisplayName()
                            + " §7• "
                            + getRarityColor(
                            pet.getRarity()
                    )
                            + rarity
                            + " §7• "
                            + (
                            pet.getVariant()
                                    == PetVariant.SHINY
                                    ? rainbowText("SHINY")
                                    : variant
                    )
                            + " §7• Level "
                            + pet.getLevel()
            );

            index++;
        }

        player.sendMessage("");
    }

    private String getRarityColor(
            Object rarity
    ) {

        return switch (
                rarity.toString()
                ) {

            case "COMMON" ->
                    "§f";

            case "UNCOMMON" ->
                    "§a";

            case "RARE" ->
                    "§9";

            case "EPIC" ->
                    "§5";

            case "LEGENDARY" ->
                    "§6";

            case "MYTHIC" ->
                    "§d";

            default ->
                    "§f";
        };
    }
}