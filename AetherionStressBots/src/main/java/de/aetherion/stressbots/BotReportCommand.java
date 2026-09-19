package de.aetherion.stressbots;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.List;

public final class BotReportCommand implements CommandExecutor {

    private final AetherionStressBots plugin;

    public BotReportCommand(AetherionStressBots plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!AetherionStressBots.canControl(sender)) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        String text = plugin.getController().reportText();
        plugin.getLogger().info("botreport requested by " + sender.getName() + "\n" + text);
        for (String line : text.split("\n")) {
            sender.sendMessage("§7" + line);
        }
        if (sender instanceof Player player && player.getInventory().firstEmpty() >= 0) {
            player.getInventory().addItem(asBook(text));
            player.sendMessage("§8A written book copy was added to your inventory.");
        }
        return true;
    }

    private static ItemStack asBook(String text) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta != null) {
            meta.setTitle("Testbot report");
            meta.setAuthor("Aetherion");
            List<String> pages = new ArrayList<>();
            String[] lines = text.split("\n");
            StringBuilder page = new StringBuilder();
            for (String line : lines) {
                if (page.length() + line.length() > 240) {
                    pages.add(page.toString());
                    page.setLength(0);
                }
                if (!page.isEmpty()) {
                    page.append('\n');
                }
                page.append(line);
            }
            if (!page.isEmpty()) {
                pages.add(page.toString());
            }
            if (pages.isEmpty()) {
                pages.add("(empty)");
            }
            meta.setPages(pages.subList(0, Math.min(pages.size(), 50)));
            book.setItemMeta(meta);
        }
        return book;
    }
}
