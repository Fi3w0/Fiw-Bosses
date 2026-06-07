package com.fiw.fiw_bosses;

import com.fiw.fiw_bosses.command.BossCommand;
import com.fiw.fiw_bosses.config.BossConfigLoader;
import com.fiw.fiw_bosses.config.MinionConfigLoader;
import com.fiw.fiw_bosses.entity.BossEntityRegistry;
import com.fiw.fiw_bosses.goal.BossGoalFactory;
import com.fiw.fiw_bosses.network.NetworkHandler;
import com.fiw.fiw_bosses.skin.SkinCache;
import com.fiw.fiw_bosses.integration.FiwToolsBridge;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FiwBosses implements ModInitializer {

    public static final String MOD_ID = "fiw_bosses";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("FIW Bosses initializing...");

        ModSounds.init();
        BossGoalFactory.init();
        BossEntityRegistry.register();
        BossConfigLoader.loadAll();
        MinionConfigLoader.loadAll();
        SkinCache.fetchAll();
        NetworkHandler.registerPayloadTypes();
        NetworkHandler.registerServerPackets();

        CommandRegistrationCallback.EVENT.register(BossCommand::register);

        // Once the server is up (and Fiw Tools has loaded its items), warn about any
        // toolId references in boss/minion configs that don't exist. No-op without Fiw Tools.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> FiwToolsBridge.reportUnknownToolIds());

        EntityTrackingEvents.START_TRACKING.register((entity, player) -> {
            if (entity instanceof com.fiw.fiw_bosses.entity.BossEntity bossEntity) {
                NetworkHandler.sendSkinToPlayer(player, bossEntity);
            }
        });

        LOGGER.info("FIW Bosses initialized!");
    }
}
