package net.bdew.generators.legacy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the subset of TOML that Forge's config system wrote for the original mod: {@code [dotted.tables]},
 * {@code key = value} with integers, floats, booleans, quoted strings and (possibly multi-line) arrays, and
 * {@code #} comments. Tables come back as nested maps.
 */
public final class MiniToml {
    private MiniToml() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parse(String text) {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> current = root;
        String[] lines = text.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = stripComment(lines[i]).trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("[") && !line.startsWith("[[")) {
                current = root;
                for (String part : line.substring(1, line.lastIndexOf(']')).split("\\.")) {
                    String key = unquote(part.trim());
                    current = (Map<String, Object>) current.computeIfAbsent(key, k -> new LinkedHashMap<String, Object>());
                }
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            String key = unquote(line.substring(0, eq).trim());
            StringBuilder value = new StringBuilder(line.substring(eq + 1).trim());
            while (depth(value) > 0 && i + 1 < lines.length) value.append(' ').append(stripComment(lines[++i]).trim());
            current.put(key, value(value.toString().trim()));
        }
        return root;
    }

    private static int depth(CharSequence s) {
        int depth = 0;
        boolean quoted = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) quoted = !quoted;
            else if (!quoted && c == '[') depth++;
            else if (!quoted && c == ']') depth--;
        }
        return depth;
    }

    private static String stripComment(String line) {
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"' && (i == 0 || line.charAt(i - 1) != '\\')) quoted = !quoted;
            else if (c == '#' && !quoted) return line.substring(0, i);
        }
        return line;
    }

    private static Object value(String s) {
        if (s.startsWith("[")) return array(s.substring(1, s.lastIndexOf(']')));
        if (s.startsWith("\"")) return unquote(s);
        if (s.equals("true") || s.equals("false")) return Boolean.parseBoolean(s);
        String n = s.replace("_", "");
        try {
            if (n.matches("[+-]?\\d+")) return Long.parseLong(n);
            return Double.parseDouble(n);
        } catch (NumberFormatException e) {
            return s;
        }
    }

    private static List<Object> array(String body) {
        List<Object> result = new ArrayList<>();
        StringBuilder item = new StringBuilder();
        boolean quoted = false;
        int nested = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '"' && (i == 0 || body.charAt(i - 1) != '\\')) quoted = !quoted;
            if (!quoted && c == '[') nested++;
            if (!quoted && c == ']') nested--;
            if (c == ',' && !quoted && nested == 0) {
                addItem(result, item);
            } else item.append(c);
        }
        addItem(result, item);
        return result;
    }

    private static void addItem(List<Object> result, StringBuilder item) {
        String s = item.toString().trim();
        item.setLength(0);
        if (!s.isEmpty()) result.add(value(s));
    }

    private static String unquote(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\""))
            return s.substring(1, s.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        return s;
    }
}
