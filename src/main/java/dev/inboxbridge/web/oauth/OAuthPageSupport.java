package dev.inboxbridge.web.oauth;

import java.util.LinkedHashMap;
import java.util.Map;

public final class OAuthPageSupport {

    private OAuthPageSupport() {
    }

    public static String localized(String language, String english, String portuguese) {
        String normalized = OAuthPageI18n.normalize(language);
        if ("pt-PT".equals(normalized) || "pt-BR".equals(normalized)) {
            return portuguese;
        }
        return OAuthPageI18n.text(language, english);
    }

    public static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    public static String jsString(String value) {
        if (value == null) {
            return "null";
        }
        String escaped = value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
        return "'" + escaped + "'";
    }

    public static String js(String value) {
        return "'" + escapeJs(value) + "'";
    }

    public static Map<String, String> orderedFields(String... values) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            fields.put(values[i], values[i + 1]);
        }
        return fields;
    }

    private static String escapeJs(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("'", "\\'");
    }
}
