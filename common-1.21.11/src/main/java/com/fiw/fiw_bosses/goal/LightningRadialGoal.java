package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.google.gson.JsonObject;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class LightningRadialGoal extends Goal {

    private static final int STATE_JUMP    = 0;
    private static final int STATE_CHANNEL = 1;
    private static final int STATE_FIRE    = 2;

    private static class BladeTracker {
        Vec3 pos;
        final Vec3 dir;
        double traveled;
        boolean done;
        final Set<UUID> hitSet = new HashSet<>();

        BladeTracker(Vec3 pos, Vec3 dir) {
            this.pos = pos;
            this.dir = dir;
        }
    }

    private final BossEntity boss;
    private final int    bladeCount;
    private final double bladeRange;
    private final float  damage;
    private final int    channelTime;
    private final double bladeSpeed;
    private final int    cooldown;

    private int     cooldownTimer;
    private int     tick;
    private int     state;
    private boolean active;

    private double jumpOriginY;
    private double bladeGroundY;
    private double spiralAngle;
    private List<BladeTracker> blades;

    public LightningRadialGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss        = boss;
        this.bladeCount  = params.has("bladeCount")  ? params.get("bladeCount").getAsInt()    : 20;
        this.bladeRange  = params.has("bladeRange")  ? params.get("bladeRange").getAsDouble() : 12.0;
        this.damage      = params.has("damage")      ? params.get("damage").getAsFloat()      : 16f;
        this.channelTime = params.has("channelTime") ? params.get("channelTime").getAsInt()   : 16;
        this.bladeSpeed  = params.has("bladeSpeed")  ? params.get("bladeSpeed").getAsDouble() : 1.5;
        this.cooldown    = cooldownTicks;
        this.blades      = new ArrayList<>();
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public void start() {
        tick         = 0;
        state        = STATE_JUMP;
        active       = true;
        blades.clear();
        spiralAngle  = 0.0;
        jumpOriginY  = boss.getY();

        boss.push(0, 0.6, 0);
        boss.hurtMarked = true;
        boss.setInvulnerable(true);

        if (!boss.level().isClientSide()) {
            ServerLevel level = (ServerLevel) boss.level();
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 1.5f, 1.5f);
        }
    }

    @Override
    public boolean canContinueToUse() {
        return active;
    }

    @Override
    public void tick() {
        tick++;
        spiralAngle += 18.0;
        if (boss.level().isClientSide()) return;
        ServerLevel level = (ServerLevel) boss.level();

        switch (state) {
            case STATE_JUMP    -> tickJump(level);
            case STATE_CHANNEL -> tickChannel(level);
            case STATE_FIRE    -> tickFire(level);
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
        active        = false;
        boss.setInvulnerable(false);
    }

    private void tickJump(ServerLevel level) {
        double cx = boss.getX();
        double cy = boss.getY() + 1.0;
        double cz = boss.getZ();

        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                cx, cy, cz, 4, 0.3, 0.3, 0.3, 0.1);
        level.sendParticles(ParticleTypes.END_ROD,
                cx, cy, cz, 2, 0.2, 0.2, 0.2, 0.05);

        if (tick >= 8 || boss.getDeltaMovement().y <= 0) {
            bladeGroundY = jumpOriginY + 0.3;
            boss.setInvulnerable(false);
            state = STATE_CHANNEL;
            tick  = 0;
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1.2f, 0.5f);
        }
    }

    private void tickChannel(ServerLevel level) {
        double cx = boss.getX();
        double cy = boss.getY();
        double cz = boss.getZ();

        double spiralR = 0.5 + (double) tick / channelTime * 2.5;

        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(i * 60.0 + spiralAngle);
            double px = cx + Math.cos(angle) * spiralR;
            double pz = cz + Math.sin(angle) * spiralR;
            double py = cy + 1.0;

            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    px, py, pz, 2, 0.1, 0.2, 0.1, 0.1);
            level.sendParticles(ParticleTypes.END_ROD,
                    px, py, pz, 1, 0, 0, 0, 0.02);
        }

        level.sendParticles(ParticleTypes.END_ROD,
                cx, cy + 1.0, cz, 3, 0.2, 0.3, 0.2, 0.05);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                cx, cy + 1.0, cz, 5, 0.3, 0.3, 0.3, 0.1);

        if (tick >= channelTime) {
            Vec3 origin = new Vec3(boss.getX(), bladeGroundY, boss.getZ());
            for (int i = 0; i < bladeCount; i++) {
                double angle = 2.0 * Math.PI * i / bladeCount;
                Vec3 dir = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                blades.add(new BladeTracker(origin, dir));
            }

            state = STATE_FIRE;
            tick  = 0;

            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 2.0f, 1.2f);
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.5f, 1.5f);
        }
    }

    private void tickFire(ServerLevel level) {
        boolean anyAlive = false;

        for (BladeTracker blade : blades) {
            if (blade.done) continue;
            anyAlive = true;

            int    steps    = Math.max(1, (int) Math.ceil(bladeSpeed));
            double stepDist = bladeSpeed / steps;

            for (int s = 0; s < steps; s++) {
                blade.pos      = blade.pos.add(blade.dir.scale(stepDist));
                blade.traveled += stepDist;

                double bx = blade.pos.x;
                double by = bladeGroundY;
                double bz = blade.pos.z;

                level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        bx, by + 0.2, bz, 2, 0.1, 0.1, 0.1, 0.08);
                level.sendParticles(ParticleTypes.END_ROD,
                        bx, by + 0.3, bz, 1, 0.05, 0.15, 0.05, 0.02);

                if (blade.traveled % 2.0 < stepDist) {
                    level.sendParticles(ParticleTypes.ASH,
                            bx, by + 0.5, bz, 3, 0.3, 0.3, 0.3, 0.05);
                }

                AABB hitBox = new AABB(
                        bx - 0.7, by - 0.5, bz - 0.7,
                        bx + 0.7, by + 2.5, bz + 0.7);
                List<Player> victims = level.getEntitiesOfClass(Player.class, hitBox,
                        p -> p.isAlive() && !blade.hitSet.contains(p.getUUID()));

                for (Player player : victims) {
                    if (blade.hitSet.contains(player.getUUID())) continue;
                    blade.hitSet.add(player.getUUID());
                    player.hurtServer(level, boss.damageSources().magic(), damage);
                    player.push(blade.dir.x * 1.2, 0.3, blade.dir.z * 1.2);
                    player.hurtMarked = true;
                    level.sendParticles(ParticleTypes.ASH,
                            player.getX(), player.getY() + 1.0, player.getZ(),
                            8, 0.3, 0.5, 0.3, 0.1);
                }

                if (blade.traveled >= bladeRange) {
                    blade.done = true;
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                            bx, by + 0.3, bz, 5, 0.3, 0.3, 0.3, 0.1);
                    break;
                }
            }
        }

        if (!anyAlive) {
            active = false;
        }
    }
}
