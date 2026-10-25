package de.aetherion.helm.gateway;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * helm.json, written by the deploy script. Server folders, ports and the agent token live only there, never in git.
 */
public final class Config {
    public record ServerDef(String id, String name, String role, String kind, int port, String xmx, String dir) {}

    public final Path file;
    public String bind = "127.0.0.1";
    public int port = 8787;          // public side, reached only through the Cloudflare tunnel
    public int agentPort = 8788;     // plugins connect here, never routed by the tunnel
    public String agentToken = "";
    public String publicOrigin = "https://panel.donnernet.de";
    public String dataDir = "/opt/aetherion-helm/data";
    public String webDir = "/opt/aetherion-helm/web";
    public List<String> ctl = List.of("sudo", "-n", "/opt/aetherion-helm/bin/helm-ctl");
    public String timezone = "Europe/Berlin";
    public boolean trustCloudflare = true;
    public boolean secureCookies = true;
    public final List<ServerDef> servers = new ArrayList<>();

    private Config(Path file) {
        this.file = file;
    }

    public static Config load(Path file) throws Exception {
        Config c = new Config(file);
        JsonNode n = Json.M.readTree(Files.readString(file));
        c.bind = Json.text(n, "bind", c.bind);
        c.port = (int) Json.num(n, "port", c.port);
        c.agentPort = (int) Json.num(n, "agentPort", c.agentPort);
        c.agentToken = Json.text(n, "agentToken", "");
        c.publicOrigin = Json.text(n, "publicOrigin", c.publicOrigin);
        c.dataDir = Json.text(n, "dataDir", c.dataDir);
        c.webDir = Json.text(n, "webDir", c.webDir);
        c.timezone = Json.text(n, "timezone", c.timezone);
        if (n.has("trustCloudflare")) c.trustCloudflare = n.get("trustCloudflare").asBoolean();
        if (n.has("secureCookies")) c.secureCookies = n.get("secureCookies").asBoolean();
        if (n.has("ctl") && n.get("ctl").isArray()) {
            List<String> ctl = new ArrayList<>();
            n.get("ctl").forEach(x -> ctl.add(x.asText()));
            c.ctl = ctl;
        }
        if (n.has("servers")) {
            for (JsonNode s : n.get("servers")) {
                c.servers.add(new ServerDef(
                        Json.text(s, "id", "?"), Json.text(s, "name", Json.text(s, "id", "?")),
                        Json.text(s, "role", ""), Json.text(s, "kind", "paper"),
                        (int) Json.num(s, "port", 0), Json.text(s, "xmx", ""), Json.text(s, "dir", "")));
            }
        }
        if (c.agentToken.length() < 24) throw new IllegalStateException("agentToken in " + file + " is missing or too short");
        return c;
    }

    public ServerDef server(String id) {
        for (ServerDef s : servers) if (s.id().equals(id)) return s;
        return null;
    }
}
