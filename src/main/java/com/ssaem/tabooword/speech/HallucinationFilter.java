package com.ssaem.tabooword.speech;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Whisper가 잡음·무음에서 자주 지어내는 문구와 효과음 표기를 걸러낸다.
 * (유튜브 자막으로 학습돼서 "시청해 주셔서 감사합니다" 같은 말이 튀어나옴)
 */
public final class HallucinationFilter {
    private static final Pattern BRACKETS = Pattern.compile("[\\[(（【♪*][^\\])）】♪*]*[\\])）】♪*]?");
    private static final List<String> PHRASES = List.of(
            "시청해 주셔서", "시청해주셔서", "시청 해주셔서",
            "구독과 좋아요", "구독 좋아요", "구독 부탁",
            "자막 제공", "자막 by", "한글자막", "MBC 뉴스", "KBS 뉴스", "SBS 뉴스",
            "다음 영상에서", "다음 시간에 만나요", "Thank you for watching");

    private HallucinationFilter() {
    }

    public static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String text = BRACKETS.matcher(raw).replaceAll(" ").replaceAll("\\s+", " ").trim();
        for (String p : PHRASES) {
            if (text.contains(p)) {
                return "";
            }
        }
        // 한글이 하나도 없으면 (영어 환각, 기호뿐) 무시
        boolean hasHangul = false;
        for (int i = 0; i < text.length() && !hasHangul; i++) {
            char c = text.charAt(i);
            hasHangul = (c >= 0xAC00 && c <= 0xD7A3) || (c >= 0x3131 && c <= 0x3163);
        }
        return hasHangul ? collapseRepeats(text) : "";
    }

    /** "지금 지금 지금 지금..." 같은 반복 폭주를 두 번까지만 남긴다. */
    static String collapseRepeats(String text) {
        String[] words = text.split(" ");
        StringBuilder sb = new StringBuilder();
        String prev = null;
        int run = 0;
        for (String w : words) {
            run = w.equals(prev) ? run + 1 : 1;
            prev = w;
            if (run <= 2) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(w);
            }
        }
        return sb.toString();
    }
}
