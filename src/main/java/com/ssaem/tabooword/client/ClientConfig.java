package com.ssaem.tabooword.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** 내 PC 전용 설정 (.minecraft/config/tabooword-client.toml) */
public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> ENGINE = BUILDER
            .comment("음성 인식 엔진:",
                    "  \"fast\"     (기본) 말하는 즉시 글자가 나옴 (0.1초 단위 스트리밍), 모델 약 130MB",
                    "  \"accurate\" Whisper. 글자가 조금 더 정확하지만 1초쯤 늦음",
                    "바꾼 뒤에는 월드에 다시 들어가세요.")
            .define("engine", "fast", o -> "fast".equals(o) || "accurate".equals(o));

    public static final ModConfigSpec.ConfigValue<String> MODEL = BUILDER
            .comment("(accurate 엔진일 때) Whisper 모델: \"small\" (기본, 약 190MB, 정확) 또는 \"base\" (약 60MB, 느린 PC용으로 더 빠름)",
                    "바꾼 뒤에는 게임을 다시 시작하세요.")
            .define("model", "small", o -> "base".equals(o) || "small".equals(o));

    public static final ModConfigSpec.IntValue THREADS = BUILDER
            .comment("음성 인식에 쓸 CPU 스레드 수. 0 = 자동 (코어 수의 절반, 최대 4)")
            .defineInRange("threads", 0, 0, 16);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {
    }
}
