package de.aetherion.proxy;

import com.velocitypowered.api.command.SimpleCommand;

import java.util.List;

/** {@code aepack status | reload | push}: console and {@code aetherion.pack.admin} only. */
final class PackCommand implements SimpleCommand {

    private static final List<String> ACTIONS = List.of("status", "reload", "push");

    private final ResourcePack pack;

    PackCommand(ResourcePack pack) {
        this.pack = pack;
    }

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();
        String action = args.length == 0 ? "status" : args[0].toLowerCase(java.util.Locale.ROOT);
        switch (action) {
            case "reload" -> {
                invocation.source().sendPlainMessage("[aepack] reading pack.properties and the zip...");
                pack.reload(invocation.source());
            }
            case "push" -> invocation.source().sendPlainMessage("[aepack] sent to " + pack.pushAll() + " players");
            default -> invocation.source().sendPlainMessage("[aepack] " + pack.status());
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return invocation.arguments().length <= 1 ? ACTIONS : List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("aetherion.pack.admin");
    }
}
