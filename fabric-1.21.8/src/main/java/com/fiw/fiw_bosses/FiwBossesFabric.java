package com.fiw.fiw_bosses;

import com.fiw.fiw_bosses.command.BossCommand;
import com.fiw.fiw_bosses.config.BossConfigLoader;
import com.fiw.fiw_bosses.config.MinionConfigLoader;
import com.fiw.fiw_bosses.core.FiwBossesCore;
import com.fiw.fiw_bosses.entity.BossEntityRegistry;
import com.fiw.fiw_bosses.goal.BossGoalFactory;
import com.fiw.fiw_bosses.integration.FiwToolsBridge;
import com.fiw.fiw_bosses.network.NetworkHandler;
import com.fiw.fiw_bosses.skin.SkinCache;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.loader.api.FabricLoader;

public class FiwBossesFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        FiwBossesCore.LOGGER.info("FIW Bosses (Fabric 1.21.8) initializing...");
        FiwBossesCore.init(() ->
                FabricLoader.getInstance().getConfigDir().resolve(FiwBossesCore.MOD_ID));

        ModSounds.init();
        BossEntityRegistry.register();
        BossGoalFactory.init();
        NetworkHandler.registerPayloadTypes();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                BossCommand.register(dispatcher));

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            BossConfigLoader.loadAll();
            MinionConfigLoader.loadAll();
            SkinCache.fetchAll();
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> FiwToolsBridge.reportUnknownToolIds());

        EntityTrackingEvents.START_TRACKING.register((entity, player) -> {
            if (entity instanceof com.fiw.fiw_bosses.entity.BossEntity boss) {
                NetworkHandler.sendSkinToPlayer(player, boss);
            }
        });

        FiwBossesCore.LOGGER.info("FIW Bosses (Fabric 1.21.8) initialized.");
    }
}
