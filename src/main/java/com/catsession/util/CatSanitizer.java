package com.catsession.util;

import java.util.regex.Pattern;

/**
 * CatSanitizer ensures that sensitive tokens, access keys, or bearer credentials
 * are never leaked into logs, crash reports, exception messages, or UI debug output.
 */
public final class CatSanitizer {

    private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)bearer\\s+[a-zA-Z0-9_\\-\\.~+/]+=*");
    private static final Pattern JWT_PATTERN = Pattern.compile("eyJ[a-zA-Z0-9_-]{10,}\\.[a-zA-Z0-9_-]{10,}\\.[a-zA-Z0-9_-]{10,}");
    private static final Pattern TOKEN_PARAM_PATTERN = Pattern.compile("(?i)(access_token|refresh_token|token|secret)=([^&\\s]+)");

    private CatSanitizer() {}

    /**
     * Sanitizes any sensitive token pattern out of the given message.
     */
    public static String sanitize(String message) {
        if (message == null) return null;
        String result = BEARER_PATTERN.matcher(message).replaceAll("Bearer [REDACTED_SECRET]");
        result = JWT_PATTERN.matcher(result).replaceAll("[REDACTED_JWT_TOKEN]");
        result = TOKEN_PARAM_PATTERN.matcher(result).replaceAll("$1=[REDACTED]");
        return result;
    }

    /**
     * Masks an access token for display in UI (only revealing that a token is present).
     */
    public static String maskToken(String token) {
        if (token == null || token.isEmpty()) {
            return "None";
        }
        return "•••••••••••••••• (Securely Stored)";
    }
}
