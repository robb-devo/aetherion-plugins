package de.aetherion.helm.gateway;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** One shared mapper and a few node helpers, so JSON stays terse everywhere else. */
public final class Json {
    public static final ObjectMapper M = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    private static final JsonNodeFactory F = JsonNodeFactory.instance;

    private Json() {}

    public static ObjectNode obj() { return F.objectNode(); }

    public static ArrayNode arr() { return F.arrayNode(); }

    public static String write(Object o) {
        try {
            return M.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static JsonNode read(String s) {
        try {
            return M.readTree(s);
        } catch (Exception e) {
            return null;
        }
    }

    public static String text(JsonNode n, String field, String def) {
        if (n == null) return def;
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? def : v.asText();
    }

    public static long num(JsonNode n, String field, long def) {
        if (n == null) return def;
        JsonNode v = n.get(field);
        return v == null || !v.isNumber() ? def : v.asLong();
    }

    public static double dbl(JsonNode n, String field, double def) {
        if (n == null) return def;
        JsonNode v = n.get(field);
        return v == null || !v.isNumber() ? def : v.asDouble();
    }
}
