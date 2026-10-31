package com.ssaem.tabooword.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** 내 PC 전용 설정 (.minecraft/config/tabooword-client.toml) */
public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> MODEL = BUILDER
            .comment("음성 인식 모델: \"base\" (약 60MB, 빠름) 또는 \"small\" (약 190MB, 더 정확하지만 느림)",
                    "바꾼 뒤에는 게임을 다시 시작하세요.")
            .define("model", "base", o -> "base".equals(o) || "small".equals(o));

    public static final ModConfigSpec.IntValue THREADS = BUILDER
            .comment("음성 인식에 쓸 CPU 스레드 수. 0 = 자동 (코어 수의 절반, 최대 4)")
            .defineInRange("threads", 0, 0, 16);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {
    }
}
