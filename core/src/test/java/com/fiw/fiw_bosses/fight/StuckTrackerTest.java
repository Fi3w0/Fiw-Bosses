package com.fiw.fiw_bosses.fight;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StuckTrackerTest {

    @Test
    void firesAfterStandingStillForTheWindow() {
        StuckTracker t = new StuckTracker();
        assertFalse(t.samplePosition(0, 0, 64, 0, 1.5, 160));
        assertFalse(t.samplePosition(100, 0.5, 64, 0, 1.5, 160));
        assertTrue(t.samplePosition(160, 1.0, 64, 0, 1.5, 160));
        assertEquals(1, t.occurrences());
        // A new window starts after firing.
        assertFalse(t.samplePosition(200, 1.0, 64, 0, 1.5, 160));
        assertTrue(t.samplePosition(320, 1.0, 64, 0, 1.5, 160));
        assertEquals(2, t.occurrences());
    }

    @Test
    void movingAwayRestartsTheWindow() {
        StuckTracker t = new StuckTracker();
        t.samplePosition(0, 0, 64, 0, 1.5, 160);
        assertFalse(t.samplePosition(150, 3, 64, 0, 1.5, 160));
        assertFalse(t.samplePosition(300, 3, 64, 0, 1.5, 160));
        assertTrue(t.samplePosition(310, 3, 64, 0, 1.5, 160));
    }

    @Test
    void progressTowardTargetIsNotStuck() {
        StuckTracker t = new StuckTracker();
        t.sampleProgress(0, 0, 64, 0, 20, 2, 100);
        assertFalse(t.sampleProgress(60, 5, 64, 0, 17, 2, 100));
        // Circling: moves but distance stays ~17.
        assertFalse(t.sampleProgress(120, 1, 64, 5, 17.5, 2, 100));
        assertTrue(t.sampleProgress(160, 4, 64, 2, 16.5, 2, 100));
    }

    @Test
    void resetAllClearsOccurrences() {
        StuckTracker t = new StuckTracker();
        t.samplePosition(0, 0, 0, 0, 1, 10);
        t.samplePosition(10, 0, 0, 0, 1, 10);
        t.resetAll();
        assertEquals(0, t.occurrences());
    }
}
