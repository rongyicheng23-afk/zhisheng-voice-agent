package com.wc.realtime;

import java.util.Objects;

/**
 * Tracks one active turn inside a realtime session.  The proxy uses this as a
 * small, dependency-free guard so that messages arriving after an interrupt
 * cannot be sent back to the browser.
 */
public final class TurnLifecycle {
    enum State { IDLE, ACTIVE, CANCELLED }

    private String turnId;
    private State state = State.IDLE;

    public synchronized boolean start(String candidateTurnId) {
        if (candidateTurnId == null || candidateTurnId.isBlank()) {
            return false;
        }
        if (state == State.ACTIVE && !Objects.equals(turnId, candidateTurnId)) {
            return false;
        }
        boolean started = state != State.ACTIVE;
        turnId = candidateTurnId;
        state = State.ACTIVE;
        return started;
    }

    public synchronized boolean interrupt(String candidateTurnId) {
        if (state != State.ACTIVE || !Objects.equals(turnId, candidateTurnId)) {
            return false;
        }
        state = State.CANCELLED;
        return true;
    }

    public synchronized boolean acceptsMessages() {
        return state != State.CANCELLED;
    }

    public synchronized String turnId() {
        return turnId;
    }
}
