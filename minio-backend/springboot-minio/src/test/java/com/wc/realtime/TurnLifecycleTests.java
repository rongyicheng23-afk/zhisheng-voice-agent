package com.wc.realtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TurnLifecycleTests {
    @Test
    void interruptRejectsLateMessagesForTheSameTurn() {
        TurnLifecycle lifecycle = new TurnLifecycle();

        assertTrue(lifecycle.start("turn-1"));
        assertTrue(lifecycle.acceptsMessages());
        assertTrue(lifecycle.interrupt("turn-1"));
        assertFalse(lifecycle.acceptsMessages());
        assertFalse(lifecycle.interrupt("turn-1"));
    }

    @Test
    void anotherTurnCannotInterruptTheActiveTurn() {
        TurnLifecycle lifecycle = new TurnLifecycle();

        lifecycle.start("turn-1");
        assertFalse(lifecycle.interrupt("turn-2"));
        assertTrue(lifecycle.acceptsMessages());
    }
}
