package com.ssaem.tabooword.client;

import com.ssaem.tabooword.TabooStatus;
import com.ssaem.tabooword.TabooWord;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * 서버가 금기어 게임을 켜면 마이크 듣기를 시작하고, 끄거나 서버를 나가면 멈춘다.
 * 서버에 이 모드가 없으면 마이크는 절대 켜지지 않는다.
 */
@Mod(value = TabooWord.MODID, dist = Dist.CLIENT)
public class TabooWordClient {
    private static final VoiceListener LISTENER = new VoiceListener();

    public TabooWordClient(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(TabooWordClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(TabooWordClient::onLogout);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        boolean inGame = mc.player != null && mc.getConnection() != null;
        boolean shouldListen = inGame && TabooStatus.enabled();

        if (shouldListen && !LISTENER.isRunning()) {
            LISTENER.start();
        } else if (!shouldListen && LISTENER.isRunning()) {
            LISTENER.stop();
        }
        if (inGame) {
            LISTENER.setLocalAlive(mc.player.isAlive());
        }
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        TabooStatus.reset();
        LISTENER.stop();
        LISTENER.clearFailure();
    }
}
