package com.fiw.fiw_bosses.util;

/**
 * Small color helper. As of MC 1.21.5+, {@code DustParticleOptions} and the
 * colored particle options take a packed {@code 0xRRGGBB} int instead of a
 * {@code Vector3f}; this packs float RGB components (0..1) into that int.
 */
public final class Colors {

    private Colors() {}

    public static int rgb(float r, float g, float b) {
        int ri = Math.round(clamp(r) * 255f);
        int gi = Math.round(clamp(g) * 255f);
        int bi = Math.round(clamp(b) * 255f);
        return (ri << 16) | (gi << 8) | bi;
    }

    private static float clamp(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
