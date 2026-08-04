package com.siberanka.loadscreenshield.manager;

import java.util.Locale;
import java.util.Set;

/**
 * Classifies status names instead of switching on enum constants so a future
 * server can add a status without causing unsafe or linkage-sensitive behavior.
 */
public final class ResourcePackStatusPolicy {

    private static final Set<String> WAITING = Set.of("ACCEPTED", "DOWNLOADED");
    private static final Set<String> FAILURE = Set.of(
            "DECLINED", "FAILED_DOWNLOAD", "INVALID_URL", "FAILED_RELOAD", "DISCARDED"
    );

    private ResourcePackStatusPolicy() {
    }

    public static Outcome classify(String statusName) {
        if (statusName == null) {
            return Outcome.UNKNOWN;
        }
        String normalized = statusName.toUpperCase(Locale.ROOT);
        if ("SUCCESSFULLY_LOADED".equals(normalized)) {
            return Outcome.SUCCESS;
        }
        if (WAITING.contains(normalized)) {
            return Outcome.WAITING;
        }
        if (FAILURE.contains(normalized)) {
            return Outcome.FAILURE;
        }
        return Outcome.UNKNOWN;
    }

    public enum Outcome {
        WAITING,
        SUCCESS,
        FAILURE,
        UNKNOWN
    }
}
