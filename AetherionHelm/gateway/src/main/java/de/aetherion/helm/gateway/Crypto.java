package de.aetherion.helm.gateway;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Password hashing (PBKDF2-SHA256), TOTP (RFC 6238) and random tokens. JDK only, nothing to audit twice. */
public final class Crypto {
    public static final SecureRandom RNG = new SecureRandom();
    private static final int ITERATIONS = 600_000;
    private static final String B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Crypto() {}

    public static String token(int bytes) {
        byte[] b = new byte[bytes];
        RNG.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String hashPassword(String password) {
        byte[] salt = new byte[16];
        RNG.nextBytes(salt);
        byte[] dk = pbkdf2(password, salt, ITERATIONS);
        return "pbkdf2$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(dk);
    }

    public static boolean checkPassword(String password, String stored) {
        if (stored == null) return false;
        String[] p = stored.split("\\$");
        if (p.length != 4 || !p[0].equals("pbkdf2")) return false;
        byte[] salt = Base64.getDecoder().decode(p[2]);
        byte[] want = Base64.getDecoder().decode(p[3]);
        return MessageDigest.isEqual(pbkdf2(password, salt, Integer.parseInt(p[1])), want);
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Burns the same time as a real check, so unknown user names do not answer faster. */
    public static void dummyCheck() {
        pbkdf2("x", new byte[16], ITERATIONS);
    }

    // ---------------------------------------------------------------- TOTP

    public static String newTotpSecret() {
        byte[] b = new byte[20];
        RNG.nextBytes(b);
        return base32(b);
    }

    /** Returns the matched time step, or -1. Accepts one step of clock drift either way. */
    public static long verifyTotp(String secret, String code, long lastStep) {
        if (secret == null || code == null) return -1;
        code = code.replaceAll("\\s", "");
        if (!code.matches("\\d{6}")) return -1;
        long now = System.currentTimeMillis() / 1000 / 30;
        byte[] key = unbase32(secret);
        for (long step = now - 1; step <= now + 1; step++) {
            if (step <= lastStep) continue;   // a code is good exactly once
            if (MessageDigest.isEqual(totp(key, step).getBytes(), code.getBytes())) return step;
        }
        return -1;
    }

    static String totp(byte[] key, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int o = h[h.length - 1] & 0xf;
            int bin = ((h[o] & 0x7f) << 24) | ((h[o + 1] & 0xff) << 16) | ((h[o + 2] & 0xff) << 8) | (h[o + 3] & 0xff);
            return String.format("%06d", bin % 1_000_000);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String base32(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                sb.append(B32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) sb.append(B32.charAt((buffer << (5 - bits)) & 31));
        return sb.toString();
    }

    static byte[] unbase32(String s) {
        s = s.toUpperCase().replaceAll("[^A-Z2-7]", "");
        ByteBuffer out = ByteBuffer.allocate(s.length() * 5 / 8);
        int buffer = 0, bits = 0;
        for (char ch : s.toCharArray()) {
            buffer = (buffer << 5) | B32.indexOf(ch);
            bits += 5;
            if (bits >= 8) {
                out.put((byte) (buffer >> (bits - 8)));
                bits -= 8;
            }
        }
        return out.array();
    }

    /** Recovery codes like 7KQ9-VX3M: no 0/O/1/I so they read cleanly off paper. */
    public static String recoveryCode() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i == 4) sb.append('-');
            sb.append(alphabet.charAt(RNG.nextInt(alphabet.length())));
        }
        return sb.toString();
    }
}
