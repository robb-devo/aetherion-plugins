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
import java.util.concurrent.TimeUnit;

/**
 * The network's one resource pack, sent by the proxy when a player first lands on a backend.
 *
 * <p>Backends advertise no pack. A pack pushed by a backend makes the client reload all its resources on
 * every server switch, even when it is the same pack; sent once from here, it stays applied while the
 * player moves between hub, mmo-r, mmo-d, mmo-c and mmo-i. The SHA-1 is read from the served zip and checked
 * every minute, so a pack swap is just: replace the zip. Players online get the new pack within a minute.
 */
@Plugin(id = "aetherion-proxy", name = "AetherionProxy", version = "1.0.0",
        description = "Sends the Aetherion resource pack once per session.", authors = {"Robb"})
public final class AetherionProxy {

    private final ProxyServer proxy;
    private final ResourcePack pack;
    private final Logger logger;
    /** The ops scripts touch this file ({@code ae-proxy-reload.sh}): there is no RCON on Velocity and no Crafty any more. */
    private final Path reloadRequest;

    @Inject
    public AetherionProxy(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.pack = new ResourcePack(this, proxy, logger, dataDirectory);
        this.reloadRequest = dataDirectory.resolve("reload.request");
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        pack.reload(null);
        proxy.getScheduler().buildTask(this, () -> pack.reload(null))
                .delay(1, TimeUnit.MINUTES)
                .repeat(1, TimeUnit.MINUTES)
                .schedule();
        proxy.getCommandManager().register(
                proxy.getCommandManager().metaBuilder("aepack").plugin(this).build(), new PackCommand(pack));
        proxy.getScheduler().buildTask(this, this::checkReloadRequest)
                .delay(5, TimeUnit.SECONDS)
                .repeat(5, TimeUnit.SECONDS)
                .schedule();
    }

    /** A {@code reload.request} file in the plugin folder runs {@code velocity reload} once, then goes away. */
    private void checkReloadRequest() {
        try {
            if (!java.nio.file.Files.deleteIfExists(reloadRequest)) {
                return;
            }
        } catch (java.io.IOException ex) {
            logger.warn("Could not clear {}: {}", reloadRequest, ex.getMessage());
            return;
        }
        logger.info("reload.request found: running velocity reload");
        proxy.getCommandManager().executeAsync(proxy.getConsoleCommandSource(), "velocity reload")
                .thenAccept(ok -> logger.info("velocity reload {}", ok ? "done" : "failed"));
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
