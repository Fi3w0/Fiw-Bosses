package com.fiw.fiw_bosses;

import com.fiw.fiw_bosses.command.BossCommand;
import com.fiw.fiw_bosses.config.BossConfigLoader;
import com.fiw.fiw_bosses.config.MinionConfigLoader;
import com.fiw.fiw_bosses.core.FiwBossesCore;
import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.entity.BossEntityRegistry;
import com.fiw.fiw_bosses.goal.BossGoalFactory;
import com.fiw.fiw_bosses.integration.FiwToolsBridge;
import com.fiw.fiw_bosses.network.NetworkHandler;
import com.fiw.fiw_bosses.skin.SkinCache;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@Mod(FiwBossesCore.MOD_ID)
public class FiwBossesNeoForge {

    public FiwBossesNeoForge(IEventBus modBus, ModContainer container) {
        FiwBossesCore.LOGGER.info("FIW Bosses (NeoForge 1.21.8) initializing...");
        FiwBossesCore.init(() -> FMLPaths.CONFIGDIR.get().resolve(FiwBossesCore.MOD_ID));

        BossEntityRegistry.register(modBus);
        ModSounds.register(modBus);
        modBus.addListener(this::registerAttributes);
        modBus.addListener(this::commonSetup);
        BossGoalFactory.init();
        NeoForge.EVENT_BUS.register(this);

        FiwBossesCore.LOGGER.info("FIW Bosses (NeoForge 1.21.8) initialized.");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModRefs.BOSS = BossEntityRegistry.BOSS.get();
            ModRefs.MINION = BossEntityRegistry.MINION.get();
            ModRefs.DOMAIN_BREAK = ModSounds.DOMAIN_BREAK.get();
        });
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
        FiwToolsBridge.reportUnknownToolIds();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        BossCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof BossEntity boss
                && event.getEntity() instanceof ServerPlayer player) {
            NetworkHandler.sendSkinToPlayer(player, boss);
        }
    }
}
