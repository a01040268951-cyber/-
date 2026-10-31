package com.ssaem.tabooword;

import java.util.Collection;

/**
 * 음성 인식의 중간 결과는 잠깐 틀렸다가 고쳐지는 일이 많다 (예: "감사원" → "감사합니다").
 * 그래서 금지 자모가 든 부분이 HOLD_MS 동안 그대로 유지되거나, 문장이 끝난 최종 결과에 있을 때만 판정한다.
 */
public final class TriggerGate {
    public static final long HOLD_MS = 400;

    private String pendingPrefix;
    private long pendingSince;

    /** @return 사망 처리할 문장, 아니면 null */
    public String offer(String text, boolean isFinal, long nowMs, Collection<? extends String> banned) {
        int idx = HangulLetters.findViolation(text, banned);
        if (idx < 0) {
            pendingPrefix = null;
            return null;
        }
        if (isFinal) {
            pendingPrefix = null;
            return text;
        }
        String prefix = text.substring(0, idx + 1);
        if (pendingPrefix == null || !text.startsWith(pendingPrefix)) {
            pendingPrefix = prefix;
            pendingSince = nowMs;
            return null;
        }
        if (nowMs - pendingSince >= HOLD_MS) {
            pendingPrefix = null;
            return text;
        }
        return null;
    }

    public void reset() {
        pendingPrefix = null;
    }
}
