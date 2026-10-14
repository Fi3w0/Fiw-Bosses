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
import com.fiw.fiw_bosses.summon.ScheduleRunner;
import com.fiw.fiw_bosses.summon.SummonHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

@Mod(FiwBossesCore.MOD_ID)
public class FiwBossesForge {

    public FiwBossesForge() {
        FiwBossesCore.LOGGER.info("FIW Bosses (Forge 1.20.1) initializing...");
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        FiwBossesCore.init(() -> FMLPaths.CONFIGDIR.get().resolve(FiwBossesCore.MOD_ID));

        BossEntityRegistry.register(modBus);
        ModSounds.register(modBus);
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::registerAttributes);
        BossGoalFactory.init();
        MinecraftForge.EVENT_BUS.register(this);

        FiwBossesCore.LOGGER.info("FIW Bosses (Forge 1.20.1) initialized.");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModRefs.BOSS = BossEntityRegistry.BOSS.get();
            ModRefs.MINION = BossEntityRegistry.MINION.get();
            ModRefs.DOMAIN_BREAK = ModSounds.DOMAIN_BREAK.get();
            NetworkHandler.register();
        });
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(BossEntityRegistry.BOSS.get(), BossEntity.createBossAttributes().build());
        event.put(BossEntityRegistry.MINION.get(), BossEntity.createBossAttributes().build());
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

    // Configs must be loaded before the worlds are: spawn and force-loaded chunks load their
    // entities during world load, and a boss whose definition isn't known yet is removed.
    @SubscribeEvent
    public void onServerAboutToStart(ServerAboutToStartEvent event) {
        BossConfigLoader.loadAll();
        MinionConfigLoader.loadAll();
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        SkinCache.fetchAll();
        FiwToolsBridge.reportUnknownToolIds();
    }

    // Scheduled world spawns (countdowns + spawn), checked once a second.
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) ScheduleRunner.onServerTick(event.getServer());
    }

    // Summon items / altars. Only the server summons; a used click is cancelled.
    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player
                && SummonHandler.onUseBlock(player, event.getHand(), event.getPos(), event.getHitVec().getDirection())) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player && SummonHandler.onUseItem(player, event.getHand())) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }
}
