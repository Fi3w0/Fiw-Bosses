package com.fiw.fiw_bosses.config;

/**
 * Config-driven water/lava behavior for bosses and custom minions.
 * When the whole block is absent from the JSON, vanilla behavior is kept.
 */
public class FluidSettings {
    // Never loses air underwater (no drowning).
    public boolean drownImmune = false;
    // Immune to fire/lava/burning damage.
    public boolean fireImmune = false;
    // false: sinks instead of swimming up — fights underwater.
    public boolean floats = true;
    // Movement multiplier while in water/lava. 1.0 = vanilla speed.
    public float swimSpeed = 1.0f;
    // false: water/lava currents don't push this entity.
    public boolean pushedByFluids = true;
    // true: pathfinding stops avoiding water, so it chases players into rivers.
    public boolean canSwim = false;
}
