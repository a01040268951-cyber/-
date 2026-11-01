package com.ssaem.tabooword;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@Mod(TabooWord.MODID)
public class TabooWord {
    public static final String MODID = "tabooword";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TabooWord(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SYNCED, TabooConfig.SPEC);
        modEventBus.addListener(TabooNetwork::register);

        NeoForge.EVENT_BUS.addListener(TabooCommand::register);
        NeoForge.EVENT_BUS.addListener(TabooWord::onLogin);
        NeoForge.EVENT_BUS.addListener(Challenges::onEarn);
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TabooNetwork.sendStatus(player);
        }
    }
}
