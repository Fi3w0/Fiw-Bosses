package com.fiw.fiw_bosses.entity;

import com.fiw.fiw_bosses.core.FiwBossesCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BossEntityRegistry {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, FiwBossesCore.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<BossEntity>> BOSS =
            ENTITIES.register("boss", () -> EntityType.Builder
                    .of(BossEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(10)
                    .build(entityKey("boss")));

    public static final DeferredHolder<EntityType<?>, EntityType<MinionEntity>> MINION =
            ENTITIES.register("minion", () -> EntityType.Builder
                    .of(MinionEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(10)
                    .build(entityKey("minion")));

    private BossEntityRegistry() {}

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }

    private static ResourceKey<EntityType<?>> entityKey(String name) {
        return ResourceKey.create(Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(FiwBossesCore.MOD_ID, name));
    }
}
