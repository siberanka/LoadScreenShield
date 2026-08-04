package com.siberanka.loadscreenshield.manager;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePackTrackerTest {

    @Test
    void joinShieldClosesOnlyAfterTheInitialPackReachesATerminalStatus() {
        ResourcePackTracker tracker = new ResourcePackTracker(true);
        UUID pack = UUID.randomUUID();

        assertFalse(tracker.record(pack, ResourcePackStatusPolicy.Outcome.WAITING));
        assertTrue(tracker.record(pack, ResourcePackStatusPolicy.Outcome.SUCCESS));
    }

    @Test
    void multiplePacksMustAllReachATerminalStatus() {
        ResourcePackTracker tracker = new ResourcePackTracker(false);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertFalse(tracker.record(first, ResourcePackStatusPolicy.Outcome.WAITING));
        assertFalse(tracker.record(second, ResourcePackStatusPolicy.Outcome.WAITING));
        assertFalse(tracker.record(first, ResourcePackStatusPolicy.Outcome.SUCCESS));
        assertTrue(tracker.record(second, ResourcePackStatusPolicy.Outcome.SUCCESS));
    }

    @Test
    void duplicateTerminalCallbacksAreIdempotentForPendingState() {
        ResourcePackTracker tracker = new ResourcePackTracker(false);
        UUID pack = UUID.randomUUID();

        assertFalse(tracker.record(pack, ResourcePackStatusPolicy.Outcome.WAITING));
        assertTrue(tracker.record(pack, ResourcePackStatusPolicy.Outcome.FAILURE));
        assertTrue(tracker.record(pack, ResourcePackStatusPolicy.Outcome.FAILURE));
    }

    @Test
    void unknownFutureStatusKeepsThePackPending() {
        ResourcePackTracker tracker = new ResourcePackTracker(false);
        UUID pack = UUID.randomUUID();

        assertFalse(tracker.record(pack, ResourcePackStatusPolicy.Outcome.UNKNOWN));
        assertTrue(tracker.record(pack, ResourcePackStatusPolicy.Outcome.SUCCESS));
    }
}
