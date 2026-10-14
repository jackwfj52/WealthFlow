package org.jack.wealthflow.knowledge;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Deterministic text preparation; stored original text is never replaced by model summaries. */
public final class KnowledgeText {
    private static final Pattern TERMS = Pattern.compile("[a-zA-Z0-9._-]{2,40}|[\\p{IsHan}]{2,}");
    private KnowledgeText() {}
    public record Part(String section, String content) {}

    public static String hash(String content) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(content.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public static List<Part> split(String body, String title, String type) {
        String normalized = body.replace("\r\n", "\n").replace('\r', '\n').strip();
        if ("STRATEGY".equals(type) && normalized.length() <= 3000)
            return List.of(new Part(title, normalized));
        List<Part> result = new ArrayList<>();
        String section = title;
        StringBuilder current = new StringBuilder();
        for (String paragraph : normalized.split("\\n\\s*\\n")) {
            String value = paragraph.strip();
            if (value.isEmpty()) continue;
            if (value.startsWith("#")) {
                if (!current.isEmpty()) { result.add(new Part(section, current.toString().strip())); current.setLength(0); }
                String heading = value.lines().findFirst().orElse(title).replaceFirst("^#{1,6}\\s*", "");
                section = heading.substring(0,Math.min(120,heading.length()));
            }
            while (value.length() > 1600) {
                if (!current.isEmpty()) { result.add(new Part(section, current.toString().strip())); current.setLength(0); }
                int end = 1600;
                if (Character.isHighSurrogate(value.charAt(end - 1))) end--;
                result.add(new Part(section, value.substring(0, end)));
                value = value.substring(end);
            }
            if (current.length() + value.length() > 1600 && !current.isEmpty()) {
                result.add(new Part(section, current.toString().strip())); current.setLength(0);
            }
            if (!current.isEmpty()) current.append("\n\n");
            current.append(value);
        }
        if (!current.isEmpty()) result.add(new Part(section, current.toString().strip()));
        return List.copyOf(result);
    }

    public static List<String> terms(String query) {
        String text = query.toLowerCase(Locale.ROOT).replace("纳斯达克100", "纳指")
                .replace("nasdaq 100", "纳指").replace("nasdaq100", "纳指");
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (String core : List.of("纳指","定投","股票","债券","现金","基金","配置","再平衡","风险","复盘","应急","退休","长期","收益","指数"))
            if (text.contains(core)) terms.add(core);
        if (text.contains("纳指")) { terms.add("纳斯达克"); terms.add("nasdaq"); }
        var matcher = TERMS.matcher(text);
        while (matcher.find() && terms.size() < 80) {
            String term = matcher.group();
            if (term.codePoints().allMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN)) {
                if (term.length() <= 6) terms.add(term);
                for (int i = 0; i + 2 <= term.length() && terms.size() < 80; i++) terms.add(term.substring(i, i + 2));
                for (int i = 0; i + 3 <= term.length() && terms.size() < 80; i++) terms.add(term.substring(i, i + 3));
            } else terms.add(term);
        }
        if (text.contains("纳指")) { terms.add("纳指"); terms.add("纳斯达克"); terms.add("nasdaq"); }
        return List.copyOf(terms);
    }

    public static byte[] pack(float[] vector) {
        ByteBuffer bytes = ByteBuffer.allocate(vector.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : vector) bytes.putFloat(value);
        return bytes.array();
    }

    public static double cosine(float[] query, byte[] packed) {
        if (packed == null || packed.length != query.length * Float.BYTES) return -1;
        ByteBuffer values = ByteBuffer.wrap(packed).order(ByteOrder.LITTLE_ENDIAN);
        double dot = 0, qn = 0, vn = 0;
        for (float q : query) {
            float v = values.getFloat();
            if (!Float.isFinite(q) || !Float.isFinite(v)) return -1;
            dot += (double) q * v; qn += (double) q * q; vn += (double) v * v;
        }
        return qn == 0 || vn == 0 ? -1 : dot / Math.sqrt(qn * vn);
    }
}
