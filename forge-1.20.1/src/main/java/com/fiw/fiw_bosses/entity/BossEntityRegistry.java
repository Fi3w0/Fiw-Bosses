package com.fiw.fiw_bosses.entity;

import com.fiw.fiw_bosses.core.FiwBossesCore;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class BossEntityRegistry {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, FiwBossesCore.MOD_ID);

    public static final RegistryObject<EntityType<BossEntity>> BOSS =
            ENTITIES.register("boss", () -> EntityType.Builder
                    .<BossEntity>of(BossEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(10)
                    .build(FiwBossesCore.MOD_ID + ":boss"));

    public static final RegistryObject<EntityType<MinionEntity>> MINION =
            ENTITIES.register("minion", () -> EntityType.Builder
                    .<MinionEntity>of(MinionEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(10)
                    .build(FiwBossesCore.MOD_ID + ":minion"));

    private BossEntityRegistry() {}

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }
}
