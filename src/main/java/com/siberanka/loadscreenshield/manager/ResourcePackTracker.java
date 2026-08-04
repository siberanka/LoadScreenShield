package com.siberanka.loadscreenshield.manager;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

final class ResourcePackTracker {

    private final Set<UUID> pendingPacks = new HashSet<>();
    private boolean waitingForInitialStatus;

    ResourcePackTracker(boolean waitingForInitialStatus) {
        this.waitingForInitialStatus = waitingForInitialStatus;
    }

    synchronized boolean record(UUID packId, ResourcePackStatusPolicy.Outcome outcome) {
        UUID safePackId = packId == null ? new UUID(0L, 0L) : packId;
        switch (outcome) {
            case WAITING, UNKNOWN -> pendingPacks.add(safePackId);
            case SUCCESS, FAILURE -> {
                waitingForInitialStatus = false;
                pendingPacks.remove(safePackId);
            }
        }
        return !waitingForInitialStatus && pendingPacks.isEmpty()
                && (outcome == ResourcePackStatusPolicy.Outcome.SUCCESS
                || outcome == ResourcePackStatusPolicy.Outcome.FAILURE);
    }
}
