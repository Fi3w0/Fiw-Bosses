package com.fiw.fiw_bosses;

import com.fiw.fiw_bosses.command.BossCommand;
import com.fiw.fiw_bosses.config.BossConfigLoader;
import com.fiw.fiw_bosses.config.MinionConfigLoader;
import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.entity.BossEntityRegistry;
import com.fiw.fiw_bosses.goal.BossGoalFactory;
import com.fiw.fiw_bosses.network.NetworkHandler;
import com.fiw.fiw_bosses.skin.SkinCache;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
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
        BossGoalFactory.init();
        NeoForge.EVENT_BUS.register(this);
        LOGGER.info("FIW Bosses (NeoForge port) initializing");
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(BossEntityRegistry.BOSS.get(), BossEntity.createBossAttributes().build());
        event.put(BossEntityRegistry.MINION.get(), BossEntity.createBossAttributes().build());
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        BossConfigLoader.loadAll();
        MinionConfigLoader.loadAll();
        SkinCache.fetchAll();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        BossCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof BossEntity boss
                && event.getEntity() instanceof ServerPlayer sp) {
            NetworkHandler.sendSkinToPlayer(sp, boss);
        }
    }
}
