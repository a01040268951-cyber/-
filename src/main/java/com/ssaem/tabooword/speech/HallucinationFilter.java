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

    /**
     * 반복 폭주 정리: "너 뭐하니? 너 뭐하니? 너 뭐하니?..." → "너 뭐하니?",
     * "지금 지금 지금 지금" → "지금 지금".
     */
    static String collapseRepeats(String text) {
        String[] words = text.split(" ");
        int len = words.length;
        String[] key = new String[len];
        for (int i = 0; i < len; i++) {
            key[i] = words[i].replaceAll("[.,!?~…]", "");
        }
        // 문장 단위 반복: 앞 p개 단어가 끝까지 되풀이되면 한 번만 남김
        for (int p = 2; p <= len / 2; p++) {
            boolean periodic = true;
            for (int k = p; k < len && periodic; k++) {
                periodic = key[k].equals(key[k - p]) || (k == len - 1 && key[k - p].startsWith(key[k]));
            }
            if (periodic) {
                return String.join(" ", java.util.Arrays.copyOf(words, p));
            }
        }
        // 단어 반복: 세 번 이상은 두 번까지만
        StringBuilder sb = new StringBuilder();
        int run = 0;
        for (int i = 0; i < len; i++) {
            run = (i > 0 && key[i].equals(key[i - 1])) ? run + 1 : 1;
            if (run <= 2) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(words[i]);
            }
        }
        return sb.toString();
    }
}
