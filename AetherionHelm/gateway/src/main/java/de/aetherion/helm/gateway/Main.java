package de.aetherion.helm.gateway;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;

/**
 * java -jar helm-gateway.jar [--config helm.json] serve
 * java -jar helm-gateway.jar [--config helm.json] user add <name> <display> <role> [mcName]   (password on stdin)
 * java -jar helm-gateway.jar [--config helm.json] user passwd <name>                        (password on stdin)
 * java -jar helm-gateway.jar [--config helm.json] user reset-2fa <name>
 * java -jar helm-gateway.jar [--config helm.json] user list
 */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        Path cfgPath = Path.of("/opt/aetherion-helm/helm.json");
        int i = 0;
        if (args.length >= 2 && args[0].equals("--config")) {
            cfgPath = Path.of(args[1]);
            i = 2;
        }
        String cmd = args.length > i ? args[i] : "serve";
        Config cfg = Config.load(cfgPath);
        if (cmd.equals("serve")) {
            Helm h = new Helm(cfg);
            h.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                h.api.stop();
                h.agents.stop();
            }));
            Thread.currentThread().join();
            return;
        }
        if (!cmd.equals("user") || args.length <= i + 1) {
            System.err.println("usage: serve | user add|passwd|reset-2fa|list");
            System.exit(2);
        }
        Db db = new Db(Path.of(cfg.dataDir));
        Auth auth = new Auth(db);
        String sub = args[i + 1];
        switch (sub) {
            case "list" -> {
                for (Auth.User u : auth.users()) {
                    System.out.printf("%-14s %-14s %-10s 2FA=%s%n", u.username(), u.display(), u.role(), u.totp() ? "ja" : "nein");
                }
            }
            case "add" -> {
                String name = args[i + 2], display = args[i + 3], role = args[i + 4];
                String mc = args.length > i + 5 ? args[i + 5] : null;
                Long existing = db.one("SELECT id FROM users WHERE username=?", rs -> rs.getLong(1), name);
                if (existing != null) {
                    System.out.println("exists " + name);
                    return;
                }
                auth.create(name, display, role, mc, readPassword());
                System.out.println("created " + name);
            }
            case "passwd" -> {
                Long id = db.one("SELECT id FROM users WHERE username=?", rs -> rs.getLong(1), args[i + 2]);
                if (id == null) throw new IllegalArgumentException("unknown user");
                auth.setPassword(id, readPassword());
                System.out.println("password set for " + args[i + 2]);
            }
            case "reset-2fa" -> {
                Long id = db.one("SELECT id FROM users WHERE username=?", rs -> rs.getLong(1), args[i + 2]);
                if (id == null) throw new IllegalArgumentException("unknown user");
                auth.resetTotp(id);
                System.out.println("2FA reset for " + args[i + 2]);
            }
            default -> System.exit(2);
        }
    }

    private static String readPassword() throws Exception {
        String pw = new BufferedReader(new InputStreamReader(System.in)).readLine();
        if (pw == null) throw new IllegalArgumentException("password expected on stdin");
        return pw.trim();
    }
}
