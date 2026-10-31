package com.ssaem.tabooword.speech;

import java.util.Collection;

import com.ssaem.tabooword.HangulLetters;

/**
 * 인식 결과로 사망 여부를 판정한다.
 *  - 최종 결과에 금지 자모 → 사망
 *  - 중간 결과: 3글자 이상 인식됐고 걸린 글자 뒤에 글자가 더 있으면 확정 → 사망.
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
        int syllables = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0xAC00 && c <= 0xD7A3) {
                syllables++;
                moreAfter |= i > idx;
            }
        }
        // 2글자 이하의 짧은 중간 결과는 자주 틀린다 (예: "저기" → "정기") → 한 번 더 확인
        if ((moreAfter && syllables >= 3) || lastInterimHit) {
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
