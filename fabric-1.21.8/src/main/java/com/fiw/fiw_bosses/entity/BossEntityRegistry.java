package com.fiw.fiw_bosses.entity;

import com.fiw.fiw_bosses.ModRefs;
import com.fiw.fiw_bosses.core.FiwBossesCore;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class BossEntityRegistry {

    private static final ResourceKey<EntityType<?>> BOSS_KEY = entityKey("boss");
    private static final ResourceKey<EntityType<?>> MINION_KEY = entityKey("minion");

    public static final EntityType<BossEntity> BOSS_TYPE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            BOSS_KEY,
            EntityType.Builder.<BossEntity>of(BossEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(128)
                    .updateInterval(2)
                    .build(BOSS_KEY)
    );

    public static final EntityType<MinionEntity> MINION_TYPE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            MINION_KEY,
            EntityType.Builder.<MinionEntity>of(MinionEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(128)
                    .updateInterval(2)
                    .build(MINION_KEY)
    );

    private BossEntityRegistry() {}

    public static void register() {
        ModRefs.BOSS = BOSS_TYPE;
        ModRefs.MINION = MINION_TYPE;
        FabricDefaultAttributeRegistry.register(BOSS_TYPE, BossEntity.createBossAttributes());
        FabricDefaultAttributeRegistry.register(MINION_TYPE, BossEntity.createBossAttributes());
    }

    private static ResourceKey<EntityType<?>> entityKey(String name) {
        return ResourceKey.create(Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(FiwBossesCore.MOD_ID, name));
    }
}
