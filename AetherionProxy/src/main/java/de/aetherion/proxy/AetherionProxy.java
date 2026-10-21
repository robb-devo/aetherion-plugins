package de.aetherion.proxy;

import com.google.inject.Inject;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.player.ServerResourcePackSendEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;

import org.slf4j.Logger;

import java.nio.file.Path;

/**
 * The network's one resource pack, sent by the proxy when a player first lands on a backend.
 *
 * <p>Backends advertise no pack. A pack pushed by a backend makes the client reload all its resources on
 * every server switch, even when it is the same pack; sent once from here, it stays applied while the
 * player moves between hub, mmo-r, mmo-d, mmo-c and mmo-i. The SHA-1 is read from the served zip, so a pack
 * swap is: replace the zip, then {@code aepack reload} (and {@code aepack push} for players already online).
 */
@Plugin(id = "aetherion-proxy", name = "AetherionProxy", version = "1.0.0",
        description = "Sends the Aetherion resource pack once per session.", authors = {"Robb"})
public final class AetherionProxy {

    private final ProxyServer proxy;
    private final ResourcePack pack;

    @Inject
    public AetherionProxy(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.pack = new ResourcePack(this, proxy, logger, dataDirectory);
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        pack.reload(null);
        proxy.getCommandManager().register(
                proxy.getCommandManager().metaBuilder("aepack").plugin(this).build(), new PackCommand(pack));
    }

    /** First backend of the session only; later switches keep the pack the client already has. */
    @Subscribe
    public void onServerConnected(ServerPostConnectEvent event) {
        if (event.getPreviousServer() == null) {
            pack.offer(event.getPlayer());
        }
    }

    /** A backend that still advertises our zip would re-push it on every switch: the proxy owns it. */
    @Subscribe
    public void onBackendPack(ServerResourcePackSendEvent event) {
        if (pack.isOurs(event.getReceivedResourcePack().getUrl())) {
            event.setResult(ResultedEvent.GenericResult.denied());
        }
    }
}
