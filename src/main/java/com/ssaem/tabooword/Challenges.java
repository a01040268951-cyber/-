package com.ssaem.tabooword;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;

/**
 * 랜덤 도전과제: 마인크래프트 발전과제 중 하나를 뽑아 주고, 달성하면 자동으로 알린다.
 *  - 모두 같은 과제 (먼저 달성한 사람이 승리)
 *  - 각자 다른 과제
 * 뽑기 대상: 아이콘이 있고 숨겨지지 않은 일반/목표 발전과제 (아주 어려운 "도전" 등급과 탭 첫 과제는 제외),
 *            이미 달성한 과제는 제외.
 */
public final class Challenges {
    private static final Random RANDOM = new Random();

    private static AdvancementHolder shared;
    private static final Map<UUID, AdvancementHolder> PER_PLAYER = new HashMap<>();

    private Challenges() {
    }

    private static boolean eligible(AdvancementHolder holder) {
        if (holder.value().parent().isEmpty()) {
            return false; // 탭의 첫 과제 (예: "Minecraft")
        }
        DisplayInfo display = holder.value().display().orElse(null);
        return display != null && !display.isHidden() && display.getType() != AdvancementType.CHALLENGE;
    }

    private static List<AdvancementHolder> candidates(MinecraftServer server, List<ServerPlayer> players) {
        List<AdvancementHolder> list = new ArrayList<>();
        for (AdvancementHolder holder : server.getAdvancements().getAllAdvancements()) {
            if (!eligible(holder)) {
                continue;
            }
            boolean doneBySomeone = false;
            for (ServerPlayer p : players) {
                doneBySomeone |= p.getAdvancements().getOrStartProgress(holder).isDone();
            }
            if (!doneBySomeone) {
                list.add(holder);
            }
        }
        return list;
    }

    /** 모두에게 같은 과제. @return 뽑힌 과제 수 (0이면 후보 없음) */
    public static int drawShared(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        List<AdvancementHolder> list = candidates(server, players);
        if (list.isEmpty()) {
            return 0;
        }
        PER_PLAYER.clear();
        shared = list.get(RANDOM.nextInt(list.size()));
        DisplayInfo d = shared.value().display().orElseThrow();
        broadcast(server, prefix().append(Component.literal("모두의 도전과제: ").withStyle(ChatFormatting.WHITE))
                .append(title(d)).append(Component.literal(" — 먼저 달성하는 사람이 승리!").withStyle(ChatFormatting.GRAY)));
        broadcast(server, Component.literal("   ").append(d.getDescription().copy().withStyle(ChatFormatting.GRAY)));
        for (ServerPlayer p : players) {
            showTitle(p, Component.literal("도전과제!").withStyle(ChatFormatting.GOLD), title(d));
        }
        return 1;
    }

    /** 각자 다른 과제. @return 과제를 받은 사람 수 */
    public static int drawEach(MinecraftServer server) {
        shared = null;
        PER_PLAYER.clear();
        int count = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            List<AdvancementHolder> list = candidates(server, List.of(p));
            if (list.isEmpty()) {
                continue;
            }
            AdvancementHolder pick = list.get(RANDOM.nextInt(list.size()));
            PER_PLAYER.put(p.getUUID(), pick);
            DisplayInfo d = pick.value().display().orElseThrow();
            broadcast(server, prefix().append(Component.literal(name(p) + ": ").withStyle(ChatFormatting.WHITE))
                    .append(title(d)).append(Component.literal(" — ").withStyle(ChatFormatting.GRAY))
                    .append(d.getDescription().copy().withStyle(ChatFormatting.GRAY)));
            showTitle(p, Component.literal("내 도전과제").withStyle(ChatFormatting.GOLD), title(d));
            count++;
        }
        return count;
    }

    public static void stop(MinecraftServer server) {
        shared = null;
        PER_PLAYER.clear();
        broadcast(server, prefix().append(Component.literal("도전과제를 취소했습니다.").withStyle(ChatFormatting.GRAY)));
    }

    /** 현재 진행 중인 과제 설명 */
    public static List<Component> describe(MinecraftServer server) {
        List<Component> lines = new ArrayList<>();
        if (shared != null) {
            DisplayInfo d = shared.value().display().orElseThrow();
            lines.add(prefix().append(Component.literal("모두의 도전과제: ").withStyle(ChatFormatting.WHITE)).append(title(d))
                    .append(Component.literal(" — ").withStyle(ChatFormatting.GRAY)).append(d.getDescription().copy().withStyle(ChatFormatting.GRAY)));
        }
        for (Map.Entry<UUID, AdvancementHolder> e : PER_PLAYER.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            DisplayInfo d = e.getValue().value().display().orElseThrow();
            lines.add(prefix().append(Component.literal((p != null ? name(p) : "(나간 플레이어)") + ": ").withStyle(ChatFormatting.WHITE))
                    .append(title(d)).append(Component.literal(" — ").withStyle(ChatFormatting.GRAY))
                    .append(d.getDescription().copy().withStyle(ChatFormatting.GRAY)));
        }
        if (lines.isEmpty()) {
            lines.add(prefix().append(Component.literal("진행 중인 도전과제가 없어요. /taboo challenge 로 뽑으세요.").withStyle(ChatFormatting.GRAY)));
        }
        return lines;
    }

    static void onEarn(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        AdvancementHolder earned = event.getAdvancement();
        boolean win = false;
        if (shared != null && shared.id().equals(earned.id())) {
            shared = null;
            win = true;
        }
        AdvancementHolder mine = PER_PLAYER.get(player.getUUID());
        if (mine != null && mine.id().equals(earned.id())) {
            PER_PLAYER.remove(player.getUUID());
            win = true;
        }
        if (!win) {
            return;
        }
        DisplayInfo d = earned.value().display().orElseThrow();
        broadcast(server, prefix().append(Component.literal(name(player) + " 도전과제 성공! ").withStyle(ChatFormatting.GREEN))
                .append(title(d)));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            showTitle(p, Component.literal(name(player) + " 성공!").withStyle(ChatFormatting.GREEN), title(d));
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
    }

    private static MutableComponent prefix() {
        return Component.literal("[도전과제] ").withStyle(ChatFormatting.GOLD);
    }

    private static MutableComponent title(DisplayInfo d) {
        return d.getTitle().copy().withStyle(ChatFormatting.YELLOW);
    }

    private static String name(ServerPlayer p) {
        return p.getGameProfile().name();
    }

    private static void broadcast(MinecraftServer server, Component message) {
        server.getPlayerList().broadcastSystemMessage(message, false);
    }

    private static void showTitle(ServerPlayer p, Component title, Component subtitle) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        p.connection.send(new ClientboundSetTitleTextPacket(title));
        p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
    }
}
