package com.fiw.fiw_bosses.fight;

/**
 * Detects an entity that stays in place (or makes no progress toward its target) for too
 * long. Fed every sample interval; MC-free so it is unit-tested.
 */
public final class StuckTracker {

    private boolean anchored;
    private double ax, ay, az;
    private double anchorTargetDist;
    private long anchorTick;
    private int occurrences;

    /** Forget the current window (call when the entity isn't eligible right now). */
    public void reset() {
        anchored = false;
    }

    /** Forget everything, including how often it got stuck (new attempt). */
    public void resetAll() {
        anchored = false;
        occurrences = 0;
    }

    public int occurrences() {
        return occurrences;
    }

    /**
     * Position-based: true once the entity stayed within {@code radius} of one spot for
     * {@code windowTicks}. Starts a fresh window after firing.
     */
    public boolean samplePosition(long tick, double x, double y, double z, double radius, long windowTicks) {
        if (!anchored || distSq(x, y, z) > radius * radius) {
            anchor(tick, x, y, z, 0);
            return false;
        }
        return fireIfDue(tick, x, y, z, 0, windowTicks);
    }

    /**
     * Progress-based: true once the distance to the target did not shrink by at least
     * {@code minProgress} within {@code windowTicks}.
     */
    public boolean sampleProgress(long tick, double x, double y, double z, double targetDist,
                                  double minProgress, long windowTicks) {
        if (!anchored || anchorTargetDist - targetDist >= minProgress) {
            anchor(tick, x, y, z, targetDist);
            return false;
        }
        return fireIfDue(tick, x, y, z, targetDist, windowTicks);
    }

    private boolean fireIfDue(long tick, double x, double y, double z, double targetDist, long windowTicks) {
        if (tick - anchorTick < windowTicks) return false;
        occurrences++;
        anchor(tick, x, y, z, targetDist);
        return true;
    }

    private void anchor(long tick, double x, double y, double z, double targetDist) {
        anchored = true;
        ax = x; ay = y; az = z;
        anchorTargetDist = targetDist;
        anchorTick = tick;
    }

    private double distSq(double x, double y, double z) {
        double dx = x - ax, dy = y - ay, dz = z - az;
        return dx * dx + dy * dy + dz * dz;
    }
}
