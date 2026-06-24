package com.fiw.fiw_bosses;

import com.fiw.fiw_bosses.core.FiwBossesCore;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class ModSounds {

    public static final SoundEvent DOMAIN_BREAK = register("domain_break");

    private ModSounds() {}

    private static SoundEvent register(String name) {
        Identifier id = Identifier.fromNamespaceAndPath(FiwBossesCore.MOD_ID, name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    public static void init() {
        ModRefs.DOMAIN_BREAK = DOMAIN_BREAK;
    }
}
