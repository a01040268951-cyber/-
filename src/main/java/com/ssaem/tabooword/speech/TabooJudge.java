package com.ssaem.tabooword.speech;

import java.util.Collection;

import com.ssaem.tabooword.HangulLetters;

/**
 * 인식 결과로 사망 여부를 판정한다.
 *  - 최종 결과에 금지 자모 → 사망
 *  - 중간 결과: 걸린 글자 뒤에 한글이 더 인식됐으면 그 글자는 확정 → 사망.
 *    맨 끝 글자에서 걸렸으면 (말이 잘렸을 수 있음, 예: "감사원") 다음 결과에서도 걸려야 사망
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
        boolean moreAfter = false;
        for (int i = idx + 1; i < text.length() && !moreAfter; i++) {
            char c = text.charAt(i);
            moreAfter = c >= 0xAC00 && c <= 0xD7A3;
        }
        if (moreAfter || lastInterimHit) {
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
