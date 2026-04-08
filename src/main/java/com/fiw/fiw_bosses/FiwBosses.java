package com.fiw.fiw_bosses;

import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.entity.BossEntityRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(FiwBosses.MOD_ID)
public class FiwBosses {
    public static final String MOD_ID = "fiw_bosses";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public FiwBosses(IEventBus modBus, ModContainer container) {
        BossEntityRegistry.ENTITIES.register(modBus);
        ModSounds.register(modBus);
        modBus.addListener(this::registerAttributes);
        LOGGER.info("FIW Bosses (NeoForge port) initializing");
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(BossEntityRegistry.BOSS.get(), BossEntity.createBossAttributes().build());
    }
}
