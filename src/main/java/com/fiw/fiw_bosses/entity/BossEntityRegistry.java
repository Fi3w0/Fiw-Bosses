package com.fiw.fiw_bosses.entity;

import com.fiw.fiw_bosses.FiwBosses;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class BossEntityRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, FiwBosses.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<BossEntity>> BOSS =
            ENTITIES.register("boss", () -> EntityType.Builder
                    .of(BossEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(10)
                    .build("boss"));
}
