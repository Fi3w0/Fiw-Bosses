package com.fiw.fiw_bosses.entity;

import com.fiw.fiw_bosses.FiwBosses;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public class BossEntityRegistry {

    private static final RegistryKey<EntityType<?>> BOSS_KEY =
            RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(FiwBosses.MOD_ID, "boss"));
    private static final RegistryKey<EntityType<?>> MINION_KEY =
            RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(FiwBosses.MOD_ID, "minion"));

    public static final EntityType<BossEntity> BOSS_TYPE = Registry.register(
            Registries.ENTITY_TYPE,
            BOSS_KEY,
            EntityType.Builder.<BossEntity>create(BossEntity::new, SpawnGroup.MONSTER)
                    .dimensions(0.6f, 1.95f)
                    .maxTrackingRange(128)
                    .trackingTickInterval(2)
                    .build(BOSS_KEY)
    );

    public static final EntityType<MinionEntity> MINION_TYPE = Registry.register(
            Registries.ENTITY_TYPE,
            MINION_KEY,
            EntityType.Builder.<MinionEntity>create(MinionEntity::new, SpawnGroup.MONSTER)
                    .dimensions(0.6f, 1.95f)
                    .maxTrackingRange(128)
                    .trackingTickInterval(2)
                    .build(MINION_KEY)
    );

    public static void register() {
        FabricDefaultAttributeRegistry.register(BOSS_TYPE, BossEntity.createBossAttributes());
        FabricDefaultAttributeRegistry.register(MINION_TYPE, BossEntity.createBossAttributes());
    }
}
