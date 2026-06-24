package com.fiw.fiw_bosses;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.entity.MinionEntity;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;

/**
 * Loader-neutral holder for registered game objects. Each loader registers its
 * entity types and sounds in its own way (Fabric {@code Registry.register},
 * NeoForge {@code DeferredRegister}) and then populates these fields, so the
 * shared {@code common} code can reference them without any loader API.
 */
public final class ModRefs {

    public static EntityType<BossEntity> BOSS;
    public static EntityType<MinionEntity> MINION;
    public static SoundEvent DOMAIN_BREAK;

    private ModRefs() {}
}
