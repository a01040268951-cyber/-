package com.ssaem.tabooword;

import java.util.List;

/** 클라이언트가 서버에게서 받은 게임 상태. 서버가 모드를 안 쓰면 계속 꺼져 있다. */
public final class TabooStatus {
    private static volatile boolean enabled = false;
    private static volatile List<String> letters = List.of();

    private TabooStatus() {
    }

    public static void set(boolean on, List<String> banned) {
        letters = List.copyOf(banned);
        enabled = on;
    }

    public static void reset() {
        set(false, List.of());
    }

    public static boolean enabled() {
        return enabled;
    }

    public static List<String> letters() {
        return letters;
    }
}
