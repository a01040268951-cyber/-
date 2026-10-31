package com.ssaem.tabooword.speech;

import java.util.Collection;

import com.ssaem.tabooword.HangulLetters;

/**
 * 인식 결과로 사망 여부를 판정한다.
 *  - 최종 결과에 금지 자모 → 사망
 *  - 중간 결과: 마지막 단어는 말하는 도중 잘렸을 수 있으므로, 그 앞 단어에서 걸렸거나
 *    두 번 연속 중간 결과에서 걸렸을 때 사망
 */
public final class TabooJudge {
    private boolean lastInterimHit;

    /** @return 사망 처리할 문장, 아니면 null */
    public String judge(String text, boolean isFinal, Collection<? extends String> banned) {
        int idx = HangulLetters.findViolation(text, banned);
        if (isFinal) {
            lastInterimHit = false;
            return idx >= 0 ? text : null;
        }
        if (idx < 0) {
            lastInterimHit = false;
            return null;
        }
        int lastWordStart = text.lastIndexOf(' ') + 1;
        if (idx < lastWordStart || lastInterimHit) {
            lastInterimHit = false;
            return text;
        }
        lastInterimHit = true;
        return null;
    }

    public void reset() {
        lastInterimHit = false;
    }
}
