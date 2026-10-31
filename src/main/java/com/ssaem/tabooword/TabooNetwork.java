package com.ssaem.tabooword;

import java.util.Arrays;
import java.util.List;

import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 서버 → 클라이언트: 게임 켜짐 여부와 금지 자모 (StatusPayload)
 * 클라이언트 → 서버: 마이크로 인식된 문장 (SpokePayload)
 */
public final class TabooNetwork {
    private static final int MAX_TEXT = 200;

    private TabooNetwork() {
    }

    public record StatusPayload(boolean enabled, String letters) implements CustomPacketPayload {
        public static final Type<StatusPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(TabooWord.MODID, "status"));
        public static final StreamCodec<ByteBuf, StatusPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, StatusPayload::enabled,
                ByteBufCodecs.STRING_UTF8, StatusPayload::letters,
                StatusPayload::new);

        @Override
        public Type<StatusPayload> type() {
            return TYPE;
        }
    }

    public record SpokePayload(String text) implements CustomPacketPayload {
        public static final Type<SpokePayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(TabooWord.MODID, "spoke"));
        public static final StreamCodec<ByteBuf, SpokePayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, SpokePayload::text,
                SpokePayload::new);

        @Override
        public Type<SpokePayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToClient(StatusPayload.TYPE, StatusPayload.STREAM_CODEC, TabooNetwork::onStatus);
        registrar.playToServer(SpokePayload.TYPE, SpokePayload.STREAM_CODEC, TabooNetwork::onSpoke);
    }

    // ---------- 클라이언트에서 실행 (클라이언트 전용 클래스는 쓰지 않음) ----------

    private static void onStatus(StatusPayload payload, IPayloadContext context) {
        TabooStatus.set(payload.enabled(), payload.letters().isEmpty()
                ? List.of()
                : Arrays.asList(payload.letters().split(",")));
    }

    // ---------- 서버에서 실행 ----------

    private static void onSpoke(SpokePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!TabooConfig.ENABLED.getAsBoolean() || !player.isAlive() || player.isSpectator()) {
            return;
        }
        String text = payload.text();
        if (text.length() > MAX_TEXT) {
            text = text.substring(0, MAX_TEXT);
        }
        int index = HangulLetters.findViolation(text, TabooConfig.letters());
        if (index < 0) {
            return; // 서버 기준으로 다시 검사해서 아니면 무시
        }

        MutableComponent notice = Component.literal("[금기어] ").withStyle(ChatFormatting.RED)
                .append(Component.literal(player.getGameProfile().name()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" 사망 — \"").withStyle(ChatFormatting.RED))
                .append(Component.literal(text.substring(0, index)).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(text.substring(index, index + 1)).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(text.substring(index + 1)).withStyle(ChatFormatting.GRAY))
                .append(Component.literal("\"").withStyle(ChatFormatting.RED));
        player.level().getServer().getPlayerList().broadcastSystemMessage(notice, false);

        ServerLevel level = (ServerLevel) player.level();
        player.hurtServer(level, player.damageSources().genericKill(), Float.MAX_VALUE);
        TabooWord.LOGGER.info("[tabooword] {} said \"{}\" and died", player.getGameProfile().name(), text);
    }

    public static StatusPayload currentStatus() {
        return new StatusPayload(TabooConfig.ENABLED.getAsBoolean(), String.join(",", TabooConfig.letters()));
    }

    public static void sendStatus(ServerPlayer player) {
        if (player.connection != null && player.connection.hasChannel(StatusPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, currentStatus());
        }
    }

    public static void broadcastStatus(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendStatus(player);
        }
    }
}
