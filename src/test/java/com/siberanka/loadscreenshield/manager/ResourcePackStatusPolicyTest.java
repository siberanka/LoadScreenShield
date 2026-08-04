package com.siberanka.loadscreenshield.manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePackStatusPolicyTest {

    @Test
    void classifiesEveryStatusAvailableThroughPaper262() {
        assertEquals(ResourcePackStatusPolicy.Outcome.WAITING,
                ResourcePackStatusPolicy.classify("ACCEPTED"));
        assertEquals(ResourcePackStatusPolicy.Outcome.WAITING,
                ResourcePackStatusPolicy.classify("DOWNLOADED"));
        assertEquals(ResourcePackStatusPolicy.Outcome.SUCCESS,
                ResourcePackStatusPolicy.classify("SUCCESSFULLY_LOADED"));
        assertEquals(ResourcePackStatusPolicy.Outcome.FAILURE,
                ResourcePackStatusPolicy.classify("DECLINED"));
        assertEquals(ResourcePackStatusPolicy.Outcome.FAILURE,
                ResourcePackStatusPolicy.classify("FAILED_DOWNLOAD"));
        assertEquals(ResourcePackStatusPolicy.Outcome.FAILURE,
                ResourcePackStatusPolicy.classify("INVALID_URL"));
        assertEquals(ResourcePackStatusPolicy.Outcome.FAILURE,
                ResourcePackStatusPolicy.classify("FAILED_RELOAD"));
        assertEquals(ResourcePackStatusPolicy.Outcome.FAILURE,
                ResourcePackStatusPolicy.classify("DISCARDED"));
    }

    @Test
    void futureAndMalformedStatusesFailClosed() {
        assertEquals(ResourcePackStatusPolicy.Outcome.UNKNOWN,
                ResourcePackStatusPolicy.classify("FUTURE_CLIENT_STAGE"));
        assertEquals(ResourcePackStatusPolicy.Outcome.UNKNOWN,
                ResourcePackStatusPolicy.classify(null));
    }

    @Test
    void cachedTerminalStatusPreventsLateJoinShieldActivation() {
        assertFalse(ResourcePackStatusPolicy.shouldActivateJoinShield("SUCCESSFULLY_LOADED"));
        assertFalse(ResourcePackStatusPolicy.shouldActivateJoinShield("DECLINED"));
        assertFalse(ResourcePackStatusPolicy.shouldActivateJoinShield("FAILED_RELOAD"));
        assertTrue(ResourcePackStatusPolicy.shouldActivateJoinShield("ACCEPTED"));
        assertTrue(ResourcePackStatusPolicy.shouldActivateJoinShield("FUTURE_CLIENT_STAGE"));
        assertTrue(ResourcePackStatusPolicy.shouldActivateJoinShield(null));
    }
}
