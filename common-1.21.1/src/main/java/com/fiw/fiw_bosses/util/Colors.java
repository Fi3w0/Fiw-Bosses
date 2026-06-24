package com.fiw.fiw_bosses.util;

import org.joml.Vector3f;

/**
 * Small color helper for MC 1.21.1 dust particles.
 */
public final class Colors {

    private Colors() {}

    public static Vector3f rgb(float r, float g, float b) {
        return new Vector3f(clamp(r), clamp(g), clamp(b));
    }

    private static float clamp(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
