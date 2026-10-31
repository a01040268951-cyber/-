package com.ssaem.tabooword;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(TabooWord.MODID)
public class TabooWord {
    public static final String MODID = "tabooword";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** 금기어를 말한 플레이어. 채팅이 먼저 보이도록 다음 틱 끝에 처리한다. */
    private record Violation(UUID playerId, char syllable) {
    }

    private static final Queue<Violation> PENDING = new ConcurrentLinkedQueue<>();

    public TabooWord(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, TabooConfig.SPEC);

        NeoForge.EVENT_BUS.addListener(TabooWord::onChat);
        NeoForge.EVENT_BUS.addListener(TabooWord::onServerTick);
        NeoForge.EVENT_BUS.addListener(TabooCommand::register);
    }

    private static void onChat(ServerChatEvent event) {
        if (!TabooConfig.ENABLED.getAsBoolean()) {
            return;
        }
        String text = event.getRawText();
        int index = HangulLetters.findViolation(text, TabooConfig.letters());
        if (index < 0) {
            return;
        }
        ServerPlayer player = event.getPlayer();
        PENDING.add(new Violation(player.getUUID(), text.charAt(index)));
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        Violation v;
        while ((v = PENDING.poll()) != null) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(v.playerId());
            if (player == null || !player.isAlive() || player.isSpectator()) {
                continue;
            }

            Component notice = Component.literal("[금기어] ").withStyle(ChatFormatting.RED)
                    .append(Component.literal(player.getGameProfile().name()).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" 사망 — ").withStyle(ChatFormatting.RED))
                    .append(Component.literal("'" + v.syllable() + "'").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("에 " + String.join(", ", TabooConfig.letters()) + " 포함").withStyle(ChatFormatting.RED));
            event.getServer().getPlayerList().broadcastSystemMessage(notice, false);

            ServerLevel level = (ServerLevel) player.level();
            player.hurtServer(level, player.damageSources().genericKill(), Float.MAX_VALUE);
            LOGGER.info("[tabooword] {} said '{}' and died", player.getGameProfile().name(), v.syllable());
        }
    }
}
