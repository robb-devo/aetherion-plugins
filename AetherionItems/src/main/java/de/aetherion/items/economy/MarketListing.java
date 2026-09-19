package de.aetherion.items.economy;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class MarketListing {

    private final UUID id;
    private final MarketChannel channel;
    private final UUID seller;
    private final long price;
    private final long created;
    private final long expires;
    private final ItemStack item;

    public MarketListing(
            UUID id,
            MarketChannel channel,
            UUID seller,
            long price,
            long created,
            long expires,
            ItemStack item
    ) {
        this.id = id;
        this.channel = channel;
        this.seller = seller;
        this.price = price;
        this.created = created;
        this.expires = expires;
        this.item = item;
    }

    public UUID id() {
        return id;
    }

    public MarketChannel channel() {
        return channel;
    }

    public UUID seller() {
        return seller;
    }

    public long price() {
        return price;
    }

    public long created() {
        return created;
    }

    public long expires() {
        return expires;
    }

    public ItemStack item() {
        return item;
    }

    public boolean expired(long now) {
        return now >= expires;
    }
}
