import { randomUUID } from "node:crypto";
import { createServer } from "node:http";
import { WebSocketServer } from "ws";
import { HUB_LANDMARKS, SPAWN, accountLevelOf, title } from "@aetherion/game-core";
import { DEFAULT_PORT, } from "@aetherion/protocol";
const MOVE_SPEED = 8;
const TICK_HZ = 20;
const players = new Map();
function send(ws, msg) {
    if (ws.readyState === ws.OPEN) {
        ws.send(JSON.stringify(msg));
    }
}
function broadcastState(tick) {
    const snapshot = [...players.values()].map((p) => ({
        id: p.id,
        name: p.name,
        x: p.x,
        y: p.y,
        z: p.z,
        yaw: p.yaw,
    }));
    for (const p of players.values()) {
        send(p.ws, {
            type: "state",
            tick,
            you: { id: p.id, x: p.x, y: p.y, z: p.z, yaw: p.yaw },
            others: snapshot.filter((o) => o.id !== p.id),
        });
    }
}
function stepPlayer(p, dt) {
    const yaw = p.yaw;
    const fx = -Math.sin(yaw);
    const fz = -Math.cos(yaw);
    const rx = Math.cos(yaw);
    const rz = -Math.sin(yaw);
    const mx = fx * p.forward + rx * p.strafe;
    const mz = fz * p.forward + rz * p.strafe;
    const len = Math.hypot(mx, mz) || 1;
    p.x += (mx / len) * MOVE_SPEED * dt * (p.forward || p.strafe ? 1 : 0);
    p.z += (mz / len) * MOVE_SPEED * dt * (p.forward || p.strafe ? 1 : 0);
    p.y = 0;
}
const httpServer = createServer((_req, res) => {
    res.writeHead(200, { "content-type": "text/plain" });
    res.end("Aetherion Online prototype server\n");
});
const wss = new WebSocketServer({ server: httpServer });
wss.on("connection", (ws) => {
    const id = randomUUID();
    let player;
    ws.on("message", (raw) => {
        let msg;
        try {
            msg = JSON.parse(String(raw));
        }
        catch {
            return;
        }
        if (msg.type === "hello") {
            const name = (msg.name || "Wanderer").slice(0, 24);
            player = {
                id,
                name,
                x: SPAWN.x,
                y: SPAWN.y,
                z: SPAWN.z,
                yaw: 0,
                forward: 0,
                strafe: 0,
                ws,
            };
            players.set(id, player);
            const demoXp = 250n;
            const level = accountLevelOf(demoXp);
            send(ws, {
                type: "welcome",
                id,
                spawn: { x: SPAWN.x, y: SPAWN.y, z: SPAWN.z },
                landmarks: HUB_LANDMARKS.map(({ id: lid, name: n, x, y, z, color }) => ({
                    id: lid,
                    name: n,
                    x,
                    y,
                    z,
                    color,
                })),
                level,
                title: title(level),
            });
            return;
        }
        if (!player)
            return;
        if (msg.type === "input") {
            player.forward = Math.max(-1, Math.min(1, msg.forward));
            player.strafe = Math.max(-1, Math.min(1, msg.strafe));
            player.yaw = msg.yaw;
            const dt = Math.max(0, Math.min(0.1, msg.dt || 1 / TICK_HZ));
            stepPlayer(player, dt);
        }
    });
    ws.on("close", () => {
        players.delete(id);
    });
});
let tick = 0;
setInterval(() => {
    tick++;
    broadcastState(tick);
}, 1000 / TICK_HZ);
const port = Number(process.env.PORT || DEFAULT_PORT);
httpServer.listen(port, () => {
    console.log(`Aetherion Online server on ws://localhost:${port}`);
});
