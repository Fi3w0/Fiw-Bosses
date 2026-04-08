package com.fiw.fiw_bosses.entity;

import com.fiw.fiw_bosses.FiwBosses;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class BossEntityRegistry {

    public static final EntityType<BossEntity> BOSS_TYPE = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(FiwBosses.MOD_ID, "boss"),
            FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, BossEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.95f))
                    .trackRangeBlocks(128)
                    .trackedUpdateRate(2)
                    .build()
    );

    public static void register() {
        FabricDefaultAttributeRegistry.register(BOSS_TYPE, BossEntity.createBossAttributes());
    }
}
