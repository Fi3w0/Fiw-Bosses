package com.fiw.fiw_bosses;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class ModSounds {

    public static final SoundEvent DOMAIN_BREAK = register("domain_break");

    private static SoundEvent register(String name) {
        Identifier id = new Identifier(FiwBosses.MOD_ID, name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void init() {
        // Triggers static initialisation, registering all sound events above
    }
}
