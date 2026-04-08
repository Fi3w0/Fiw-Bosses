package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.util.TextUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Sends a random message from a list to nearby players.
 * Instant execution — no movement lock, no windup.
 *
 * JSON params:
 *   messages  (array, required)  list of message strings, supports & color codes
 *   radius    (float, 32.0)      broadcast radius in blocks
 *
 * Example:
 *   "params": {
 *     "messages": ["&cYou will fall!", "&4&lNone survive!", "&6Fear me!"],
 *     "radius": 40.0
 *   }
 */
public class RandomMessageGoal extends Goal {

    private final BossEntity boss;
    private final List<String> messages;
    private final float radius;
    private final int cooldown;
    private int cooldownTimer;

    public RandomMessageGoal(BossEntity boss, int cooldownTicks, JsonObject params) {
        this.boss     = boss;
        this.cooldown = cooldownTicks;
        this.radius   = params.has("radius") ? params.get("radius").getAsFloat() : 32.0f;
        this.messages = new ArrayList<>();
        this.cooldownTimer = 0;
        this.setControls(EnumSet.noneOf(Control.class));

        if (params.has("messages")) {
            JsonArray arr = params.getAsJsonArray("messages");
            for (int i = 0; i < arr.size(); i++) {
                messages.add(arr.get(i).getAsString());
            }
        }
    }

    @Override
    public boolean canStart() {
        if (messages.isEmpty()) return false;
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean shouldContinue() {
        return false;
    }

    @Override
    public void start() {
        if (boss.getWorld().isClient || messages.isEmpty()) return;
        ServerWorld world = (ServerWorld) boss.getWorld();

        String picked = messages.get(boss.getRandom().nextInt(messages.size()));
        var bossName = boss.getCustomName();
        Text line = Text.literal("[").formatted(Formatting.DARK_GRAY)
                .append(bossName != null ? bossName.copy() : Text.literal("Boss"))
                .append(Text.literal("] ").formatted(Formatting.DARK_GRAY))
                .append(TextUtil.parseColorCodes(picked));

        float rSq = radius * radius;
        for (var player : world.getPlayers()) {
            if (player.squaredDistanceTo(boss) <= rSq) {
                player.sendMessage(line, false);
            }
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
    }
}
