package de.aetherion.helm.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Minecraft server list ping (status) for host-only servers without a Helm agent. */
public final class McPing {
    private McPing() {}

    public record Result(boolean ok, String version, String motd, int online, int max, long latencyMs, ArrayNode sample) {}

    public static Result ping(String host, int port, int timeoutMs) {
        if (port <= 0) return fail();
        long t0 = System.nanoTime();
        try (Socket sock = new Socket()) {
            sock.connect(new InetSocketAddress(host, port), timeoutMs);
            sock.setSoTimeout(timeoutMs);
            DataInputStream in = new DataInputStream(sock.getInputStream());
            DataOutputStream out = new DataOutputStream(sock.getOutputStream());
            writeHandshake(out, host, port);
            writePacket(out, new byte[]{0x00});
            int len = readVarInt(in);
            if (len <= 0 || len > 1_000_000) return fail();
            byte[] payload = in.readNBytes(len);
            if (payload.length == 0 || payload[0] != 0x00) return fail();
            String json = readString(new DataInputStream(new java.io.ByteArrayInputStream(payload, 1, payload.length - 1)));
            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
            return parse(json, ms);
        } catch (Exception e) {
            return fail();
        }
    }

    private static Result fail() {
        return new Result(false, null, null, 0, 0, 0, Json.arr());
    }

    private static Result parse(String json, long ms) {
        JsonNode n = Json.read(json);
        if (n == null || !n.isObject()) return fail();
        JsonNode desc = n.path("description");
        String motd = desc.isTextual() ? desc.asText() : desc.isObject() ? desc.path("text").asText("") : "";
        JsonNode ver = n.path("version");
        String version = ver.path("name").asText("");
        JsonNode players = n.path("players");
        int online = players.path("online").asInt(0);
        int max = players.path("max").asInt(0);
        ArrayNode sample = Json.arr();
        if (players.path("sample").isArray()) {
            for (JsonNode p : players.path("sample")) {
                ObjectNode o = Json.obj();
                o.put("name", p.path("name").asText(""));
                o.put("uuid", p.path("id").asText(""));
                sample.add(o);
            }
        }
        return new Result(true, version, motd, online, max, ms, sample);
    }

    private static void writeHandshake(DataOutputStream out, String host, int port) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream p = new DataOutputStream(buf);
        writeVarInt(p, 0x00);
        writeVarInt(p, 777);
        writeString(p, host);
        p.writeShort(port);
        writeVarInt(p, 1);
        writePacket(out, buf.toByteArray());
    }

    private static void writePacket(DataOutputStream out, byte[] payload) throws IOException {
        writeVarInt(out, payload.length);
        out.write(payload);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int num = 0, shift = 0;
        while (true) {
            int b = in.readUnsignedByte();
            num |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return num;
            shift += 7;
            if (shift > 28) throw new IOException("varint");
        }
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while (true) {
            if ((value & ~0x7F) == 0) {
                out.writeByte(value);
                return;
            }
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    private static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, b.length);
        out.write(b);
    }

    private static String readString(DataInputStream in) throws IOException {
        int len = readVarInt(in);
        if (len < 0 || len > 32767) throw new IOException("string len");
        return new String(in.readNBytes(len), StandardCharsets.UTF_8);
    }
}
