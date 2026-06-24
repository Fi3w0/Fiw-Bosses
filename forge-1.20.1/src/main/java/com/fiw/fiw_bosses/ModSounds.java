package com.fiw.fiw_bosses;

import com.fiw.fiw_bosses.core.FiwBossesCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, FiwBossesCore.MOD_ID);

    public static final RegistryObject<SoundEvent> DOMAIN_BREAK =
            SOUND_EVENTS.register("domain_break",
                    () -> SoundEvent.createVariableRangeEvent(
                            new ResourceLocation(FiwBossesCore.MOD_ID, "domain_break")));

    private ModSounds() {}

    public static void register(IEventBus modBus) {
        SOUND_EVENTS.register(modBus);
    }
}
