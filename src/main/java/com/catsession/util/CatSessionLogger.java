package com.catsession.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Security-enforcing logger wrapper that guarantees zero leakage of credentials,
 * tokens, or sensitive payloads in accordance with strict privacy guidelines.
 */
public final class CatSessionLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger("CatSession");

    private CatSessionLogger() {}

    public static void info(String message) {
        LOGGER.info(CatSanitizer.sanitize(message));
    }

    public static void warn(String message) {
        LOGGER.warn(CatSanitizer.sanitize(message));
    }

    public static void error(String message) {
        LOGGER.error(CatSanitizer.sanitize(message));
    }

    public static void error(String message, Throwable throwable) {
        String cleanMessage = CatSanitizer.sanitize(message);
        LOGGER.error(cleanMessage, new SanitizedException(throwable));
    }

    private static class SanitizedException extends Exception {
        SanitizedException(Throwable cause) {
            super(cause != null ? CatSanitizer.sanitize(cause.getMessage()) : "Sanitized Exception", null);
            if (cause != null) {
                setStackTrace(cause.getStackTrace());
            }
        }
    }
}
