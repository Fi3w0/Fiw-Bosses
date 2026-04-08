package com.fiw.fiw_bosses.goal;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.util.TextUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

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
        this.setFlags(EnumSet.noneOf(Flag.class));

        if (params.has("messages")) {
            JsonArray arr = params.getAsJsonArray("messages");
            for (int i = 0; i < arr.size(); i++) {
                messages.add(arr.get(i).getAsString());
            }
        }
    }

    @Override
    public boolean canUse() {
        if (messages.isEmpty()) return false;
        if (cooldownTimer > 0) { cooldownTimer--; return false; }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        if (boss.level().isClientSide || messages.isEmpty()) return;
        ServerLevel level = (ServerLevel) boss.level();

        String picked = messages.get(boss.getRandom().nextInt(messages.size()));
        var bossName = boss.getCustomName();
        Component line = Component.literal("[").withStyle(ChatFormatting.DARK_GRAY)
                .append(bossName != null ? bossName.copy() : Component.literal("Boss"))
                .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY))
                .append(TextUtil.parseColorCodes(picked));

        float rSq = radius * radius;
        for (var player : level.players()) {
            if (player.distanceToSqr(boss) <= rSq) {
                player.sendSystemMessage(line);
            }
        }
    }

    @Override
    public void stop() {
        cooldownTimer = cooldown;
    }
}
