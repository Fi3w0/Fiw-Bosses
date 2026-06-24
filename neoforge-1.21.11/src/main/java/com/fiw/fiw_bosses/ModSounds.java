package com.fiw.fiw_bosses;

import com.fiw.fiw_bosses.core.FiwBossesCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, FiwBossesCore.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> DOMAIN_BREAK =
            SOUND_EVENTS.register("domain_break",
                    () -> SoundEvent.createVariableRangeEvent(
                            Identifier.fromNamespaceAndPath(FiwBossesCore.MOD_ID, "domain_break")));

    private ModSounds() {}

    public static void register(IEventBus modBus) {
        SOUND_EVENTS.register(modBus);
    }
}
