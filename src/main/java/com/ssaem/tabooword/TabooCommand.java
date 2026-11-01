package com.ssaem.tabooword;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * /taboo            현재 상태 보기
 * /taboo on | off   켜기 / 끄기 (OP)
 * /taboo add ㄱ     금지 자모 추가 (OP)
 * /taboo remove ㄱ  금지 자모 빼기 (OP)
 * /taboo challenge          모두에게 같은 랜덤 도전과제 (OP)
 * /taboo challenge each     각자 다른 랜덤 도전과제 (OP)
 * /taboo challenge show     지금 도전과제 보기
 * /taboo challenge stop     도전과제 취소 (OP)
 */
public final class TabooCommand {
    private TabooCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("taboo")
                .executes(TabooCommand::status)
                .then(Commands.literal("status").executes(TabooCommand::status))
                .then(Commands.literal("on")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> setEnabled(ctx, true)))
                .then(Commands.literal("off")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> setEnabled(ctx, false)))
                .then(Commands.literal("add")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("letter", StringArgumentType.greedyString())
                                .executes(ctx -> add(ctx, StringArgumentType.getString(ctx, "letter").trim()))))
                .then(Commands.literal("challenge")
                        .executes(TabooCommand::challengeShared)
                        .then(Commands.literal("each")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(TabooCommand::challengeEach))
                        .then(Commands.literal("show").executes(TabooCommand::challengeShow))
                        .then(Commands.literal("stop")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(ctx -> {
                                    Challenges.stop(ctx.getSource().getServer());
                                    return 1;
                                })))
                .then(Commands.literal("remove")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("letter", StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(TabooConfig.letters().stream().map(String::valueOf), builder))
                                .executes(ctx -> remove(ctx, StringArgumentType.getString(ctx, "letter").trim())))));
    }

    private static int challengeShared(CommandContext<CommandSourceStack> ctx) {
        if (!Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(ctx.getSource())) {
            return challengeShow(ctx); // OP가 아니면 보기만
        }
        if (Challenges.drawShared(ctx.getSource().getServer()) == 0) {
            ctx.getSource().sendFailure(Component.literal("뽑을 도전과제가 없어요. (모두 이미 달성)"));
            return 0;
        }
        return 1;
    }

    private static int challengeEach(CommandContext<CommandSourceStack> ctx) {
        if (Challenges.drawEach(ctx.getSource().getServer()) == 0) {
            ctx.getSource().sendFailure(Component.literal("뽑을 도전과제가 없어요. (모두 이미 달성)"));
            return 0;
        }
        return 1;
    }

    private static int challengeShow(CommandContext<CommandSourceStack> ctx) {
        for (Component line : Challenges.describe(ctx.getSource().getServer())) {
            ctx.getSource().sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        boolean on = TabooConfig.ENABLED.getAsBoolean();
        List<? extends String> letters = TabooConfig.letters();
        Component msg = Component.literal("[금기어] ").withStyle(ChatFormatting.RED)
                .append(Component.literal(on ? "켜짐" : "꺼짐").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                .append(Component.literal(" · 금지 자모: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(letters.isEmpty() ? "(없음)" : String.join(" ", letters)).withStyle(ChatFormatting.YELLOW));
        ctx.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    private static int setEnabled(CommandContext<CommandSourceStack> ctx, boolean on) {
        TabooConfig.ENABLED.set(on);
        TabooConfig.SPEC.save();
        TabooNetwork.broadcastStatus(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("[금기어] " + (on ? "켜졌습니다" : "꺼졌습니다"))
                .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY), true);
        return 1;
    }

    private static int add(CommandContext<CommandSourceStack> ctx, String letter) {
        if (!HangulLetters.isLetter(letter)) {
            ctx.getSource().sendFailure(Component.literal("한글 자모 한 글자만 넣어 주세요. 예: /taboo add ㄱ"));
            return 0;
        }
        List<String> letters = new ArrayList<>(TabooConfig.letters());
        if (letters.contains(letter)) {
            ctx.getSource().sendFailure(Component.literal(letter + " 은(는) 이미 금지 목록에 있어요."));
            return 0;
        }
        letters.add(letter);
        TabooConfig.BANNED_LETTERS.set(letters);
        TabooConfig.SPEC.save();
        TabooNetwork.broadcastStatus(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("[금기어] 추가: " + letter + " → 금지 자모: " + String.join(" ", letters))
                .withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> ctx, String letter) {
        List<String> letters = new ArrayList<>(TabooConfig.letters());
        if (!letters.remove(letter)) {
            ctx.getSource().sendFailure(Component.literal(letter + " 은(는) 금지 목록에 없어요."));
            return 0;
        }
        TabooConfig.BANNED_LETTERS.set(letters);
        TabooConfig.SPEC.save();
        TabooNetwork.broadcastStatus(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("[금기어] 제거: " + letter + " → 금지 자모: "
                + (letters.isEmpty() ? "(없음)" : String.join(" ", letters))).withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }
}
