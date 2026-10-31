package com.ssaem.tabooword;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 월드별 서버 설정 (saves/월드이름/serverconfig/tabooword-server.toml).
 * /taboo 명령어로도 바꿀 수 있다.
 */
public final class TabooConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("금기어 모드 켜기/끄기 (true = 켜짐)")
            .define("enabled", true);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> BANNED_LETTERS = BUILDER
            .comment("금지할 한글 자모 목록. 이 자모가 들어간 글자를 채팅에 치면 즉사.",
                    "예: [\"ㅇ\"]  /  [\"ㅇ\", \"ㄱ\"]")
            .defineListAllowEmpty("bannedLetters", List.of("ㅇ"), () -> "ㅇ", HangulLetters::isLetter);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private TabooConfig() {
    }

    public static List<? extends String> letters() {
        return BANNED_LETTERS.get();
    }
}
