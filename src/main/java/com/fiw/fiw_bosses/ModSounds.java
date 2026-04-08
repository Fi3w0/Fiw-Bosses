package com.fiw.fiw_bosses;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, FiwBosses.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> DOMAIN_BREAK =
            SOUND_EVENTS.register("domain_break",
                    () -> SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(FiwBosses.MOD_ID, "domain_break")));

    public static void register(IEventBus modBus) {
        SOUND_EVENTS.register(modBus);
    }
}
